<template>
  <div class="base-empty">
    <div class="empty-icon">{{ icon }}</div>
    <div class="empty-text">{{ text }}</div>
    <div v-if="description" class="empty-description">{{ description }}</div>
    <slot />
  </div>
</template>

<script setup>
defineProps({
  icon: {
    type: String,
    default: '📭'
  },
  text: {
    type: String,
    default: '暂无数据'
  },
  description: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
.base-empty {
  text-align: center;
  padding: 60px 20px;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.empty-description {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
