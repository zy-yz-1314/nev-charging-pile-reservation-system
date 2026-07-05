package com.powerqueue.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.powerqueue.ai.DeepSeekClient;
import com.powerqueue.ai.DeepSeekProperties;
import com.powerqueue.common.BusinessException;
import com.powerqueue.common.ResultCode;
import com.powerqueue.common.UserContext;
import com.powerqueue.dto.AiChatDTO;
import com.powerqueue.dto.RecommendDTO;
import com.powerqueue.entity.AiChatLog;
import com.powerqueue.mapper.AiChatLogMapper;
import com.powerqueue.service.AiAssistantService;
import com.powerqueue.service.RecommendService;
import com.powerqueue.vo.AiChatVO;
import com.powerqueue.vo.PileScoreVO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * LLM 助手两阶段编排实现(L4)。
 *
 * <p>核心思想:<b>大模型只组织语言,不产生数据</b>,从架构上杜绝幻觉。
 * <ol>
 *   <li>阶段一:DeepSeek 提取意图/槽位 {location, urgency, targetEnergy, carPowerKW};</li>
 *   <li>中间:后端用槽位串联平台真实接口(推荐 → 真实距离/等待/动态价);</li>
 *   <li>阶段二:把真实数据 + 用户诉求喂给 DeepSeek,仅做自然语言组织 + 桩 id 列表;</li>
 *   <li>校验:回填 pileId 必须来自候选集,防模型塞入不存在的桩。</li>
 * </ol>
 * 任一阶段失败 / 熔断 → 降级返回智能匹配结构化列表(degraded=true,不报错)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantServiceImpl implements AiAssistantService {

    private static final String RATE_PREFIX = "ai:rate:";

    private final DeepSeekClient deepSeekClient;
    private final DeepSeekProperties deepSeekProperties;
    private final ObjectMapper objectMapper;
    private final RecommendService recommendService;
    private final AiChatLogMapper aiChatLogMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public AiChatVO chat(AiChatDTO dto) {
        Long userId = UserContext.getUserId();
        checkRateLimit(userId);

        // DeepSeek 不可用直接降级(不进入熔断统计)
        if (!deepSeekClient.isAvailable()) {
            return degrade(dto, userId, "DeepSeek API key 未配置");
        }
        // 熔断器保护两阶段调用
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("deepseek");
        try {
            return cb.executeSupplier(() -> doChat(dto, userId));
        } catch (Exception e) {
            log.warn("AI 助手降级 userId={}: {}", userId, e.getMessage());
            return degrade(dto, userId, e.getMessage());
        }
    }

    // ============ 两阶段编排 ============

    private AiChatVO doChat(AiChatDTO dto, Long userId) {
        long start = System.currentTimeMillis();

        JsonNode slots = extractSlots(dto.getQuery());
        List<PileScoreVO> recs = gatherRecommendations(dto, slots);
        String snapshot = buildSnapshot(recs);
        JsonNode replyNode = composeReply(dto.getQuery(), slots, snapshot);

        String text = replyNode.path("reply").asText("");
        List<PileScoreVO> result = filterByReply(recs, replyNode.path("pileIds"));

        int latency = (int) (System.currentTimeMillis() - start);
        AiChatVO vo = new AiChatVO();
        vo.setSessionId(dto.getSessionId());
        vo.setReply(text.isBlank() ? defaultReply(recs) : text);
        vo.setPiles(result);
        vo.setDegraded(false);
        vo.setLatencyMs(latency);
        saveLog(userId, dto, slots, snapshot, text, false, latency);
        return vo;
    }

    /** 阶段一:意图/槽位提取 */
    private JsonNode extractSlots(String query) {
        String system = "你是充电调度助手的意图识别模块。从用户输入提取结构化参数,"
                + "严格输出 JSON,字段:location(字符串,地名/位置描述),"
                + "urgency(布尔,是否着急赶时间),targetEnergy(数字或null,目标充电电量度数),"
                + "carPowerKW(数字或null,车型支持充电功率)。不要输出任何多余内容。";
        return callJson(List.of(
                msg("system", system),
                msg("user", query)));
    }

    /** 阶段二:语言组织(仅依据真实数据,禁止编造数字) */
    private JsonNode composeReply(String query, JsonNode slots, String snapshot) {
        String system = "你是充电调度推荐助手。仅依据下方【平台实时数据】组织一段简洁友好的自然语言推荐,"
                + "向用户说明最推荐哪几个桩、距离、预计等待、动态电价。"
                + "禁止编造任何数字,所有数据必须来自提供的快照。"
                + "输出 JSON:{\"reply\":推荐文案字符串,\"pileIds\":[推荐桩ID数字数组]},"
                + "pileIds 必须且只能来自快照中的 pileId。";
        String user = "用户诉求:" + query + "\n槽位:" + safeText(slots)
                + "\n平台实时数据快照:" + snapshot;
        return callJson(List.of(
                msg("system", system),
                msg("user", user)));
    }

    private JsonNode callJson(List<Map<String, String>> messages) {
        String content = deepSeekClient.chat(messages, true);
        return deepSeekClient.parseJson(content);
    }

    // ============ 真实数据编排(平台接口,非模型产出) ============

    private List<PileScoreVO> gatherRecommendations(AiChatDTO dto, JsonNode slots) {
        if (dto.getLng() == null || dto.getLat() == null) {
            return List.of();
        }
        try {
            return recommendService.recommend(buildRecDto(dto, slots));
        } catch (Exception e) {
            log.warn("AI 编排推荐失败: {}", e.getMessage());
            return List.of();
        }
    }

    private RecommendDTO buildRecDto(AiChatDTO dto, JsonNode slots) {
        RecommendDTO r = new RecommendDTO();
        r.setLng(dto.getLng());
        r.setLat(dto.getLat());
        double carPower = firstPositive(dto.getCarPowerKW(),
                slotDouble(slots, "carPowerKW"), 60.0);
        r.setCarPowerKW(BigDecimal.valueOf(carPower));
        BigDecimal te = dto.getTargetEnergy() != null ? dto.getTargetEnergy() : slotDecimal(slots, "targetEnergy");
        r.setTargetEnergy(te);
        r.setTopN(5);
        r.setProfile(slotBool(slots, "urgency") ? "urgent" : "default");
        return r;
    }

    private String buildSnapshot(List<PileScoreVO> recs) {
        try {
            ArrayNode arr = objectMapper.createArrayNode();
            for (PileScoreVO p : recs) {
                arr.addObject()
                        .put("pileId", p.getPileId())
                        .put("stationName", p.getStationName())
                        .put("pileNo", p.getPileNo())
                        .put("distanceKm", p.getDistanceKm() != null ? p.getDistanceKm().doubleValue() : 0)
                        .put("waitMin", p.getWaitMin() != null ? p.getWaitMin() : 0)
                        .put("finalPrice", p.getFinalPrice() != null ? p.getFinalPrice().doubleValue() : 0)
                        .put("score", p.getScore() != null ? p.getScore().doubleValue() : 0);
            }
            return objectMapper.writeValueAsString(arr);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 校验回填 pileId 必须在候选集内,防模型塞入不存在的桩;过滤为空则返回原候选 */
    private List<PileScoreVO> filterByReply(List<PileScoreVO> recs, JsonNode pileIdsNode) {
        if (recs.isEmpty() || pileIdsNode == null || !pileIdsNode.isArray()) {
            return recs;
        }
        Set<Long> valid = recs.stream().map(PileScoreVO::getPileId).collect(Collectors.toSet());
        List<Long> picked = new ArrayList<>();
        for (JsonNode n : pileIdsNode) {
            if (n.isNumber()) {
                long id = n.asLong();
                if (valid.contains(id)) {
                    picked.add(id);
                }
            }
        }
        if (picked.isEmpty()) {
            return recs;
        }
        return recs.stream().filter(p -> picked.contains(p.getPileId())).collect(Collectors.toList());
    }

    private String defaultReply(List<PileScoreVO> recs) {
        if (recs.isEmpty()) {
            return "附近暂未找到合适的充电桩,请稍后再试或扩大范围。";
        }
        PileScoreVO top = recs.get(0);
        return "为您推荐最近的 " + top.getStationName() + " " + top.getPileNo() + " 号桩,"
                + "距离约 " + top.getDistanceKm() + " km,预计等待 " + top.getWaitMin() + " 分钟。";
    }

    // ============ 降级 ============

    private AiChatVO degrade(AiChatDTO dto, Long userId, String reason) {
        long start = System.currentTimeMillis();
        List<PileScoreVO> recs;
        try {
            recs = recommendService.recommend(buildRecDto(dto, null));
        } catch (Exception e) {
            recs = List.of();
        }
        int latency = (int) (System.currentTimeMillis() - start);
        AiChatVO vo = new AiChatVO();
        vo.setSessionId(dto.getSessionId());
        vo.setReply("智能助手暂时不可用,已为您切换到智能匹配列表");
        vo.setPiles(recs);
        vo.setDegraded(true);
        vo.setLatencyMs(latency);
        saveLog(userId, dto, null, null, "DEGRADED: " + reason, true, latency);
        return vo;
    }

    // ============ 限流 + 日志 ============

    private void checkRateLimit(Long userId) {
        String key = RATE_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        if (count != null && count > deepSeekProperties.getRateLimitPerMinute()) {
            throw new BusinessException(ResultCode.AI_RATE_LIMITED);
        }
    }

    private void saveLog(Long userId, AiChatDTO dto, JsonNode slots, String snapshot,
                         String reply, boolean degraded, int latencyMs) {
        try {
            AiChatLog log = new AiChatLog();
            log.setUserId(userId);
            log.setSessionId(dto.getSessionId() == null ? "anon" : dto.getSessionId());
            log.setUserQuery(dto.getQuery());
            log.setExtractedSlots(slots == null ? null : slots.toString());
            log.setRecommendSnapshot(snapshot);
            log.setAiReply(reply);
            log.setDegraded(degraded ? 1 : 0);
            log.setLatencyMs(latencyMs);
            aiChatLogMapper.insert(log);
        } catch (Exception e) {
            AiAssistantServiceImpl.log.warn("AI 日志落库失败: {}", e.getMessage());
        }
    }

    // ============ 小工具 ============

    private Map<String, String> msg(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    private String safeText(JsonNode node) {
        return node == null ? "{}" : node.toString();
    }

    private boolean slotBool(JsonNode slots, String field) {
        return slots != null && slots.path(field).asBoolean(false);
    }

    private double slotDouble(JsonNode slots, String field) {
        if (slots == null) {
            return 0;
        }
        JsonNode n = slots.path(field);
        return n.isNumber() ? n.asDouble() : 0;
    }

    private BigDecimal slotDecimal(JsonNode slots, String field) {
        if (slots == null) {
            return null;
        }
        JsonNode n = slots.path(field);
        return n.isNumber() ? BigDecimal.valueOf(n.asDouble()) : null;
    }

    private double firstPositive(BigDecimal a, double b, double fallback) {
        if (a != null && a.doubleValue() > 0) {
            return a.doubleValue();
        }
        if (b > 0) {
            return b;
        }
        return fallback;
    }
}
