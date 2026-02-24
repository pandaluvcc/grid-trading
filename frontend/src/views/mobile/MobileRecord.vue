<template>
  <div class="record-page">
    <!-- 顶部栏 -->
    <div class="top-bar">
      <el-icon class="back-btn" @click="goBack"><ArrowLeft /></el-icon>
      <span class="page-title">录入成交</span>
      <span class="placeholder"></span>
    </div>

    <!-- 操作类型切换 -->
    <div class="action-switcher">
      <div 
        class="switch-item" 
        :class="{ active: actionType === 'buy' }"
        @click="actionType = 'buy'"
      >
        买入
      </div>
      <div 
        class="switch-item" 
        :class="{ active: actionType === 'sell' }"
        @click="actionType = 'sell'"
      >
        卖出
      </div>
    </div>

    <!-- 选择网格 -->
    <div class="grid-selector">
      <div class="selector-label">选择网格</div>
      <div class="grid-chips">
        <div 
          v-for="grid in availableGrids" 
          :key="grid.id"
          class="grid-chip"
          :class="{ 
            selected: selectedGrid?.id === grid.id,
            small: grid.gridType === 'SMALL',
            medium: grid.gridType === 'MEDIUM',
            large: grid.gridType === 'LARGE'
          }"
          @click="selectGrid(grid)"
        >
          <span class="chip-level">{{ grid.level }}</span>
          <span class="chip-price">{{ formatPrice(actionType === 'buy' ? grid.buyPrice : grid.sellPrice) }}</span>
        </div>
      </div>
      <div class="selector-hint" v-if="availableGrids.length === 0">
        {{ actionType === 'buy' ? '无可买入网格' : '无可卖出网格' }}
      </div>
    </div>

    <!-- 价格输入 -->
    <div class="price-input-section" v-if="selectedGrid">
      <div class="input-label">实际成交价</div>
      <div class="price-input-wrapper">
        <span class="currency">¥</span>
        <input 
          type="number" 
          v-model="inputPrice"
          class="price-input"
          :placeholder="'计划价 ' + formatPrice(planPrice)"
          inputmode="decimal"
        />
      </div>
      <div class="price-hint">
        计划价：¥{{ formatPrice(planPrice) }}
        <span v-if="priceDiff !== 0" :class="{ better: priceDiff > 0, worse: priceDiff < 0 }">
          （{{ priceDiff > 0 ? '优于' : '劣于' }}计划 {{ Math.abs(priceDiff).toFixed(2) }}）
        </span>
      </div>
    </div>

    <!-- 交易摘要 -->
    <div class="summary-section" v-if="selectedGrid && inputPrice">
      <div class="summary-row">
        <span class="summary-label">网格</span>
        <span class="summary-value">第{{ selectedGrid.level }}格（{{ formatGridType(selectedGrid.gridType) }}网）</span>
      </div>
      <div class="summary-row">
        <span class="summary-label">操作</span>
        <span class="summary-value" :class="actionType">{{ actionType === 'buy' ? '买入' : '卖出' }}</span>
      </div>
      <div class="summary-row">
        <span class="summary-label">金额</span>
        <span class="summary-value">¥{{ formatAmount(tradeAmount) }}</span>
      </div>
      <div class="summary-row" v-if="actionType === 'sell'">
        <span class="summary-label">预计收益</span>
        <span class="summary-value profit">+{{ formatAmount(expectedProfit) }}</span>
      </div>
    </div>

    <!-- 底部确认按钮 -->
    <div class="bottom-action">
      <button 
        class="confirm-btn"
        :class="{ disabled: !canSubmit, [actionType]: true }"
        :disabled="!canSubmit || submitting"
        @click="handleSubmit"
      >
        {{ submitting ? '提交中...' : (actionType === 'buy' ? '确认买入' : '确认卖出') }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getGridLines, executeTick, updateActualBuyPrice } from '../../api'

const router = useRouter()
const route = useRoute()

const strategyId = ref(route.query.strategyId)
const preselectedGridId = ref(route.query.gridId)
const preselectedAction = ref(route.query.action)

const actionType = ref('buy')
const gridLines = ref([])
const selectedGrid = ref(null)
const inputPrice = ref('')
const submitting = ref(false)
const amountPerGrid = ref(1000) // 将从策略中获取

// 可选网格列表
const availableGrids = computed(() => {
  if (actionType.value === 'buy') {
    return gridLines.value
      .filter(g => g.state === 'WAIT_BUY')
      .sort((a, b) => a.level - b.level)
  } else {
    return gridLines.value
      .filter(g => g.state === 'BOUGHT' || g.state === 'WAIT_SELL')
      .sort((a, b) => a.level - b.level)
  }
})

// 计划价
const planPrice = computed(() => {
  if (!selectedGrid.value) return 0
  return actionType.value === 'buy' 
    ? selectedGrid.value.buyPrice 
    : selectedGrid.value.sellPrice
})

// 价格差异
const priceDiff = computed(() => {
  if (!inputPrice.value || !planPrice.value) return 0
  const diff = Number(inputPrice.value) - Number(planPrice.value)
  // 买入时更低更好，卖出时更高更好
  return actionType.value === 'buy' ? -diff : diff
})

// 交易金额
const tradeAmount = computed(() => {
  return amountPerGrid.value
})

// 预计收益（卖出时）
const expectedProfit = computed(() => {
  if (!selectedGrid.value || actionType.value !== 'sell') return 0
  const buyPrice = selectedGrid.value.actualBuyPrice || selectedGrid.value.buyPrice
  const sellPrice = Number(inputPrice.value) || selectedGrid.value.sellPrice
  const quantity = amountPerGrid.value / buyPrice
  return quantity * sellPrice - amountPerGrid.value
})

// 是否可提交
const canSubmit = computed(() => {
  return selectedGrid.value && inputPrice.value && Number(inputPrice.value) > 0
})

// 加载网格
const loadGrids = async () => {
  if (!strategyId.value) return
  try {
    const res = await getGridLines(strategyId.value)
    gridLines.value = res.data.gridPlans || []
    
    // 获取单格金额
    if (res.data.strategy?.amountPerGrid) {
      amountPerGrid.value = Number(res.data.strategy.amountPerGrid)
    }
    
    // 预选网格
    if (preselectedGridId.value) {
      const grid = gridLines.value.find(g => g.id == preselectedGridId.value)
      if (grid) {
        selectedGrid.value = grid
      }
    }
    
    // 预选操作类型
    if (preselectedAction.value) {
      actionType.value = preselectedAction.value
    }
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载失败')
  }
}

// 选择网格
const selectGrid = (grid) => {
  selectedGrid.value = grid
  // 默认填入计划价
  inputPrice.value = actionType.value === 'buy' 
    ? grid.buyPrice.toString() 
    : grid.sellPrice.toString()
}

// 切换操作类型时重置
watch(actionType, () => {
  selectedGrid.value = null
  inputPrice.value = ''
})

// 提交
const handleSubmit = async () => {
  if (!canSubmit.value || submitting.value) return
  
  submitting.value = true
  try {
    // 通过执行Tick来触发交易
    // 实际场景中可能需要不同的API
    await executeTick(strategyId.value, Number(inputPrice.value))
    
    ElMessage.success(actionType.value === 'buy' ? '买入成功' : '卖出成功')
    router.back()
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error(error.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// 返回
const goBack = () => {
  router.back()
}

// 格式化
const formatPrice = (val) => val == null ? '-' : Number(val).toFixed(3)
const formatAmount = (val) => val == null ? '0' : Math.round(Number(val)).toString()
const formatGridType = (type) => {
  const map = { SMALL: '小', MEDIUM: '中', LARGE: '大' }
  return map[type] || '小'
}

onMounted(() => {
  loadGrids()
})
</script>

<style scoped>
.record-page {
  min-height: 100vh;
  background: #f5f6fa;
  padding-bottom: 100px;
}

/* 顶部栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.back-btn {
  font-size: 22px;
  color: #303133;
  cursor: pointer;
  padding: 8px;
  margin: -8px;
}

.page-title {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.placeholder {
  width: 38px;
}

/* 操作类型切换 */
.action-switcher {
  display: flex;
  margin: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 4px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.switch-item {
  flex: 1;
  padding: 12px;
  text-align: center;
  font-size: 15px;
  font-weight: 500;
  color: #909399;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.switch-item.active {
  background: #f56c6c;
  color: #fff;
}

.switch-item:last-child.active {
  background: #67c23a;
}

/* 网格选择 */
.grid-selector {
  margin: 0 16px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.selector-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
}

.grid-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.grid-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 14px;
  border: 2px solid #dcdfe6;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  min-width: 70px;
}

.grid-chip.selected {
  border-color: #667eea;
  background: #f0f3ff;
}

.grid-chip.small { border-left: 3px solid #409eff; }
.grid-chip.medium { border-left: 3px solid #e6a23c; }
.grid-chip.large { border-left: 3px solid #f56c6c; }

.chip-level {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.chip-price {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.selector-hint {
  text-align: center;
  color: #909399;
  font-size: 14px;
  padding: 20px 0;
}

/* 价格输入 */
.price-input-section {
  margin: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.input-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 12px;
}

.price-input-wrapper {
  display: flex;
  align-items: center;
  border-bottom: 2px solid #667eea;
  padding-bottom: 8px;
}

.currency {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin-right: 8px;
}

.price-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  background: transparent;
}

.price-input::placeholder {
  color: #c0c4cc;
  font-weight: 400;
}

.price-hint {
  font-size: 13px;
  color: #909399;
  margin-top: 12px;
}

.price-hint .better {
  color: #67c23a;
}

.price-hint .worse {
  color: #f56c6c;
}

/* 交易摘要 */
.summary-section {
  margin: 16px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}

.summary-row:last-child {
  border-bottom: none;
}

.summary-label {
  font-size: 14px;
  color: #909399;
}

.summary-value {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.summary-value.buy {
  color: #f56c6c;
}

.summary-value.sell {
  color: #67c23a;
}

.summary-value.profit {
  color: #67c23a;
  font-weight: 600;
}

/* 底部按钮 */
.bottom-action {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  background: #fff;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.06);
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
}

.confirm-btn {
  width: 100%;
  padding: 16px;
  border: none;
  border-radius: 12px;
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  cursor: pointer;
  transition: all 0.2s;
}

.confirm-btn.buy {
  background: linear-gradient(135deg, #f56c6c 0%, #e74c3c 100%);
}

.confirm-btn.sell {
  background: linear-gradient(135deg, #67c23a 0%, #27ae60 100%);
}

.confirm-btn.disabled {
  background: #dcdfe6;
  cursor: not-allowed;
}

.confirm-btn:active:not(.disabled) {
  transform: scale(0.98);
}
</style>
