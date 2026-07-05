package com.powerqueue.service;

import com.powerqueue.vo.QueueEstimateVO;

/**
 * 充电桩等待队列服务(L1)。
 * 基于 Redis Sorted Set 实现优先级排队,与 DB 乐观锁状态流转解耦。
 */
public interface QueueService {

    /** 用户进入某桩等待队列,返回排队位置与预估等待 */
    QueueEstimateVO enqueue(Long userId, Long pileId);

    /** 查询某桩排队预估(若当前用户已在队内,返回其真实排名;否则返回入队后的预估值) */
    QueueEstimateVO estimate(Long userId, Long pileId);

    /** 预估某桩排队等待时长(分钟);空闲或无队返回 0 */
    int estimateWaitMinutes(Long pileId);

    /** 我在某桩的排名(0 起);不在队内返回 -1 */
    long myRank(Long pileId, Long userId);

    /** 取队首并移除;返回队首 userId,空队返回 null(队首轮换用) */
    Long popHead(Long pileId);

    /** 确认占位:轮到后 10 分钟窗口内调用,成功返回生成的预约单号/订单 id */
    Long confirm(Long userId, Long pileId);

    /** 退出排队 */
    void leave(Long userId, Long pileId);
}
