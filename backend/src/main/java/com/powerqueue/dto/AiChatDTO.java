package com.powerqueue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * LLM 自然语言助手入参(L4)。
 * 用户口语化充电需求,如「我人在国贸,着急,现在去哪最快充上」。
 */
@Data
public class AiChatDTO {

    /** 会话 ID(多轮上下文预留) */
    private String sessionId;

    /** 用户口语输入 */
    @NotBlank(message = "提问内容不能为空")
    private String query;

    /** 用户实时位置(APP 端 GPS,用于真实算路与推荐) */
    private BigDecimal lng;
    private BigDecimal lat;

    /** 车型支持功率(kW) */
    private BigDecimal carPowerKW;

    /** 目标充电电量(度) */
    private BigDecimal targetEnergy;
}
