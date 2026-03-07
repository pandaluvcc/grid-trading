<template>
  <div class="message-center">
    <div class="header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </el-button>
      <span class="title">消息中心</span>
      <el-button text v-if="allSuggestions.length > 0" @click="handleMarkAllRead">
        全部已读
      </el-button>
    </div>

    <div class="tabs">
      <div 
        v-for="tab in tabs" 
        :key="tab.value"
        class="tab-item"
        :class="{ active: currentTab === tab.value }"
        @click="currentTab = tab.value"
      >
        {{ tab.label }}
        <span v-if="getTabCount(tab.value) > 0" class="tab-count">
          {{ getTabCount(tab.value) }}
        </span>
      </div>
    </div>

    <div class="content">
      <div v-if="filteredSuggestions.length === 0" class="empty-state">
        <el-icon><CircleCheck /></el-icon>
        <p>暂无{{ currentTab === 'all' ? '' : getTabLabel(currentTab) }}消息</p>
      </div>

      <div v-else class="suggestion-list">
        <div 
          v-for="item in filteredSuggestions" 
          :key="item.id"
          class="suggestion-item"
          :class="{ unread: !item.read }"
        >
          <div class="item-header">
            <div class="item-left">
              <span class="type-icon">{{ getTypeIcon(item.type) }}</span>
              <div class="item-info">
                <span class="strategy-name">{{ item.strategyName || item.symbol }}</span>
                <span class="suggestion-type">{{ getTypeText(item.type) }}</span>
              </div>
            </div>
            <div class="item-right">
              <span class="time">{{ formatTime(item.createdAt) }}</span>
              <span v-if="!item.read" class="unread-dot"></span>
            </div>
          </div>

          <div class="item-body">
            <div class="suggestion-detail">
              <span>第{{ item.gridLevel }}网（{{ getGridTypeName(item.gridType) }}）</span>
              <span>¥{{ formatPrice(item.price) }}</span>
            </div>
            <div v-if="item.reason" class="suggestion-reason">
              {{ item.reason }}
            </div>
          </div>

          <div class="item-footer">
            <el-button size="small" text @click="goToStrategyDetail(item.strategyId)">
              查看详情
            </el-button>
            <el-button 
              v-if="item.type !== 'RISK'"
              size="small" 
              :type="item.type === 'BUY' ? 'danger' : 'success'"
              @click="quickExecute(item)"
            >
              一键执行
            </el-button>
            <el-button size="small" text @click="handleIgnore(item)">
              忽略
            </el-button>
          </div>
        </div>
      </div>
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
            <span class="label">策略：</span>
            <span class="value">{{ currentSuggestion.strategyName || currentSuggestion.symbol }}</span>
          </div>
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, CircleCheck } from '@element-plus/icons-vue'
import { getAllStrategies, getSmartSuggestions, executeTick } from '../../api'

const router = useRouter()

const strategies = ref([])
const allSuggestions = ref([])
const currentTab = ref('all')
const loading = ref(false)

const executeDialogVisible = ref(false)
const currentSuggestion = ref(null)
const tradeTime = ref('')
const feeInput = ref('')
const executing = ref(false)

const tabs = [
  { label: '全部', value: 'all' },
  { label: '买入', value: 'BUY' },
  { label: '卖出', value: 'SELL' },
  { label: '风险', value: 'RISK' }
]

const filteredSuggestions = computed(() => {
  if (currentTab.value === 'all') {
    return allSuggestions.value
  }
  return allSuggestions.value.filter(item => item.type === currentTab.value)
})

const getTabCount = (tabValue) => {
  if (tabValue === 'all') {
    return allSuggestions.value.filter(item => !item.read).length
  }
  return allSuggestions.value.filter(item => item.type === tabValue && !item.read).length
}

const getTabLabel = (tabValue) => {
  const tab = tabs.find(t => t.value === tabValue)
  return tab ? tab.label : ''
}

const formatPrice = (val) => {
  if (val == null) return '-'
  return Number(val).toFixed(3)
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

const getTypeIcon = (type) => {
  const map = {
    'BUY': '📥',
    'SELL': '📤',
    'RISK': '⚠️'
  }
  return map[type] || '💡'
}

const getTypeText = (type) => {
  const map = {
    'BUY': '建议买入',
    'SELL': '建议卖出',
    'RISK': '风险提示'
  }
  return map[type] || '建议'
}

const formatTime = (time) => {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date
  
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 172800000) return '昨天'
  
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day}`
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getAllStrategies()
    strategies.value = res.data
    
    const suggestions = []
    for (const s of strategies.value) {
      if (s.lastPrice) {
        try {
          const data = await getSmartSuggestions(s.id, s.lastPrice)
          if (data.suggestions) {
            data.suggestions.forEach((suggestion, index) => {
              suggestions.push({
                id: `${s.id}-${suggestion.gridLineId}`,
                strategyId: s.id,
                strategyName: s.name,
                symbol: s.symbol,
                ...suggestion,
                read: false,
                createdAt: new Date().toISOString()
              })
            })
          }
          if (data.risks) {
            data.risks.forEach((risk, index) => {
              suggestions.push({
                id: `${s.id}-risk-${index}`,
                strategyId: s.id,
                strategyName: s.name,
                symbol: s.symbol,
                type: 'RISK',
                reason: risk.message,
                read: false,
                createdAt: new Date().toISOString()
              })
            })
          }
        } catch (error) {
          console.error(`获取策略${s.id}的智能建议失败:`, error)
        }
      }
    }
    
    allSuggestions.value = suggestions.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

const goToStrategyDetail = (strategyId) => {
  router.push(`/m/strategy/${strategyId}`)
}

const quickExecute = (item) => {
  currentSuggestion.value = item
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
  if (!currentSuggestion.value) {
    ElMessage.error('参数错误')
    return
  }

  executing.value = true
  try {
    await executeTick(currentSuggestion.value.strategyId, {
      gridLineId: currentSuggestion.value.gridLineId,
      type: currentSuggestion.value.type,
      price: currentSuggestion.value.price,
      quantity: currentSuggestion.value.quantity,
      fee: feeInput.value ? parseFloat(feeInput.value) : null,
      tradeTime: tradeTime.value
    })
    ElMessage.success('执行成功')
    executeDialogVisible.value = false
    currentSuggestion.value = null
    await loadData()
  } catch (error) {
    console.error('执行失败:', error)
    ElMessage.error('执行失败: ' + (error.response?.data?.message || error.message))
  } finally {
    executing.value = false
  }
}

const handleIgnore = async (item) => {
  try {
    await ElMessageBox.confirm('确定要忽略这条建议吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    allSuggestions.value = allSuggestions.value.filter(s => s.id !== item.id)
    ElMessage.success('已忽略')
  } catch {
  }
}

const handleMarkAllRead = () => {
  allSuggestions.value.forEach(item => {
    item.read = true
  })
  ElMessage.success('已全部标记为已读')
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.message-center {
  min-height: 100vh;
  background: #f5f6fa;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.title {
  font-size: 18px;
  font-weight: 600;
}

.header .el-button {
  color: white;
}

.tabs {
  display: flex;
  background: white;
  padding: 8px 16px;
  gap: 24px;
  border-bottom: 1px solid #eee;
}

.tab-item {
  position: relative;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  padding: 8px 0;
}

.tab-item.active {
  color: #667eea;
  font-weight: 600;
}

.tab-item.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: #667eea;
  border-radius: 2px;
}

.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 4px;
  background: #f56c6c;
  color: white;
  font-size: 11px;
  border-radius: 9px;
}

.content {
  padding: 12px 16px;
  padding-bottom: 80px;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: #909399;
}

.empty-state .el-icon {
  font-size: 64px;
  color: #67c23a;
  margin-bottom: 16px;
}

.empty-state p {
  font-size: 14px;
  margin: 0;
}

.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.suggestion-item {
  background: white;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.suggestion-item.unread {
  border-left: 3px solid #667eea;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.item-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.type-icon {
  font-size: 24px;
}

.item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.strategy-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.suggestion-type {
  font-size: 12px;
  color: #909399;
}

.item-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time {
  font-size: 12px;
  color: #909399;
}

.unread-dot {
  width: 8px;
  height: 8px;
  background: #f56c6c;
  border-radius: 50%;
}

.item-body {
  margin-bottom: 12px;
}

.suggestion-detail {
  display: flex;
  gap: 12px;
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.suggestion-reason {
  font-size: 13px;
  color: #909399;
  line-height: 1.5;
}

.item-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
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
