import request from './request'
import type { UserInfo, LoginResponse } from '@/types'

export const authApi = {
  getConfig: () =>
    request.get<{ mockLoginEnabled: boolean }, { mockLoginEnabled: boolean }>('/auth/config'),

  getWecomUrl: () => request.get<string, string>('/auth/wecom/url'),

  callback: (code: string, state: string) =>
    request.post<LoginResponse, LoginResponse>('/auth/wecom/callback', { code, state }),

  getMe: () => request.get<UserInfo, UserInfo>('/auth/me'),

  mockLogin: (userId: string, name: string, role: string) =>
    request.post<LoginResponse, LoginResponse>('/auth/mock-login', { userId, name, role }),
}
