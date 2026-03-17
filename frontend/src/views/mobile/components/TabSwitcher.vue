<template>
  <div class="tab-switcher">
    <div
      class="tab-item"
      :class="{ active: activeTab === 'grids' }"
      @click="$emit('update:activeTab', 'grids')"
    >
      网格计划
    </div>
    <div
      class="tab-item"
      :class="{ active: activeTab === 'records' }"
      @click="$emit('update:activeTab', 'records')"
    >
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
  margin: 0 16px 16px;
  background: var(--bg-light);
  border-radius: 10px;
  padding: 4px;
  transition: background-color var(--transition-base);
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.tab-item.active {
  background: var(--bg-card);
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
</style>