<template>
  <MobileLayout :title="strategyTitle" :show-back="true" :show-tab-bar="false" :show-header="false">
    <div v-if="loading" class="skeleton-container">
      <!-- 头部骨架 -->
      <div class="skeleton-header">
        <div class="skeleton-row">
          <div class="skeleton-box skeleton-title"></div>
          <div class="skeleton-box skeleton-tag"></div>
        </div>
        <div class="skeleton-stats">
          <div class="skeleton-stat-item">
            <div class="skeleton-box skeleton-stat-label"></div>
            <div class="skeleton-box skeleton-stat-value"></div>
          </div>
          <div class="skeleton-stat-item">
            <div class="skeleton-box skeleton-stat-label"></div>
            <div class="skeleton-box skeleton-stat-value"></div>
          </div>
          <div class="skeleton-stat-item">
            <div class="skeleton-box skeleton-stat-label"></div>
            <div class="skeleton-box skeleton-stat-value"></div>
          </div>
        </div>
      </div>

      <!-- 智能建议骨架 -->
      <div class="skeleton-card">
        <div class="skeleton-box skeleton-card-title"></div>
        <div class="skeleton-box skeleton-card-content"></div>
      </div>

      <!-- 价格触发卡片骨架 -->
      <div class="skeleton-card skeleton-card-small">
        <div class="skeleton-box skeleton-input"></div>
        <div class="skeleton-box skeleton-btn"></div>
      </div>

      <!-- Tab 骨架 -->
      <div class="skeleton-tabs">
        <div class="skeleton-tab"></div>
        <div class="skeleton-tab"></div>
      </div>

      <!-- 列表骨架 -->
      <div class="skeleton-list">
        <div class="skeleton-list-item" v-for="i in 3" :key="i"></div>
      </div>
    </div>

    <div v-else-if="strategy">
      <!-- 策略头部 -->
      <StrategyHeader
        :strategy="strategy"
        :position-profit="positionProfit"
        :position-profit-percent="positionProfitPercent"
        :position-profit-percent-value="positionProfitPercentValue"
        :today-profit="todayProfit"
        :today-profit-percent="todayProfitPercent"
        :today-profit-percent-value="todayProfitPercentValue"
        :holding-days="holdingDays"
        :position-ratio="positionRatio"
        :cost-price="costPrice"
        :total-fee="totalFee"
        :average-buy-price="averageBuyPrice"
        v-model:price-input="priceInput"
        :risks="risks"
        @price-change="onPriceChange"
        @show-risk="showRiskDialog = true"
      />

      <!-- 智能建议 -->
      <SmartSuggestion
        v-if="strategy && hasSuggestions"
        :strategy-id="strategyId"
        :initial-last-price="parseFloat(priceInput) || strategy.lastPrice"
        ref="smartSuggestionRef"
        @suggestion-updated="handleSuggestionUpdated"
      />

      <!-- 价格触发卡片 -->
      <PriceTriggerCard
        :price="priceInput"
        :suggestion="currentSuggestion"
        @execute="handleExecute"
      />

      <!-- 标签切换器 -->
      <TabSwitcher v-model:active-tab="activeTab" />

      <!-- 标签内容 -->
      <div class="tab-content">
        <div v-show="activeTab === 'grids'" class="grid-list">
          <MobileGridCard v-for="grid in gridLines" :key="grid.id" :grid="grid" />
        </div>

        <div v-show="activeTab === 'records'">
          <TradeRecordList :records="tradeRecords" @edit-fee="openFeeDialog" />
        </div>
      </div>

      <!-- 操作按钮组 -->
      <div class="action-buttons">
        <el-button type="success" @click="ocrDialogVisible = true">
          <el-icon><Upload /></el-icon>
          OCR导入成交记录
        </el-button>
      </div>
    </div>

    <div v-else class="error-state">
      <div class="error-icon">
        <el-icon :size="64" color="#f56c6c"><CircleCloseFilled /></el-icon>
      </div>
      <div class="error-text">加载失败，请重试</div>
      <el-button type="primary" @click="loadStrategyDetail" class="retry-btn">
        重新加载
      </el-button>
    </div>

    <!-- 手续费编辑弹窗 -->
    <FeeEditDialog
      v-model="feeDialogVisible"
      :current-record="currentEditRecord"
      :saving="savingFee"
      @save="handleSaveFee"
    />

    <!-- OCR导入弹窗 -->
    <OcrImportDialog v-model="ocrDialogVisible" :strategy-id="strategyId" @import-success="handleOcrImportSuccess" />

    <!-- 交易执行确认弹窗 -->
    <TradeExecuteDialog
      v-model="executeDialogVisible"
      :suggestion="currentSuggestion"
      :executing="executing"
      @confirm="handleConfirmExecute"
    />

    <!-- 风险提示弹窗 -->
    <RiskAlertDialog v-model="showRiskDialog" :risks="risks" />
  </MobileLayout>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Upload, CircleCloseFilled } from '@element-plus/icons-vue'
import { getStrategyDetail, updateStrategyLastPrice } from '@/services/strategy'
import { getGridLines, executeTick } from '@/services/grid'
import { getTradeRecords, updateTradeFee } from '@/services/trade'
import MobileLayout from './MobileLayout.vue'
import StrategyHeader from './components/StrategyHeader.vue'
import RiskAlertDialog from './components/RiskAlertDialog.vue'
import SmartSuggestion from '@/components/SmartSuggestion.vue'
import PriceTriggerCard from './components/PriceTriggerCard.vue'
import TabSwitcher from './components/TabSwitcher.vue'
import MobileGridCard from './MobileGridCard.vue'
import TradeRecordList from './components/TradeRecordList.vue'
import FeeEditDialog from './components/FeeEditDialog.vue'
import OcrImportDialog from './components/OcrImportDialog.vue'
import TradeExecuteDialog from './components/TradeExecuteDialog.vue'

const route = useRoute()
const strategyId = computed(() => Number(route.params.id))

const loading = ref(true)
const strategy = ref(null)
const gridLines = ref([])
const tradeRecords = ref([])
const priceInput = ref('')
const activeTab = ref('grids')
const risks = ref([])
const showRiskDialog = ref(false)
const smartSuggestionRef = ref(null)
const currentSuggestion = ref(null)
const executeDialogVisible = ref(false)
const executing = ref(false)
const savingFee = ref(false)
const ocrDialogVisible = ref(false)
const feeDialogVisible = ref(false)
const currentEditRecord = ref(null)

const strategyTitle = computed(() => {
  return strategy.value?.name || strategy.value?.symbol || '策略详情'
})

// 计算属性 - 各种统计数据
const positionProfit = computed(() => strategy.value?.positionProfit || 0)
const positionProfitPercent = computed(() => {
  const val = strategy.value?.positionProfitPercent || 0
  // 后端已返回百分比值（如-13.752），无需再乘100
  return `${val >= 0 ? '+' : ''}${val.toFixed(3)}%`
})
const positionProfitPercentValue = computed(() => strategy.value?.positionProfitPercent || 0)
const todayProfit = computed(() => strategy.value?.todayProfit || 0)
const todayProfitPercent = computed(() => {
  const val = strategy.value?.todayProfitPercent || 0
  // 后端已返回百分比值，无需再乘100
  return `${val >= 0 ? '+' : ''}${val.toFixed(3)}%`
})
const todayProfitPercentValue = computed(() => strategy.value?.todayProfitPercent || 0)
const holdingDays = computed(() => strategy.value?.holdingDays || 0)
const positionRatio = computed(() => strategy.value?.positionRatio || 0)
const costPrice = computed(() => strategy.value?.costPrice || 0)
const totalFee = computed(() => strategy.value?.totalFee || 0)
const averageBuyPrice = computed(() => strategy.value?.avgBuyPrice || strategy.value?.averageBuyPrice || 0)

// 是否有建议操作
const hasSuggestions = computed(() => {
  if (!currentSuggestion.value) return false
  return currentSuggestion.value.type === 'BUY' || currentSuggestion.value.type === 'SELL'
})

onMounted(() => {
  loadStrategyDetail()
})

// 监听策略ID变化，重新加载数据
watch(strategyId, () => {
  loadStrategyDetail()
})

// 加载策略详情
const loadStrategyDetail = async () => {
  loading.value = true
  try {
    const [strategyRes, gridRes, tradeRes] = await Promise.all([
      getStrategyDetail(strategyId.value),
      getGridLines(strategyId.value),
      getTradeRecords(strategyId.value)
    ])
    strategy.value = strategyRes.data
    // 后端返回 { strategy: {...}, gridPlans: [...] }，需要取 gridPlans
    gridLines.value = gridRes.data.gridPlans || gridRes.data
    tradeRecords.value = tradeRes.data
    priceInput.value = String(strategy.value.lastPrice || '')
    // 模拟风险数据
    risks.value = []
  } catch (e) {
    ElMessage.error('加载失败：' + (e.response?.data?.message || e.message))
  } finally {
    loading.value = false
  }
}

// 价格变化时触发智能建议更新，并保存到后端
const onPriceChange = async () => {
  const price = parseFloat(priceInput.value)
  if (price && !isNaN(price)) {
    try {
      // 保存现价到后端，并使用返回值更新本地数据
      const res = await updateStrategyLastPrice(strategyId.value, price)
      // 更新本地策略数据，刷新头部面板
      strategy.value = res.data
    } catch (e) {
      console.error('保存现价失败:', e)
    }
  }
  // 更新智能建议
  if (smartSuggestionRef.value) {
    smartSuggestionRef.value.updateLastPrice(price)
  }
}

// 智能建议更新
const handleSuggestionUpdated = (suggestion) => {
  currentSuggestion.value = suggestion
}

// 执行价格触发
const handleExecute = async () => {
  const price = parseFloat(priceInput.value)
  if (!price || isNaN(price)) {
    ElMessage.warning('请输入有效的价格')
    return
  }

  if (currentSuggestion.value && currentSuggestion.value.type) {
    // 有建议的情况下，显示确认弹窗
    executeDialogVisible.value = true
  } else {
    // 无建议的情况下直接执行
    await doExecute(price)
  }
}

// 确认执行交易
const handleConfirmExecute = async (data) => {
  await doExecute(data)
  executeDialogVisible.value = false
}

// 实际执行逻辑
const doExecute = async (data) => {
  executing.value = true
  try {
    await executeTick(strategyId.value, data)
    ElMessage.success('执行成功')
    // 执行成功后重新加载数据
    await loadStrategyDetail()
  } catch (e) {
    ElMessage.error('执行失败：' + (e.response?.data?.message || e.message))
  } finally {
    executing.value = false
  }
}

// 打开手续费编辑弹窗
const openFeeDialog = (record) => {
  currentEditRecord.value = record
  feeDialogVisible.value = true
}

// 保存手续费
const handleSaveFee = async (fee) => {
  if (!currentEditRecord.value) return
  savingFee.value = true
  try {
    await updateTradeFee(currentEditRecord.value.id, fee)
    ElMessage.success('保存成功')
    feeDialogVisible.value = false
    loadStrategyDetail()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message))
  } finally {
    savingFee.value = false
  }
}

// OCR导入成功回调
const handleOcrImportSuccess = () => {
  ElMessage.success('导入成功')
  ocrDialogVisible.value = false
  loadStrategyDetail()
}
</script>

<style scoped>
/* 骨架屏样式 */
.skeleton-container {
  padding: 0;
}

.skeleton-header {
  background: var(--bg-card);
  border-radius: 0 0 20px 20px;
  padding: 16px;
  transition: background-color var(--transition-base);
}

.skeleton-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.skeleton-box {
  background: linear-gradient(90deg, var(--bg-light) 25%, var(--border-lighter) 50%, var(--bg-light) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
  border-radius: 8px;
  transition: background-color var(--transition-base);
}

.skeleton-title {
  width: 100px;
  height: 20px;
}

.skeleton-tag {
  width: 50px;
  height: 20px;
  border-radius: 10px;
}

.skeleton-stats {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-top: 24px;
  padding: 0 8px;
}

.skeleton-stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-stat-label {
  width: 40px;
  height: 12px;
}

.skeleton-stat-value {
  width: 60px;
  height: 20px;
}

.skeleton-card {
  background: var(--bg-card);
  border-radius: 16px;
  padding: 16px;
  margin: 16px;
  transition: background-color var(--transition-base);
}

.skeleton-card-small {
  display: flex;
  gap: 12px;
  align-items: center;
}

.skeleton-card-title {
  width: 80px;
  height: 14px;
  margin-bottom: 12px;
}

.skeleton-card-content {
  width: 100%;
  height: 48px;
}

.skeleton-input {
  flex: 1;
  height: 48px;
  border-radius: 12px;
}

.skeleton-btn {
  width: 80px;
  height: 48px;
  border-radius: 12px;
}

.skeleton-tabs {
  display: flex;
  gap: 8px;
  margin: 0 16px 16px;
  padding: 4px;
  background: var(--bg-light);
  border-radius: 10px;
  transition: background-color var(--transition-base);
}

.skeleton-tab {
  flex: 1;
  height: 40px;
  background: var(--bg-card);
  border-radius: 8px;
  transition: background-color var(--transition-base);
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 16px;
}

.skeleton-list-item {
  height: 80px;
  background: var(--bg-card);
  border-radius: 14px;
  transition: background-color var(--transition-base);
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

.grid-list {
  margin-top: 0;
  padding: 0 16px;
}

.tab-content {
  margin-top: 0;
}

.action-buttons {
  margin: 24px 16px;
  display: flex;
  gap: 12px;
}

.action-buttons .el-button {
  flex: 1;
  height: 48px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
}

.error-state {
  text-align: center;
  padding: 80px 20px;
}

.error-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.error-text {
  font-size: 16px;
  color: var(--text-secondary);
  margin-bottom: 24px;
}

.retry-btn {
  margin-top: 16px;
  height: 48px;
  padding: 0 32px;
  border-radius: 12px;
  font-weight: 600;
}
</style>
