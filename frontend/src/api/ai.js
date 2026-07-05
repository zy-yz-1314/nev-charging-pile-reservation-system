import request from './request'

/** LLM 自然语言助手 */
export const aiChatApi = (dto) => request.post('/ai/chat', dto)
