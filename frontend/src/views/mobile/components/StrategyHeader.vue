<template>
  <div class="broker-header">
    <div class="header-top">
      <div class="symbol-name-big">{{ strategy.name || strategy.symbol }}</div>
      <div class="symbol-code">{{ strategy.symbol }}</div>
      <div class="risk-icon-wrapper" v-if="risks && risks.length > 0" @click="$emit('show-risk')">
        <el-icon class="risk-icon"><Warning /></el-icon>
      </div>
    </div>
    <div class="symbol-sub" v-if="strategy.name">{{ strategy.name }}</div>

    <div class="divider-line"></div>

    <div class="profit-section">
      <div class="profit-col">
        <div class="profit-label">持仓盈亏</div>
        <div class="profit-value" :class="getProfitClass(positionProfit)">
          {{ formatProfit(positionProfit) }}
        </div>
        <div class="profit-percent" :class="getProfitClass(positionProfitPercentValue)">
          {{ positionProfitPercent }}
        </div>
      </div>
      <div class="profit-col">
        <div class="profit-label">当日参考盈亏</div>
        <div class="profit-value" :class="getProfitClass(todayProfit)">
          {{ formatProfit(todayProfit) }}
        </div>
        <div class="profit-percent" :class="getProfitClass(todayProfitPercentValue)">
          {{ todayProfitPercent }}
        </div>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-row">
        <div class="stat-item">
          <span class="stat-label">持股天数</span>
          <span class="stat-value">{{ holdingDays }}</span>
        </div>
        <div class="stat-item price-item">
          <span class="stat-label">现价</span>
          <div class="price-input-wrapper">
            <el-input
              v-model="localPriceInput"
              type="number"
              size="small"
              class="inline-price-input"
              @change="handlePriceChange"
            />
          </div>
        </div>
      </div>
      <div class="stat-row">
        <div class="stat-item">
          <span class="stat-label">个股仓位</span>
          <span class="stat-value">{{ positionRatio }}%</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">成本价</span>
          <span class="stat-value price-value">¥{{ formatPrice(costPrice) }}</span>
        </div>
      </div>
      <div class="stat-row">
        <div class="stat-item">
          <span class="stat-label">税费合计</span>
          <span class="stat-value fee">¥{{ formatFee(totalFee) }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">买入均价</span>
          <span class="stat-value price-value">¥{{ formatPrice(averageBuyPrice) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Warning } from '@element-plus/icons-vue'
import { formatPrice, formatFee } from '@/utils/format'

const props = defineProps({
  strategy: {
    type: Object,
    required: true
  },
  positionProfit: {
    type: [Number, String],
    default: 0
  },
  positionProfitPercent: {
    type: String,
    default: '--'
  },
  positionProfitPercentValue: {
    type: Number,
    default: 0
  },
  todayProfit: {
    type: [Number, String],
    default: 0
  },
  todayProfitPercent: {
    type: String,
    default: '--'
  },
  todayProfitPercentValue: {
    type: Number,
    default: 0
  },
  holdingDays: {
    type: [Number, String],
    default: 0
  },
  positionRatio: {
    type: [Number, String],
    default: 0
  },
  costPrice: {
    type: [Number, String],
    default: 0
  },
  totalFee: {
    type: [Number, String],
    default: 0
  },
  averageBuyPrice: {
    type: [Number, String],
    default: 0
  },
  priceInput: {
    type: [Number, String],
    default: ''
  },
  risks: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:priceInput', 'price-change', 'show-risk'])

const localPriceInput = ref(props.priceInput)

watch(
  () => props.priceInput,
  (newVal) => {
    localPriceInput.value = newVal
  }
)

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

const handlePriceChange = () => {
  emit('update:priceInput', localPriceInput.value)
  emit('price-change', localPriceInput.value)
}
</script>

<style scoped>
.broker-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px 16px 16px;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.symbol-name-big {
  font-size: 24px;
  font-weight: bold;
}

.symbol-code {
  font-size: 14px;
  opacity: 0.9;
  margin-top: 4px;
}

.risk-icon-wrapper {
  cursor: pointer;
}

.risk-icon {
  font-size: 24px;
  color: #ffd700;
}

.symbol-sub {
  font-size: 14px;
  opacity: 0.9;
  margin-bottom: 12px;
}

.divider-line {
  height: 1px;
  background: rgba(255, 255, 255, 0.2);
  margin: 12px 0;
}

.profit-section {
  display: flex;
  gap: 40px;
  margin-bottom: 20px;
}

.profit-col {
  flex: 1;
}

.profit-label {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 4px;
}

.profit-value {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 2px;
}

.profit-percent {
  font-size: 12px;
  opacity: 0.9;
}

.stats-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-row {
  display: flex;
  gap: 20px;
}

.stat-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.stat-label {
  opacity: 0.8;
}

.stat-value {
  font-weight: 500;
}

.stat-value.price-value {
  color: #ffd700;
}

.stat-value.fee {
  color: #ffd700;
}

.price-item {
  align-items: center;
}

.price-input-wrapper {
  width: 100px;
}

.inline-price-input {
  --el-input-bg-color: rgba(255, 255, 255, 0.1);
  --el-input-text-color: white;
  --el-input-border-color: rgba(255, 255, 255, 0.3);
  --el-input-placeholder-color: rgba(255, 255, 255, 0.5);
}

.inline-price-input :deep(.el-input__inner) {
  padding: 4px 8px;
  height: 28px;
  font-size: 13px;
  text-align: right;
}
</style>
