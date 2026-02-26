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
      <div class="price-row">
        <span class="price-label">买</span>
        <span class="price-value buy">
          {{ formatPrice(grid.buyPrice) }}
          <span v-if="showBuyCheck" class="check-mark">✅</span>
        </span>
      </div>
      <div class="price-row">
        <span class="price-label">卖</span>
        <span class="price-value sell">
          {{ formatPrice(grid.sellPrice) }}
          <span v-if="showSellCheck" class="check-mark">✅</span>
        </span>
      </div>
    </div>

    <!-- 右侧状态和收益 -->
    <div class="card-right">
      <div class="cycle-tag" :class="cycleClass">
        {{ cycleText }}
      </div>
      <div class="profit" :class="profitClass">
        +{{ formatAmount(displayProfit) }}
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

// 显示收益：有完成轮次时显示真实收益，否则显示预计收益
const displayProfit = computed(() => {
  const cycles = completedCycles.value
  if (cycles > 0 && props.grid.actualProfit !== undefined) {
    return props.grid.actualProfit
  }
  return props.grid.profit || 0
})

// 收益样式：区分真实收益和预计收益
const profitClass = computed(() => {
  return completedCycles.value > 0 ? 'actual-profit' : 'estimated-profit'
})

// 格式化
const formatPrice = (val) => {
  if (val == null) return '-'
  return Number(val).toFixed(3)
}

const formatAmount = (val) => {
  if (val == null) return '0'
  return Math.round(Number(val)).toString()
}
</script>

<style scoped>
.grid-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 10px;
  padding: 12px 14px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
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
  gap: 4px;
}

.price-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.price-label {
  font-size: 12px;
  color: #909399;
  width: 16px;
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

.check-mark {
  font-size: 12px;
}

/* 右侧 */
.card-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
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
  font-size: 14px;
  font-weight: 600;
  color: #67c23a;
}
</style>
