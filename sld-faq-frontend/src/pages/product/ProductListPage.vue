<template>
  <div class="product-list-page">
    <van-nav-bar
      title="产品库"
      left-arrow
      @click-left="router.back()"
      fixed
      placeholder
    />

    <div class="search-bar">
      <van-search
        v-model="keyword"
        placeholder="搜索产品名称、型号、品牌"
        @search="onSearch"
        @clear="onSearch"
      />
    </div>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        :finished="finished"
        finished-text="没有更多了"
        @load="onLoad"
      >
        <div v-if="list.length === 0 && !loading" class="empty-wrapper">
          <van-empty description="暂无产品" :image-size="80" />
        </div>
        <div
          v-for="item in list"
          :key="item.id"
          class="product-card"
          @click="router.push(`/product/detail/${item.id}`)"
        >
          <div class="product-card-header">
            <span class="product-name">{{ item.name || item.model }}</span>
            <van-tag v-if="item.model && item.name" type="primary" plain size="small">{{ item.model }}</van-tag>
          </div>
          <div v-if="item.brand" class="product-brand">{{ item.brand }}</div>
          <div v-if="specsPreview(item)" class="product-specs">{{ specsPreview(item) }}</div>
        </div>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { productApi } from '@/api/product'
import type { ProductVO } from '@/types'

const router = useRouter()
const keyword = ref('')
const list = ref<ProductVO[]>([])
const loading = ref(false)
const finished = ref(false)
const refreshing = ref(false)
const page = ref(0)
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
    const res = await productApi.list({ keyword: keyword.value || undefined, page: page.value, size: PAGE_SIZE })
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

function onSearch() { fetchList(true) }
async function onRefresh() { await fetchList(true); refreshing.value = false }
function onLoad() { fetchList() }

function specsPreview(item: ProductVO): string {
  if (!item.specs) return ''
  try {
    const obj = JSON.parse(item.specs)
    return Object.entries(obj).slice(0, 3).map(([k, v]) => `${k}:${v}`).join('  ')
  } catch {
    return ''
  }
}
</script>

<style scoped>
.product-list-page { min-height: 100vh; background: #f5f5f5; }
.search-bar { background: white; }
.empty-wrapper { padding: 20px 0; }
.product-card {
  background: white; margin: 8px 12px; border-radius: 8px;
  padding: 12px 14px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); cursor: pointer;
}
.product-card:active { background: #f7f8fa; }
.product-card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.product-name { font-size: 15px; font-weight: 500; color: #323233; flex: 1; }
.product-brand { font-size: 12px; color: #969799; margin-bottom: 4px; }
.product-specs { font-size: 12px; color: #646566; }
</style>
