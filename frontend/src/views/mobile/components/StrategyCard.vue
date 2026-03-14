<template>
  <div class="strategy-card" :class="{ compact: layout === 'compact' }" @click="$emit('click', strategy)">
    <div class="card-top">
      <div class="card-title">
        <div class="title-row">
          <span class="strategy-name">{{ strategy.name || strategy.symbol }}</span>
          <span class="market-value">市值 ¥{{ formatAmount(marketValue) }}</span>
        </div>
        <div class="strategy-code" v-if="strategy.name">{{ strategy.symbol }}</div>
      </div>
      <div class="card-status">
        <div class="strategy-icons" v-if="suggestions || risks?.length">
          <el-tooltip v-if="risks?.length > 0" :content="risksTooltip" placement="top" effect="dark">
            <span class="suggestion-icon risk-icon" @click.stop>
              <el-icon color="#e6a23c"><Warning /></el-icon>
              <span class="icon-count">{{ risks.length }}</span>
            </span>
          </el-tooltip>
          <el-tooltip
            v-if="suggestionsCount > 0"
            :content="`有${suggestionsCount}条建议操作`"
            placement="top"
            effect="dark"
          >
            <span class="suggestion-icon action-icon" @click.stop>
              <el-icon color="#409eff"><Bell /></el-icon>
              <span class="icon-count">{{ suggestionsCount }}</span>
            </span>
          </el-tooltip>
        </div>
        <el-tag size="small" :type="strategy.status === 'RUNNING' ? 'success' : 'info'">
          {{ strategy.status === 'RUNNING' ? '运行中' : '已停止' }}
        </el-tag>
      </div>
    </div>

    <div class="price-row">
      <span class="current-price">¥{{ formatPrice(strategy.lastPrice || strategy.basePrice) }}</span>
      <span class="price-change" :class="priceChangeClass">
        {{ priceChangeText }}
      </span>
    </div>

    <div class="stats-row">
      <div class="stat-item">
        <span class="stat-label">成本</span>
        <span class="stat-value">¥{{ formatPrice(strategy.costPrice) }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">持仓</span>
        <span class="stat-value">{{ formatQuantity(strategy.position) }}股</span>
      </div>
      <div class="stat-item profit">
        <span class="stat-label">盈亏</span>
        <span class="stat-value" :class="getProfitClass(strategy.positionProfit)">
          {{ formatProfit(strategy.positionProfit) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, Bell } from '@element-plus/icons-vue'
import { formatPrice, formatQuantity, formatAmount } from '@/utils/format'

const props = defineProps({
  strategy: {
    type: Object,
    required: true
  },
  layout: {
    type: String,
    default: 'compact' // compact / detailed
  },
  suggestions: {
    type: Object,
    default: null
  },
  risks: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['click'])

const suggestionsCount = computed(() => {
  if (!props.suggestions) return 0
  return (props.suggestions.buyCount || 0) + (props.suggestions.sellCount || 0)
})

const marketValue = computed(() => {
  return props.strategy.marketValue || props.strategy.position * (props.strategy.lastPrice || props.strategy.basePrice)
})

const priceChangeClass = computed(() => {
  const lastPrice = props.strategy.lastPrice || 0
  const costPrice = props.strategy.costPrice || 0
  if (lastPrice > costPrice) return 'up'
  if (lastPrice < costPrice) return 'down'
  return ''
})

const priceChangeText = computed(() => {
  const lastPrice = props.strategy.lastPrice || 0
  const costPrice = props.strategy.costPrice || 0
  if (costPrice === 0) return '--'
  const change = lastPrice - costPrice
  const changePercent = (change / costPrice) * 100
  const sign = change >= 0 ? '+' : ''
  return `${sign}${changePercent.toFixed(3)}%`
})

const risksTooltip = computed(() => {
  return props.risks.map((r) => r.title).join('；')
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
.strategy-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: all 0.2s;
}

.strategy-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.strategy-card.compact {
  margin-bottom: 8px;
  padding: 12px 16px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.card-title {
  flex: 1;
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.strategy-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.market-value {
  font-size: 12px;
  color: #606266;
  font-weight: 500;
}

.strategy-code {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.card-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.strategy-icons {
  display: flex;
  gap: 4px;
}

.suggestion-icon {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.icon-count {
  position: absolute;
  top: -6px;
  right: -6px;
  background: #f56c6c;
  color: white;
  font-size: 10px;
  padding: 1px 3px;
  border-radius: 6px;
  min-width: 12px;
  text-align: center;
}

.price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.current-price {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.price-change {
  font-size: 14px;
  font-weight: 500;
}

.price-change.up {
  color: #f56c6c;
}

.price-change.down {
  color: #67c23a;
}

.stats-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.stat-label {
  font-size: 11px;
  color: #909399;
}

.stat-value {
  font-size: 12px;
  font-weight: 500;
  color: #303133;
}
</style>
