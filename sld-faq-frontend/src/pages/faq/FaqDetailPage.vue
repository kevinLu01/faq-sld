<template>
  <div class="faq-detail-page">
    <van-nav-bar
      title="FAQ 详情"
      left-arrow
      @click-left="router.back()"
      fixed
      placeholder
    />

    <!-- Loading -->
    <div v-if="loading" class="center-loading">
      <van-loading size="32" color="#1989fa" />
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-state">
      <van-empty :description="error" image="error">
        <template #bottom>
          <van-button type="primary" @click="fetchDetail">重新加载</van-button>
        </template>
      </van-empty>
    </div>

    <!-- Content -->
    <div v-else-if="faq" class="detail-content">
      <!-- Category & meta -->
      <div class="meta-row">
        <van-tag v-if="faq.categoryName" type="primary" plain>{{ faq.categoryName }}</van-tag>
        <span class="view-count">
          <van-icon name="eye-o" size="13" />
          {{ faq.viewCount }} 次浏览
        </span>
      </div>

      <!-- Question -->
      <div class="question-section">
        <h1 class="question-title">{{ faq.question }}</h1>
      </div>

      <!-- Keywords -->
      <div v-if="faq.keywords" class="keywords-section">
        <van-tag
          v-for="kw in keywordList"
          :key="kw"
          plain
         
          style="margin-right: 6px; margin-bottom: 4px"
        >
          {{ kw }}
        </van-tag>
      </div>

      <!-- Divider -->
      <van-divider />

      <!-- Answer -->
      <div class="answer-section">
        <p class="answer-label">回答</p>
        <div class="answer-content">{{ faq.answer }}</div>
      </div>

      <!-- Source refs -->
      <div v-if="faq.sourceRefs && faq.sourceRefs.length > 0" class="source-section">
        <van-collapse v-model="activeCollapse">
          <van-collapse-item title="来源文件信息" name="source">
            <div
              v-for="(ref, idx) in faq.sourceRefs"
              :key="idx"
              class="source-item"
            >
              <div class="source-header">
                <van-icon name="description" size="14" color="#969799" />
                <span class="source-filename">{{ ref.fileName }}</span>
              </div>
              <p class="source-chunk">{{ ref.chunkContent }}</p>
            </div>
          </van-collapse-item>
        </van-collapse>
      </div>

      <!-- Published at -->
      <div v-if="faq.publishedAt" class="published-at">
        发布于 {{ faq.publishedAt.slice(0, 10) }}
      </div>

      <div style="height: 32px" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { faqApi } from '@/api/faq'
import type { FaqVO } from '@/types'

const router = useRouter()
const route = useRoute()

const id = Number(route.params.id)
const faq = ref<FaqVO | null>(null)
const loading = ref(false)
const error = ref('')
const activeCollapse = ref<string[]>([])

const keywordList = computed(() => {
  if (!faq.value?.keywords) return []
  return faq.value.keywords.split(',').map((k) => k.trim()).filter(Boolean)
})

async function fetchDetail() {
  loading.value = true
  error.value = ''
  try {
    faq.value = await faqApi.getById(id)
  } catch {
    error.value = '加载失败，请重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.faq-detail-page {
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
  padding: 16px;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.view-count {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #c8c9cc;
}

.question-section {
  background: white;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-left: 4px solid #1989fa;
}

.question-title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #323233;
  line-height: 1.6;
}

.keywords-section {
  margin-bottom: 4px;
}

.answer-section {
  background: white;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-bottom: 12px;
}

.answer-label {
  margin: 0 0 10px;
  font-size: 12px;
  color: #969799;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.answer-content {
  font-size: 15px;
  color: #323233;
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.source-section {
  margin-bottom: 12px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.source-item {
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}

.source-item:last-child {
  border-bottom: none;
}

.source-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.source-filename {
  font-size: 12px;
  color: #646566;
  font-weight: 500;
}

.source-chunk {
  margin: 0;
  font-size: 12px;
  color: #969799;
  line-height: 1.6;
  background: #f7f8fa;
  border-radius: 4px;
  padding: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.published-at {
  text-align: center;
  font-size: 12px;
  color: #c8c9cc;
  margin-top: 8px;
}
</style>
