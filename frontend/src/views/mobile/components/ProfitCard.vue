<template>
  <div class="profit-card">
    <!-- 核心数据：持仓盈亏 -->
    <div class="profit-main">
      <div class="profit-label">持仓盈亏</div>
      <div class="profit-big-value" :class="getProfitClass(totalPositionProfit)">
        {{ formatProfit(totalPositionProfit) }}
      </div>
    </div>

    <!-- 分隔线 -->
    <div class="profit-divider"></div>

    <!-- 次要数据 -->
    <div class="profit-sub">
      <div class="sub-item">
        <div class="profit-label">今日盈亏</div>
        <div class="profit-value" :class="getProfitClass(todayProfit)">
          {{ formatProfit(todayProfit) }}
        </div>
      </div>
      <div class="sub-item">
        <div class="profit-label">证券市值</div>
        <div class="profit-value market-value">¥{{ formatAmount(totalMarketValue) }}</div>
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
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 24px;
  gap: 40px;
}

.profit-main {
  text-align: center;
}

.profit-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.profit-big-value {
  font-size: 28px;
  font-weight: 700;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  letter-spacing: -0.5px;
}

.profit-divider {
  width: 1px;
  height: 48px;
  background: var(--border-light);
}

.profit-sub {
  display: flex;
  gap: 24px;
}

.sub-item {
  text-align: center;
}

.profit-value {
  font-size: 16px;
  font-weight: 600;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
}

.profit-value.market-value {
  color: var(--text-primary);
}

/* 盈亏颜色 */
.profit-positive {
  color: var(--profit-positive);
}

.profit-negative {
  color: var(--profit-negative);
}

.profit-zero {
  color: var(--text-secondary);
}
</style>
