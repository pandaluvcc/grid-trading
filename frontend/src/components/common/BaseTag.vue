<template>
  <el-tag v-bind="$attrs" :type="type" :size="size" :effect="effect" :round="round">
    <slot />
  </el-tag>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    default: 'primary' // success / warning / danger / info
  },
  size: {
    type: String,
    default: 'default' // small / default / large
  },
  effect: {
    type: String,
    default: 'light' // dark / light / plain
  },
  round: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
/* 统一标签样式 */
:deep(.el-tag) {
  border-radius: 4px;
  font-weight: 500;
}

:deep(.el-tag--success) {
  background-color: #f0f9ff;
  border-color: #90cdf4;
  color: #2b6cb0;
}

:deep(.el-tag--warning) {
  background-color: #fffbeb;
  border-color: #fcd34d;
  color: #92400e;
}

:deep(.el-tag--danger) {
  background-color: #fef2f2;
  border-color: #fca5a5;
  color: #991b1b;
}

:deep(.el-tag--info) {
  background-color: #f3f4f6;
  border-color: #d1d5db;
  color: #1f2937;
}
</style>
