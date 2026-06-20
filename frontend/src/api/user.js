import request from './request'

export const getMeApi = () => request.get('/user/me')
export const updateProfileApi = (data) => request.put('/user/profile', data)
export const rechargeApi = (amount) =>
  request.post('/user/recharge', null, { params: { amount } })
