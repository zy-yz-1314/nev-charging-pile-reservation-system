package com.powerqueue.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 大模型统一调用 SDK(L4)。
 *
 * <p>封装为单一入口,便于在调用方加超时/熔断/降级。使用 {@link RestClient} 调用
 * OpenAI 兼容的 /chat/completions 接口。api-key 未配置时抛异常,由上层降级处理。
 */
@Slf4j
@Component
public class DeepSeekClient {

    private final DeepSeekProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(DeepSeekProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int connectMs = 2000;
        int readMs = Math.max(props.getStage1TimeoutMs(), props.getStage2TimeoutMs());
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        this.restClient = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** API key 是否已配置(未配置时助手降级) */
    public boolean isAvailable() {
        return props.getApiKey() != null && !props.getApiKey().isBlank();
    }

    /**
     * 发送对话。{@code json=true} 时请求 JSON 对象输出。
     *
     * @param messages [{role,content}, ...]
     * @return 模型回复文本(通常是 JSON 字符串)
     */
    @SuppressWarnings("unchecked")
    public String chat(List<Map<String, String>> messages, boolean json) {
        if (!isAvailable()) {
            throw new IllegalStateException("DeepSeek API key 未配置");
        }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("model", props.getModel());
        body.put("messages", messages);
        body.put("temperature", 0.3);
        if (json) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        Map<String, Object> resp;
        try {
            resp = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("DeepSeek 调用失败: {}", e.getMessage());
            throw new IllegalStateException("DeepSeek 调用失败", e);
        }
        if (resp == null) {
            throw new IllegalStateException("DeepSeek 返回空");
        }
        List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("DeepSeek 无 choices");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return String.valueOf(message.get("content"));
    }

    /** 解析模型回复为 JSON 节点(阶段输出为 JSON 对象时使用) */
    public JsonNode parseJson(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception e) {
            throw new IllegalStateException("DeepSeek 输出解析失败: " + content, e);
        }
    }
}
