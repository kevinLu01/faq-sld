<template>
  <div class="review-detail-page">
    <van-nav-bar
      title="产品候选详情"
      left-arrow
      @click-left="router.back()"
      fixed
      placeholder
    />

    <div v-if="loading" class="loading-wrapper">
      <van-loading size="24px">加载中...</van-loading>
    </div>

    <template v-else-if="candidate">
      <div class="detail-card">
        <div class="detail-header">
          <van-tag v-if="candidate.category" type="primary" plain>{{ candidate.category }}</van-tag>
          <van-tag :type="statusTag.type" plain>{{ statusTag.text }}</van-tag>
        </div>

        <div class="detail-section">
          <div class="detail-row">
            <span class="detail-label">产品名称</span>
            <span class="detail-value">{{ candidate.name || '—' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">型号</span>
            <span class="detail-value">{{ candidate.model || '—' }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">品牌/厂家</span>
            <span class="detail-value">{{ candidate.brand || '—' }}</span>
          </div>
          <div v-if="candidate.compatModels" class="detail-row">
            <span class="detail-label">适配机型</span>
            <div class="detail-compat">
              <van-tag
                v-for="m in compatModelList"
                :key="m"
                plain
                style="margin: 2px 4px 2px 0"
              >{{ m }}</van-tag>
            </div>
          </div>
        </div>

        <div v-if="specsEntries.length" class="detail-section">
          <div class="section-title">规格参数</div>
          <div v-for="[k, v] in specsEntries" :key="k" class="detail-row">
            <span class="detail-label">{{ k }}</span>
            <span class="detail-value">{{ v }}</span>
          </div>
        </div>

        <div v-if="candidate.sourceSummary" class="detail-section">
          <div class="section-title">来源摘要</div>
          <p class="source-text">{{ candidate.sourceSummary }}</p>
        </div>

        <div v-if="candidate.rejectReason" class="detail-section reject-section">
          <div class="section-title">驳回原因</div>
          <p class="reject-text">{{ candidate.rejectReason }}</p>
        </div>

        <div class="detail-meta">
          <span>来自：{{ candidate.fileName || '未知文件' }}</span>
          <span>置信度：{{ candidate.confidence ? Math.round(candidate.confidence * 100) + '%' : '—' }}</span>
        </div>
      </div>
    </template>

    <!-- 底部操作栏（仅 PENDING 状态显示） -->
    <div v-if="candidate?.status === 'PENDING'" class="action-bar">
      <van-button
        block
        plain
        type="danger"
        @click="showRejectDialog = true"
        style="flex: 1"
      >驳回</van-button>
      <van-button
        block
        type="primary"
        @click="onApprove"
        :loading="approving"
        style="flex: 1"
      >通过入库</van-button>
    </div>

    <van-dialog
      v-model:show="showRejectDialog"
      title="驳回原因"
      show-cancel-button
      @confirm="onReject"
    >
      <van-field
        v-model="rejectReason"
        type="textarea"
        placeholder="请输入驳回原因（选填）"
        rows="3"
        style="padding: 16px"
      />
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { productCandidateApi } from '@/api/product'
import type { ProductCandidateVO } from '@/types'

const router = useRouter()
const route = useRoute()
const id = Number(route.params.id)

const candidate = ref<ProductCandidateVO | null>(null)
const loading = ref(true)
const approving = ref(false)
const showRejectDialog = ref(false)
const rejectReason = ref('')

const statusTag = computed(() => {
  const map: Record<string, { text: string; type: 'primary' | 'success' | 'danger' | 'warning' | 'default' }> = {
    PENDING: { text: '待审核', type: 'warning' },
    APPROVED: { text: '已通过', type: 'success' },
    REJECTED: { text: '已驳回', type: 'danger' },
  }
  return map[candidate.value?.status ?? ''] ?? { text: '未知', type: 'default' }
})

const compatModelList = computed(() =>
  candidate.value?.compatModels
    ? candidate.value.compatModels.split(',').map((s) => s.trim()).filter(Boolean)
    : []
)

const specsEntries = computed<[string, string][]>(() => {
  if (!candidate.value?.specs) return []
  try {
    const obj = JSON.parse(candidate.value.specs)
    return Object.entries(obj) as [string, string][]
  } catch {
    return []
  }
})

async function loadDetail() {
  try {
    candidate.value = await productCandidateApi.getById(id)
  } catch {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

async function onApprove() {
  approving.value = true
  try {
    await productCandidateApi.approve(id)
    showToast('已通过，产品入库成功')
    router.replace({ path: '/product/review/list', query: { refresh: Date.now().toString() } })
  } catch {
    showToast('操作失败')
  } finally {
    approving.value = false
  }
}

async function onReject() {
  try {
    await productCandidateApi.reject(id, rejectReason.value)
    showToast('已驳回')
    router.replace({ path: '/product/review/list', query: { refresh: Date.now().toString() } })
  } catch {
    showToast('操作失败')
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.review-detail-page { min-height: 100vh; background: #f5f5f5; padding-bottom: 80px; }
.loading-wrapper { display: flex; justify-content: center; padding: 40px; }
.detail-card { background: white; margin: 12px; border-radius: 8px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.detail-header { display: flex; gap: 6px; margin-bottom: 16px; flex-wrap: wrap; }
.detail-section { border-top: 1px solid #f5f5f5; padding-top: 12px; margin-top: 12px; }
.section-title { font-size: 12px; color: #969799; margin-bottom: 8px; }
.detail-row { display: flex; align-items: flex-start; padding: 4px 0; }
.detail-label { width: 80px; flex-shrink: 0; font-size: 13px; color: #969799; }
.detail-value { flex: 1; font-size: 14px; color: #323233; line-height: 1.5; }
.detail-compat { flex: 1; display: flex; flex-wrap: wrap; }
.source-text { margin: 0; font-size: 13px; color: #646566; line-height: 1.6; }
.reject-section { background: #fff2f2; border-radius: 4px; padding: 8px; }
.reject-text { margin: 0; font-size: 13px; color: #ee0a24; }
.detail-meta { margin-top: 12px; display: flex; justify-content: space-between; font-size: 11px; color: #c8c9cc; }
.action-bar { position: fixed; bottom: 0; left: 0; right: 0; display: flex; gap: 12px; padding: 12px 16px; background: white; box-shadow: 0 -2px 8px rgba(0,0,0,0.06); }
</style>
