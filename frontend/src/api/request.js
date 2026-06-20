import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截:附带 token
request.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = 'Bearer ' + store.token
  }
  return config
})

// 响应拦截:统一处理业务码(后端始终返回 HTTP 200 + Result.code)
request.interceptors.response.use(
  (resp) => {
    const data = resp.data
    if (data.code === 200) {
      return data
    }
    if (data.code === 401) {
      const store = useUserStore()
      store.logout()
      router.push('/login')
    }
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message || 'error'))
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    return Promise.reject(error)
  },
)

export default request
