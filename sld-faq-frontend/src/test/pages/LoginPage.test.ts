import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

// --------------------------------------------------------------------------
// Mocks – declared before any project imports
// --------------------------------------------------------------------------

const mockRouterReplace = vi.fn()

// useRoute returns different query objects per test; we keep a mutable ref.
const mockRouteQuery: Record<string, string> = {}

vi.mock('vue-router', () => ({
  useRouter: () => ({ replace: mockRouterReplace }),
  useRoute: () => ({ query: mockRouteQuery }),
}))

vi.mock('@/api/auth', () => ({
  authApi: {
    getWecomUrl: vi.fn(),
    callback: vi.fn(),
    mockLogin: vi.fn(),
  },
}))

vi.mock('vant', () => ({
  showToast: vi.fn(),
}))

// --------------------------------------------------------------------------
// Imports after mocks
// --------------------------------------------------------------------------

import LoginPage from '@/pages/LoginPage.vue'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { LoginResponse } from '@/types'

// --------------------------------------------------------------------------
// Stubs for Vant components used in the template
// --------------------------------------------------------------------------

const globalStubs = {
  'van-loading': { template: '<div class="van-loading"></div>' },
  'van-icon': { template: '<div class="van-icon"></div>' },
  'van-button': {
    template: '<button class="van-button" @click="$emit(\'click\')"><slot /></button>',
    emits: ['click'],
  },
}

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------

const mockLoginResponse: LoginResponse = {
  token: 'mock-jwt-token',
  user: { id: 1, name: '张审核', roles: ['REVIEWER'] },
}

function mountLoginPage() {
  return mount(LoginPage, { global: { stubs: globalStubs } })
}

// --------------------------------------------------------------------------
// Tests
// --------------------------------------------------------------------------

describe('LoginPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    mockRouterReplace.mockReset()

    // Reset route query
    for (const key of Object.keys(mockRouteQuery)) {
      delete mockRouteQuery[key]
    }

    // Default: DEV mode so we see mock login buttons
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(import.meta as any).env = { DEV: true }
  })

  it('showsMockLoginButtons_inDevEnvironment', async () => {
    // DEV = true is set in beforeEach
    const wrapper = mountLoginPage()
    await flushPromises()

    // The mock-login-area section should be visible
    expect(wrapper.find('.mock-login-area').exists()).toBe(true)

    // Three van-button stubs should be rendered (REVIEWER, SUBMITTER, ADMIN)
    const buttons = wrapper.findAll('.van-button')
    expect(buttons.length).toBeGreaterThanOrEqual(3)
  })

  it('handlesMockLogin_setsTokenAndRedirects', async () => {
    ;(authApi.mockLogin as ReturnType<typeof vi.fn>).mockResolvedValue(mockLoginResponse)

    const wrapper = mountLoginPage()
    await flushPromises()

    // Click the first mock-login button (REVIEWER – "以审核员身份登录")
    const buttons = wrapper.findAll('.van-button')
    await buttons[0].trigger('click')
    await flushPromises()

    // Token should be persisted via the pinia store
    const store = useUserStore()
    expect(store.token).toBe('mock-jwt-token')
    expect(localStorage.getItem('token')).toBe('mock-jwt-token')

    // Should navigate to /home
    expect(mockRouterReplace).toHaveBeenCalledWith('/home')
  })

  it('showsLoadingState_duringOAuthCallback', async () => {
    // Switch to production mode so the OAuth callback path is taken
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(import.meta as any).env = { DEV: false }

    // Provide code + state to trigger handleWecomCallback
    mockRouteQuery.code = 'abc123'
    mockRouteQuery.state = 'xyz'

    // callback returns a promise that is never resolved so loading persists
    let resolveCallback!: (value: LoginResponse) => void
    ;(authApi.callback as ReturnType<typeof vi.fn>).mockReturnValue(
      new Promise<LoginResponse>((resolve) => {
        resolveCallback = resolve
      })
    )

    const wrapper = mountLoginPage()
    // Let onMounted run and kick off the async call
    await flushPromises()

    // While the callback promise is still pending, loading indicator must exist
    expect(wrapper.find('.van-loading').exists()).toBe(true)

    // Resolve so the test doesn't leave dangling promises
    resolveCallback(mockLoginResponse)
    await flushPromises()
  })

  it('showsError_whenCallbackFails', async () => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ;(import.meta as any).env = { DEV: false }

    mockRouteQuery.code = 'bad-code'
    mockRouteQuery.state = 'bad-state'

    ;(authApi.callback as ReturnType<typeof vi.fn>).mockRejectedValue(new Error('network error'))

    const wrapper = mountLoginPage()
    await flushPromises()

    // The error area should display the failure message
    expect(wrapper.find('.error-text').exists()).toBe(true)
    expect(wrapper.find('.error-text').text()).toContain('企业微信授权失败')
  })
})
