import request from './request'

// ===== 数据看板 =====
export const dashboardApi = () => request.get('/admin/dashboard')

// ===== 充电站 =====
export const adminStationPageApi = (params) => request.get('/admin/stations', { params })
export const adminStationAddApi = (data) => request.post('/admin/stations', data)
export const adminStationUpdateApi = (id, data) => request.put(`/admin/stations/${id}`, data)
export const adminStationDeleteApi = (id) => request.delete(`/admin/stations/${id}`)

// ===== 充电桩 =====
export const adminPilePageApi = (params) => request.get('/admin/piles', { params })
export const adminPileAddApi = (data) => request.post('/admin/piles', data)
export const adminPileUpdateApi = (id, data) => request.put(`/admin/piles/${id}`, data)
export const adminPileDeleteApi = (id) => request.delete(`/admin/piles/${id}`)

// ===== 用户 =====
export const adminUserPageApi = (params) => request.get('/admin/users', { params })
export const adminUserAddApi = (data) => request.post('/admin/users', data)
export const adminUserUpdateApi = (id, data) => request.put(`/admin/users/${id}`, data)
export const adminUserStatusApi = (id, status) =>
  request.put(`/admin/users/${id}/status`, null, { params: { status } })
export const adminUserResetPwdApi = (id) => request.put(`/admin/users/${id}/reset-password`)
export const adminUserDeleteApi = (id) => request.delete(`/admin/users/${id}`)

// ===== 订单 =====
export const adminReservationPageApi = (params) =>
  request.get('/admin/reservations', { params })
