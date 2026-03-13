<template>
  <div class="review-detail-page">
    <van-nav-bar
      title="审核详情"
      left-arrow
      @click-left="goBack"
      fixed
      placeholder
    />

    <!-- Loading state -->
    <div v-if="loading" class="center-loading">
      <van-loading size="32" color="#1989fa" />
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="error-state">
      <van-empty :description="error" image="error">
        <template #bottom>
          <van-button type="primary" size="small" @click="fetchDetail">重新加载</van-button>
        </template>
      </van-empty>
    </div>

    <!-- Content -->
    <div v-else-if="candidate" class="detail-content">
      <!-- Question -->
      <div class="section-card question-card">
        <p class="section-label">问题</p>
        <p class="question-text">{{ candidate.question }}</p>
      </div>

      <!-- Answer -->
      <div class="section-card">
        <p class="section-label">答案</p>
        <ExpandableText :text="candidate.answer" :max-length="200" />
      </div>

      <!-- Meta info -->
      <div class="section-card meta-card">
        <div class="meta-row" v-if="candidate.category">
          <span class="meta-key">分类</span>
          <van-tag type="primary" plain>{{ candidate.category }}</van-tag>
        </div>
        <div class="meta-row" v-if="candidate.keywords">
          <span class="meta-key">关键词</span>
          <span class="meta-value">{{ candidate.keywords }}</span>
        </div>
        <div class="meta-row" v-if="candidate.confidence != null">
          <span class="meta-key">置信度</span>
          <div class="confidence-row">
            <span class="confidence-stars">{{ getStars(candidate.confidence) }}</span>
            <span class="confidence-num">{{ (candidate.confidence! * 100).toFixed(0) }}%</span>
          </div>
        </div>
        <div class="meta-row" v-if="candidate.fileName">
          <span class="meta-key">来源文件</span>
          <span class="meta-value file-name">{{ candidate.fileName }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-key">创建时间</span>
          <span class="meta-value">{{ candidate.createdAt?.slice(0, 16) || '' }}</span>
        </div>
      </div>

      <!-- Source chunk -->
      <div v-if="candidate.sourceChunk" class="section-card">
        <p class="section-label">来源原文</p>
        <ExpandableText
          :text="candidate.sourceChunk"
          :max-length="200"
          text-class="source-text"
        />
      </div>

      <!-- Reject reason if rejected -->
      <div v-if="candidate.status === 'REJECTED' && candidate.rejectReason" class="section-card reject-card">
        <p class="section-label reject-label">驳回原因</p>
        <p class="reject-reason">{{ candidate.rejectReason }}</p>
      </div>

      <!-- Bottom padding for fixed action bar -->
      <div style="height: 80px" />
    </div>

    <!-- Fixed action bar (only shown for PENDING) -->
    <div v-if="candidate && candidate.status === 'PENDING'" class="action-bar">
      <van-button
        type="danger"
        size="small"
        plain
        :loading="actionLoading === 'reject'"
        @click="showRejectDialog = true"
      >
        驳回
      </van-button>
      <van-button
        type="default"
        size="small"
        :loading="actionLoading === 'edit'"
        @click="openEditDialog"
      >
        编辑后通过
      </van-button>
      <van-button
        type="primary"
        size="small"
        :loading="actionLoading === 'approve'"
        @click="doApprove"
      >
        通过
      </van-button>
    </div>

    <!-- Reject dialog -->
    <van-dialog
      v-model:show="showRejectDialog"
      title="驳回原因"
      show-cancel-button
      :before-close="handleRejectConfirm"
    >
      <div class="dialog-content">
        <van-field
          v-model="rejectReason"
          type="textarea"
          placeholder="请输入驳回原因（必填）"
          rows="3"
          autosize
        />
      </div>
    </van-dialog>

    <!-- Edit & approve dialog -->
    <van-dialog
      v-model:show="showEditDialog"
      title="编辑后通过"
      show-cancel-button
      :before-close="handleEditConfirm"
    >
      <div class="dialog-content">
        <van-field
          v-model="editQuestion"
          label="问题"
          type="textarea"
          rows="2"
          autosize
          placeholder="请输入问题"
        />
        <van-field
          v-model="editAnswer"
          label="答案"
          type="textarea"
          rows="4"
          autosize
          placeholder="请输入答案"
          style="margin-top: 8px"
        />
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { candidateApi } from '@/api/candidate'
import type { CandidateVO } from '@/types'
import ExpandableText from '@/components/ExpandableText.vue'

const router = useRouter()
const route = useRoute()

const id = Number(route.params.id)
const candidate = ref<CandidateVO | null>(null)
const loading = ref(false)
const error = ref('')
const actionLoading = ref<'approve' | 'reject' | 'edit' | null>(null)

// Reject
const showRejectDialog = ref(false)
const rejectReason = ref('')

// Edit approve
const showEditDialog = ref(false)
const editQuestion = ref('')
const editAnswer = ref('')

function goBack() {
  router.back()
}

async function fetchDetail() {
  loading.value = true
  error.value = ''
  try {
    candidate.value = await candidateApi.getById(id)
  } catch {
    error.value = '加载失败，请重试'
  } finally {
    loading.value = false
  }
}

function getStars(confidence?: number): string {
  if (confidence == null) return ''
  const stars = Math.round(confidence * 5)
  return '⭐'.repeat(Math.max(0, Math.min(5, stars)))
}

async function doApprove() {
  actionLoading.value = 'approve'
  try {
    await candidateApi.approve(id)
    showSuccessToast('已通过')
    router.push({ path: '/review/list', query: { refresh: Date.now().toString() } })
  } catch {
    // error handled by interceptor
  } finally {
    actionLoading.value = null
  }
}

async function handleRejectConfirm(action: string) {
  if (action === 'cancel') {
    rejectReason.value = ''
    return true
  }
  if (!rejectReason.value.trim()) {
    showToast('请输入驳回原因')
    return false
  }
  actionLoading.value = 'reject'
  try {
    await candidateApi.reject(id, rejectReason.value.trim())
    showSuccessToast('已驳回')
    rejectReason.value = ''
    router.push({ path: '/review/list', query: { refresh: Date.now().toString() } })
    return true
  } catch {
    return false
  } finally {
    actionLoading.value = null
  }
}

function openEditDialog() {
  if (!candidate.value) return
  editQuestion.value = candidate.value.question
  editAnswer.value = candidate.value.answer
  showEditDialog.value = true
}

async function handleEditConfirm(action: string) {
  if (action === 'cancel') return true
  if (!editQuestion.value.trim() || !editAnswer.value.trim()) {
    showToast('问题和答案不能为空')
    return false
  }
  actionLoading.value = 'edit'
  try {
    await candidateApi.editApprove(id, editQuestion.value.trim(), editAnswer.value.trim())
    showSuccessToast('编辑并通过成功')
    router.push({ path: '/review/list', query: { refresh: Date.now().toString() } })
    return true
  } catch {
    return false
  } finally {
    actionLoading.value = null
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.review-detail-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.center-loading {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.error-state {
  padding: 40px 0;
}

.detail-content {
  padding: 12px 12px 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.section-card {
  background: white;
  border-radius: 8px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.question-card {
  border-left: 3px solid #1989fa;
}

.section-label {
  margin: 0 0 8px;
  font-size: 12px;
  color: #969799;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.question-text {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #323233;
  line-height: 1.6;
}

.meta-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meta-key {
  font-size: 13px;
  color: #969799;
  min-width: 56px;
  flex-shrink: 0;
}

.meta-value {
  font-size: 13px;
  color: #323233;
  flex: 1;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.confidence-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.confidence-stars {
  font-size: 12px;
}

.confidence-num {
  font-size: 12px;
  color: #969799;
}

.reject-card {
  border-left: 3px solid #ee0a24;
  background: #fff9f9;
}

.reject-label {
  color: #ee0a24;
}

.reject-reason {
  margin: 0;
  font-size: 14px;
  color: #ee0a24;
  line-height: 1.5;
}

.action-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: white;
  padding: 10px 16px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
  display: flex;
  gap: 10px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.08);
  z-index: 100;
}

.action-bar .van-button {
  flex: 1;
}

.dialog-content {
  padding: 16px;
}
</style>
