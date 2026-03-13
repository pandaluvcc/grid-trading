<template>
  <div class="execute-card">
    <div class="execute-title">
      <el-icon><Promotion /></el-icon>
      <span>价格触发</span>
    </div>
    <div class="execute-form">
      <el-input
        v-model="localPriceInput"
        type="number"
        placeholder="输入当前价格"
        size="large"
        class="price-input"
        @change="handlePriceChange"
      >
        <template #prefix>¥</template>
      </el-input>
      <el-button type="primary" size="large" class="execute-btn" :loading="executing" @click="handleExecute">
        执行
      </el-button>
    </div>
    <div class="execute-hint">输入价格后系统将自动判断买卖</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Promotion } from '@element-plus/icons-vue'

const props = defineProps({
  priceInput: {
    type: [Number, String],
    default: ''
  },
  executing: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:priceInput', 'price-change', 'execute'])

const localPriceInput = ref(props.priceInput)

watch(
  () => props.priceInput,
  (newVal) => {
    localPriceInput.value = newVal
  }
)

const handlePriceChange = () => {
  emit('update:priceInput', localPriceInput.value)
  emit('price-change', localPriceInput.value)
}

const handleExecute = () => {
  emit('execute', localPriceInput.value)
}
</script>

<style scoped>
.execute-card {
  background: white;
  margin: 12px 16px;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.execute-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #303133;
}

.execute-title .el-icon {
  color: #409eff;
}

.execute-form {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.price-input {
  flex: 1;
}

.execute-btn {
  width: 100px;
  font-weight: 600;
}

.execute-hint {
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>
