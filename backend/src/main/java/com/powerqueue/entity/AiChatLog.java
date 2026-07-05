package com.powerqueue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LLM 助手对话日志(L4),用于审计与效果分析。
 * recommend_snapshot 记录阶段二注入平台的真实数据快照,确保推荐可追溯、可复核(防幻觉)。
 */
@Data
@TableName("ai_chat_log")
public class AiChatLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String sessionId;
    private String userQuery;
    /** 阶段一槽位提取结果(JSON) */
    private String extractedSlots;
    /** 阶段二注入的真实数据快照(JSON) */
    private String recommendSnapshot;
    private String aiReply;
    /** 1 已降级(熔断/超时) */
    private Integer degraded;
    private Integer latencyMs;
    private LocalDateTime createTime;
}
