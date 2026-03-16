<template>
  <div class="home-page">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <!-- Header -->
      <div class="home-header">
        <div class="user-info">
          <van-image
            round
            width="40"
            height="40"
            :src="userStore.userInfo?.avatar || defaultAvatar"
            fit="cover"
          />
          <div class="user-text">
            <p class="user-greeting">{{ greeting }}，</p>
            <p class="user-name">{{ userStore.userInfo?.name || '用户' }}</p>
          </div>
        </div>
        <van-icon name="bell-o" size="22" color="#646566" @click="router.push('/review/list')" />
      </div>

      <!-- Stats cards -->
      <div class="stats-section">
        <div v-if="statsLoading" class="stats-loading">
          <van-loading size="20" color="#1989fa" />
        </div>
        <div v-else class="stats-grid">
          <div class="stat-card" @click="router.push('/review/list')">
            <p class="stat-value pending-color">{{ pendingCount }}</p>
            <p class="stat-label">待审核</p>
          </div>
          <div class="stat-card">
            <p class="stat-value success-color">{{ todayHandled }}</p>
            <p class="stat-label">总已审核</p>
          </div>
        </div>
      </div>

      <!-- Quick entry grid -->
      <div class="section-title">快捷入口</div>
      <div class="quick-grid">
        <div
          v-for="entry in quickEntries"
          :key="entry.path"
          class="quick-card"
          @click="router.push(entry.path)"
        >
          <div class="quick-icon" :style="{ background: entry.bg }">
            <van-icon :name="entry.icon" size="26" color="white" />
          </div>
          <p class="quick-label">{{ entry.label }}</p>
        </div>
      </div>

      <!-- Recent FAQ candidates -->
      <div class="section-title">
        <span>最新待审核</span>
        <span class="section-more" @click="router.push('/review/list')">查看全部</span>
      </div>
      <div v-if="recentLoading" class="list-loading">
        <van-loading size="20" color="#1989fa" />
      </div>
      <div v-else-if="recentCandidates.length === 0" class="empty-tip">
        <van-empty description="暂无待审核内容" image-size="80" />
      </div>
      <div v-else class="recent-list">
        <div
          v-for="item in recentCandidates"
          :key="item.id"
          class="recent-card"
          @click="router.push(`/review/detail/${item.id}`)"
        >
          <div class="recent-header">
            <van-tag v-if="item.category" type="primary" plain>{{ item.category }}</van-tag>
            <span class="recent-file">{{ item.fileName || '未知文件' }}</span>
          </div>
          <p class="recent-question">{{ item.question }}</p>
        </div>
      </div>

      <!-- Bottom spacer for tabbar -->
      <div style="height: 70px" />
    </van-pull-refresh>

    <!-- Tabbar -->
    <van-tabbar v-model="activeTab" fixed placeholder>
      <van-tabbar-item icon="home-o" name="home" @click="router.push('/home')">首页</van-tabbar-item>
      <van-tabbar-item icon="passed" name="review" @click="router.push('/review/list')">FAQ审核</van-tabbar-item>
      <van-tabbar-item icon="shop-o" name="product" @click="router.push('/product/list')">产品库</van-tabbar-item>
      <van-tabbar-item icon="records-o" name="faq" @click="router.push('/faq/list')">FAQ</van-tabbar-item>
      <van-tabbar-item icon="contact-o" name="me" @click="router.push('/me')">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { candidateApi } from '@/api/candidate'
import type { CandidateVO } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const refreshing = ref(false)
const statsLoading = ref(false)
const recentLoading = ref(false)
const pendingCount = ref(0)
const todayHandled = ref(0)
const recentCandidates = ref<CandidateVO[]>([])
const activeTab = ref('home')

const defaultAvatar = 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const quickEntries = [
  { label: '上传资料', path: '/upload', icon: 'upgrade', bg: '#1989fa' },
  { label: 'FAQ 审核', path: '/review/list', icon: 'passed', bg: '#07c160' },
  { label: '产品审核', path: '/product/review/list', icon: 'orders-o', bg: '#ff976a' },
  { label: '产品库', path: '/product/list', icon: 'shop-o', bg: '#7232dd' },
  { label: 'FAQ 库', path: '/faq/list', icon: 'records-o', bg: '#10aeff' },
  { label: '我的', path: '/me', icon: 'contact-o', bg: '#969799' },
]

async function loadStats() {
  statsLoading.value = true
  try {
    const [pendingRes, reviewedRes] = await Promise.all([
      candidateApi.list({ status: 'PENDING', page: 0, size: 1 }),
      candidateApi.list({ status: 'APPROVED,REJECTED,MERGED', page: 0, size: 1 }),
    ])
    pendingCount.value = pendingRes.total
    todayHandled.value = reviewedRes.total
  } catch {
    // ignore
  } finally {
    statsLoading.value = false
  }
}

async function loadRecentCandidates() {
  recentLoading.value = true
  try {
    const res = await candidateApi.list({ status: 'PENDING', page: 0, size: 5 })
    recentCandidates.value = res.items
  } catch {
    // ignore
  } finally {
    recentLoading.value = false
  }
}

async function onRefresh() {
  await Promise.all([loadStats(), loadRecentCandidates()])
  refreshing.value = false
}

onMounted(() => {
  loadStats()
  loadRecentCandidates()
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.home-header {
  background: white;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-text {
  line-height: 1.3;
}

.user-greeting {
  margin: 0;
  font-size: 12px;
  color: #969799;
}

.user-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}

.stats-section {
  margin: 12px 16px;
}

.stats-loading {
  background: white;
  border-radius: 8px;
  padding: 24px;
  display: flex;
  justify-content: center;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.stat-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
}

.stat-value {
  margin: 0 0 4px;
  font-size: 28px;
  font-weight: 700;
}

.stat-label {
  margin: 0;
  font-size: 12px;
  color: #969799;
}

.pending-color {
  color: #ff976a;
}

.success-color {
  color: #07c160;
}

.section-title {
  padding: 12px 16px 8px;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-more {
  font-size: 13px;
  font-weight: 400;
  color: #1989fa;
}

.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 0 16px;
}

.quick-card {
  background: white;
  border-radius: 8px;
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.15s;
}

.quick-card:active {
  transform: scale(0.97);
}

.quick-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-label {
  margin: 0;
  font-size: 13px;
  color: #323233;
  font-weight: 500;
}

.list-loading {
  padding: 24px;
  display: flex;
  justify-content: center;
}

.recent-list {
  padding: 0 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recent-card {
  background: white;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
}

.recent-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.recent-file {
  font-size: 11px;
  color: #c8c9cc;
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-question {
  margin: 0;
  font-size: 14px;
  color: #323233;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.empty-tip {
  padding: 20px 0;
}
</style>
