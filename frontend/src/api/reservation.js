import request from './request'

export const grabPileApi = (pileId) => request.post('/reservations', { pileId })
export const myReservationsApi = () => request.get('/reservations')
export const startChargingApi = (id) => request.put(`/reservations/${id}/start`)
export const finishChargingApi = (id) => request.put(`/reservations/${id}/finish`)
export const cancelReservationApi = (id) => request.put(`/reservations/${id}/cancel`)
