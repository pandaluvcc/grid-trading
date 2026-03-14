<template>
  <div class="record-list">
    <div v-if="records.length === 0" class="empty-records">暂无成交记录</div>
    <div
      v-for="record in records"
      :key="record.id"
      class="record-card"
      :class="isBuy(record.type) ? 'record-buy' : 'record-sell'"
      @click="$emit('edit-fee', record)"
    >
      <!-- 第一行：交易类型 | 交易时间 -->
      <div class="record-row row-header">
        <span class="type-tag" :class="isBuy(record.type) ? 'tag-buy' : 'tag-sell'">
          {{ getTypeLabel(record.type) }}
        </span>
        <span class="trade-time">{{ formatTime(record.tradeTime) }}</span>
      </div>

      <!-- 第二行：数量 | 金额 -->
      <div class="record-row row-detail">
        <span class="label">数量</span>
        <span class="value quantity">{{ formatQuantity(record.quantity) }}股</span>
        <span class="label">金额</span>
        <span class="value amount">¥{{ formatAmount(record.amount) }}</span>
      </div>

      <!-- 第三行：价格 | 费用 -->
      <div class="record-row row-detail">
        <span class="label">价格</span>
        <span class="value price">¥{{ formatPrice(record.price) }}</span>
        <span class="label">费用</span>
        <span v-if="record.fee" class="value fee">¥{{ Number(record.fee).toFixed(2) }}</span>
        <span v-else class="value fee-hint">点击录入</span>
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

const isBuy = (type) => {
  return type === 'BUY' || type === 'OPENING_BUY'
}

const getTypeLabel = (type) => {
  if (type === 'OPENING_BUY') return '建仓'
  if (type === 'BUY') return '买入'
  return '卖出'
}
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

.record-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  cursor: pointer;
  transition: all 0.25s ease;
  border-left: 4px solid transparent;
  position: relative;
  overflow: hidden;
}

.record-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
}

.record-card:active {
  transform: scale(0.99);
}

/* 买入样式 - 红色系 */
.record-card.record-buy {
  border-left-color: #f56c6c;
  background: linear-gradient(135deg, #fff 0%, #fff5f5 100%);
}

/* 卖出样式 - 蓝色系 */
.record-card.record-sell {
  border-left-color: #409eff;
  background: linear-gradient(135deg, #fff 0%, #f0f7ff 100%);
}

/* 第一行 */
.record-row.row-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.type-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.type-tag.tag-buy {
  background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
  color: #fff;
}

.type-tag.tag-sell {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: #fff;
}

.trade-time {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

/* 第二、三行 */
.record-row.row-detail {
  display: grid;
  grid-template-columns: 36px 1fr 36px 1fr;
  align-items: center;
  margin-bottom: 6px;
}

.record-row.row-detail:last-child {
  margin-bottom: 0;
}

.label {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
}

.value.quantity {
  color: #606266;
}

.value.amount {
  color: #303133;
}

.value.price {
  color: #303133;
}

.value.fee {
  color: #e6a23c;
}

.value.fee-hint {
  color: #c0c4cc;
  font-size: 12px;
  font-style: italic;
  font-weight: 400;
}

/* 买入卡片内的价格高亮 */
.record-buy .value.price {
  color: #f56c6c;
}

/* 卖出卡片内的价格高亮 */
.record-sell .value.price {
  color: #409eff;
}
</style>
