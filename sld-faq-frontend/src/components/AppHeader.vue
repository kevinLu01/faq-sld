<template>
  <van-nav-bar
    :title="title"
    :left-arrow="showBack"
    :right-text="rightText"
    fixed
    placeholder
    @click-left="onClickLeft"
    @click-right="emit('click-right')"
  />
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    title: string
    showBack?: boolean
    rightText?: string
  }>(),
  {
    showBack: true,
    rightText: '',
  }
)

const emit = defineEmits<{
  (e: 'click-right'): void
}>()

const router = useRouter()

function onClickLeft() {
  router.back()
}
</script>
