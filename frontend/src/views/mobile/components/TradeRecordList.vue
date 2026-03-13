<template>
  <div class="record-list">
    <div v-if="records.length === 0" class="empty-records">暂无成交记录</div>
    <div v-for="record in records" :key="record.id" class="record-item" @click="$emit('edit-fee', record)">
      <div class="record-left">
        <el-tag size="small" :type="record.type === 'SELL' ? 'success' : 'danger'">
          {{ record.type === 'OPENING_BUY' ? '建仓-买入' : record.type === 'BUY' ? '买入' : '卖出' }}
        </el-tag>
        <span class="record-price">¥{{ formatPrice(record.price) }}</span>
      </div>
      <div class="record-right">
        <span class="record-amount">{{ formatQuantity(record.quantity) }}股 · {{ formatAmount(record.amount) }}元</span>
        <span v-if="record.fee" class="record-fee">费用: ¥{{ Number(record.fee).toFixed(2) }}</span>
        <span v-else class="record-fee-hint">点击录入费用</span>
        <span class="record-time">{{ formatTime(record.tradeTime) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatPrice, formatQuantity, formatAmount, formatTime } from '@/utils/format'

defineProps({
  records: {
    type: Array,
    default: () => []
  }
})

defineEmits(['edit-fee'])
</script>

<style scoped>
.record-list {
  padding: 0 16px 20px;
}

.empty-records {
  text-align: center;
  padding: 60px 0;
  color: #909399;
  font-size: 14px;
}

.record-item {
  background: white;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  cursor: pointer;
  transition: all 0.2s;
}

.record-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.record-left {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.record-price {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.record-right {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.record-amount {
  color: #606266;
}

.record-fee {
  color: #f56c6c;
}

.record-fee-hint {
  color: #909399;
  font-style: italic;
}

.record-time {
  color: #c0c4cc;
  align-self: flex-end;
}
</style>
