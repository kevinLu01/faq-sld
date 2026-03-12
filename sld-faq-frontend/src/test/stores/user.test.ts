import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useUserStore } from '@/stores/user'
import type { UserInfo } from '@/types'

// Helper: build a minimal UserInfo object
function makeUserInfo(roles: string[]): UserInfo {
  return {
    id: 1,
    name: 'Test User',
    roles,
  }
}

describe('useUserStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('setToken_storesTokenInLocalStorage', () => {
    const store = useUserStore()

    store.setToken('my-secret-token')

    expect(store.token).toBe('my-secret-token')
    expect(localStorage.getItem('token')).toBe('my-secret-token')
  })

  it('logout_clearsTokenAndUserInfo', () => {
    const store = useUserStore()
    store.setToken('some-token')
    store.setUserInfo(makeUserInfo(['SUBMITTER']))

    store.logout()

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('isLoggedIn_trueWhenTokenExists', () => {
    const store = useUserStore()
    store.setToken('valid-token')

    expect(store.isLoggedIn).toBe(true)
  })

  it('isLoggedIn_falseWhenNoToken', () => {
    const store = useUserStore()
    // No token set — state initialises from localStorage which is cleared in beforeEach

    expect(store.isLoggedIn).toBe(false)
  })

  it('isReviewer_trueForReviewerRole', () => {
    const store = useUserStore()
    store.setUserInfo(makeUserInfo(['REVIEWER']))

    expect(store.isReviewer).toBe(true)
  })

  it('isReviewer_trueForAdminRole', () => {
    const store = useUserStore()
    store.setUserInfo(makeUserInfo(['ADMIN']))

    expect(store.isReviewer).toBe(true)
  })

  it('isReviewer_falseForSubmitterRole', () => {
    const store = useUserStore()
    store.setUserInfo(makeUserInfo(['SUBMITTER']))

    expect(store.isReviewer).toBeFalsy()
  })
})
