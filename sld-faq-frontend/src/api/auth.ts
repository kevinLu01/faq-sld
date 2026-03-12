import request from './request'
import type { UserInfo, LoginResponse } from '@/types'

export const authApi = {
  getWecomUrl: () => request.get<string, string>('/auth/wecom/url'),

  callback: (code: string, state: string) =>
    request.post<LoginResponse, LoginResponse>('/auth/wecom/callback', { code, state }),

  getMe: () => request.get<UserInfo, UserInfo>('/auth/me'),

  // Mock login for development only
  mockLogin: (userId: string, name: string, role: string) =>
    request.post<LoginResponse, LoginResponse>('/auth/mock-login', { userId, name, role }),
}
