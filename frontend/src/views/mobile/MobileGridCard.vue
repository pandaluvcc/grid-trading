<template>
  <div class="grid-card" :class="[gridTypeClass, stateClass]">
    <!-- 左侧序号和类型 -->
    <div class="card-left">
      <div class="level">{{ grid.level }}</div>
      <div class="type-badge" :class="gridTypeClass">
        {{ typeLabel }}
      </div>
    </div>

    <!-- 中间价格信息 -->
    <div class="card-center">
      <!-- 买入价 -->
      <div class="price-row">
        <span class="price-label">买</span>
        <div class="price-value-wrapper">
          <!-- 主显示：真实价格（如果有）或建议价格 -->
          <span class="price-value buy" :class="{ 'price-deviation': hasBuyDeviation }">
            {{ formatPrice(displayBuyPrice) }}
            <span v-if="showBuyCheck" class="check-mark">✅</span>
          </span>
          <!-- 副显示：当真实价格与建议价格不同时，显示建议价格（删除线） -->
          <span v-if="hasBuyDeviation" class="price-original">
            {{ formatPrice(grid.buyPrice) }}
          </span>
          <!-- 偏差提示 -->
          <span v-if="hasBuyDeviation" class="price-diff-badge"> 偏差{{ buyDeviation }} </span>
        </div>
      </div>

      <!-- 卖出价 -->
      <div class="price-row">
        <span class="price-label">卖</span>
        <div class="price-value-wrapper">
          <!-- 主显示：真实价格（如果有）或建议价格 -->
          <span class="price-value sell" :class="{ 'price-deviation': hasSellDeviation }">
            {{ formatPrice(displaySellPrice) }}
            <span v-if="showSellCheck" class="check-mark">✅</span>
          </span>
          <!-- 副显示：当真实价格与建议价格不同时，显示建议价格（删除线） -->
          <span v-if="hasSellDeviation" class="price-original">
            {{ formatPrice(grid.sellPrice) }}
          </span>
          <!-- 偏差提示 -->
          <span v-if="hasSellDeviation" class="price-diff-badge"> 偏差{{ sellDeviation }} </span>
        </div>
      </div>
    </div>

    <!-- 右侧状态和收益 -->
    <div class="card-right">
      <div class="cycle-tag" :class="cycleClass">
        {{ cycleText }}
      </div>
      <!-- 实际收益（已实现） -->
      <div class="profit actual-profit" :class="getProfitClass(actualProfit)">
        实:{{ actualProfit >= 0 ? '+' : '' }}{{ formatAmount(actualProfit) }}
      </div>
      <!-- 预计收益（浮动） -->
      <div class="profit expected-profit" :class="getProfitClass(expectedProfit)">
        预:{{ expectedProfit >= 0 ? '+' : '' }}{{ formatAmount(expectedProfit) }}
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
    SMALL: '小',
    MEDIUM: '中',
    LARGE: '大'
  }
  return map[props.grid.gridType] || '小'
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

// 完成轮次（一轮 = 一次买入 + 一次卖出）
const completedCycles = computed(() => {
  return Math.min(buyCount.value, sellCount.value)
})

// ✅ 显示逻辑：表示"当前有未配对的交易"
// 买✅：buyCount > sellCount（当前持仓待卖）
// 卖✅：sellCount > buyCount（理论情况，网格交易一般不会出现）
const showBuyCheck = computed(() => buyCount.value > sellCount.value)
const showSellCheck = computed(() => sellCount.value > buyCount.value)

// ===== ✅ 新增：价格显示和偏差计算 =====
// 偏差阈值：超过此值时标红提示
const DEVIATION_THRESHOLD = 0.01 // 0.01 = 1分

// 显示的买入价（优先真实价格）
const displayBuyPrice = computed(() => {
  return props.grid.actualBuyPrice || props.grid.buyPrice
})

// 显示的卖出价（状态为WAIT_BUY时直接用sellPrice，不使用actualSellPrice）
const displaySellPrice = computed(() => {
  if (props.grid.state === 'WAIT_BUY') {
    return props.grid.sellPrice
  }
  return props.grid.actualSellPrice || props.grid.sellPrice
})

// 买入价是否有偏差
const hasBuyDeviation = computed(() => {
  if (!props.grid.actualBuyPrice) return false // 没有真实价格，无偏差
  if (!props.grid.buyPrice) return false
  const diff = Math.abs(Number(props.grid.actualBuyPrice) - Number(props.grid.buyPrice))
  return diff > DEVIATION_THRESHOLD
})

// 卖出价是否有偏差（WAIT_BUY状态不显示偏差）
const hasSellDeviation = computed(() => {
  if (props.grid.state === 'WAIT_BUY') return false
  if (!props.grid.actualSellPrice) return false // 没有真实价格，无偏差
  if (!props.grid.sellPrice) return false
  const diff = Math.abs(Number(props.grid.actualSellPrice) - Number(props.grid.sellPrice))
  return diff > DEVIATION_THRESHOLD
})

// 买入价偏差（格式化显示）
const buyDeviation = computed(() => {
  if (!hasBuyDeviation.value) return ''
  const actual = Number(props.grid.actualBuyPrice)
  const plan = Number(props.grid.buyPrice)
  const diff = actual - plan
  const sign = diff > 0 ? '+' : ''
  return `${sign}${diff.toFixed(3)}`
})

// 卖出价偏差（格式化显示）
const sellDeviation = computed(() => {
  if (!hasSellDeviation.value) return ''
  const actual = Number(props.grid.actualSellPrice)
  const plan = Number(props.grid.sellPrice)
  const diff = actual - plan
  const sign = diff > 0 ? '+' : ''
  return `${sign}${diff.toFixed(3)}`
})

// 轮次文字（简洁显示）
const cycleText = computed(() => {
  return `${completedCycles.value}轮`
})

// 轮次标签样式（根据轮次数量显示不同颜色）
const cycleClass = computed(() => {
  const cycles = completedCycles.value
  if (cycles > 0) {
    return 'has-cycles'
  }
  return 'no-cycles'
})

// 实际收益（已实现落袋收益）
const actualProfit = computed(() => {
  return props.grid.actualProfit ?? 0
})

// 预计收益（按买卖价计算的理论收益）
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
  // 保留2位小数，去掉末尾的0
  return num.toFixed(2).replace(/\.?0+$/, '')
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
.grid-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 10px;
  padding: 12px 14px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  border-left: 4px solid #dcdfe6;
}

/* 按网格类型设置左边框颜色 */
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
  background: linear-gradient(90deg, #fff9e6 0%, #fff 100%);
}

/* 左侧 */
.card-left {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 36px;
  margin-right: 12px;
}

.level {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.type-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  margin-top: 4px;
  color: #fff;
}

.type-badge.type-small {
  background: #409eff;
}

.type-badge.type-medium {
  background: #e6a23c;
}

.type-badge.type-large {
  background: #f56c6c;
}

/* 中间 */
.card-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px; /* 增加间距以容纳偏差提示 */
}

.price-row {
  display: flex;
  align-items: flex-start; /* 改为flex-start以支持多行 */
  gap: 8px;
}

.price-label {
  font-size: 12px;
  color: #909399;
  width: 16px;
  margin-top: 2px; /* 与价格对齐 */
}

.price-value-wrapper {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.price-value {
  font-size: 15px;
  font-weight: 600;
  font-family: 'SF Mono', 'Monaco', monospace;
  display: flex;
  align-items: center;
  gap: 4px;
}

.price-value.buy {
  color: #f56c6c;
}

.price-value.sell {
  color: #67c23a;
}

/* ✅ 新增：价格偏差标红 */
.price-value.price-deviation {
  font-weight: 700;
  position: relative;
}

.price-value.price-deviation.buy {
  color: #f56c6c;
  text-shadow: 0 0 1px rgba(245, 108, 108, 0.3);
}

.price-value.price-deviation.sell {
  color: #e6a23c; /* 卖出价偏差用橙色警告 */
  text-shadow: 0 0 1px rgba(230, 162, 60, 0.3);
}

/* ✅ 新增：原始价格（删除线） */
.price-original {
  font-size: 11px;
  color: #909399;
  text-decoration: line-through;
  font-family: 'SF Mono', 'Monaco', monospace;
}

/* ✅ 新增：偏差标签 */
.price-diff-badge {
  display: inline-block;
  font-size: 10px;
  color: #e6a23c;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 3px;
  padding: 1px 4px;
  font-weight: 500;
  margin-left: 4px;
}

.check-mark {
  font-size: 12px;
}

/* 右侧 */
.card-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.cycle-tag {
  font-size: 12px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 4px;
}

.cycle-tag.no-cycles {
  background: #f0f2f5;
  color: #909399;
}

.cycle-tag.has-cycles {
  background: #e1f3d8;
  color: #67c23a;
}

.profit {
  font-size: 12px;
  font-weight: 600;
}

.profit.actual-profit {
  /* 实际收益默认绿色（正收益），通过动态类覆盖 */
}

.profit.expected-profit {
  /* 预计收益默认灰色，通过动态类覆盖 */
}
</style>
