<template>
  <div class="mobile-home">
    <!-- 顶部渐变背景区 -->
    <div class="header-area">
      <div class="greeting">我的网格</div>
      
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

    <!-- 快速操作区 -->
    <div class="action-area">
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
          @click="selectStrategy(s)"
        >
          <div class="strategy-main">
            <div class="strategy-symbol">{{ s.symbol }}</div>
            <div class="strategy-stats">
              <span class="stat">
                <span class="stat-label">持仓</span>
                <span class="stat-value">{{ s.boughtCount }}/19</span>
              </span>
              <span class="stat">
                <span class="stat-label">收益</span>
                <span class="stat-value profit" :class="{ negative: s.realizedProfit < 0 }">
                  {{ formatProfit(s.realizedProfit) }}
                </span>
              </span>
            </div>
          </div>
          <el-icon class="strategy-arrow"><ArrowRight /></el-icon>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  HomeFilled, Plus, List, ArrowRight, 
  Bottom, Top, CircleCheck 
} from '@element-plus/icons-vue'
import { getAllStrategies, getStrategyDetail, getGridLines } from '../../api'

const router = useRouter()

const strategies = ref([])
const currentStrategy = ref(null)
const gridLines = ref([])
const loading = ref(false)

// 总收益
const totalProfit = computed(() => {
  return strategies.value.reduce((sum, s) => sum + Number(s.realizedProfit || 0), 0)
})

// 今日收益（模拟，实际需要后端支持）
const todayProfit = computed(() => 0)

// 找出最需要操作的网格
const pendingGrid = computed(() => {
  if (!currentStrategy.value || gridLines.value.length === 0) return null
  
  // 找最靠近的待买入网格（level最小的WAIT_BUY）
  const waitBuyGrids = gridLines.value
    .filter(g => g.state === 'WAIT_BUY')
    .sort((a, b) => a.level - b.level)
  
  // 找已买入的网格
  const boughtGrids = gridLines.value
    .filter(g => g.state === 'BOUGHT' || g.state === 'WAIT_SELL')
    .sort((a, b) => a.sellPrice - b.sellPrice)
  
  // 优先提示卖出（已有持仓）
  if (boughtGrids.length > 0) {
    const g = boughtGrids[0]
    return {
      id: g.id,
      level: g.level,
      action: 'sell',
      price: g.sellPrice,
      type: formatGridType(g.gridType),
      strategyId: currentStrategy.value.id
    }
  }
  
  // 其次提示买入
  if (waitBuyGrids.length > 0) {
    const g = waitBuyGrids[0]
    return {
      id: g.id,
      level: g.level,
      action: 'buy',
      price: g.buyPrice,
      type: formatGridType(g.gridType),
      strategyId: currentStrategy.value.id
    }
  }
  
  return null
})

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const res = await getAllStrategies()
    strategies.value = res.data.map(s => ({
      ...s,
      boughtCount: 0 // 需要单独获取
    }))
    
    // 加载第一个策略的网格数据（不跳转）
    if (strategies.value.length > 0) {
      await loadStrategyGrids(strategies.value[0])
    }
  } catch (error) {
    console.error('加载失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载策略的网格数据（不跳转）
const loadStrategyGrids = async (s) => {
  currentStrategy.value = s
  try {
    const res = await getGridLines(s.id)
    gridLines.value = res.data.gridPlans || []
    
    // 更新该策略的已买入数量
    const idx = strategies.value.findIndex(item => item.id === s.id)
    if (idx >= 0) {
      strategies.value[idx].boughtCount = gridLines.value.filter(
        g => g.state === 'BOUGHT' || g.state === 'WAIT_SELL'
      ).length
    }
  } catch (error) {
    console.error('加载网格失败:', error)
  }
}

// 选择策略 - 跳转到详情页
const selectStrategy = (s) => {
  router.push(`/m/strategy/${s.id}`)
}

// 去录入
const goToRecord = (grid) => {
  router.push({
    path: '/m/record',
    query: {
      strategyId: grid.strategyId,
      gridId: grid.id,
      action: grid.action
    }
  })
}

// 快速录入
const goToQuickRecord = () => {
  if (currentStrategy.value) {
    router.push(`/m/record?strategyId=${currentStrategy.value.id}`)
  } else if (strategies.value.length > 0) {
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

const formatGridType = (type) => {
  const map = { SMALL: '小', MEDIUM: '中', LARGE: '大' }
  return map[type] || '小'
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

.greeting {
  color: rgba(255,255,255,0.9);
  font-size: 15px;
  margin-bottom: 16px;
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
  gap: 10px;
}

.strategy-item {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  cursor: pointer;
}

.strategy-item:active {
  background: #fafafa;
}

.strategy-main {
  flex: 1;
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
</style>
