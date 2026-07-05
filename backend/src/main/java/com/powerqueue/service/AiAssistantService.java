package com.powerqueue.service;

import com.powerqueue.dto.AiChatDTO;
import com.powerqueue.vo.AiChatVO;

/**
 * LLM 自然语言助手服务(L4)。
 * 两阶段编排:意图/槽位提取 → 串联平台实时接口 → 语言组织输出。
 * 大模型不可用时熔断降级为智能匹配列表。
 */
public interface AiAssistantService {

    /** 自然语言对话 */
    AiChatVO chat(AiChatDTO dto);
}
