<template>
  <div class="tab-switcher">
    <div class="tab-item" :class="{ active: activeTab === 'grids' }" @click="$emit('update:activeTab', 'grids')">
      网格状态
    </div>
    <div class="tab-item" :class="{ active: activeTab === 'records' }" @click="$emit('update:activeTab', 'records')">
      成交记录
    </div>
  </div>
</template>

<script setup>
defineProps({
  activeTab: {
    type: String,
    default: 'grids'
  }
})

defineEmits(['update:activeTab'])
</script>

<style scoped>
.tab-switcher {
  display: flex;
  background: white;
  margin: 0 16px 12px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  transition: all 0.3s;
}

.tab-item.active {
  background: #409eff;
  color: white;
  font-weight: 500;
}
</style>
