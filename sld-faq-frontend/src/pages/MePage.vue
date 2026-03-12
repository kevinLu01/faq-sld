<template>
  <div class="me-page">
    <van-nav-bar title="我的" fixed placeholder />

    <div class="page-content">
      <!-- User profile card -->
      <div class="profile-card">
        <van-image
          round
          width="72"
          height="72"
          :src="userStore.userInfo?.avatar || defaultAvatar"
          fit="cover"
          class="avatar"
        />
        <div class="profile-info">
          <p class="profile-name">{{ userStore.userInfo?.name || '未知用户' }}</p>
          <div class="role-badges">
            <van-tag
              v-for="role in userStore.userInfo?.roles"
              :key="role"
              :type="getRoleTagType(role)"
              size="medium"
              style="margin-right: 6px"
            >
              {{ getRoleLabel(role) }}
            </van-tag>
          </div>
          <p v-if="userStore.userInfo?.department" class="profile-dept">
            <van-icon name="label-o" size="12" />
            {{ userStore.userInfo.department }}
          </p>
        </div>
      </div>

      <!-- Info list -->
      <div class="info-card">
        <van-cell-group inset>
          <van-cell
            v-if="userStore.userInfo?.mobile"
            title="手机号"
            :value="userStore.userInfo.mobile"
            icon="phone-o"
          />
          <van-cell
            title="用户 ID"
            :value="String(userStore.userInfo?.id || '')"
            icon="manager-o"
          />
        </van-cell-group>
      </div>

      <!-- Actions -->
      <div class="action-list">
        <van-cell-group inset>
          <van-cell
            title="上传资料"
            icon="upgrade"
            is-link
            @click="router.push('/upload')"
          />
          <van-cell
            title="审核中心"
            icon="passed"
            is-link
            @click="router.push('/review/list')"
          />
          <van-cell
            title="FAQ 知识库"
            icon="records-o"
            is-link
            @click="router.push('/faq/list')"
          />
        </van-cell-group>
      </div>

      <!-- Logout -->
      <div class="logout-section">
        <van-button
          type="danger"
          plain
          round
          block
          @click="confirmLogout"
        >
          退出登录
        </van-button>
      </div>

      <!-- App version -->
      <p class="app-version">FAQ 知识助手 v1.0.0</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

function getRoleLabel(role: string): string {
  const map: Record<string, string> = {
    ADMIN: '管理员',
    REVIEWER: '审核员',
    SUBMITTER: '提交员',
  }
  return map[role] || role
}

function getRoleTagType(role: string): 'primary' | 'success' | 'warning' | 'danger' | 'default' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'default'> = {
    ADMIN: 'danger',
    REVIEWER: 'primary',
    SUBMITTER: 'success',
  }
  return map[role] || 'default'
}

async function confirmLogout() {
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确认退出当前账号？',
      confirmButtonText: '退出',
      confirmButtonColor: '#ee0a24',
    })
    userStore.logout()
    showToast('已退出登录')
    router.replace('/login')
  } catch {
    // User cancelled
  }
}
</script>

<style scoped>
.me-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.page-content {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.profile-card {
  background: white;
  border-radius: 12px;
  padding: 24px 16px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.avatar {
  flex-shrink: 0;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 700;
  color: #323233;
}

.role-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}

.profile-dept {
  margin: 0;
  font-size: 12px;
  color: #969799;
  display: flex;
  align-items: center;
  gap: 4px;
}

.info-card,
.action-list {
  border-radius: 8px;
  overflow: hidden;
}

.logout-section {
  margin-top: 8px;
}

.app-version {
  text-align: center;
  font-size: 12px;
  color: #c8c9cc;
  margin: 8px 0 0;
}
</style>
