<template>
  <div id="app">
    <!-- 顶部导航栏 -->
    <el-header class="header">
      <div class="header-content">
        <h1 class="title">策略管理</h1>
      </div>
    </el-header>

    <!-- 主内容区 -->
    <el-main class="main-content">
      <!-- 创建策略表单 -->
      <el-card class="form-card">
        <template #header>
          <div class="card-header">
            <span>创建策略</span>
          </div>
        </template>
        <el-form :model="strategyForm" label-width="120px">
          <el-form-item label="策略名称">
            <el-input v-model="strategyForm.name" placeholder="请输入策略名称" />
          </el-form-item>
          <el-form-item label="基准价格">
            <el-input-number 
              v-model="strategyForm.basePrice" 
              :precision="2"
              :step="0.01"
              :min="0"
              placeholder="请输入基准价格" 
            />
          </el-form-item>
          <el-form-item label="网格数量">
            <el-input-number 
              v-model="strategyForm.gridCount" 
              :min="1"
              :step="1"
              placeholder="请输入网格数量" 
            />
          </el-form-item>
          <el-form-item label="网格间距">
            <el-input-number 
              v-model="strategyForm.gridSpacing" 
              :precision="3"
              :step="0.001"
              :min="0"
              placeholder="例如 0.03 表示 3%" 
            />
            <span class="help-text">（例如 0.03 表示 3%）</span>
          </el-form-item>
          <el-form-item label="单格金额">
            <el-input-number 
              v-model="strategyForm.amountPerGrid" 
              :precision="2"
              :step="1"
              :min="0"
              placeholder="请输入单格金额" 
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="createStrategy" :loading="creating">
              创建策略
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 策略列表 -->
      <el-card class="list-card">
        <template #header>
          <div class="card-header">
            <span>策略列表</span>
            <el-button type="primary" size="small" @click="loadStrategies">
              刷新
            </el-button>
          </div>
        </template>
        <el-table 
          :data="strategies" 
          v-loading="loading"
          border
          stripe
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="名称" width="180" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.status)">
                {{ scope.row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentPrice" label="最新价格" width="150">
            <template #default="scope">
              {{ scope.row.currentPrice ? scope.row.currentPrice.toFixed(2) : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="basePrice" label="基准价格" width="150">
            <template #default="scope">
              {{ scope.row.basePrice ? scope.row.basePrice.toFixed(2) : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="gridCount" label="网格数量" width="120" />
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="scope">
              {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button type="primary" size="small" @click="openExecuteDialog(scope.row)">
                执行
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 策略执行弹窗 -->
      <el-dialog 
        v-model="executeDialogVisible" 
        title="策略执行 / 推演" 
        width="700px"
      >
        <div v-if="currentStrategy" class="execute-dialog">
          <!-- 策略基本信息 -->
          <el-descriptions title="策略信息" :column="2" border>
            <el-descriptions-item label="策略ID">{{ currentStrategy.id }}</el-descriptions-item>
            <el-descriptions-item label="策略名称">{{ currentStrategy.name }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="getStatusType(currentStrategy.status)">
                {{ currentStrategy.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="基准价格">
              {{ currentStrategy.basePrice ? currentStrategy.basePrice.toFixed(2) : '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 执行操作区 -->
          <el-divider></el-divider>
          <div class="execute-section">
            <el-form :model="executeForm" label-width="100px">
              <el-form-item label="当前价格">
                <el-input-number 
                  v-model="executeForm.price" 
                  :precision="2"
                  :step="0.01"
                  :min="0"
                  placeholder="请输入当前价格"
                  style="width: 200px;"
                />
              </el-form-item>
              <el-form-item>
                <el-button 
                  type="primary" 
                  @click="executeTick" 
                  :loading="executing"
                >
                  执行一次
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 执行结果展示 -->
          <div v-if="executeResult" class="result-section">
            <el-divider>执行结果</el-divider>
            
            <!-- 策略状态 -->
            <el-descriptions :column="2" border>
              <el-descriptions-item label="策略状态">
                <el-tag :type="getStatusType(executeResult.status || currentStrategy.status)">
                  {{ executeResult.status || currentStrategy.status }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="当前价格">
                {{ executeResult.currentPrice ? executeResult.currentPrice.toFixed(2) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="当前持仓">
                {{ executeResult.position ? executeResult.position.toFixed(4) : '0.0000' }}
              </el-descriptions-item>
              <el-descriptions-item label="可用现金">
                {{ executeResult.availableCash ? executeResult.availableCash.toFixed(2) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="已投入资金">
                {{ executeResult.investedAmount ? executeResult.investedAmount.toFixed(2) : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="已实现收益">
                {{ executeResult.realizedProfit ? executeResult.realizedProfit.toFixed(2) : '-' }}
              </el-descriptions-item>
            </el-descriptions>

            <!-- 成交记录 -->
            <div v-if="executeResult.trades && executeResult.trades.length > 0" class="trades-section">
              <h4 style="margin-top: 20px; margin-bottom: 10px;">本次成交记录</h4>
              <el-table :data="executeResult.trades" border size="small">
                <el-table-column prop="type" label="类型" width="80">
                  <template #default="scope">
                    <el-tag :type="scope.row.type === 'BUY' ? 'success' : 'danger'" size="small">
                      {{ scope.row.type === 'BUY' ? '买入' : '卖出' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="price" label="价格" width="100">
                  <template #default="scope">
                    {{ scope.row.price ? scope.row.price.toFixed(2) : '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="quantity" label="数量" width="100">
                  <template #default="scope">
                    {{ scope.row.quantity ? scope.row.quantity.toFixed(4) : '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="amount" label="金额" width="100">
                  <template #default="scope">
                    {{ scope.row.amount ? scope.row.amount.toFixed(2) : '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="executedAt" label="时间" min-width="150">
                  <template #default="scope">
                    {{ formatDate(scope.row.executedAt) }}
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div v-else class="no-trades">
              <el-empty description="本次执行无成交" :image-size="80" />
            </div>
          </div>

          <!-- 成交记录 -->
          <div class="trade-history-section">
            <el-divider>成交记录</el-divider>
            <el-button
              type="primary"
              size="small"
              @click="loadTradeHistory"
              :loading="loadingTrades"
              style="margin-bottom: 15px;"
            >
              刷新成交记录
            </el-button>
            <el-table
              :data="tradeHistory"
              v-loading="loadingTrades"
              border
              stripe
              max-height="400"
            >
              <el-table-column prop="tradeTime" label="时间" width="180">
                <template #default="scope">
                  {{ formatDate(scope.row.tradeTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="type" label="买/卖" width="100">
                <template #default="scope">
                  <el-tag :type="scope.row.type === 'BUY' ? 'success' : 'danger'">
                    {{ scope.row.type === 'BUY' ? '买入' : '卖出' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="网格序号" width="100">
                <template #default="scope">
                  {{ scope.row.gridLevel !== null ? scope.row.gridLevel : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="price" label="成交价" width="120">
                <template #default="scope">
                  {{ scope.row.price ? scope.row.price.toFixed(2) : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="amount" label="金额" width="120">
                <template #default="scope">
                  {{ scope.row.amount ? scope.row.amount.toFixed(2) : '-' }}
                </template>
              </el-table-column>
            </el-table>
            <div v-if="!tradeHistory || tradeHistory.length === 0" class="no-data">
              <el-empty description="暂无成交记录" :image-size="80" />
            </div>
          </div>
        </div>
      </el-dialog>
    </el-main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

// 表单数据
const strategyForm = ref({
  name: '',
  basePrice: null,
  gridCount: null,
  gridSpacing: null,
  amountPerGrid: null
})

// 策略列表
const strategies = ref([])

// 加载状态
const loading = ref(false)
const creating = ref(false)

// 执行弹窗相关
const executeDialogVisible = ref(false)
const currentStrategy = ref(null)
const executeForm = ref({
  price: null
})
const executing = ref(false)
const executeResult = ref(null)

// 成交记录
const tradeHistory = ref([])
const loadingTrades = ref(false)

// 创建策略
const createStrategy = async () => {
  // 表单验证
  if (!strategyForm.value.name) {
    ElMessage.warning('请输入策略名称')
    return
  }
  if (!strategyForm.value.basePrice || strategyForm.value.basePrice <= 0) {
    ElMessage.warning('请输入有效的基准价格')
    return
  }
  if (!strategyForm.value.gridCount || strategyForm.value.gridCount <= 0) {
    ElMessage.warning('请输入有效的网格数量')
    return
  }
  if (!strategyForm.value.gridSpacing || strategyForm.value.gridSpacing <= 0) {
    ElMessage.warning('请输入有效的网格间距')
    return
  }
  if (!strategyForm.value.amountPerGrid || strategyForm.value.amountPerGrid <= 0) {
    ElMessage.warning('请输入有效的单格金额')
    return
  }

  creating.value = true

  try {
    await axios.post('/api/strategies', strategyForm.value)
    ElMessage.success('策略创建成功')
    
    // 重置表单
    strategyForm.value = {
      name: '',
      basePrice: null,
      gridCount: null,
      gridSpacing: null,
      amountPerGrid: null
    }
    
    // 刷新列表
    await loadStrategies()
  } catch (error) {
    ElMessage.error('创建失败：' + (error.response?.data?.message || error.message))
  } finally {
    creating.value = false
  }
}

// 加载策略列表
const loadStrategies = async () => {
  loading.value = true
  try {
    const response = await axios.get('/api/strategies')
    strategies.value = response.data
  } catch (error) {
    ElMessage.error('加载失败：' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

// 获取状态标签类型
const getStatusType = (status) => {
  const typeMap = {
    'ACTIVE': 'success',
    'PAUSED': 'warning',
    'STOPPED': 'info',
    'COMPLETED': 'success'
  }
  return typeMap[status] || 'info'
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', { 
    year: 'numeric', 
    month: '2-digit', 
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 打开执行弹窗
const openExecuteDialog = (strategy) => {
  currentStrategy.value = strategy
  executeForm.value = {
    price: strategy.currentPrice || strategy.basePrice || null
  }
  executeResult.value = null
  executeDialogVisible.value = true

  // 加载成交记录
  loadTradeHistory()
}

// 加载成交记录
const loadTradeHistory = async () => {
  if (!currentStrategy.value || !currentStrategy.value.id) return

  loadingTrades.value = true
  try {
    const response = await axios.get(`/api/strategies/${currentStrategy.value.id}/trades`)
    tradeHistory.value = response.data
  } catch (error) {
    console.error('加载成交记录失败：', error)
    ElMessage.error('加载成交记录失败：' + (error.response?.data?.message || error.message))
  } finally {
    loadingTrades.value = false
  }
}

// 执行一次tick
const executeTick = async () => {
  if (!executeForm.value.price || executeForm.value.price <= 0) {
    ElMessage.warning('请输入有效的当前价格')
    return
  }

  executing.value = true
  try {
    const response = await axios.post(
      `/api/strategies/${currentStrategy.value.id}/tick`,
      { price: executeForm.value.price }
    )
    executeResult.value = response.data
    ElMessage.success('执行成功')
    
    // 刷新策略列表
    await loadStrategies()

    // 刷新成交记录
    await loadTradeHistory()
  } catch (error) {
    ElMessage.error('执行失败：' + (error.response?.data?.message || error.message))
  } finally {
    executing.value = false
  }
}

// 页面加载时获取策略列表
onMounted(() => {
  loadStrategies()
})
</script>

<style scoped>
#app {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  padding: 0 20px;
  box-shadow: 0 2px 4px rgba(0,0,0,0.1);
}

.header-content {
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
}

.title {
  margin: 0;
  font-size: 28px;
  font-weight: 600;
}

.main-content {
  max-width: 1200px;
  margin: 20px auto;
  padding: 20px;
}

.form-card,
.list-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
}

.help-text {
  margin-left: 10px;
  color: #909399;
  font-size: 14px;
}

.execute-dialog {
  padding: 10px 0;
}

.execute-section {
  margin: 20px 0;
}

.result-section {
  margin-top: 20px;
}

.trades-section {
  margin-top: 15px;
}

.no-trades {
  margin-top: 20px;
  text-align: center;
}

.trade-history-section {
  margin-top: 30px;
}

.no-data {
  margin-top: 20px;
  text-align: center;
}
</style>

<style>
/* 全局样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen',
    'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans', 'Helvetica Neue',
    sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

.el-header {
  height: 70px !important;
  line-height: 70px;
}
</style>
