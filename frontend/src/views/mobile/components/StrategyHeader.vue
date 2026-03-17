<template>
  <div class="broker-header">
    <!-- 顶部导航 -->
    <div class="header-nav">
      <div class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </div>
      <div class="header-center">
        <div class="symbol-name">{{ strategy.name || strategy.symbol }}</div>
        <div class="symbol-code">{{ strategy.symbol }}</div>
      </div>
      <div class="header-right">
        <div class="risk-icon-wrapper" v-if="risks && risks.length > 0" @click="$emit('show-risk')">
          <el-icon class="risk-icon"><Warning /></el-icon>
          <span class="risk-count">{{ risks.length }}</span>
        </div>
      </div>
    </div>

    <!-- 盈亏核心数据 -->
    <div class="profit-section">
      <div class="profit-main">
        <div class="profit-label">持仓盈亏</div>
        <div class="profit-value" :class="getProfitClass(positionProfit)">
          {{ formatProfit(positionProfit) }}
        </div>
        <div class="profit-percent" :class="getProfitClass(positionProfitPercentValue)">
          {{ positionProfitPercent }}
        </div>
      </div>
      <div class="profit-divider"></div>
      <div class="profit-sub">
        <div class="profit-label">今日盈亏</div>
        <div class="profit-value sub" :class="getProfitClass(todayProfit)">
          {{ formatProfit(todayProfit) }}
        </div>
        <div class="profit-percent sub" :class="getProfitClass(todayProfitPercentValue)">
          {{ todayProfitPercent }}
        </div>
      </div>
    </div>

    <!-- 现价输入 -->
    <div class="price-section">
      <div class="price-row">
        <div class="price-item">
          <span class="price-label">现价</span>
          <el-input
            v-model="localPriceInput"
            type="number"
            size="small"
            class="price-input"
            placeholder="输入"
            @change="handlePriceChange"
          />
        </div>
        <div class="price-item">
          <span class="price-label">成本</span>
          <span class="price-value">¥{{ formatPrice(costPrice) }}</span>
        </div>
      </div>
    </div>

    <!-- 统计数据 -->
    <div class="stats-row">
      <div class="stat-item">
        <span class="stat-value">{{ holdingDays }}<span class="stat-unit">天</span></span>
        <span class="stat-label">持股</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-value">{{ positionRatio }}<span class="stat-unit">%</span></span>
        <span class="stat-label">仓位</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-value">¥{{ formatFee(totalFee) }}</span>
        <span class="stat-label">税费</span>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <span class="stat-value">¥{{ formatPrice(averageBuyPrice) }}</span>
        <span class="stat-label">买入均价</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Warning, ArrowLeft } from '@element-plus/icons-vue'
import { formatPrice, formatFee } from '@/utils/format'

const router = useRouter()

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

const goBack = () => {
  router.push('/m')
}
</script>

<style scoped>
.broker-header {
  background: var(--bg-card);
  padding: 0 0 16px;
  border-radius: 0 0 20px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: background-color var(--transition-base);
}

/* 顶部导航 */
.header-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  padding-top: calc(12px + env(safe-area-inset-top));
  border-bottom: 1px solid var(--border-lighter);
}

.back-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-light);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.back-btn:active {
  transform: scale(0.95);
  background: var(--border-light);
}

.back-btn .el-icon {
  font-size: 18px;
  color: var(--text-primary);
}

.header-center {
  flex: 1;
  text-align: center;
}

.symbol-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}

.symbol-code {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.header-right {
  width: 36px;
  display: flex;
  justify-content: flex-end;
}

.risk-icon-wrapper {
  position: relative;
  cursor: pointer;
}

.risk-icon {
  font-size: 22px;
  color: var(--warning-color);
}

.risk-count {
  position: absolute;
  top: -4px;
  right: -8px;
  background: var(--danger-color);
  color: white;
  font-size: 10px;
  font-weight: 600;
  min-width: 16px;
  height: 16px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 4px;
}

/* 盈亏核心数据 */
.profit-section {
  display: flex;
  justify-content: center;
  padding: 20px 24px;
  gap: 48px;
}

.profit-main {
  text-align: center;
}

.profit-divider {
  width: 1px;
  background: var(--border-light);
}

.profit-sub {
  text-align: center;
}

.profit-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.profit-value {
  font-size: 28px;
  font-weight: 700;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  letter-spacing: -0.5px;
}

.profit-value.sub {
  font-size: 22px;
}

.profit-percent {
  font-size: 14px;
  font-weight: 500;
  margin-top: 4px;
}

.profit-percent.sub {
  font-size: 13px;
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

/* 现价输入 */
.price-section {
  padding: 0 16px;
  margin-bottom: 16px;
}

.price-row {
  display: flex;
  gap: 24px;
  padding: 12px 16px;
  background: var(--bg-light);
  border-radius: 12px;
  transition: background-color var(--transition-base);
}

.price-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.price-label {
  font-size: 13px;
  color: var(--text-secondary);
}

.price-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.price-input {
  width: 80px;
}

.price-input :deep(.el-input__wrapper) {
  background: var(--bg-card);
  border-radius: 8px;
  box-shadow: none;
  padding: 4px 10px;
}

.price-input :deep(.el-input__inner) {
  font-size: 14px;
  font-weight: 600;
  text-align: right;
  color: var(--text-primary);
}

/* 统计数据 */
.stats-row {
  display: flex;
  align-items: center;
  padding: 0 16px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-unit {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
  margin-left: 2px;
}

.stat-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: var(--border-light);
}
</style>