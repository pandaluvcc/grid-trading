<template>
  <div class="strategy-detail">
    <!-- 头部 -->
    <div class="header">
      <h1>策略详情</h1>
      <div class="header-actions">
        <el-button @click="goBack">返回列表</el-button>
        <el-button type="primary" @click="openOcrDialog">导入成交截图</el-button>
      </div>
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
        <el-descriptions-item label="已实现收益">
          <span :class="{'profit-positive': strategy.realizedProfit > 0, 'profit-negative': strategy.realizedProfit < 0}">
            {{ formatAmount(strategy.realizedProfit) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="预计收益">
          <span style="font-weight: 600; color: #67c23a">
            {{ formatAmount(strategy.expectedProfit) }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="已投入">
          <span>{{ formatAmount(strategy.investedAmount) }}</span>
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
        @plan-price-updated="handlePlanPriceUpdated"
      />
    </el-card>

    <!-- 成交记录 -->
    <el-card class="trade-card">
      <TradeRecordTable :trade-records="tradeRecords" :loading="tradeRecordsLoading" />
    </el-card>

    <!-- OCR导入弹窗 -->
    <el-dialog v-model="ocrDialogVisible" title="OCR导入成交记录" width="80%">
      <div class="ocr-section">
        <el-upload
          class="ocr-uploader"
          :auto-upload="false"
          :show-file-list="true"
          :limit="5"
          accept="image/*"
          multiple
          :on-change="handleOcrFileChange"
          :on-remove="handleOcrFileChange"
          :on-exceed="handleOcrFileExceed"
        >
          <el-button type="primary">选择截图（最多5张）</el-button>
        </el-upload>

        <el-button
          type="success"
          :disabled="!ocrFiles.length"
          :loading="ocrParsing"
          @click="handleOcrParse"
        >
          解析截图
        </el-button>

        <el-button
          :disabled="!ocrRecords.length"
          :loading="ocrRematching"
          @click="handleOcrRematch"
        >
          重新匹配
        </el-button>
      </div>

      <el-alert
        v-if="ocrError"
        type="error"
        :closable="false"
        show-icon
        style="margin-top: 12px"
      >
        <template #title>{{ ocrError }}</template>
      </el-alert>

      <el-card v-if="ocrRecords.length" class="ocr-result" style="margin-top: 16px">
        <template #header>
          <div class="card-header">
            <span>识别结果（可编辑）</span>
            <el-tag type="info">共 {{ ocrRecords.length }} 条</el-tag>
          </div>
        </template>

        <el-table :data="ocrRecords" border height="360">
          <el-table-column prop="matchStatus" label="匹配" width="130">
            <template #default="scope">
              <div class="match-cell">
                <el-tag :type="matchTagType(scope.row.matchStatus)">
                  {{ scope.row.matchStatus || '-' }}
                </el-tag>
                <el-tooltip v-if="scope.row.outOfRange" content="超区间匹配" placement="top">
                  <el-icon class="warn-icon"><WarningFilled /></el-icon>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="scope">
              <el-select v-model="scope.row.type" placeholder="类型" size="small">
                <el-option label="BUY" value="BUY" />
                <el-option label="SELL" value="SELL" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="标记" width="160">
            <template #default="scope">
              <el-checkbox
                v-model="scope.row.opening"
                label="建仓"
                @change="(val) => handleOpeningChange(scope.row, val)"
              />
              <el-checkbox
                v-model="scope.row.closing"
                label="清仓"
                @change="(val) => handleClosingChange(scope.row, val)"
              />
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="180">
            <template #default="scope">
              <el-date-picker
                v-model="scope.row.tradeTime"
                type="datetime"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                size="small"
              />
            </template>
          </el-table-column>
          <el-table-column label="价格" width="120">
            <template #default="scope">
              <el-input-number v-model="scope.row.price" :precision="3" :min="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="120">
            <template #default="scope">
              <el-input-number v-model="scope.row.quantity" :precision="0" :min="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="金额" width="140">
            <template #default="scope">
              <el-input-number v-model="scope.row.amount" :precision="2" :min="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="手续费" width="120">
            <template #default="scope">
              <el-input-number v-model="scope.row.fee" :precision="2" :min="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="网格" width="90">
            <template #default="scope">
              <span>{{ scope.row.matchedLevel ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="180">
            <template #default="scope">
              <span>{{ scope.row.matchMessage || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <template #footer>
        <el-button @click="ocrDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="!ocrRecords.length"
          :loading="ocrImporting"
          @click="handleOcrImport"
        >
          确认导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled, WarningFilled } from '@element-plus/icons-vue'
import {
  getStrategyDetail,
  getGridLines,
  executeTick,
  getTradeRecords,
  updateActualBuyPrice,
  ocrRecognize,
  ocrImport,
  ocrRematch
} from '../api'
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

// OCR相关
const ocrDialogVisible = ref(false)
const ocrFiles = ref([])
const ocrParsing = ref(false)
const ocrRematching = ref(false)
const ocrImporting = ref(false)
const ocrRecords = ref([])
const ocrError = ref('')

// 返回列表
const goBack = () => {
  router.push('/pc')
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

// 处理计划买入价更新
const handlePlanPriceUpdated = async () => {
  // 重新加载网格计划以显示更新后的数据
  await loadGridLines()
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

// 打开OCR导入弹窗
const openOcrDialog = () => {
  ocrDialogVisible.value = true
  ocrFiles.value = []
  ocrRecords.value = []
  ocrError.value = ''
}

// 处理OCR文件选择
const handleOcrFileChange = (file, fileList) => {
  const list = Array.isArray(fileList) ? fileList : []
  ocrFiles.value = list.map(item => item.raw).filter(Boolean)
}

const handleOcrFileExceed = () => {
  ElMessage.warning('一次最多上传5张截图')
}

// 处理OCR解析
const handleOcrParse = async () => {
  if (!ocrFiles.value.length) {
    ElMessage.warning('请先选择截图')
    return
  }
  if (ocrFiles.value.length > 5) {
    ElMessage.warning('一次最多上传5张截图')
    return
  }

  ocrParsing.value = true
  ocrError.value = ''
  try {
    const response = await ocrRecognize({
      files: ocrFiles.value,
      strategyId: strategyId.value,
      brokerType: 'EASTMONEY'
    })

    if (!response.data?.success) {
      ocrError.value = response.data?.message || 'OCR识别失败'
      ocrRecords.value = []
      return
    }

    ocrRecords.value = (response.data.records || []).map((item) => ({
      ...item,
      tradeTime: item.tradeTime || ''
    }))
  } catch (error) {
    console.error('OCR解析失败:', error)
    ocrError.value = error.response?.data?.message || 'OCR解析失败'
  } finally {
    ocrParsing.value = false
  }
}

const handleOcrRematch = async () => {
  if (!ocrRecords.value.length) {
    ElMessage.warning('没有可匹配的数据')
    return
  }
  ocrRematching.value = true
  try {
    const response = await ocrRematch({
      strategyId: strategyId.value,
      records: ocrRecords.value
    })
    if (!response.data?.success) {
      ocrError.value = response.data?.message || '重新匹配失败'
      return
    }
    ocrRecords.value = (response.data.records || []).map((item) => ({
      ...item,
      tradeTime: item.tradeTime || ''
    }))
  } catch (error) {
    console.error('重新匹配失败:', error)
    ocrError.value = error.response?.data?.message || '重新匹配失败'
  } finally {
    ocrRematching.value = false
  }
}

const handleOpeningChange = (row, checked) => {
  if (!checked) {
    return
  }
  ocrRecords.value.forEach((item) => {
    if (item !== row) {
      item.opening = false
    }
  })
}

const handleClosingChange = (row, checked) => {
  if (!checked) {
    return
  }
  ocrRecords.value.forEach((item) => {
    if (item !== row) {
      item.closing = false
    }
  })
}

// 匹配状态标签类型
const matchTagType = (status) => {
  switch (status) {
    case 'MATCHED':
      return 'success'
    case 'DUPLICATE':
      return 'warning'
    case 'INVALID':
      return 'danger'
    case 'UNMATCHED':
      return 'info'
    default:
      return 'info'
  }
}

// 格式化价格
const formatPrice = (value) => {
  if
