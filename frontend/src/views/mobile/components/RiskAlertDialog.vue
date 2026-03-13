<template>
  <el-dialog v-model="visible" title="风险提示" width="90%" @close="$emit('update:modelValue', false)">
    <div class="risk-list">
      <div v-for="(risk, index) in risks" :key="index" class="risk-item">
        <el-icon class="risk-icon"><Warning /></el-icon>
        <div class="risk-content">
          <div class="risk-title">{{ risk.title }}</div>
          <div class="risk-description">{{ risk.description }}</div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button type="primary" @click="$emit('update:modelValue', false)"> 我知道了 </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Warning } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  risks: {
    type: Array,
    default: () => []
  }
})

defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})
</script>

<style scoped>
.risk-list {
  padding: 16px 0;
}

.risk-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fef0f0;
  border-radius: 8px;
  margin-bottom: 8px;
}

.risk-icon {
  color: #f56c6c;
  font-size: 20px;
  flex-shrink: 0;
}

.risk-content {
  flex: 1;
}

.risk-title {
  font-weight: 600;
  color: #c45656;
  margin-bottom: 4px;
}

.risk-description {
  font-size: 13px;
  color: #de7a7a;
  line-height: 1.5;
}
</style>
