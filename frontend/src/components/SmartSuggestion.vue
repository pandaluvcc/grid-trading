<template>
  <div class="smart-suggestion">
    <div class="suggestions-section" v-if="suggestions && suggestions.length > 0">
      <div class="section-title">
        <el-icon><Bell /></el-icon>
        <span>建议操作</span>
        <el-tag size="small" type="danger">{{ suggestions.length }}</el-tag>
        <span class="page-indicator">[{{ currentIndex + 1 }}/{{ suggestions.length }}]</span>
        <div class="nav-buttons">
          <el-button 
            size="small" 
            :disabled="currentIndex === 0"
            @click="goPrev"
          >
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <el-button 
            size="small" 
            :disabled="currentIndex === suggestions.length - 1"
            @click="goNext"
          >
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <div 
        class="suggestion-list"
        @touchstart="handleTouchStart"
        @touchmove="handleTouchMove"
        @touchend="handleTouchEnd"
      >
        <transition name="suggestion-fade" mode="out-in">
          <SuggestionCard
            v-if="suggestions[currentIndex]"
            :key="suggestions[currentIndex].gridLineId"
            :suggestion="suggestions[currentIndex]"
            @execute="handleExecute"
          />
        </transition>
      </div>
    </div>

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

    <div class="no-suggestions" v-if="!loading && (!suggestions || suggestions.length === 0)">
      <el-icon><CircleCheck /></el-icon>
      <p>当前价格暂无待操作建议</p>
    </div>

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
            <span class="value">1000股</span>
          </div>
          <div class="summary-item">
            <span class="label">金额：</span>
            <span class="value">¥{{ formatAmount(currentSuggestion.price * 1000) }}</span>
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
  Bell,
  CircleCheck,
  VideoPause,
  ArrowLeft,
  ArrowRight
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

const emit = defineEmits(['priceUpdated', 'suggestionUpdated', 'viewDetails', 'execute'])

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
const currentIndex = ref(0)

const touchStartX = ref(0)
const touchEndX = ref(0)
const touchStartTime = ref(0)

const goPrev = () => {
  if (currentIndex.value > 0) {
    currentIndex.value--
  }
}

const goNext = () => {
  if (currentIndex.value < suggestions.value.length - 1) {
    currentIndex.value++
  }
}

const handleTouchStart = (e) => {
  touchStartX.value = e.touches[0].clientX
  touchStartTime.value = Date.now()
}

const handleTouchMove = (e) => {
  touchEndX.value = e.touches[0].clientX
}

const handleTouchEnd = () => {
  const touchDiff = touchStartX.value - touchEndX.value
  const timeDiff = Date.now() - touchStartTime.value
  
  if (timeDiff < 300 && Math.abs(touchDiff) > 50) {
    if (touchDiff > 0) {
      goNext()
    } else {
      goPrev()
    }
  }
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
    fetchSuggestions(props.initialLastPrice)
  }
})

watch(() => props.strategyId, () => {
})

const fetchSuggestions = async (price) => {
  if (!price || loading.value) return

  loading.value = true
  try {
    const data = await getSmartSuggestions(props.strategyId, price)

    priceAnalysis.value = data.priceAnalysis
    suggestions.value = data.suggestions || []
    risks.value = data.risks || []
    deferredGrids.value = data.deferredGrids || []
    
    currentIndex.value = 0

    emit('suggestionUpdated', data)
  } catch (error) {
    console.error('获取智能建议失败:', error)
  } finally {
    loading.value = false
  }
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
      quantity: 1000,
      amount: currentSuggestion.value.price * 1000,
      fee: feeInput.value ? parseFloat(feeInput.value) : null,
      tradeTime: tradeTime.value
    })
    executeDialogVisible.value = false
    currentSuggestion.value = null
    ElMessage.success('执行成功')
    if (props.initialLastPrice) {
      fetchSuggestions(props.initialLastPrice)
    }
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

defineExpose({
  fetchSuggestions
})
</script>

<style scoped>
.smart-suggestion {
  padding: 0 16px 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  flex-wrap: wrap;
  z-index: 10;
  position: relative;
  overflow: visible;
}

.section-title .el-icon {
  font-size: 18px;
  flex-shrink: 0;
  z-index: 11;
}

.section-title span {
  z-index: 11;
  position: relative;
}

.page-indicator {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
}

.nav-buttons {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.nav-buttons .el-button {
  padding: 4px 8px;
}

.suggestion-fade-enter-active,
.suggestion-fade-leave-active {
  transition: all 0.3s ease;
}

.suggestion-fade-enter-from,
.suggestion-fade-leave-to {
  opacity: 0;
  transform: translateX(20px);
}

.suggestions-section, .deferred-section {
  margin-top: 16px;
  background: white;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
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
