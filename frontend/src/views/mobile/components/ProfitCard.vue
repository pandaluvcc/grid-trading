<template>
  <div class="profit-card">
    <div class="profit-row">
      <div class="profit-col">
        <div class="profit-label">证券市值</div>
        <div class="profit-value market-value">¥{{ formatAmount(totalMarketValue) }}</div>
      </div>
      <div class="profit-col">
        <div class="profit-label">已实现收益</div>
        <div class="profit-value" :class="getProfitClass(totalPositionProfit)">
          {{ formatProfit(totalPositionProfit) }}
        </div>
        <div class="profit-sub" :class="getProfitClass(todayProfit)">
          <span>今日 {{ formatProfit(todayProfit) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { formatAmount } from '@/utils/format'

defineProps({
  totalMarketValue: {
    type: [Number, String],
    default: 0
  },
  totalPositionProfit: {
    type: [Number, String],
    default: 0
  },
  todayProfit: {
    type: [Number, String],
    default: 0
  }
})

const formatProfit = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  const num = Number(val)
  return isNaN(num) ? '--' : `${num >= 0 ? '+' : ''}${num.toFixed(2)}`
}

// 获取盈亏颜色类：正数红色，负数绿色，零值灰色
const getProfitClass = (val) => {
  if (val === null || val === undefined || val === '') return 'profit-zero'
  const num = Number(val)
  if (isNaN(num) || num === 0) return 'profit-zero'
  return num > 0 ? 'profit-positive' : 'profit-negative'
}
</script>

<style scoped>
.profit-card {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  padding: 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.profit-row {
  display: flex;
  gap: 40px;
}

.profit-col {
  flex: 1;
}

.profit-label {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 8px;
}

.profit-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 4px;
}

.profit-value.market-value {
  color: white;
}

.profit-sub {
  font-size: 12px;
  opacity: 0.9;
}
</style>
