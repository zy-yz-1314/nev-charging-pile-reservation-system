package com.powerqueue.vo;

import lombok.Data;

/**
 * 等待队列预估结果(L1)。
 */
@Data
public class QueueEstimateVO {

    /** 充电桩 ID */
    private Long pileId;
    /** 我前面还有几人 */
    private Long aheadCount;
    /** 我的排名(0 起,不在队内为 -1) */
    private Long myRank;
    /** 预估等待时间(分钟) */
    private Integer estimateWaitMin;
    /** 单桩平均充电时长(分钟,预估依据) */
    private Integer avgDurationMin;
    /** 队列态:QUEUED / WAITING_CONFIRM */
    private String queueState;
}
