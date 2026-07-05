import request from './request'

/** 生成充电计划(L3 向导) */
export const createPlanApi = (dto) => request.post('/charge-plans', dto)
/** 我的计划列表 */
export const myPlansApi = () => request.get('/charge-plans')
/** 启停/修改时刻 */
export const updatePlanApi = (id, enabled, chargeTime) =>
  request.put(`/charge-plans/${id}`, null, { params: { enabled, chargeTime } })
/** 删除计划 */
export const deletePlanApi = (id) => request.delete(`/charge-plans/${id}`)
