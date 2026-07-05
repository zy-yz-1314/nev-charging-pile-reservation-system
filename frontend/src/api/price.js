import request from './request'

/** 动态定价试算 */
export const priceCalcApi = (pileId, at) =>
  request.get('/price/calc', { params: { pileId, at } })
