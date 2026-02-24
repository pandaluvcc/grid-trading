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
        <span class="price-value buy">{{ formatPrice(grid.buyPrice) }}</span>
      </div>
      <div class="price-row">
        <span class="price-label">卖</span>
        <span class="price-value sell">{{ formatPrice(grid.sellPrice) }}</span>
      </div>
    </div>

    <!-- 右侧状态和收益 -->
    <div class="card-right">
      <div class="state-tag" :class="stateClass">
        {{ stateLabel }}
      </div>
      <div class="profit">
        +{{ formatAmount(grid.profit) }}
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

// 状态文字
const stateLabel = computed(() => {
  const map = {
    WAIT_BUY: '待买',
    BOUGHT: '已买',
    WAIT_SELL: '已买',
    SOLD: '已卖'
  }
  return map[props.grid.state] || '待买'
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
}

.price-value.buy {
  color: #f56c6c;
}

.price-value.sell {
  color: #67c23a;
}

/* 右侧 */
.card-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.state-tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
}

.state-tag.state-wait {
  background: #f0f2f5;
  color: #909399;
}

.state-tag.state-bought {
  background: #fdf6ec;
  color: #e6a23c;
}

.profit {
  font-size: 14px;
  font-weight: 600;
  color: #67c23a;
}
</style>
