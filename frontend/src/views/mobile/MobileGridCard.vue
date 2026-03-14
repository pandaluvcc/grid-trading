<template>
  <div class="grid-card" :class="[gridTypeClass, stateClass]">
    <!-- 第一行：序号+类型 | 轮次+数量 -->
    <div class="row row-header">
      <div class="left-info">
        <span class="level-badge">{{ grid.level }}</span>
        <span class="type-tag" :class="gridTypeClass">{{ typeLabel }}</span>
      </div>
      <div class="right-info">
        <span class="cycle-tag" :class="cycleClass">{{ cycleText }}</span>
        <span class="quantity-tag">{{ formatQuantity(grid.quantity) }}股</span>
      </div>
    </div>

    <!-- 第二行：买入价 | 实际收益 -->
    <div class="row row-price">
      <div class="price-item">
        <span class="price-label">买</span>
        <span class="price-value buy" :class="{ 'price-deviation': hasBuyDeviation }">
          {{ formatPrice(displayBuyPrice) }}
          <span v-if="showBuyCheck" class="check-mark">✅</span>
        </span>
        <span v-if="hasBuyDeviation" class="deviation-badge">{{ buyDeviation }}</span>
      </div>
      <div class="profit-item">
        <span class="profit-label">实</span>
        <span class="profit-value" :class="getProfitClass(actualProfit)">
          {{ actualProfit >= 0 ? '+' : '' }}{{ formatAmount(actualProfit) }}
        </span>
      </div>
    </div>

    <!-- 第三行：卖出价 | 预计收益 -->
    <div class="row row-price">
      <div class="price-item">
        <span class="price-label">卖</span>
        <span class="price-value sell" :class="{ 'price-deviation': hasSellDeviation }">
          {{ formatPrice(displaySellPrice) }}
          <span v-if="showSellCheck" class="check-mark">✅</span>
        </span>
        <span v-if="hasSellDeviation" class="deviation-badge">{{ sellDeviation }}</span>
      </div>
      <div class="profit-item">
        <span class="profit-label">预</span>
        <span class="profit-value" :class="getProfitClass(expectedProfit)">
          {{ expectedProfit >= 0 ? '+' : '' }}{{ formatAmount(expectedProfit) }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  grid: {
    type: Object,
    required: true
  }
})

// 网格类型样式
const gridTypeClass = computed(() => {
  const map = {
    SMALL: 'type-small',
    MEDIUM: 'type-medium',
    LARGE: 'type-large'
  }
  return map[props.grid.gridType] || 'type-small'
})

// 网格类型文字
const typeLabel = computed(() => {
  const map = {
    SMALL: '小网',
    MEDIUM: '中网',
    LARGE: '大网'
  }
  return map[props.grid.gridType] || '小网'
})

// 状态样式
const stateClass = computed(() => {
  const state = props.grid.state
  if (state === 'BOUGHT' || state === 'WAIT_SELL') {
    return 'state-bought'
  }
  return 'state-wait'
})

// 买入次数
const buyCount = computed(() => {
  return props.grid.buyCount || 0
})

// 卖出次数
const sellCount = computed(() => {
  return props.grid.sellCount || 0
})

// 完成轮次
const completedCycles = computed(() => {
  return Math.min(buyCount.value, sellCount.value)
})

// 显示买入✅
const showBuyCheck = computed(() => buyCount.value > sellCount.value)
const showSellCheck = computed(() => sellCount.value > buyCount.value)

// 偏差阈值
const DEVIATION_THRESHOLD = 0.01

// 显示的买入价
const displayBuyPrice = computed(() => {
  return props.grid.actualBuyPrice || props.grid.buyPrice
})

// 显示的卖出价
const displaySellPrice = computed(() => {
  if (props.grid.state === 'WAIT_BUY') {
    return props.grid.sellPrice
  }
  return props.grid.actualSellPrice || props.grid.sellPrice
})

// 买入价是否有偏差
const hasBuyDeviation = computed(() => {
  if (!props.grid.actualBuyPrice) return false
  if (!props.grid.buyPrice) return false
  const diff = Math.abs(Number(props.grid.actualBuyPrice) - Number(props.grid.buyPrice))
  return diff > DEVIATION_THRESHOLD
})

// 卖出价是否有偏差
const hasSellDeviation = computed(() => {
  if (props.grid.state === 'WAIT_BUY') return false
  if (!props.grid.actualSellPrice) return false
  if (!props.grid.sellPrice) return false
  const diff = Math.abs(Number(props.grid.actualSellPrice) - Number(props.grid.sellPrice))
  return diff > DEVIATION_THRESHOLD
})

// 买入价偏差
const buyDeviation = computed(() => {
  if (!hasBuyDeviation.value) return ''
  const actual = Number(props.grid.actualBuyPrice)
  const plan = Number(props.grid.buyPrice)
  const diff = actual - plan
  const sign = diff > 0 ? '+' : ''
  return `${sign}${diff.toFixed(3)}`
})

// 卖出价偏差
const sellDeviation = computed(() => {
  if (!hasSellDeviation.value) return ''
  const actual = Number(props.grid.actualSellPrice)
  const plan = Number(props.grid.sellPrice)
  const diff = actual - plan
  const sign = diff > 0 ? '+' : ''
  return `${sign}${diff.toFixed(3)}`
})

// 轮次文字
const cycleText = computed(() => {
  return `${completedCycles.value}轮`
})

// 轮次标签样式
const cycleClass = computed(() => {
  const cycles = completedCycles.value
  if (cycles > 0) {
    return 'has-cycles'
  }
  return 'no-cycles'
})

// 实际收益
const actualProfit = computed(() => {
  return props.grid.actualProfit ?? 0
})

// 预计收益
const expectedProfit = computed(() => {
  return props.grid.expectedProfit ?? props.grid.profit ?? 0
})

// 格式化
const formatPrice = (val) => {
  if (val == null) return '-'
  return Number(val).toFixed(3)
}

const formatAmount = (val) => {
  if (val == null) return '0'
  const num = Number(val)
  return num.toFixed(2).replace(/\.?0+$/, '')
}

const formatQuantity = (val) => {
  if (val == null) return '0'
  const num = Number(val)
  return Math.floor(num).toLocaleString()
}

// 获取盈亏颜色类
const getProfitClass = (val) => {
  if (val === null || val === undefined || val === '') return 'profit-zero'
  const num = Number(val)
  if (isNaN(num) || num === 0) return 'profit-zero'
  return num > 0 ? 'profit-positive' : 'profit-negative'
}
</script>

<style scoped>
.grid-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border-left: 4px solid transparent;
  transition: all 0.25s ease;
}

.grid-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
}

/* 网格类型左边框颜色 */
.grid-card.type-small {
  border-left-color: #409eff;
}

.grid-card.type-medium {
  border-left-color: #e6a23c;
}

.grid-card.type-large {
  border-left-color: #f56c6c;
}

/* 已买入状态背景 */
.grid-card.state-bought {
  background: linear-gradient(135deg, #fff 0%, #fffbe6 100%);
}

/* 行布局 */
.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.row-header {
  margin-bottom: 12px;
}

.row-price {
  margin-bottom: 8px;
}

.row-price:last-child {
  margin-bottom: 0;
}

/* 第一行左侧 */
.left-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.level-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #606266;
  font-size: 13px;
  font-weight: 700;
}

.type-tag {
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.type-tag.type-small {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
}

.type-tag.type-medium {
  background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
}

.type-tag.type-large {
  background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
}

/* 第一行右侧 */
.right-info {
  display: flex;
  align-items: center;
  gap: 6px;
}

.cycle-tag {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.cycle-tag.no-cycles {
  background: #f0f2f5;
  color: #909399;
}

.cycle-tag.has-cycles {
  background: #e1f3d8;
  color: #67c23a;
}

.quantity-tag {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

/* 价格行 */
.price-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.price-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.price-value {
  font-size: 15px;
  font-weight: 700;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  display: flex;
  align-items: center;
  gap: 4px;
}

.price-value.buy {
  color: #f56c6c;
}

.price-value.sell {
  color: #409eff;
}

.price-value.price-deviation {
  text-shadow: 0 0 2px currentColor;
}

.check-mark {
  font-size: 11px;
}

.deviation-badge {
  font-size: 10px;
  color: #e6a23c;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 3px;
  padding: 1px 4px;
  font-weight: 500;
}

/* 收益行 */
.profit-item {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 80px;
}

.profit-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
  width: 18px;
  flex-shrink: 0;
}

.profit-value {
  font-size: 14px;
  font-weight: 700;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  min-width: 60px;
  text-align: right;
}

.profit-value.profit-zero {
  color: #909399;
}

.profit-value.profit-positive {
  color: #f56c6c;
}

.profit-value.profit-negative {
  color: #67c23a;
}
</style>
