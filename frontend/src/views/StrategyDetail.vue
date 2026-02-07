<template>
  <div class="strategy-detail">
    <!-- 头部 -->
    <div class="header">
      <h1>策略详情</h1>
      <el-button @click="goBack">返回列表</el-button>
    </div>

    <!-- 策略基础信息 -->
    <el-card class="info-card" v-loading="strategyLoading">
      <template #header>
        <div class="card-header">
          <span>策略摘要（固定模板v2.0 - 锚点反弹模型）</span>
          <el-tag :type="strategy?.status === 'RUNNING' ? 'success' : 'info'">
            {{ strategy?.status }}
          </el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border v-if="strategy">
        <el-descriptions-item label="证券代码">
          <span style="font-weight: 600; font-size: 16px">{{ strategy.symbol }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="基准价">
          <span style="font-weight: 600; color: #409eff">{{ formatPrice(strategy.basePrice) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="单格金额">
          <span style="font-weight: 600">{{ formatAmount(strategy.amountPerGrid) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="网格总数">
          <el-tag type="danger" size="large">19 条（固定）</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="最大投资">
          <span style="font-weight: 600">{{ formatAmount(strategy.maxCapital) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="网格模型">
          <el-tag type="info" size="small">小网×13</el-tag>
          <el-tag type="warning" size="small" style="margin-left: 4px">中网×4</el-tag>
          <el-tag type="danger" size="small" style="margin-left: 4px">大网×2</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="可用资金">
          <span>{{ formatAmount(strategy.availableCash) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="已投入">
          <span>{{ formatAmount(strategy.investedAmount) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="已实现收益">
          <span :class="{'profit-positive': strategy.realizedProfit > 0, 'profit-negative': strategy.realizedProfit < 0}">
            {{ formatAmount(strategy.realizedProfit) }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 16px"
      >
        <template #title>
          <strong>模型说明：</strong>小网连续阶梯（5%），中网锚点反弹（15%），大网极端反弹（30%），所有数据由后端生成，前端仅展示。
        </template>
      </el-alert>
    </el-card>

    <!-- 执行操作区 -->
    <el-card class="execute-card">
      <template #header>
        <span>执行操作</span>
      </template>

      <div class="execute-panel">
        <el-form inline>
          <el-form-item label="当前价格">
            <el-input-number
              v-model="currentPrice"
              :precision="2"
              :step="0.01"
              :min="0.01"
              placeholder="请输入当前价格"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              @click="handleExecute"
              :loading="executing"
            >
              执行一次
            </el-button>
          </el-form-item>
        </el-form>

        <div class="execute-tip">
          <el-icon><InfoFilled /></el-icon>
          输入价格后点击"执行一次"，系统将根据当前价格触发买卖逻辑
        </div>
      </div>
    </el-card>

    <!-- 网格计划表 -->
    <el-card class="grid-card">
      <GridLineTable 
        :grid-lines="gridLines" 
        :loading="gridLinesLoading" 
        @update-actual-buy-price="handleUpdateActualBuyPrice"
      />
    </el-card>

    <!-- 成交记录 -->
    <el-card class="trade-card">
      <TradeRecordTable :trade-records="tradeRecords" :loading="tradeRecordsLoading" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { getStrategyDetail, getGridLines, executeTick, getTradeRecords, updateActualBuyPrice } from '../api'
import GridLineTable from '../components/GridLineTable.vue'
import TradeRecordTable from '../components/TradeRecordTable.vue'

const route = useRoute()
const router = useRouter()

const strategyId = ref(route.params.id)
const strategy = ref(null)
const gridLines = ref([])
const tradeRecords = ref([])
const currentPrice = ref(null)

const strategyLoading = ref(false)
const gridLinesLoading = ref(false)
const tradeRecordsLoading = ref(false)
const executing = ref(false)

// 返回列表
const goBack = () => {
  router.push('/')
}

// 加载策略详情
const loadStrategy = async () => {
  strategyLoading.value = true
  try {
    const response = await getStrategyDetail(strategyId.value)
    strategy.value = response.data

    // 设置默认价格为基准价
    if (!currentPrice.value && strategy.value.basePrice) {
      currentPrice.value = Number(strategy.value.basePrice)
    }
  } catch (error) {
    console.error('加载策略详情失败:', error)
    ElMessage.error('加载策略详情失败')
  } finally {
    strategyLoading.value = false
  }
}

// 加载网格计划
const loadGridLines = async () => {
  gridLinesLoading.value = true
  try {
    const response = await getGridLines(strategyId.value)

    // 后端返回的数据结构：{ strategy: {...}, gridPlans: [...] }
    if (response.data.gridPlans) {
      // ⚠️ 严格按照后端返回的 level 排序，不做任何自定义排序
      gridLines.value = response.data.gridPlans.sort((a, b) => {
        return a.level - b.level  // 按 level 升序（1, 2, 3, ..., 19）
      })
    } else {
      gridLines.value = []
    }
  } catch (error) {
    console.error('加载网格计划失败:', error)
    ElMessage.error('加载网格计划失败')
  } finally {
    gridLinesLoading.value = false
  }
}

// 加载成交记录
const loadTradeRecords = async () => {
  tradeRecordsLoading.value = true
  try {
    const response = await getTradeRecords(strategyId.value)
    tradeRecords.value = response.data || []
  } catch (error) {
    console.error('加载成交记录失败:', error)
    ElMessage.error('加载成交记录失败')
  } finally {
    tradeRecordsLoading.value = false
  }
}

// 处理实际买入价更新
const handleUpdateActualBuyPrice = async ({ gridLineId, actualBuyPrice }) => {
  try {
    await updateActualBuyPrice(gridLineId, actualBuyPrice)
    ElMessage.success('实际买入价已更新，后续网格已重算')
    // 重新加载网格计划
    await loadGridLines()
  } catch (error) {
    console.error('更新实际买入价失败:', error)
    ElMessage.error(error.response?.data?.message || '更新实际买入价失败')
  }
}

// 执行一次
const handleExecute = async () => {
  if (!currentPrice.value || currentPrice.value <= 0) {
    ElMessage.warning('请输入有效的当前价格')
    return
  }

  executing.value = true
  try {
    await executeTick(strategyId.value, currentPrice.value)
    ElMessage.success('执行成功')

    // 重新加载所有数据
    await Promise.all([
      loadStrategy(),
      loadGridLines(),
      loadTradeRecords()
    ])
  } catch (error) {
    console.error('执行失败:', error)
    ElMessage.error(error.response?.data?.message || '执行失败')
  } finally {
    executing.value = false
  }
}

// 格式化价格
const formatPrice = (value) => {
  if (value == null || value === '' || isNaN(value)) return '-'
  const num = Number(value)
  if (isNaN(num)) return '-'
  return num.toFixed(2)
}

// 格式化金额
const formatAmount = (value) => {
  if (value == null || value === '' || isNaN(value)) return '0.00'
  const num = Number(value)
  if (isNaN(num)) return '0.00'
  return num.toFixed(2)
}

onMounted(async () => {
  // 并行加载所有数据
  await Promise.all([
    loadStrategy(),
    loadGridLines(),
    loadTradeRecords()
  ])
})
</script>

<style scoped>
.strategy-detail {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h1 {
  margin: 0;
  font-size: 24px;
}

.info-card,
.execute-card,
.grid-card,
.trade-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.execute-panel {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.execute-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #909399;
  font-size: 14px;
}

.profit-positive {
  color: #67c23a;
  font-weight: 500;
}
</style>
600;
}

.profit-negative {
  color: #f56c6c;
  font-weight: 600;
}

:deep(.el-descriptions__label) {
  font-weight: 500;
}

:deep(.el-descriptions__content) {
  font-family: 'Courier New', monospace