import request from './request'

export const listStationsApi = (keyword) =>
  request.get('/stations', { params: { keyword } })
export const getStationApi = (id) => request.get(`/stations/${id}`)
export const getPilesApi = (stationId) => request.get(`/stations/${stationId}/piles`)
/** 站点实时负载(空闲率/三色/负载系数,L2) */
export const stationLoadApi = (stationId) => request.get(`/stations/${stationId}/load`)
/** 站点未来N小时需求预测(L2) */
export const stationForecastApi = (stationId, hours) =>
  request.get(`/stations/${stationId}/forecast`, { params: { hours } })
