/**
 * Tests for the Axios request wrapper defined in src/api/request.ts.
 *
 * Strategy
 * --------
 * request.ts calls `axios.create()` at module load time and registers
 * interceptors on the returned instance.  We cannot easily intercept those
 * calls via vi.mock('axios') because the module is evaluated once.
 *
 * Instead we import the real `request` instance (which uses a real axios
 * instance internally) and use `axios-mock-adapter` – but that requires an
 * extra dependency we don't have.
 *
 * The cleanest approach that fits the existing dependency tree is to:
 *   1. Mock the *module-level* side-effects (pinia store, vant, vue-router).
 *   2. Reach into the interceptors arrays of the axios instance that
 *      `request.ts` builds, pull out the handler functions, and call them
 *      directly – without ever making a real HTTP call.
 *
 * This lets us test the business logic inside the interceptors (auth header
 * injection, response unwrapping, 401 handling) in pure unit-test style.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// --------------------------------------------------------------------------
// Mock external side-effects before importing any project module
// --------------------------------------------------------------------------

// Mock vant so showToast is a spy we can inspect
vi.mock('vant', () => ({
  showToast: vi.fn(),
}))

// vi.mock() is hoisted to the top of the file by Vitest, so any variables
// referenced inside the factory must also be hoisted via vi.hoisted().
const mockRouterPush = vi.hoisted(() => vi.fn())

// Mock vue-router so router.push is a spy
vi.mock('@/router', () => ({
  default: { push: mockRouterPush },
}))

// --------------------------------------------------------------------------
// Imports that depend on the mocks above
// --------------------------------------------------------------------------

import { showToast } from 'vant'
import { useUserStore } from '@/stores/user'

// Import the request instance AFTER mocks are in place
import request from '@/api/request'

// --------------------------------------------------------------------------
// Helpers to extract interceptor handlers from the axios instance
// --------------------------------------------------------------------------

// axios stores registered interceptors in instance.interceptors.request.handlers
// and instance.interceptors.response.handlers (private, but accessible at runtime).
// Each handler is { fulfilled, rejected }.

function getRequestInterceptor(instance: typeof request) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handlers = (instance.interceptors.request as any).handlers as Array<{
    fulfilled: (config: unknown) => unknown
    rejected: (error: unknown) => unknown
  }>
  return handlers[0]
}

function getResponseInterceptor(instance: typeof request) {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handlers = (instance.interceptors.response as any).handlers as Array<{
    fulfilled: (response: unknown) => unknown
    rejected: (error: unknown) => unknown
  }>
  return handlers[0]
}

// --------------------------------------------------------------------------
// Tests
// --------------------------------------------------------------------------

describe('request interceptors', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
    mockRouterPush.mockReset()
  })

  // ---- Request interceptor -----------------------------------------------

  it('request_addsAuthorizationHeader_whenTokenExists', () => {
    const store = useUserStore()
    store.setToken('test-jwt-token')

    const { fulfilled } = getRequestInterceptor(request)
    const config = { headers: {} as Record<string, string> }

    const result = fulfilled(config) as typeof config
    expect(result.headers['Authorization']).toBe('Bearer test-jwt-token')
  })

  it('request_noAuthorizationHeader_whenNoToken', () => {
    // Token is empty (localStorage was cleared in beforeEach)
    const { fulfilled } = getRequestInterceptor(request)
    const config = { headers: {} as Record<string, string> }

    const result = fulfilled(config) as typeof config
    expect(result.headers['Authorization']).toBeUndefined()
  })

  // ---- Response interceptor – success path --------------------------------

  it('response_returnsData_whenCodeIsZero', async () => {
    const { fulfilled } = getResponseInterceptor(request)
    const fakeResponse = { data: { code: 0, message: 'ok', data: { id: 42 } } }

    const result = await fulfilled(fakeResponse)
    expect(result).toEqual({ id: 42 })
  })

  it('response_throwsError_whenCodeIsNonZero', async () => {
    const { fulfilled } = getResponseInterceptor(request)
    const fakeResponse = {
      data: { code: 400, message: '参数错误', data: null },
    }

    await expect(fulfilled(fakeResponse)).rejects.toThrow('参数错误')
    expect(showToast).toHaveBeenCalledWith('参数错误')
  })

  // ---- Response interceptor – error path ---------------------------------

  it('response_redirectsToLogin_on401', async () => {
    const { rejected } = getResponseInterceptor(request)
    const error = { response: { status: 401 } }

    await expect(rejected(error)).rejects.toEqual(error)
    expect(mockRouterPush).toHaveBeenCalledWith('/login')
    expect(showToast).toHaveBeenCalledWith('登录已过期，请重新登录')
  })
})
