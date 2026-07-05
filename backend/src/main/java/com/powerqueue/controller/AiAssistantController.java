package com.powerqueue.controller;

import com.powerqueue.common.Result;
import com.powerqueue.dto.AiChatDTO;
import com.powerqueue.service.AiAssistantService;
import com.powerqueue.vo.AiChatVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 自然语言助手接口(L4,车主端,需登录)。
 * 用户口语化充电需求 → 两阶段编排 → 自然语言推荐 + 可跳转桩列表;
 * 大模型异常时熔断降级为智能匹配列表(degraded=true)。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    /** 自然语言对话 */
    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody @Valid AiChatDTO dto) {
        return Result.success("智能助手回复", aiAssistantService.chat(dto));
    }
}
