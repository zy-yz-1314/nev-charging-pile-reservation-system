import request from './request'

export const listStationsApi = (keyword) =>
  request.get('/stations', { params: { keyword } })
export const getStationApi = (id) => request.get(`/stations/${id}`)
export const getPilesApi = (stationId) => request.get(`/stations/${stationId}/piles`)
