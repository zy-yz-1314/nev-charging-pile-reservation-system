package com.powerqueue.vo;

import lombok.Data;

import java.util.List;

/**
 * LLM 助手回复(L4):自然语言推荐文案 + 可直接跳转充电的桩列表。
 * degraded=true 表示大模型不可用,已降级为智能匹配结构化列表。
 */
@Data
public class AiChatVO {

    private String sessionId;
    /** 自然语言推荐文案 */
    private String reply;
    /** 推荐桩(数据全部来自平台实时接口,非模型编造) */
    private List<PileScoreVO> piles;
    /** 是否降级 */
    private Boolean degraded;
    /** 总耗时(毫秒) */
    private Integer latencyMs;
}
