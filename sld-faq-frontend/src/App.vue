<script setup lang="ts">
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'

const userStore = useUserStore()

onMounted(async () => {
  // 有 token 但没有 userInfo（页面刷新场景）
  if (userStore.token && !userStore.userInfo) {
    try {
      const userInfo = await authApi.getMe()
      userStore.setUserInfo(userInfo)
    } catch (e) {
      // token 失效，清除并跳转登录（401 拦截器会处理跳转）
      userStore.logout()
    }
  }
})
</script>

<template>
  <router-view />
</template>

<style>
* {
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}

body {
  margin: 0;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', Arial, sans-serif;
  background: #f5f5f5;
  color: #333;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  max-width: 100vw;
  min-height: 100vh;
  overflow-x: hidden;
}

/* Scrollbar style */
::-webkit-scrollbar {
  width: 0;
  height: 0;
}

/* Prevent overscroll bounce on iOS */
html {
  overflow: hidden;
  height: 100%;
}

body {
  height: 100%;
  overflow: auto;
  -webkit-overflow-scrolling: touch;
}
</style>
