<template>
  <div class="history-page">
    <!-- 顶部栏 -->
    <div class="top-bar">
      <el-icon class="back-btn" @click="goBack"><ArrowLeft /></el-icon>
      <span class="page-title">成交记录</span>
      <span class="placeholder"></span>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-card">
      <div class="stat-item">
        <div class="stat-value">{{ totalBuy }}</div>
        <div class="stat-label">买入次数</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <div class="stat-value">{{ totalSell }}</div>
        <div class="stat-label">卖出次数</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <div class="stat-value profit" :class="{ negative: totalProfit < 0 }">
          {{ formatProfit(totalProfit) }}
        </div>
        <div class="stat-label">总收益</div>
      </div>
    </div>

    <!-- 策略选择（如果有多个） -->
    <div class="filter-bar" v-if="strategies.length > 1">
      <div 
        v-for="s in strategies" 
        :key="s.id"
        class="filter-chip"
        :class="{ active: selectedStrategyId === s.id }"
        @click="selectStrategy(s.id)"
      >
        {{ s.symbol }}
      </div>
    </div>

    <!-- 记录列表 -->
    <div class="record-list" v-if="!loading">
      <div v-if="records.length === 0" class="empty-state">
        <el-icon><Document /></el-icon>
        <span>暂无成交记录</span>
      </div>

      <div 
        v-for="(group, date) in groupedRecords" 
        :key="date"
        class="record-group"
      >
        <div class="group-date">{{ date }}</div>
        <div class="group-items">
          <div 
            v-for="record in group" 
            :key="record.id"
            class="record-item"
          >
            <div class="record-icon" :class="record.type.toLowerCase()">
              <el-icon v-if="record.type === 'BUY'"><Bottom /></el-icon>
              <el-icon v-else><Top /></el-icon>
            </div>
            <div class="record-info">
              <div class="record-main">
                <span class="record-action">{{ record.type === 'BUY' ? '买入' : '卖出' }}</span>
                <span class="record-grid">第{{ record.gridLineLevel }}格</span>
              </div>
              <div class="record-time">{{ formatTime(record.tradeTime) }}</div>
            </div>
            <div class="record-amount">
              <div class="amount-value">¥{{ formatPrice(record.price) }}</div>
              <div class="amount-qty">{{ formatAmount(record.amount) }}元</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载中 -->
    <div class="loading-state" v-else>
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <!-- 底部导航 -->
    <div class="bottom-nav">
      <div class="nav-item" @click="goHome">
        <el-icon><HomeFilled /></el-icon>
        <span>首页</span>
      </div>
      <div class="nav-item main" @click="goToRecord">
        <div class="nav-main-btn">
          <el-icon><Plus /></el-icon>
        </div>
        <span>录入</span>
      </div>
      <div class="nav-item active">
        <el-icon><List /></el-icon>
        <span>记录</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  ArrowLeft, HomeFilled, Plus, List, 
  Document, Bottom, Top, Loading 
} from '@element-plus/icons-vue'
import { getAllStrategies, getTradeRecords } from '../../api'

const router = useRouter()

const strategies = ref([])
const selectedStrategyId = ref(null)
const records = ref([])
const loading = ref(false)

// 统计
const totalBuy = computed(() => records.value.filter(r => r.type === 'BUY').length)
const totalSell = computed(() => records.value.filter(r => r.type === 'SELL').length)
const totalProfit = computed(() => {
  // 简化计算：卖出金额 - 买入金额
  const sellAmount = records.value
    .filter(r => r.type === 'SELL')
    .reduce((sum, r) => sum + Number(r.amount || 0), 0)
  const buyAmount = records.value
    .filter(r => r.type === 'BUY')
    .reduce((sum, r) => sum + Number(r.amount || 0), 0)
  return sellAmount - buyAmount
})

// 按日期分组
const groupedRecords = computed(() => {
  const groups = {}
  const sorted = [...records.value].sort((a, b) => 
    new Date(b.tradeTime) - new Date(a.tradeTime)
  )
  
  sorted.forEach(record => {
    const date = formatDate(record.tradeTime)
    if (!groups[date]) {
      groups[date] = []
    }
    groups[date].push(record)
  })
  
  return groups
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getAllStrategies()
    strategies.value = res.data
    
    if (strategies.value.length > 0) {
      selectedStrategyId.value = strategies.value[0].id
      await loadRecords()
    }
  } catch (error) {
    console.error('加载失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载记录
const loadRecords = async () => {
  if (!selectedStrategyId.value) return
  
  try {
    const res = await getTradeRecords(selectedStrategyId.value)
    records.value = res.data || []
  } catch (error) {
    console.error('加载记录失败:', error)
  }
}

// 选择策略
const selectStrategy = async (id) => {
  selectedStrategyId.value = id
  await loadRecords()
}

// 导航
const goBack = () => router.back()
const goHome = () => router.push('/m')
const goToRecord = () => {
  if (selectedStrategyId.value) {
    router.push(`/m/record?strategyId=${selectedStrategyId.value}`)
  }
}

// 格式化
const formatPrice = (val) => val == null ? '-' : Number(val).toFixed(3)
const formatAmount = (val) => val == null ? '0' : Math.round(Number(val)).toString()
const formatProfit = (val) => {
  if (val == null) return '0.00'
  const num = Number(val)
  return (num >= 0 ? '+' : '') + num.toFixed(2)
}
const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  
  if (d.toDateString() === today.toDateString()) return '今天'
  if (d.toDateString() === yesterday.toDateString()) return '昨天'
  return `${d.getMonth() + 1}月${d.getDate()}日`
}
const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.history-page {
  min-height: 100vh;
  background: #f5f6fa;
  padding-bottom: 80px;
}

/* 顶部栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
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

/* 统计卡片 */
.stats-card {
  margin: 16px;
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  display: flex;
  align-items: center;
  box-shadow: 0 4px 20px rgba(0,0,0,0.06);
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
}

.stat-value.profit {
  color: #67c23a;
}

.stat-value.profit.negative {
  color: #f56c6c;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.stat-divider {
  width: 1px;
  height: 30px;
  background: #ebeef5;
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  gap: 10px;
  padding: 0 16px;
  margin-bottom: 16px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.filter-chip {
  flex-shrink: 0;
  padding: 8px 16px;
  background: #fff;
  border-radius: 20px;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  border: 1px solid #ebeef5;
}

.filter-chip.active {
  background: #667eea;
  color: #fff;
  border-color: #667eea;
}

/* 记录列表 */
.record-list {
  padding: 0 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
  color: #909399;
}

.empty-state .el-icon {
  font-size: 48px;
  color: #dcdfe6;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 60px;
  font-size: 32px;
  color: #667eea;
}

.record-group {
  margin-bottom: 20px;
}

.group-date {
  font-size: 13px;
  color: #909399;
  margin-bottom: 10px;
  padding-left: 4px;
}

.group-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.record-item {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.record-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-right: 12px;
}

.record-icon.buy {
  background: #fef0f0;
  color: #f56c6c;
}

.record-icon.sell {
  background: #f0f9eb;
  color: #67c23a;
}

.record-info {
  flex: 1;
}

.record-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.record-action {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.record-grid {
  font-size: 12px;
  color: #909399;
  background: #f5f6fa;
  padding: 2px 8px;
  border-radius: 4px;
}

.record-time {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 4px;
}

.record-amount {
  text-align: right;
}

.amount-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.amount-qty {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
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
</style>
