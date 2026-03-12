import { defineStore } from 'pinia'
import type { UserInfo } from '@/types'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: null as UserInfo | null,
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isReviewer: (state) =>
      state.userInfo?.roles.includes('REVIEWER') ||
      state.userInfo?.roles.includes('ADMIN'),
  },
  actions: {
    setToken(token: string) {
      this.token = token
      localStorage.setItem('token', token)
    },
    setUserInfo(info: UserInfo) {
      this.userInfo = info
    },
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
    },
  },
})
