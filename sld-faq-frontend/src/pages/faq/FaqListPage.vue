<template>
  <div class="faq-list-page">
    <van-nav-bar
      title="FAQ 知识库"
      left-arrow
      @click-left="router.back()"
      fixed
      placeholder
    />

    <!-- Search bar -->
    <van-search
      v-model="keyword"
      placeholder="搜索常见问题..."
      show-action
      @search="onSearch"
      @cancel="onCancelSearch"
      @clear="onSearch"
    />

    <!-- FAQ list -->
    <div class="list-wrapper">
      <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
        <van-list
          v-model:loading="loading"
          :finished="finished"
          :finished-text="list.length ? '没有更多了' : ''"
          @load="onLoad"
        >
          <div v-if="list.length === 0 && !loading" class="empty-wrapper">
            <van-empty
              :description="keyword ? `未找到与「${keyword}」相关的问题` : '暂无 FAQ 内容'"
              :image-size="80"
            />
          </div>
          <div
            v-for="item in list"
            :key="item.id"
            class="faq-card"
            @click="router.push(`/faq/detail/${item.id}`)"
          >
            <div class="faq-header">
              <van-tag v-if="item.categoryName" type="primary" plain size="small">
                {{ item.categoryName }}
              </van-tag>
              <span class="view-count">
                <van-icon name="eye-o" size="12" />
                {{ item.viewCount }}
              </span>
            </div>
            <p class="faq-question">{{ item.question }}</p>
            <p class="faq-answer">{{ item.answer }}</p>
          </div>
        </van-list>
      </van-pull-refresh>
    </div>

    <!-- Bottom spacer -->
    <div style="height: 20px" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { faqApi } from '@/api/faq'
import type { FaqVO } from '@/types'

const router = useRouter()

const list = ref<FaqVO[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const keyword = ref('')
const page = ref(0)
const total = ref(0)
const PAGE_SIZE = 20

async function fetchList(reset = false) {
  if (reset) {
    page.value = 0
    list.value = []
    finished.value = false
  }
  if (finished.value) return
  loading.value = true
  try {
    const res = await faqApi.list({
      keyword: keyword.value || undefined,
      page: page.value,
      size: PAGE_SIZE,
    })
    list.value = [...list.value, ...res.items]
    total.value = res.total
    page.value++
    if (list.value.length >= res.total) {
      finished.value = true
    }
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

function onSearch() {
  fetchList(true)
}

function onCancelSearch() {
  keyword.value = ''
  fetchList(true)
}

onMounted(() => {
  fetchList(true)
})
</script>

<style scoped>
.faq-list-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.list-wrapper {
  padding: 8px 0;
}

.empty-wrapper {
  padding: 40px 0;
}

.faq-card {
  background: white;
  margin: 6px 12px;
  border-radius: 8px;
  padding: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: transform 0.15s;
}

.faq-card:active {
  transform: scale(0.98);
}

.faq-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.view-count {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #c8c9cc;
}

.faq-question {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: #323233;
  line-height: 1.5;
}

.faq-answer {
  margin: 0;
  font-size: 13px;
  color: #646566;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
