<template>
  <div class="product-detail-page">
    <van-nav-bar
      title="产品详情"
      left-arrow
      @click-left="router.back()"
      fixed
      placeholder
    />

    <div v-if="loading" class="loading-wrapper">
      <van-loading size="24px">加载中...</van-loading>
    </div>

    <template v-else-if="product">
      <div class="detail-card">
        <div class="product-title">{{ product.name || product.model || '未知产品' }}</div>
        <div v-if="product.model && product.name" class="product-model">型号：{{ product.model }}</div>
        <div v-if="product.brand" class="product-brand">品牌：{{ product.brand }}</div>

        <div v-if="product.compatModels" class="detail-section">
          <div class="section-title">适配机型</div>
          <div class="compat-tags">
            <van-tag
              v-for="m in compatModelList"
              :key="m"
              plain
              style="margin: 2px 4px 2px 0"
            >{{ m }}</van-tag>
          </div>
        </div>

        <div v-if="specsEntries.length" class="detail-section">
          <div class="section-title">规格参数</div>
          <div v-for="[k, v] in specsEntries" :key="k" class="spec-row">
            <span class="spec-key">{{ k }}</span>
            <span class="spec-val">{{ v }}</span>
          </div>
        </div>

        <div v-if="product.description" class="detail-section">
          <div class="section-title">产品描述</div>
          <p class="desc-text">{{ product.description }}</p>
        </div>

        <div class="detail-meta">
          <span>入库时间：{{ product.publishedAt?.slice(0, 10) || product.createdAt?.slice(0, 10) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { productApi } from '@/api/product'
import type { ProductVO } from '@/types'

const router = useRouter()
const route = useRoute()
const id = Number(route.params.id)

const product = ref<ProductVO | null>(null)
const loading = ref(true)

const compatModelList = computed(() =>
  product.value?.compatModels
    ? product.value.compatModels.split(',').map((s) => s.trim()).filter(Boolean)
    : []
)

const specsEntries = computed<[string, string][]>(() => {
  if (!product.value?.specs) return []
  try {
    return Object.entries(JSON.parse(product.value.specs)) as [string, string][]
  } catch {
    return []
  }
})

async function loadDetail() {
  try {
    product.value = await productApi.getById(id)
  } catch {
    showToast('加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.product-detail-page { min-height: 100vh; background: #f5f5f5; }
.loading-wrapper { display: flex; justify-content: center; padding: 40px; }
.detail-card { background: white; margin: 12px; border-radius: 8px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.product-title { font-size: 18px; font-weight: 600; color: #323233; margin-bottom: 4px; }
.product-model { font-size: 13px; color: #646566; margin-bottom: 2px; }
.product-brand { font-size: 13px; color: #646566; margin-bottom: 8px; }
.detail-section { border-top: 1px solid #f5f5f5; padding-top: 12px; margin-top: 12px; }
.section-title { font-size: 12px; color: #969799; margin-bottom: 8px; }
.compat-tags { display: flex; flex-wrap: wrap; }
.spec-row { display: flex; padding: 5px 0; border-bottom: 1px solid #f7f8fa; }
.spec-key { width: 90px; flex-shrink: 0; font-size: 13px; color: #969799; }
.spec-val { flex: 1; font-size: 14px; color: #323233; }
.desc-text { margin: 0; font-size: 13px; color: #646566; line-height: 1.6; }
.detail-meta { margin-top: 12px; font-size: 11px; color: #c8c9cc; }
</style>
