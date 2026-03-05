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

    <!-- 建议操作列表 -->
    <div class="suggestions-section" v-if="suggestions && suggestions.length > 0">
      <div class="section-title">
        <el-icon><Bell /></el-icon>
        <span>建议操作</span>
        <el-tag size="small" type="danger">{{ suggestions.length }}</el-tag>
      </div>

      <div class="suggestion-list">
        <div
          v-for="(item, index) in suggestions"
          :key="index"
          class="suggestion-item"
          :class="[`priority-${item.priority.toLowerCase()}`, item.type.toLowerCase()]"
          @click="handleSuggestion(item)"
        >
          <div class="suggestion-icon">
            <el-icon v-if="item.type === 'BUY'"><Bottom /></el-icon>
            <el-icon v-else><Top /></el-icon>
          </div>

          <div class="suggestion-content">
            <div class="suggestion-header">
              <span class="suggestion-type">
                {{ item.type === 'BUY' ? '建议买入' : '建议卖出' }}
              </span>
              <el-tag
                size="small"
                :type="getPriorityType(item.priority)"
              >
                {{ getPriorityText(item.priority) }}
              </el-tag>
            </div>

            <div class="suggestion-detail">
              <span>第{{ item.gridLevel }}格</span>
              <span class="dot">·</span>
              <span>{{ getGridTypeText(item.gridType) }}</span>
              <span class="dot">·</span>
              <span class="price">¥{{ formatPrice(item.price) }}</span>
            </div>

            <div class="suggestion-amount" v-if="item.type === 'SELL'">
              预期收益: <span class="profit">+¥{{ formatPrice(item.expectedProfit) }}</span>
            </div>

            <div class="suggestion-reason">
              {{ item.reason }}
            </div>
          </div>

          <div class="suggestion-action">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 无建议时的提示 -->
    <div class="no-suggestions" v-else-if="currentPriceInput && !loading">
      <el-icon><CircleCheck /></el-icon>
      <p>当前价格暂无待操作建议</p>
    </div>

    <!-- 风险提示 -->
    <div class="risks-section" v-if="risks && risks.length > 0">
      <div class="section-title">
        <el-icon><Warning /></el-icon>
        <span>风险提示</span>
      </div>

      <div class="risk-list">
        <el-alert
          v-for="(risk, index) in risks"
          :key="index"
          :title="risk.message"
          :type="getRiskType(risk.level)"
          :closable="false"
          show-icon
        />
      </div>
    </div>

    <!-- 优化建议 -->
    <div class="optimizations-section" v-if="optimizations && optimizations.length > 0">
      <div class="section-title">
        <el-icon><Sunny /></el-icon>
        <span>优化建议</span>
      </div>

      <div class="optimization-list">
        <div
          v-for="(opt, index) in optimizations"
          :key="index"
          class="optimization-item"
        >
          <el-icon class="opt-icon"><InfoFilled /></el-icon>
          <span>{{ opt.message }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import {
  RefreshRight,
  Bell,
  Bottom,
  Top,
  ArrowRight,
  CircleCheck,
  Warning,
  Sunny,
  InfoFilled
} from '@element-plus/icons-vue'
import { getSmartSuggestions } from '../api.js'
import { ElMessage } from 'element-plus'

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

const emit = defineEmits(['priceUpdated', 'suggestionUpdated', 'viewDetails'])

const currentPriceInput = ref('')
const loading = ref(false)
const priceAnalysis = ref(null)
const suggestions = ref([])
const risks = ref([])
const optimizations = ref([])

// 初始化价格
onMounted(() => {
  if (props.initialLastPrice) {
    currentPriceInput.value = props.initialLastPrice.toString()
    fetchSuggestions()
  }
})

// 监听策略变化
watch(() => props.strategyId, () => {
  if (currentPriceInput.value) {
    fetchSuggestions()
  }
})

// 价格变化处理
const onPriceChange = () => {
  if (currentPriceInput.value) {
    fetchSuggestions()
    emit('priceUpdated', parseFloat(currentPriceInput.value))
  }
}

// 刷新建议
const refreshSuggestions = () => {
  if (!currentPriceInput.value) {
    ElMessage.warning('请先输入当前价格')
    return
  }
  fetchSuggestions()
}

// 获取智能建议
const fetchSuggestions = async () => {
  if (!currentPriceInput.value || loading.value) return

  loading.value = true
  try {
    const price = parseFloat(currentPriceInput.value)
    const data = await getSmartSuggestions(props.strategyId, price)

    priceAnalysis.value = data.priceAnalysis
    suggestions.value = data.suggestions || []
    risks.value = data.risks || []
    optimizations.value = data.optimizations || []

    emit('suggestionUpdated', data)
  } catch (error) {
    console.error('获取智能建议失败:', error)
    ElMessage.error('获取智能建议失败')
  } finally {
    loading.value = false
  }
}

// 处理建议点击
const handleSuggestion = (item) => {
  emit('viewDetails', item)
}

// 格式化价格
const formatPrice = (value) => {
  if (value === null || value === undefined) return '0.00'
  return parseFloat(value).toFixed(2)
}

// 获取优先级类型
const getPriorityType = (priority) => {
  const typeMap = {
    'HIGH': 'danger',
    'MEDIUM': 'warning',
    'LOW': 'info'
  }
  return typeMap[priority] || 'info'
}

// 获取优先级文本
const getPriorityText = (priority) => {
  const textMap = {
    'HIGH': '高优先级',
    'MEDIUM': '中优先级',
    'LOW': '低优先级'
  }
  return textMap[priority] || priority
}

// 获取网格类型文本
const getGridTypeText = (type) => {
  const textMap = {
    'SMALL': '小网',
    'MEDIUM': '中网',
    'LARGE': '大网'
  }
  return textMap[type] || type
}

// 获取风险类型
const getRiskType = (level) => {
  const typeMap = {
    'HIGH': 'error',
    'MEDIUM': 'warning',
    'LOW': 'info'
  }
  return typeMap[level] || 'info'
}
</script>

<style scoped>
.smart-suggestion {
  padding: 0 16px 16px;
}

/* 价格卡片 */
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
  opacity: 0.8;
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

/* 建议操作区域 */
.suggestions-section {
  margin-top: 16px;
  background: white;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  margin-bottom: 12px;
}

.section-title .el-icon {
  font-size: 18px;
  color: #409eff;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.suggestion-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 12px;
  border-left: 4px solid #409eff;
  cursor: pointer;
  transition: all 0.3s;
}

.suggestion-item:active {
  transform: scale(0.98);
}

.suggestion-item.buy {
  border-left-color: #67c23a;
}

.suggestion-item.buy .suggestion-icon {
  color: #67c23a;
  background: #f0f9ff;
}

.suggestion-item.sell {
  border-left-color: #f56c6c;
}

.suggestion-item.sell .suggestion-icon {
  color: #f56c6c;
  background: #fef0f0;
}

.suggestion-item.priority-high {
  background: linear-gradient(135deg, #fff5f5 0%, #ffe5e5 100%);
}

.suggestion-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 50%;
  font-size: 20px;
  flex-shrink: 0;
}

.suggestion-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.suggestion-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.suggestion-type {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.suggestion-detail {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
}

.suggestion-detail .dot {
  color: #dcdfe6;
}

.suggestion-detail .price {
  color: #409eff;
  font-weight: bold;
}

.suggestion-amount {
  font-size: 13px;
  color: #909399;
}

.suggestion-amount .profit {
  color: #67c23a;
  font-weight: bold;
}

.suggestion-reason {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

.suggestion-action {
  display: flex;
  align-items: center;
  color: #909399;
  font-size: 18px;
}

/* 无建议提示 */
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

/* 风险提示区域 */
.risks-section {
  margin-top: 16px;
  background: white;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.risks-section .section-title .el-icon {
  color: #f56c6c;
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.risk-list :deep(.el-alert) {
  border-radius: 8px;
}

/* 优化建议区域 */
.optimizations-section {
  margin-top: 16px;
  background: white;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.optimizations-section .section-title .el-icon {
  color: #e6a23c;
}

.optimization-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.optimization-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: #fef7ec;
  border-radius: 8px;
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
}

.opt-icon {
  color: #e6a23c;
  font-size: 16px;
  flex-shrink: 0;
  margin-top: 2px;
}
</style>




