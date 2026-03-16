<template>
  <div class="candidate-list-panel">
    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        :finished-text="list.length ? '没有更多了' : ''"
        @load="onLoad"
      >
        <div v-if="list.length === 0 && !loading" class="empty-wrapper">
          <van-empty description="暂无内容" :image-size="80" />
        </div>
        <div
          v-for="item in list"
          :key="item.id"
          class="candidate-card"
          @click="router.push(`/product/review/detail/${item.id}`)"
        >
          <div class="card-header">
            <van-tag v-if="item.category" type="primary" plain>{{ item.category }}</van-tag>
            <van-tag :type="getStatusTag(item.status).type" plain>
              {{ getStatusTag(item.status).text }}
            </van-tag>
            <span class="card-file">{{ item.fileName || '' }}</span>
          </div>
          <p class="card-title">
            {{ item.name || item.model || '未知产品' }}
            <span v-if="item.model && item.name" class="card-model">{{ item.model }}</span>
          </p>
          <p v-if="item.brand" class="card-brand">品牌：{{ item.brand }}</p>
          <div class="card-footer">
            <span class="card-stars">{{ getStars(item.confidence) }}</span>
            <span class="card-date">{{ item.createdAt?.slice(0, 10) || '' }}</span>
          </div>
        </div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { productCandidateApi } from '@/api/product'
import type { ProductCandidateVO } from '@/types'

const props = defineProps<{
  status: string
  refreshTrigger: number
}>()

const router = useRouter()
const list = ref<ProductCandidateVO[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const page = ref(0)
const PAGE_SIZE = 20

function getQueryStatus(): string {
  if (props.status === 'REVIEWED') return 'APPROVED,REJECTED'
  return props.status
}

async function fetchList(reset = false) {
  if (reset) {
    page.value = 0
    list.value = []
    finished.value = false
  }
  if (finished.value) return
  loading.value = true
  try {
    const res = await productCandidateApi.list({
      status: getQueryStatus(),
      page: page.value,
      size: PAGE_SIZE,
    })
    list.value = [...list.value, ...res.items]
    page.value++
    if (list.value.length >= res.total) finished.value = true
  } catch {
    showToast('加载失败')
    finished.value = true
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  await fetchList(true)
  refreshing.value = false
}

function onLoad() {
  fetchList()
}

function getStars(confidence?: number): string {
  if (!confidence) return ''
  const stars = Math.round(confidence * 5)
  return '⭐'.repeat(Math.max(0, Math.min(5, stars)))
}

function getStatusTag(status: ProductCandidateVO['status']): { text: string; type: 'primary' | 'success' | 'danger' | 'warning' | 'default' } {
  const map: Record<string, { text: string; type: 'primary' | 'success' | 'danger' | 'warning' | 'default' }> = {
    PENDING: { text: '待审核', type: 'warning' },
    APPROVED: { text: '已通过', type: 'success' },
    REJECTED: { text: '已驳回', type: 'danger' },
  }
  return map[status] || { text: status, type: 'default' }
}

watch(() => props.refreshTrigger, () => fetchList(true))
onMounted(() => fetchList(true))
</script>

<style scoped>
.candidate-list-panel { min-height: calc(100vh - 100px); background: #f5f5f5; }
.empty-wrapper { padding: 20px 0; }
.candidate-card {
  background: white; margin: 8px 12px; border-radius: 8px;
  padding: 12px 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); cursor: pointer;
}
.candidate-card:active { background: #f7f8fa; }
.card-header { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.card-file { margin-left: auto; font-size: 11px; color: #c8c9cc; max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.card-title { margin: 0 0 4px; font-size: 14px; font-weight: 500; color: #323233; }
.card-model { margin-left: 6px; font-size: 12px; color: #969799; font-weight: normal; }
.card-brand { margin: 0 0 8px; font-size: 12px; color: #969799; }
.card-footer { display: flex; align-items: center; justify-content: space-between; }
.card-stars { font-size: 12px; }
.card-date { font-size: 11px; color: #c8c9cc; }
</style>
