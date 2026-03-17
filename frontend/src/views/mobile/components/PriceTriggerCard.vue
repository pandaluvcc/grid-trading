<template>
  <div class="execute-card">
    <div class="execute-form">
      <div class="input-wrapper">
        <span class="input-prefix">¥</span>
        <el-input
          v-model="localPriceInput"
          type="number"
          placeholder="输入当前价格"
          size="large"
          class="price-input"
          @change="handlePriceChange"
        />
      </div>
      <el-button
        type="primary"
        size="large"
        class="execute-btn"
        :loading="executing"
        @click="handleExecute"
      >
        执行
      </el-button>
    </div>
    <!-- 建议提示 -->
    <div v-if="suggestion && suggestion.type" class="suggestion-hint" :class="suggestion.type.toLowerCase()">
      <span class="suggestion-type">{{ suggestion.type === 'BUY' ? '买入' : '卖出' }}</span>
      <span class="suggestion-info">第{{ suggestion.gridLevel }}网 · ¥{{ formatPrice(suggestion.price) }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { formatPrice } from '@/utils/format'

const props = defineProps({
  priceInput: {
    type: [Number, String],
    default: ''
  },
  executing: {
    type: Boolean,
    default: false
  },
  suggestion: {
    type: Object,
    default: null
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
  background: var(--bg-card);
  margin: 16px;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: background-color var(--transition-base);
}

.execute-form {
  display: flex;
  gap: 12px;
  align-items: center;
}

.input-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  background: var(--bg-light);
  border-radius: 12px;
  padding: 0 16px;
  transition: background-color var(--transition-base);
}

.input-prefix {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-right: 8px;
}

.price-input {
  flex: 1;
}

.price-input :deep(.el-input__wrapper) {
  background: transparent;
  box-shadow: none;
  padding: 0;
}

.price-input :deep(.el-input__inner) {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.execute-btn {
  height: 48px;
  padding: 0 28px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  background: var(--primary-color);
  border: none;
}

.execute-btn:hover {
  background: var(--primary-color);
  opacity: 0.9;
}

/* 建议提示 */
.suggestion-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
}

.suggestion-hint.buy {
  background: rgba(245, 108, 108, 0.08);
}

.suggestion-hint.sell {
  background: rgba(103, 194, 58, 0.08);
}

.suggestion-type {
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 4px;
}

.suggestion-hint.buy .suggestion-type {
  background: var(--profit-positive);
  color: white;
}

.suggestion-hint.sell .suggestion-type {
  background: var(--profit-negative);
  color: white;
}

.suggestion-info {
  color: var(--text-secondary);
}
</style>