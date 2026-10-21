import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi, getMe } from '../api/auth'

/**
 * 用户状态（Pinia）：token + 用户信息，持久化到 localStorage
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: JSON.parse(localStorage.getItem('user') || 'null')
  }),

  getters: {
    isLoggedIn: state => !!state.token,
    isAdmin: state => state.user?.role === 'admin',
    displayName: state => state.user?.nickname || state.user?.username || ''
  },

  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },

    async register(form) {
      await registerApi(form)
    },

    /** 从后端刷新用户信息（页面加载时校正本地缓存） */
    async fetchMe() {
      const user = await getMe()
      this.user = user
      localStorage.setItem('user', JSON.stringify(user))
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
