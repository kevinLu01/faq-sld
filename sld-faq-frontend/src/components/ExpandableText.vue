<template>
  <div class="expandable-text">
    <div
      :class="['text-content', textClass, { collapsed: !expanded && needsCollapse }]"
      v-text="text"
    />
    <span v-if="needsCollapse" class="toggle-btn" @click="expanded = !expanded">
      {{ expanded ? '收起' : '展开全文' }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = withDefaults(
  defineProps<{
    text: string
    maxLength?: number
    textClass?: string
  }>(),
  {
    maxLength: 200,
    textClass: '',
  }
)

const expanded = ref(false)

const needsCollapse = computed(() => props.text.length > props.maxLength)
</script>

<style scoped>
.expandable-text {
  width: 100%;
}

.text-content {
  font-size: 14px;
  color: #323233;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.text-content.collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.toggle-btn {
  display: inline-block;
  margin-top: 6px;
  font-size: 13px;
  color: #1989fa;
  cursor: pointer;
  user-select: none;
}

/* Source text variant */
:deep(.source-text) {
  font-size: 13px;
  color: #646566;
  background: #f7f8fa;
  border-radius: 4px;
  padding: 10px;
  border-left: 3px solid #d9d9d9;
}
</style>
