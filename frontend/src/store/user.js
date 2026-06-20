import { defineStore } from 'pinia'

/**
 * 用户登录态。token 与用户信息持久化到 localStorage。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('pq_token') || '',
    user: JSON.parse(localStorage.getItem('pq_user') || 'null'),
  }),
  getters: {
    isLogin: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN',
    nickname: (s) => s.user?.nickname || s.user?.username || '用户',
  },
  actions: {
    setAuth(token, user) {
      this.token = token
      this.user = user
      localStorage.setItem('pq_token', token)
      localStorage.setItem('pq_user', JSON.stringify(user))
    },
    setUser(user) {
      this.user = user
      localStorage.setItem('pq_user', JSON.stringify(user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('pq_token')
      localStorage.removeItem('pq_user')
    },
  },
})
