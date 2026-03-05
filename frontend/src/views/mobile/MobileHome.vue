<template>
  <div class="mobile-home">
    <!-- 顶部渐变背景区 -->
    <div class="header-area">
      <div class="header-top">
        <div class="greeting">我的网格</div>
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
      
      <!-- 收益卡片 -->
      <div class="profit-card">
        <div class="profit-label">已实现收益</div>
        <div class="profit-value" :class="{ negative: totalProfit < 0 }">
          {{ formatProfit(totalProfit) }}
        </div>
        <div class="profit-sub">
          <span>今日 {{ formatProfit(todayProfit) }}</span>
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
            <el-tag 
              size="small" 
              :type="s.status === 'RUNNING' ? 'success' : 'info'"
            >
              {{ s.status === 'RUNNING' ? '运行中' : '已停止' }}
            </el-tag>
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
              <span class="stat-value">¥{{ formatPrice(s.basePrice) }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">持仓</span>
              <span class="stat-value">{{ calculatePositionRatio(s) }}%</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">盈亏</span>
              <span class="stat-value profit" :class="{ negative: s.realizedProfit < 0 }">
                {{ formatProfit(s.realizedProfit) }}
              </span>
            </div>
          </div>

          <!-- 触发提醒 -->
          <div class="strategy-alerts" v-if="s.triggerCount > 0">
            <el-icon color="#ff9800"><BellFilled /></el-icon>
            <span>{{ s.triggerCount }}条触发提醒</span>
            <span class="alert-detail">{{ formatTriggers(s.triggers) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部导航 -->
    <div class="bottom-nav">
      <div class="nav-item active">
        <el-icon><HomeFilled /></el-icon>
        <span>首页</span>
      </div>
      <div class="nav-item main" @click="goToQuickRecord">
        <div class="nav-main-btn">
          <el-icon><Plus /></el-icon>
        </div>
        <span>录入</span>
      </div>
      <div class="nav-item" @click="goToHistory">
        <el-icon><List /></el-icon>
        <span>记录</span>
      </div>
    </div>

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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  HomeFilled, Plus, List, RefreshRight, BellFilled
} from '@element-plus/icons-vue'
import { getAllStrategies, updateStrategyLastPrice } from '../../api'

const router = useRouter()

const strategies = ref([])
const loading = ref(false)
const batchUpdateDialogVisible = ref(false)
const priceInputs = ref({})
const updating = ref(false)

// 总收益
const totalProfit = computed(() => {
  return strategies.value.reduce((sum, s) => sum + Number(s.realizedProfit || 0), 0)
})

// 今日收益（模拟，实际需要后端支持）
const todayProfit = computed(() => 0)

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
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
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

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.mobile-home {
  min-height: 100vh;
  background: #f5f6fa;
  padding-bottom: 80px;
}

/* 顶部区域 */
.header-area {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px 16px 30px;
  border-radius: 0 0 24px 24px;
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
  padding: 20px 0;
}

.profit-label {
  color: rgba(255,255,255,0.8);
  font-size: 13px;
  margin-bottom: 8px;
}

.profit-value {
  color: #7dffb3;
  font-size: 36px;
  font-weight: 700;
  font-family: 'DIN', 'Helvetica Neue', sans-serif;
}

.profit-value.negative {
  color: #ffb3b3;
}

.profit-sub {
  color: rgba(255,255,255,0.7);
  font-size: 12px;
  margin-top: 8px;
}

/* 快速操作区 */
.action-area {
  padding: 0 16px;
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
  padding: 20px 16px;
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
  align-items: center;
  margin-bottom: 10px;
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
  display: flex;
  justify-content: space-around;
  margin: 10px 0;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
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

/* 底部导航 */
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-around;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.06);
  padding-bottom: env(safe-area-inset-bottom);
  z-index: 100;
}

.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: #909399;
  font-size: 10px;
  cursor: pointer;
  padding: 8px 20px;
}

.nav-item .el-icon {
  font-size: 22px;
}

.nav-item.active {
  color: #667eea;
}

.nav-item.main {
  position: relative;
  top: -10px;
}

.nav-main-btn {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 26px;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
}

.nav-item.main span {
  margin-top: 4px;
}

/* 智能建议区域 */
.suggestion-area {
  margin: -30px 16px 16px;
  position: relative;
  z-index: 2;
}
</style>
