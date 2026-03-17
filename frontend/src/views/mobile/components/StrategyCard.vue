<template>
  <van-swipe-cell>
    <div class="strategy-card" @click="$emit('click', strategy)">
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

    <!-- 右侧删除按钮 -->
    <template #right>
      <van-button square type="danger" class="delete-btn" text="删除" @click="handleDelete" />
    </template>
  </van-swipe-cell>
</template>

<script setup>
import { computed } from 'vue'
import { Warning, Bell } from '@element-plus/icons-vue'
import { showConfirmDialog } from 'vant'
import { ElMessage } from 'element-plus'
import { deleteStrategy } from '@/api'
import { formatPrice, formatQuantity, formatAmount } from '@/utils/format'
import { SwipeCell as VanSwipeCell, Button as VanButton } from 'vant'

const props = defineProps({
  strategy: {
    type: Object,
    required: true
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

const emit = defineEmits(['click', 'deleted'])

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

// 处理删除
const handleDelete = async () => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除策略「${props.strategy.symbol}」吗？\n此操作不可恢复。`,
      confirmButtonText: '删除',
      confirmButtonColor: '#ee0a24',
      cancelButtonText: '取消'
    })

    // 执行删除
    await deleteStrategy(props.strategy.id)
    ElMessage.success('删除成功')

    // 通知父组件
    emit('deleted', props.strategy.id)
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败，请重试')
    }
  }
}
</script>

<style scoped>
.strategy-card {
  background: var(--bg-card);
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: transform var(--transition-fast), background-color var(--transition-fast);
}

.strategy-card:active {
  transform: scale(0.98);
  background-color: var(--bg-light);
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
  color: var(--text-primary);
}

.market-value {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
}

.strategy-code {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
}

.card-status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.strategy-icons {
  display: flex;
  gap: 6px;
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
  background: var(--danger-color);
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
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border-lighter);
}

.current-price {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
}

.price-change {
  font-size: 14px;
  font-weight: 500;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
}

.price-change.up {
  color: var(--profit-positive);
}

.price-change.down {
  color: var(--profit-negative);
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

.stat-item.profit {
  text-align: right;
}

.stat-label {
  font-size: 11px;
  color: var(--text-secondary);
}

.stat-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
}

/* 盈亏颜色 */
.stat-value.profit-positive {
  color: var(--profit-positive);
}

.stat-value.profit-negative {
  color: var(--profit-negative);
}

.stat-value.profit-zero {
  color: var(--profit-zero);
}

/* 删除按钮样式 */
.delete-btn {
  height: 100%;
  border-radius: 0 14px 14px 0;
}

/* vant SwipeCell 样式覆盖 */
:deep(.van-swipe-cell) {
  margin-bottom: 12px;
  border-radius: 14px;
  overflow: hidden;
}

:deep(.van-swipe-cell__right) {
  display: flex;
  align-items: center;
}
</style>
