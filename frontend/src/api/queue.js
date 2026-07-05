import request from './request'

/** 加入等待队列 */
export const enqueueApi = (pileId) => request.post('/queues', { pileId })
/** 查询排队预估 */
export const queueEstimateApi = (pileId) => request.get(`/queues/${pileId}/estimate`)
/** 确认占位 */
export const queueConfirmApi = (pileId) => request.post(`/queues/${pileId}/confirm`)
/** 退出排队 */
export const queueLeaveApi = (pileId) => request.delete(`/queues/${pileId}`)
