import request from './request'

/** 智能匹配推荐 POST /api/recommend */
export const recommendApi = (dto) => request.post('/recommend', dto)
