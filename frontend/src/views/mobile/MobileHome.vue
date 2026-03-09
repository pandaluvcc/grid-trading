<template>
  <MobileLayout title="我的网格" :show-back="false" :show-tab-bar="false">
    <!-- 顶部渐变背景区 -->
    <div class="header-area">
      <div class="header-top">
        <div class="greeting">我的网格</div>
        <div class="header-right">
          <el-button 
            text
            class="message-center-btn"
            @click="goToMessageCenter"
          >
            <el-badge v-if="totalSuggestionsCount > 0" :value="totalSuggestionsCount" class="message-badge">
              <el-icon class="bell-icon"><Bell /></el-icon>
            </el-badge>
            <el-icon v-else class="bell-icon"><Bell /></el-icon>
          </el-button>
          <el-button 
            type="primary" 
            size="small" 
            @click="showBatchUpdateDialog"
            class="update-price-btn"
          >
            <el-icon><RefreshRight /></el-icon>
            更新行情
          </el-button>
        </div>
      </div>
      
      <!-- 收益卡片 -->
      <div class="profit-card">
        <div class="profit-row">
          <div class="profit-col">
            <div class="profit-label">证券市值</div>
            <div class="profit-value market-value">
              ¥{{ formatAmount(totalMarketValue) }}
            </div>
          </div>
          <div class="profit-col">
            <div class="profit-label">已实现收益</div>
            <div class="profit-value" :class="{ negative: totalPositionProfit < 0 }">
              {{ formatProfit(totalPositionProfit) }}
            </div>
            <div class="profit-sub">
              <span>今日 {{ formatProfit(todayProfit) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 快速操作区（保留作为备用） -->
    <div class="action-area" v-if="false">
      <!-- 待操作提示 -->
      <div class="pending-section" v-if="pendingGrid">
        <div class="pending-card" @click="goToRecord(pendingGrid)">
          <div class="pending-icon">
            <el-icon v-if="pendingGrid.action === 'buy'"><Bottom /></el-icon>
            <el-icon v-else><Top /></el-icon>
          </div>
          <div class="pending-info">
            <div class="pending-title">
              {{ pendingGrid.action === 'buy' ? '建议买入' : '建议卖出' }}
            </div>
            <div class="pending-detail">
              第{{ pendingGrid.level }}格 · {{ pendingGrid.type }}网 · 
              ¥{{ formatPrice(pendingGrid.price) }}
            </div>
          </div>
          <div class="pending-action">
            <span>去录入</span>
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <!-- 无待操作时 -->
      <div class="no-pending" v-else>
        <el-icon><CircleCheck /></el-icon>
        <span>暂无待操作网格</span>
      </div>
    </div>

    <!-- 策略列表 -->
    <div class="strategy-section">
      <div class="section-header">
        <span class="section-title">我的策略</span>
        <div class="section-actions">
          <span class="section-count">{{ strategies.length }}个</span>
          <span class="add-btn" @click="goToCreate">
            <el-icon><Plus /></el-icon>
          </span>
        </div>
      </div>

      <div class="strategy-list">
        <div 
          v-for="s in strategies" 
          :key="s.id" 
          class="strategy-item"
          @click="goToDetail(s)"
        >
          <div class="strategy-header">
            <div class="strategy-title">
              <div class="strategy-name">{{ s.name || s.symbol }}</div>
              <div class="strategy-code" v-if="s.name">{{ s.symbol }}</div>
            </div>
            <div class="strategy-header-right">
              <div class="strategy-icons" v-if="strategySuggestions[s.id]">
                <!-- 风险提示图标 -->
                <el-tooltip 
                  v-if="getStrategyRisks(s.id).length > 0"
                  :content="formatRisksTooltip(s.id)"
                  placement="top"
                  effect="dark"
                >
                  <span class="suggestion-icon risk-icon" @click.stop>
                    <el-icon color="#e6a23c"><Warning /></el-icon>
                    <span class="icon-count">{{ getStrategyRisks(s.id).length }}</span>
                  </span>
                </el-tooltip>

                <!-- 建议操作数量图标 -->
                <el-tooltip 
                  v-if="getStrategySuggestionsCount(s.id) > 0"
                  :content="`有${getStrategySuggestionsCount(s.id)}条建议操作`"
                  placement="top"
                  effect="dark"
                >
                  <span class="suggestion-icon action-icon" @click.stop>
                    <el-icon color="#409eff"><Bell /></el-icon>
                    <span class="icon-count">{{ getStrategySuggestionsCount(s.id) }}</span>
                  </span>
                </el-tooltip>
              </div>
              <el-tag 
                size="small" 
                :type="s.status === 'RUNNING' ? 'success' : 'info'"
              >
                {{ s.status === 'RUNNING' ? '运行中' : '已停止' }}
              </el-tag>
            </div>
          </div>

          <!-- 现价和涨跌 -->
          <div class="strategy-price">
            <span class="price">现价 ¥{{ formatPrice(s.lastPrice || s.basePrice) }}</span>
            <span class="price-change" :class="getPriceChangeClass(s)">
              {{ formatPriceChange(s) }}
            </span>
          </div>

          <!-- 统计信息 -->
          <div class="strategy-stats">
            <div class="stat-item">
              <span class="stat-label">成本</span>
              <span class="stat-value">¥{{ formatPrice(s.costPrice) }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">持仓</span>
              <span class="stat-value">{{ formatQuantity(s.position) }}股</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">仓位</span>
              <span class="stat-value">{{ formatPositionRatio(s.positionRatio) }}%</span>
            </div>
            <div class="stat-item profit-item">
              <span class="stat-label">盈亏</span>
              <div class="profit-group">
                <span class="stat-value profit" :class="{ negative: s.positionProfit < 0 }">
                  {{ formatProfit(s.positionProfit) }}
                </span>
                <span class="profit-percent" :class="{ negative: s.positionProfitPercent < 0 }">
                  {{ formatProfitPercent(s.positionProfitPercent) }}
                </span>
              </div>
            </div>
          </div>



          <!-- 触发提醒 -->
          <div class="strategy-alerts" v-if="s.triggerCount > 0">
            <el-icon color="#ff9800"><BellFilled /></el-icon>
            <span>{{ s.triggerCount }}条触发提醒</span>
            <span class="alert-detail">{{ formatTriggers(s.triggers) }}</span>
          </div>

          <!-- 建议预览卡片（方案A：底部建议条） -->
          <div class="suggestion-preview" v-if="getTopSuggestion(s.id)">
            <div class="preview-header">
              <div class="preview-content">
                <span class="preview-icon">💡</span>
                <span class="preview-text">
                  {{ getTopSuggestion(s.id).type === 'BUY' ? '建议买入' : '建议卖出' }}
                  第{{ getTopSuggestion(s.id).gridLevel }}网 · ¥{{ formatPrice(getTopSuggestion(s.id).price) }}
                </span>
                <span class="more-suggestions-inline" v-if="getMoreSuggestionsCount(s.id) > 0" @click.stop="goToDetail(s)">
                  还有{{ getMoreSuggestionsCount(s.id) }}条建议 &gt;
                </span>
              </div>
            </div>
            <div class="preview-actions">
              <el-button size="small" text @click.stop="goToDetail(s)">详情</el-button>
              <el-button 
                size="small" 
                type="primary" 
                @click.stop="quickExecute(s, getTopSuggestion(s.id))"
              >
                执行
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 执行确认弹窗 -->
    <el-dialog
      v-model="executeDialogVisible"
      :title="`确认执行${currentSuggestion?.type === 'BUY' ? '买入' : '卖出'}`"
      width="90%"
      :close-on-click-modal="false"
    >
      <div class="execute-dialog-content" v-if="currentSuggestion && currentStrategy">
        <div class="strategy-info">
          <span class="strategy-name">{{ currentStrategy.name || currentStrategy.symbol }}</span>
          <el-tag size="small" type="info">{{ currentStrategy.symbol }}</el-tag>
        </div>
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

    <!-- 批量更新行情弹窗 -->
    <el-dialog
      v-model="batchUpdateDialogVisible"
      title="批量更新行情价格"
      width="90%"
      :close-on-click-modal="false"
    >
      <div class="batch-update-content">
        <div 
          v-for="s in strategies" 
          :key="s.id" 
          class="price-update-item"
        >
          <div class="update-item-header">
            <span class="item-name">{{ s.name || s.symbol }}</span>
            <span class="item-code">{{ s.symbol }}</span>
          </div>
          <div class="update-item-input">
            <el-input
              v-model="priceInputs[s.id]"
              type="number"
              placeholder="输入当前价格"
              size="large"
            >
              <template #prefix>¥</template>
            </el-input>
            <span class="last-price" v-if="s.lastPrice">
              ← 上次：{{ formatPrice(s.lastPrice) }}
            </span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="batchUpdateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="handleBatchUpdate">确认更新</el-button>
      </template>
    </el-dialog>
  </MobileLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  RefreshRight, BellFilled, Warning, Bell
} from '@element-plus/icons-vue'
import { getAllStrategies, updateStrategyLastPrice, getSmartSuggestions, executeTick } from '../../api'
import MobileLayout from './MobileLayout.vue'

const router = useRouter()

const strategies = ref([])
const loading = ref(false)
const batchUpdateDialogVisible = ref(false)
const priceInputs = ref({})
const updating = ref(false)
const strategySuggestions = ref({}) // 存储每个策略的智能建议

// 执行确认弹窗相关状态
const executeDialogVisible = ref(false)
const currentSuggestion = ref(null)
const currentStrategy = ref(null)
const tradeTime = ref('')
const feeInput = ref('')
const executing = ref(false)

// 总收益
const totalProfit = computed(() => {
  return strategies.value.reduce((sum, s) => sum + Number(s.realizedProfit || 0), 0)
})

// 总市值
const totalMarketValue = computed(() => {
  return strategies.value.reduce((sum, s) => {
    const lastPrice = Number(s.lastPrice || s.basePrice || 0)
    const position = Number(s.position || 0)
    return sum + lastPrice * position
  }, 0)
})

// 总持仓盈亏
const totalPositionProfit = computed(() => {
  return strategies.value.reduce((sum, s) => sum + Number(s.positionProfit || 0), 0)
})

// 今日收益（模拟，实际需要后端支持）
const todayProfit = computed(() => 0)

// 总建议数量
const totalSuggestionsCount = computed(() => {
  let count = 0
  for (const strategyId in strategySuggestions.value) {
    count += getStrategySuggestionsCount(strategyId)
  }
  return count
})

// 显示批量更新弹窗
const showBatchUpdateDialog = () => {
  // 初始化价格输入框，默认为上次价格或基准价
  priceInputs.value = {}
  strategies.value.forEach(s => {
    priceInputs.value[s.id] = s.lastPrice || s.basePrice
  })
  batchUpdateDialogVisible.value = true
}

// 批量更新价格
const handleBatchUpdate = async () => {
  updating.value = true
  try {
    const updates = []
    for (const s of strategies.value) {
      const newPrice = priceInputs.value[s.id]
      if (newPrice && newPrice != s.lastPrice) {
        updates.push(
          updateStrategyLastPrice(s.id, newPrice).then(() => {
            s.lastPrice = newPrice
            s.lastPriceUpdatedAt = new Date().toISOString()
          })
        )
      }
    }
    
    await Promise.all(updates)
    
    ElMessage.success(`已更新 ${updates.length} 个策略的价格`)
    batchUpdateDialogVisible.value = false
    
    // 重新加载数据以获取触发提醒
    await loadData()
  } catch (error) {
    console.error('批量更新失败:', error)
    ElMessage.error('更新失败: ' + (error.response?.data?.message || error.message))
  } finally {
    updating.value = false
  }
}

// 计算持仓比例
const calculatePositionRatio = (s) => {
  if (!s.maxCapital || s.maxCapital == 0) return 0
  const invested = s.investedAmount || 0
  return ((invested / s.maxCapital) * 100).toFixed(1)
}

// 获取涨跌样式
const getPriceChangeClass = (s) => {
  if (!s.lastPrice) return ''
  const change = s.lastPrice - s.basePrice
  if (change > 0) return 'up'
  if (change < 0) return 'down'
  return ''
}

// 格式化涨跌
const formatPriceChange = (s) => {
  if (!s.lastPrice) return ''
  const change = s.lastPrice - s.basePrice
  const changePercent = ((change / s.basePrice) * 100).toFixed(2)
  const changeStr = change >= 0 ? `+${change.toFixed(3)}` : change.toFixed(3)
  return `${changeStr} (${changePercent}%)`
}

// 格式化触发提醒
const formatTriggers = (triggers) => {
  if (!triggers || triggers.length === 0) return ''
  const types = triggers.map(t => t.action === 'BUY' ? '买入' : '卖出')
  return types.join('、')
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getAllStrategies()
    strategies.value = res.data.map(s => ({
      ...s,
      triggerCount: 0, // TODO: 从后端获取触发提醒数量
      triggers: [] // TODO: 从后端获取触发提醒列表
    }))
    
    // 为每个策略获取智能建议
    await loadSuggestionsForStrategies()
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

// 为所有策略加载智能建议
const loadSuggestionsForStrategies = async () => {
  for (const s of strategies.value) {
    if (s.lastPrice) {
      try {
        const data = await getSmartSuggestions(s.id, s.lastPrice)
        strategySuggestions.value[s.id] = data
      } catch (error) {
        console.error(`获取策略${s.id}的智能建议失败:`, error)
        strategySuggestions.value[s.id] = null
      }
    }
  }
}

// 获取策略的风险提示
const getStrategyRisks = (strategyId) => {
  const suggestion = strategySuggestions.value[strategyId]
  return suggestion?.risks || []
}

// 获取策略的建议操作数量
const getStrategySuggestionsCount = (strategyId) => {
  const suggestion = strategySuggestions.value[strategyId]
  return suggestion?.suggestions?.length || 0
}

// 格式化风险提示文字
const formatRisksTooltip = (strategyId) => {
  const risks = getStrategyRisks(strategyId)
  if (risks.length === 0) return ''
  return risks.map(r => r.message).join('\n')
}

// 去详情
const goToDetail = (s) => {
  router.push(`/m/strategy/${s.id}`)
}

// 快速录入
const goToQuickRecord = () => {
  if (strategies.value.length > 0) {
    router.push(`/m/record?strategyId=${strategies.value[0].id}`)
  } else {
    ElMessage.warning('请先创建策略')
    router.push('/m/create')
  }
}

// 查看历史
const goToHistory = () => {
  router.push('/m/history')
}

// 创建策略
const goToCreate = () => {
  router.push('/m/create')
}

// 格式化
const formatProfit = (val) => {
  if (val == null) return '0.00'
  const num = Number(val)
  return (num >= 0 ? '+' : '') + num.toFixed(2)
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

const formatPositionRatio = (val) => {
  if (val == null) return '0.0'
  return Number(val).toFixed(1)
}

const formatProfitPercent = (val) => {
  if (val == null) return '0.00%'
  const num = Number(val)
  return (num >= 0 ? '+' : '') + num.toFixed(2) + '%'
}

// 获取策略的顶级建议
const getTopSuggestion = (strategyId) => {
  const suggestion = strategySuggestions.value[strategyId]
  if (!suggestion || !suggestion.suggestions || suggestion.suggestions.length === 0) {
    return null
  }
  return suggestion.suggestions[0]
}

// 获取剩余建议数量
const getMoreSuggestionsCount = (strategyId) => {
  const suggestion = strategySuggestions.value[strategyId]
  if (!suggestion || !suggestion.suggestions) {
    return 0
  }
  return Math.max(0, suggestion.suggestions.length - 1)
}

const getGridTypeName = (type) => {
  const map = {
    'SMALL': '小网',
    'MEDIUM': '中网',
    'LARGE': '大网'
  }
  return map[type] || type
}

// 前往消息中心
const goToMessageCenter = () => {
  router.push('/m/messages')
}

// 快速执行
const quickExecute = (strategy, suggestion) => {
  currentStrategy.value = strategy
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

// 取消执行
const cancelExecute = () => {
  executeDialogVisible.value = false
  currentSuggestion.value = null
  currentStrategy.value = null
}

// 确认执行
const confirmExecute = async () => {
  if (!tradeTime.value) {
    ElMessage.warning('请选择交易时间')
    return
  }
  if (!currentStrategy.value || !currentSuggestion.value) {
    ElMessage.error('参数错误')
    return
  }

  executing.value = true
  try {
    await executeTick(currentStrategy.value.id, {
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
    currentStrategy.value = null
    await loadData()
  } catch (error) {
    console.error('执行失败:', error)
    ElMessage.error('执行失败: ' + (error.response?.data?.message || error.message))
  } finally {
    executing.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* 顶部区域 */
.header-area {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 16px;
  border-radius: 16px;
  margin-bottom: 12px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.greeting {
  color: rgba(255,255,255,0.9);
  font-size: 15px;
}

.update-price-btn {
  background: rgba(255,255,255,0.2);
  border: none;
  color: #fff;
  font-size: 13px;
  border-radius: 16px;
  padding: 6px 12px;
  height: 32px;
}

.update-price-btn:hover {
  background: rgba(255,255,255,0.3);
}

.profit-card {
  text-align: center;
  padding: 16px;
  background: rgba(255,255,255,0.1);
  border-radius: 12px;
  backdrop-filter: blur(10px);
}

.profit-row {
  display: flex;
  justify-content: space-around;
  gap: 16px;
}

.profit-col {
  flex: 1;
}

.profit-label {
  color: rgba(255,255,255,0.8);
  font-size: 13px;
  margin-bottom: 8px;
}

.profit-value {
  color: #7dffb3;
  font-size: 22px;
  font-weight: 600;
  font-family: 'DIN', 'Helvetica Neue', sans-serif;
  text-shadow: 0 2px 4px rgba(0,0,0,0.1);
  transition: all 0.3s ease;
}

.profit-value:hover {
  transform: scale(1.05);
}

.profit-value.negative {
  color: #ffb3b3;
}

.profit-value.market-value {
  color: #fff;
}

.profit-sub {
  color: rgba(255,255,255,0.7);
  font-size: 12px;
  margin-top: 8px;
}

/* 快速操作区 */
.action-area {
  margin-top: -20px;
}

.pending-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  display: flex;
  align-items: center;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
  cursor: pointer;
}

.pending-card:active {
  transform: scale(0.98);
}

.pending-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  margin-right: 12px;
}

.pending-info {
  flex: 1;
}

.pending-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.pending-detail {
  font-size: 13px;
  color: #909399;
}

.pending-action {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #667eea;
  font-size: 14px;
  font-weight: 500;
}

.no-pending {
  background: #fff;
  border-radius: 16px;
  padding: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #67c23a;
  font-size: 15px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.08);
}

.no-pending .el-icon {
  font-size: 20px;
}

/* 策略列表 */
.strategy-section {
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.section-count {
  font-size: 13px;
  color: #909399;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.add-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
}

.add-btn:active {
  opacity: 0.8;
}

.strategy-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.strategy-item {
  background: #fff;
  border-radius: 12px;
  padding: 14px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  cursor: pointer;
  transition: all 0.3s;
}

.strategy-item:active {
  transform: scale(0.98);
}

.strategy-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 10px;
}

.strategy-header-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.strategy-icons {
  display: flex;
  align-items: center;
  gap: 6px;
}

.strategy-title {
  flex: 1;
}

.strategy-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.strategy-code {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.strategy-price {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 0;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.strategy-price .price {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.strategy-price .price-change {
  font-size: 13px;
  font-weight: 500;
}

.strategy-price .price-change.up {
  color: #f56c6c;
}

.strategy-price .price-change.down {
  color: #67c23a;
}

.strategy-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 10px 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-item.profit-item {
  align-items: flex-start;
}

.profit-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.stat-value.profit {
  color: #f56c6c;
}

.stat-value.negative {
  color: #67c23a;
}

.profit-percent {
  font-size: 12px;
  font-weight: 500;
  color: #f56c6c;
}

.profit-percent.negative {
  color: #67c23a;
}

.suggestion-icon {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: #f5f7fa;
  border-radius: 12px;
  cursor: pointer;
  font-size: 12px;
}

.suggestion-icon:hover {
  background: #e6e8eb;
}

.suggestion-icon .icon-count {
  font-size: 11px;
  font-weight: 600;
}

.risk-icon .icon-count {
  color: #e6a23c;
}

.action-icon .icon-count {
  color: #409eff;
}

.strategy-alerts {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  padding: 8px 10px;
  background: #fff7e6;
  border-radius: 6px;
  font-size: 13px;
  color: #ff9800;
}

.strategy-alerts .alert-detail {
  flex: 1;
  text-align: right;
  font-size: 12px;
  color: #666;
}

/* 批量更新弹窗 */
.batch-update-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 60vh;
  overflow-y: auto;
}

.price-update-item {
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 12px;
}

.update-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.item-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.item-code {
  font-size: 13px;
  color: #909399;
}

.update-item-input {
  display: flex;
  align-items: center;
  gap: 10px;
}

.update-item-input .el-input {
  flex: 1;
}

.last-price {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}

.strategy-item:active {
  background: #fafafa;
}

.strategy-main {
  flex: 1;
}

.strategy-name {
  font-size: 16px;
  font-weight: 600;
  color: #222;
  margin-bottom: 4px;
}

.strategy-code {
  font-size: 12px;
  color: #8a8f99;
  margin-bottom: 8px;
}

.strategy-symbol {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.strategy-stats {
  display: flex;
  gap: 20px;
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-label {
  font-size: 11px;
  color: #909399;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.stat-value.profit {
  color: #67c23a;
}

.stat-value.profit.negative {
  color: #f56c6c;
}

.strategy-arrow {
  color: #c0c4cc;
  font-size: 18px;
}

/* 智能建议区域 */
.suggestion-area {
  margin: -30px 0 16px;
  position: relative;
  z-index: 2;
}

/* 建议预览卡片样式 */
.suggestion-preview {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #eee;
}

.preview-header {
  margin-bottom: 8px;
}

.preview-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}



.more-suggestions-inline {
  font-size: 12px;
  color: #409eff;
  cursor: pointer;
  margin-right: 8px;
  white-space: nowrap;
}

.more-suggestions-inline:hover {
  text-decoration: underline;
}

.preview-icon {
  font-size: 18px;
}

.preview-text {
  flex: 1;
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.preview-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}



/* 执行弹窗样式 */
.execute-dialog-content {
  padding: 10px 0;
}

.strategy-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eee;
}

.strategy-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
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

/* 消息中心按钮样式 */
.message-center-btn {
  padding: 6px 8px;
  position: relative;
}

.message-badge {
  position: absolute;
  top: -2px;
  right: -2px;
}

.bell-icon {
  font-size: 20px;
  color: rgba(255,255,255,0.9);
}
</style>
