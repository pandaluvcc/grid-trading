<template>
  <div class="smart-suggestion">
    <!-- 价格输入卡片 -->
    <div class="price-card">
      <div class="price-header">
        <span class="price-label">当前价格</span>
        <el-button
          text
          size="small"
          :icon="RefreshRight"
          @click="refreshSuggestions"
          :loading="loading"
        >
          刷新
        </el-button>
      </div>
      <div class="price-input-group">
        <el-input
          v-model="currentPriceInput"
          type="number"
          size="large"
          placeholder="请输入当前价格"
          @change="onPriceChange"
          clearable
        >
          <template #prefix>¥</template>
        </el-input>
      </div>

      <!-- 价格分析 -->
      <div class="price-analysis" v-if="priceAnalysis">
        <div class="analysis-item">
          <span class="analysis-label">基准价</span>
          <span class="analysis-value">¥{{ formatPrice(priceAnalysis.basePrice) }}</span>
        </div>
        <div class="analysis-item">
          <span class="analysis-label">偏离度</span>
          <span
            class="analysis-value"
            :class="{
              'price-high': priceAnalysis.position === 'HIGH',
              'price-low': priceAnalysis.position === 'LOW'
            }"
          >
            {{ priceAnalysis.deviationPercent > 0 ? '+' : '' }}{{ priceAnalysis.deviationPercent }}%
          </span>
        </div>
        <div class="analysis-item" v-if="priceAnalysis.nearestGridLevel">
          <span class="analysis-label">最近网格</span>
          <span class="analysis-value">第{{ priceAnalysis.nearestGridLevel }}格</span>
        </div>
      </div>
    </div>

    <!-- 风险提示区 -->
    <div class="risk-section" v-if="risks && risks.length > 0">
      <div class="section-title">
        <el-icon><Warning /></el-icon>
        <span>风险提示</span>
      </div>
      <div class="risk-list">
        <div v-for="(risk, index) in risks" :key="index" class="risk-item">
          · {{ risk.message }}
        </div>
      </div>
    </div>

    <!-- 建议操作区 -->
    <div class="suggestions-section" v-if="suggestions && suggestions.length > 0">
      <div class="section-title">
        <el-icon><Bell /></el-icon>
        <span>建议操作</span>
        <el-tag size="small" type="danger">{{ suggestions.length }}</el-tag>
        <el-button
          text
          size="small"
          @click="toggleSuggestions"
          class="expand-btn"
        >
          <el-icon>{{ expanded ? ArrowUp : ArrowDown }}</el-icon>
          {{ expanded ? '收起' : `展开${suggestions.length - 1}条` }}
        </el-button>
      </div>

      <div class="suggestion-list">
        <!-- 第一条建议始终显示 -->
        <SuggestionCard
          v-if="suggestions[0]"
          :key="suggestions[0].gridLineId"
          :suggestion="suggestions[0]"
          @view-grid="handleViewGrid"
          @execute="handleExecute"
        />
        
        <!-- 其余建议折叠显示 -->
        <transition-group name="suggestion-fade" tag="div" v-if="expanded">
          <SuggestionCard
            v-for="(item, index) in suggestions.slice(1)"
            :key="item.gridLineId"
            :suggestion="item"
            @view-grid="handleViewGrid"
            @execute="handleExecute"
          />
        </transition-group>
      </div>
    </div>

    <!-- 暂缓网格区 -->
    <div class="deferred-section" v-if="deferredGrids && deferredGrids.length > 0">
      <div class="section-title">
        <el-icon><VideoPause /></el-icon>
        <span>暂缓买入</span>
        <el-tag size="small" type="info">{{ deferredGrids.length }}</el-tag>
      </div>
      <div class="deferred-list">
        <div v-for="(grid, index) in deferredGrids" :key="index" class="deferred-item">
          <div class="deferred-info">
            <span class="deferred-grid">第{{ grid.gridLevel }}网（{{ getGridTypeName(grid.gridType) }}）</span>
            <span class="deferred-reason">- {{ getDeferredReasonText(grid.deferredReason) }}</span>
          </div>
          <el-button size="small" type="primary" @click="handleResumeBuy(grid)">
            手动补买
          </el-button>
        </div>
      </div>
    </div>

    <!-- 无建议时的提示 -->
    <div class="no-suggestions" v-else-if="currentPriceInput && !loading">
      <el-icon><CircleCheck /></el-icon>
      <p>当前价格暂无待操作建议</p>
    </div>

    <!-- 执行确认弹窗 -->
    <el-dialog
      v-model="executeDialogVisible"
      :title="`确认执行${currentSuggestion?.type === 'BUY' ? '买入' : '卖出'}`"
      width="90%"
      :close-on-click-modal="false"
    >
      <div class="execute-dialog-content" v-if="currentSuggestion">
        <div class="suggestion-summary">
          <div class="summary-item">
            <span class="label">网格：</span>
            <span class="value">第{{ currentSuggestion.gridLevel }}网（{{ getGridTypeName(currentSuggestion.gridType) }}）</span>
          </div>
          <div class="summary-item">
            <span class="label">价格：</span>
            <span class="value">¥{{ formatPrice(currentSuggestion.price) }}</span>
          </div>
          <div class="summary-item">
            <span class="label">数量：</span>
            <span class="value">{{ formatQuantity(currentSuggestion.quantity) }}股</span>
          </div>
          <div class="summary-item">
            <span class="label">金额：</span>
            <span class="value">¥{{ formatAmount(currentSuggestion.amount) }}</span>
          </div>
          <div class="summary-item" v-if="currentSuggestion.quantityRatio < 1">
            <span class="label">仓位：</span>
            <span class="value">{{ currentSuggestion.quantityRatio === 0.5 ? '半仓' : `${currentSuggestion.quantityRatio * 100}%仓` }}</span>
          </div>
        </div>
        
        <div class="input-section">
          <div class="input-group">
            <label>交易时间</label>
            <el-date-picker
              v-model="tradeTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择交易时间"
              size="large"
              style="width: 100%"
            />
          </div>
          <div class="input-group">
            <label>手续费（可选）</label>
            <el-input
              v-model="feeInput"
              type="number"
              placeholder="输入手续费"
              size="large"
            >
              <template #prefix>¥</template>
            </el-input>
          </div>
        </div>
      </div>
      
      <template #footer>
        <el-button @click="cancelExecute">取消</el-button>
        <el-button type="primary" :loading="executing" @click="confirmExecute">
          确认执行
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import {
  RefreshRight,
  Bell,
  CircleCheck,
  Warning,
  VideoPause,
  ArrowUp,
  ArrowDown
} from '@element-plus/icons-vue'
import { getSmartSuggestions } from '../api.js'
import { ElMessage } from 'element-plus'
import SuggestionCard from './SuggestionCard.vue'

const props = defineProps({
  strategyId: {
    type: Number,
    required: true
  },
  initialLastPrice: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['priceUpdated', 'suggestionUpdated', 'viewDetails', 'viewGrid', 'execute'])

const currentPriceInput = ref('')
const loading = ref(false)
const priceAnalysis = ref(null)
const suggestions = ref([])
const risks = ref([])
const deferredGrids = ref([])

const executeDialogVisible = ref(false)
const currentSuggestion = ref(null)
const tradeTime = ref('')
const feeInput = ref('')
const executing = ref(false)
const expanded = ref(false)

const toggleSuggestions = () => {
  expanded.value = !expanded.value
}

const formatPrice = (value) => {
  if (value === null || value === undefined) return '0.000'
  return parseFloat(value).toFixed(3)
}

const formatQuantity = (value) => {
  if (value === null || value === undefined) return '0'
  return Math.round(Number(value)).toString()
}

const formatAmount = (value) => {
  if (value === null || value === undefined) return '0'
  return Math.round(Number(value)).toString()
}

const getGridTypeName = (type) => {
  const map = {
    'SMALL': '小网',
    'MEDIUM': '中网',
    'LARGE': '大网'
  }
  return map[type] || type
}

const getDeferredReasonText = (reason) => {
  const map = {
    'DENSE_BUY': '短期密集买入',
    'POSITION_LIMIT': '持仓比例达到上限'
  }
  return map[reason] || reason
}

onMounted(() => {
  if (props.initialLastPrice) {
    currentPriceInput.value = props.initialLastPrice.toString()
    fetchSuggestions()
  }
})

watch(() => props.strategyId, () => {
  if (currentPriceInput.value) {
    fetchSuggestions()
  }
})

const onPriceChange = () => {
  if (currentPriceInput.value) {
    fetchSuggestions()
    emit('priceUpdated', parseFloat(currentPriceInput.value))
  }
}

const refreshSuggestions = () => {
  if (!currentPriceInput.value) {
    ElMessage.warning('请先输入当前价格')
    return
  }
  fetchSuggestions()
}

const fetchSuggestions = async () => {
  if (!currentPriceInput.value || loading.value) return

  loading.value = true
  try {
    const price = parseFloat(currentPriceInput.value)
    const data = await getSmartSuggestions(props.strategyId, price)

    priceAnalysis.value = data.priceAnalysis
    suggestions.value = data.suggestions || []
    risks.value = data.risks || []
    deferredGrids.value = data.deferredGrids || []

    emit('suggestionUpdated', data)
  } catch (error) {
    console.error('获取智能建议失败:', error)
    ElMessage.error('获取智能建议失败')
  } finally {
    loading.value = false
  }
}

const handleViewGrid = (suggestion) => {
  emit('viewGrid', suggestion)
}

const handleExecute = (suggestion) => {
  currentSuggestion.value = suggestion
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  tradeTime.value = `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
  feeInput.value = ''
  executeDialogVisible.value = true
}

const cancelExecute = () => {
  executeDialogVisible.value = false
  currentSuggestion.value = null
}

const confirmExecute = async () => {
  if (!tradeTime.value) {
    ElMessage.warning('请选择交易时间')
    return
  }

  executing.value = true
  try {
    emit('execute', {
      ...currentSuggestion.value,
      fee: feeInput.value ? parseFloat(feeInput.value) : null,
      tradeTime: tradeTime.value
    })
    executeDialogVisible.value = false
    currentSuggestion.value = null
    ElMessage.success('执行成功')
    fetchSuggestions()
  } catch (error) {
    console.error('执行失败:', error)
    ElMessage.error('执行失败')
  } finally {
    executing.value = false
  }
}

const handleResumeBuy = (grid) => {
  ElMessage.info('暂缓补买功能开发中')
}
</script>

<style scoped>
.smart-suggestion {
  padding: 0 16px 16px;
}

.price-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 20px;
  color: white;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
}

.price-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.price-label {
  font-size: 14px;
  opacity: 0.9;
}

.price-input-group {
  margin-bottom: 16px;
}

.price-input-group :deep(.el-input) {
  font-size: 28px;
  font-weight: bold;
}

.price-input-group :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.2);
  box-shadow: none;
  border: 1px solid rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
}

.price-input-group :deep(.el-input__inner) {
  color: white;
  font-size: 24px;
  font-weight: bold;
}

.price-input-group :deep(.el-input__prefix) {
  color: white;
  font-size: 20px;
}

.price-analysis {
  display: flex;
  gap: 20px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.2);
}

.analysis-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.analysis-label {
  font-size: 12px;
  opacity: 0.85;
}

.analysis-value {
  font-size: 16px;
  font-weight: bold;
}

.analysis-value.price-high {
  color: #ffeb3b;
}

.analysis-value.price-low {
  color: #4caf50;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
  justify-content: space-between;
}

.section-title .el-icon {
  font-size: 18px;
}

.expand-btn {
  margin-left: auto;
  font-size: 12px;
  color: #409eff;
}

/* 建议卡片过渡动画 */
.suggestion-fade-enter-active,
.suggestion-fade-leave-active {
  transition: all 0.3s ease;
}

.suggestion-fade-enter-from,
.suggestion-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

.risk-section, .suggestions-section, .deferred-section {
  margin-top: 16px;
  background: white;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.risk-item {
  font-size: 14px;
  color: #e6a23c;
  line-height: 1.6;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.deferred-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.deferred-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.deferred-info {
  flex: 1;
  font-size: 14px;
  color: #606266;
}

.deferred-grid {
  font-weight: 500;
  color: #303133;
}

.deferred-reason {
  color: #909399;
  margin-left: 4px;
}

.no-suggestions {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
  background: white;
  border-radius: 16px;
  margin-top: 16px;
}

.no-suggestions .el-icon {
  font-size: 48px;
  color: #67c23a;
  margin-bottom: 12px;
}

.no-suggestions p {
  font-size: 14px;
  margin: 0;
}

.execute-dialog-content {
  padding: 10px 0;
}

.suggestion-summary {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
}

.summary-item .label {
  color: #909399;
}

.summary-item .value {
  color: #303133;
  font-weight: 500;
}

.input-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input-group label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
</style>
