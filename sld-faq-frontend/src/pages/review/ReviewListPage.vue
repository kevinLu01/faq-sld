<template>
  <div class="review-list-page">
    <van-nav-bar
      title="审核中心"
      left-arrow
      @click-left="router.back()"
      fixed
      placeholder
    />

    <van-tabs v-model:active="activeStatus" @change="onTabChange" sticky offset-top="46">
      <van-tab title="待审核" name="PENDING">
        <CandidateListPanel status="PENDING" :refresh-trigger="refreshTrigger" />
      </van-tab>
      <van-tab title="已审核" name="REVIEWED">
        <CandidateListPanel status="REVIEWED" :refresh-trigger="refreshTrigger" />
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import CandidateListPanel from '@/components/CandidateListPanel.vue'

const router = useRouter()
const activeStatus = ref('PENDING')
const refreshTrigger = ref(0)

function onTabChange() {
  refreshTrigger.value++
}
</script>

<style scoped>
.review-list-page {
  min-height: 100vh;
  background: #f5f5f5;
}
</style>
