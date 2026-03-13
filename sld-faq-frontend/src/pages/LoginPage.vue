<template>
  <div class="login-page">
    <div class="login-container">
      <!-- Logo area -->
      <div class="logo-area">
        <div class="logo-icon">
          <svg width="64" height="64" viewBox="0 0 64 64" fill="none">
            <rect width="64" height="64" rx="16" fill="#1989fa" />
            <text x="32" y="42" text-anchor="middle" font-size="28" font-weight="bold" fill="white">FAQ</text>
          </svg>
        </div>
        <h1 class="brand-title">FAQ 知识助手</h1>
        <p class="brand-subtitle">企业知识沉淀，智能整理助手</p>
      </div>

      <!-- Loading state -->
      <div v-if="loading" class="status-area">
        <van-loading size="32" color="#1989fa" />
        <p class="status-text">{{ loadingText }}</p>
      </div>

      <!-- Error state -->
      <div v-else-if="errorMsg" class="status-area error-area">
        <van-icon name="warning-o" size="40" color="#ee0a24" />
        <p class="error-text">{{ errorMsg }}</p>
        <van-button type="primary" round size="normal" @click="retry">重新登录</van-button>
      </div>

      <!-- Dev mock login -->
      <div v-else-if="isDev" class="mock-login-area">
        <p class="mock-hint">开发模式 — 选择角色快速登录</p>
        <van-button
          type="primary"
          round
          block
          :loading="mockLoading"
          @click="mockLogin('dev001', '张审核', 'REVIEWER')"
        >
          以审核员身份登录
        </van-button>
        <van-button
          type="default"
          round
          block
          style="margin-top: 12px"
          :loading="mockLoading"
          @click="mockLogin('dev002', '李提交', 'SUBMITTER')"
        >
          以提交员身份登录
        </van-button>
        <van-button
          type="warning"
          round
          block
          style="margin-top: 12px"
          :loading="mockLoading"
          @click="mockLogin('dev003', '王管理', 'ADMIN')"
        >
          以管理员身份登录
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const loadingText = ref('正在登录...')
const errorMsg = ref('')
const mockLoading = ref(false)
const isDev = import.meta.env.DEV

async function handleWecomCallback(code: string, state: string) {
  loading.value = true
  loadingText.value = '正在验证企业微信身份...'
  try {
    const res = await authApi.callback(code, state)
    userStore.setToken(res.token)
    userStore.setUserInfo(res.user)
    showToast({ message: `欢迎回来，${res.user.name}`, type: 'success' })
    const redirect = route.query.redirect as string
    router.replace(redirect || '/home')
  } catch {
    errorMsg.value = '企业微信授权失败，请重试'
  } finally {
    loading.value = false
  }
}

async function redirectToWecom() {
  loading.value = true
  loadingText.value = '正在跳转企业微信授权...'
  try {
    const url = await authApi.getWecomUrl()
    window.location.href = url
  } catch {
    loading.value = false
    errorMsg.value = '获取授权地址失败，请重试'
  }
}

async function mockLogin(userId: string, name: string, role: string) {
  mockLoading.value = true
  try {
    const res = await authApi.mockLogin(userId, name, role)
    userStore.setToken(res.token)
    userStore.setUserInfo(res.user)
    showToast({ message: `欢迎，${res.user.name}`, type: 'success' })
    const redirect = route.query.redirect as string
    router.replace(redirect || '/home')
  } catch {
    showToast('模拟登录失败')
  } finally {
    mockLoading.value = false
  }
}

function retry() {
  errorMsg.value = ''
  if (isDev) return
  redirectToWecom()
}

onMounted(() => {
  // If already logged in, skip login page
  if (userStore.isLoggedIn) {
    router.replace('/home')
    return
  }

  const code = route.query.code as string
  const state = route.query.state as string

  if (code && state) {
    // WeChat Work OAuth callback
    handleWecomCallback(code, state)
  } else if (!isDev) {
    // Production: redirect to WeChat Work OAuth
    redirectToWecom()
  }
  // Dev mode: show mock login buttons
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f4fd 0%, #f5f5f5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
}

.login-container {
  width: 100%;
  max-width: 360px;
  text-align: center;
}

.logo-area {
  margin-bottom: 48px;
}

.logo-icon {
  display: inline-block;
  margin-bottom: 16px;
  filter: drop-shadow(0 4px 12px rgba(25, 137, 250, 0.3));
}

.brand-title {
  font-size: 24px;
  font-weight: 700;
  color: #1989fa;
  margin: 0 0 8px;
}

.brand-subtitle {
  font-size: 14px;
  color: #969799;
  margin: 0;
}

.status-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.status-text {
  font-size: 14px;
  color: #646566;
  margin: 0;
}

.error-area {
  gap: 12px;
}

.error-text {
  font-size: 14px;
  color: #ee0a24;
  margin: 0;
}

.mock-login-area {
  text-align: left;
}

.mock-hint {
  font-size: 13px;
  color: #969799;
  text-align: center;
  margin: 0 0 20px;
}
</style>
