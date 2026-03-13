<template>
  <el-button
    v-bind="$attrs"
    :size="size"
    :type="type"
    :round="round"
    :circle="circle"
    :loading="loading"
    :disabled="disabled"
    :icon="icon"
    @click="$emit('click', $event)"
  >
    <slot />
  </el-button>
</template>

<script setup>
defineProps({
  size: {
    type: String,
    default: 'default' // default / small / large
  },
  type: {
    type: String,
    default: 'default' // primary / success / warning / danger / info / text
  },
  round: {
    type: Boolean,
    default: false
  },
  circle: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  disabled: {
    type: Boolean,
    default: false
  },
  icon: {
    type: [Object, String],
    default: null
  }
})

defineEmits(['click'])
</script>

<style scoped>
/* 统一按钮样式 */
:deep(.el-button) {
  border-radius: 8px;
  font-weight: 500;
  transition: all 0.2s ease;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #5568d3 0%, #6a3f8f 100%);
}

:deep(.el-button--small) {
  padding: 7px 15px;
  font-size: 12px;
}

:deep(.el-button--large) {
  padding: 12px 24px;
  font-size: 16px;
}
</style>
