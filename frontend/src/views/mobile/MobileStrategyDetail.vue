<template>
  <MobileLayout :title="strategyTitle" :show-back="true" :show-tab-bar="false">
    <!-- 加载中 -->
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <template v-else-if="strategy">
      <!-- 顶部概览卡片 -->
      <div class="overview-card">
        <div class="overview-header">
            <div class="symbol-block">
              <span class="symbol">{{ strategy.name || strategy.symbol }}</span>
              <span class="symbol-code" v-if="strategy.name">{{ strategy.symbol }}</span>
            </div>
          <el-tag 
            size="small" 
            :type="strategy.status === 'RUNNING' ? 'success' : 'info'"
          >
            {{ strategy.status === 'RUNNING' ? '运行中' : '已停止' }}
          </el-tag>
        </div>
        
        <div class="overview-stats">
          <div class="stat-item main">
            <span class="stat-label">已实现收益</span>
            <span class="stat-value profit" :class="{ negative: strategy.realizedProfit < 0 }">
              {{ formatProfit(strategy.realizedProfit) }}
            </span>
          </div>
        </div>

        <div class="overview-grid">
          <div class="stat-item">
            <span class="stat-label">基准价</span>
            <span class="stat-value">{{ formatPrice(strategy.basePrice) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">单格金额</span>
            <span class="stat-value">¥{{ formatAmount(strategy.amountPerGrid) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">可用资金</span>
            <span class="stat-value">¥{{ formatAmount(strategy.availableCash) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">已投入</span>
            <span class="stat-value">¥{{ formatAmount(strategy.investedAmount) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">累计手续费</span>
            <span class="stat-value fee">¥{{ totalFee.toFixed(2) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">净收益</span>
            <span class="stat-value" :class="{ profit: netProfit >= 0, negative: netProfit < 0 }">
              {{ formatProfit(netProfit) }}
            </span>
          </div>
        </div>
      </div>

      <!-- 快速执行区域 -->
      <div class="execute-card">
        <div class="execute-title">
          <el-icon><Promotion /></el-icon>
          <span>价格触发</span>
        </div>
        <div class="execute-form">
          <el-input
            v-model="priceInput"
            type="number"
            placeholder="输入当前价格"
            size="large"
            class="price-input"
          >
            <template #prefix>¥</template>
          </el-input>
          <el-button 
            type="primary" 
            size="large"
            class="execute-btn"
            :loading="executing"
            @click="handleExecute"
          >
            执行
          </el-button>
        </div>
        <div class="execute-hint">
          输入价格后系统将自动判断买卖
        </div>
      </div>

      <!-- Tab切换 -->
      <div class="tab-switcher">
        <div 
          class="tab-item" 
          :class="{ active: activeTab === 'grids' }"
          @click="activeTab = 'grids'"
        >
          网格状态
        </div>
        <div 
          class="tab-item" 
          :class="{ active: activeTab === 'records' }"
          @click="activeTab = 'records'"
        >
          成交记录
        </div>
      </div>

      <div class="ocr-action">
        <el-button type="primary" size="small" @click="openOcrDialog">导入成交截图</el-button>
      </div>

      <!-- 网格列表 -->
      <div v-show="activeTab === 'grids'" class="grid-list">
        <MobileGridCard
          v-for="grid in gridLines"
          :key="grid.id"
          :grid="grid"
        />
      </div>

      <!-- 成交记录 -->
      <div v-show="activeTab === 'records'" class="record-list">
        <div v-if="tradeRecords.length === 0" class="empty-records">
          暂无成交记录
        </div>
        <div 
          v-for="record in tradeRecords" 
          :key="record.id" 
          class="record-item"
          @click="openFeeDialog(record)"
        >
          <div class="record-left">
            <el-tag 
              size="small" 
              :type="record.type === 'BUY' ? 'danger' : 'success'"
            >
              {{ record.type === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
            <span class="record-price">¥{{ formatPrice(record.price) }}</span>
          </div>
          <div class="record-right">
            <span class="record-amount">{{ formatAmount(record.amount) }}元</span>
            <span v-if="record.fee" class="record-fee">费用: ¥{{ Number(record.fee).toFixed(2) }}</span>
            <span v-else class="record-fee-hint">点击录入费用</span>
            <span class="record-time">{{ formatTime(record.tradeTime) }}</span>
          </div>
        </div>
      </div>

      <!-- OCR导入弹窗 -->
      <el-dialog v-model="ocrDialogVisible" title="OCR导入成交记录" width="95%">
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
            <el-button type="primary" size="small">选择截图（最多5张）</el-button>
          </el-upload>

          <el-button
            type="success"
            size="small"
            :disabled="!ocrFiles.length"
            :loading="ocrParsing"
            @click="handleOcrParse"
          >
            解析截图
          </el-button>

          <el-button
            size="small"
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
          style="margin-top: 10px"
        >
          <template #title>{{ ocrError }}</template>
        </el-alert>

        <div v-if="ocrRecords.length" class="ocr-list">
          <div v-for="(record, idx) in ocrRecords" :key="idx" class="ocr-item">
            <div class="ocr-row">
              <el-tag :type="matchTagType(record.matchStatus)" size="small">
                {{ record.matchStatus || '-' }}
              </el-tag>
              <el-tooltip v-if="record.outOfRange" content="超区间匹配" placement="top">
                <el-icon class="warn-icon"><WarningFilled /></el-icon>
              </el-tooltip>
              <el-select v-model="record.type" size="small" class="ocr-type">
                <el-option label="BUY" value="BUY" />
                <el-option label="SELL" value="SELL" />
              </el-select>
              <span class="ocr-level">网格: {{ record.matchedLevel ?? '-' }}</span>
            </div>

            <div class="ocr-row">
              <el-checkbox
                v-model="record.opening"
                label="建仓"
                @change="(val) => handleOpeningChange(record, val)"
              />
              <el-checkbox
                v-model="record.closing"
                label="清仓"
                @change="(val) => handleClosingChange(record, val)"
              />
            </div>

            <el-date-picker
              v-model="record.tradeTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              size="small"
              class="ocr-datetime"
            />

            <div class="ocr-row">
              <div class="ocr-field">
                <span class="ocr-label">价格</span>
                <el-input-number v-model="record.price" :precision="3" :min="0" size="small" />
              </div>
              <div class="ocr-field">
                <span class="ocr-label">数量</span>
                <el-input-number v-model="record.quantity" :precision="0" :min="0" size="small" />
              </div>
            </div>

            <div class="ocr-row">
              <div class="ocr-field">
                <span class="ocr-label">金额</span>
                <el-input-number v-model="record.amount" :precision="2" :min="0" size="small" />
              </div>
              <div class="ocr-field">
                <span class="ocr-label">费用</span>
                <el-input-number v-model="record.fee" :precision="2" :min="0" size="small" />
              </div>
            </div>

            <div class="ocr-message">{{ record.matchMessage || '' }}</div>
          </div>
        </div>

        <template #footer>
          <el-button size="small" @click="ocrDialogVisible = false">取消</el-button>
          <el-button
            type="primary"
            size="small"
            :disabled="!ocrRecords.length"
            :loading="ocrImporting"
            @click="handleOcrImport"
          >
            确认导入
          </el-button>
        </template>
      </el-dialog>

      <!-- 费用录入弹窗 -->
      <el-dialog
        v-model="feeDialogVisible"
        title="录入手续费"
        width="90%"
        :close-on-click-modal="true"
        class="fee-dialog"
      >
        <div v-if="editingRecord" class="fee-dialog-content">
          <div class="fee-record-info">
            <el-tag 
              size="small" 
              :type="editingRecord.type === 'BUY' ? 'danger' : 'success'"
            >
              {{ editingRecord.type === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
            <span>¥{{ formatPrice(editingRecord.price) }}</span>
            <span>{{ formatAmount(editingRecord.amount) }}元</span>
          </div>
          <el-input
            v-model="feeInput"
            type="number"
            placeholder="输入手续费"
            size="large"
            class="fee-input"
          >
            <template #prefix>¥</template>
          </el-input>
        </div>
        <template #footer>
          <el-button @click="feeDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingFee" @click="saveFee">保存</el-button>
        </template>
      </el-dialog>

      <!-- 价格触发后的费用录入弹窗 -->
      <el-dialog
        v-model="tickFeeDialogVisible"
        title="录入本次交易费用"
        width="90%"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        :show-close="false"
        class="tick-fee-dialog"
      >
        <div class="tick-fee-content">
          <div class="tick-fee-hint">本次触发产生了以下交易，请录入手续费：</div>
          <div 
            v-for="trade in tickTrades" 
            :key="trade.id" 
            class="tick-fee-item"
          >
            <div class="tick-fee-trade-info">
              <el-tag 
                size="small" 
                :type="trade.type === 'BUY' ? 'danger' : 'success'"
              >
                {{ trade.type === 'BUY' ? '买入' : '卖出' }}
              </el-tag>
              <span class="tick-fee-price">¥{{ formatPrice(trade.price) }}</span>
              <span class="tick-fee-amount">{{ formatAmount(trade.amount) }}元</span>
            </div>
            <el-input
              v-model="tickFeeInputs[trade.id]"
              type="number"
              placeholder="手续费"
              size="default"
              class="tick-fee-input"
            >
              <template #prefix>¥</template>
            </el-input>
          </div>
        </div>
        <template #footer>
          <el-button @click="skipTickFees">跳过</el-button>
          <el-button type="primary" :loading="savingTickFees" @click="saveTickFees">保存</el-button>
        </template>
      </el-dialog>
    </template>
  </MobileLayout>
</template>

<script setup>
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Promotion, WarningFilled } from '@element-plus/icons-vue'
import {
  getStrategyDetail,
  getGridLines,
  executeTick,
  getTradeRecords,
  updateTradeFee,
  ocrRecognize,
  ocrImport,
  ocrRematch
} from '../../api'
import MobileLayout from './MobileLayout.vue'
import MobileGridCard from './MobileGridCard.vue'

const route = useRoute()
const strategyId = computed(() => route.params.id)

const strategy = ref(null)
const gridLines = ref([])
const tradeRecords = ref([])
const priceInput = ref('')
const activeTab = ref('grids')

const loading = ref(true)
const executing = ref(false)
let isUnmounted = false

// 费用编辑相关
const feeDialogVisible = ref(false)
const editingRecord = ref(null)
const feeInput = ref('')
const savingFee = ref(false)

// 价格触发后的费用录入弹窗
const tickFeeDialogVisible = ref(false)
const tickTrades = ref([])  // 本次触发产生的交易
const tickFeeInputs = ref({})  // { tradeId: feeValue }
const savingTickFees = ref(false)

// OCR导入相关
const ocrDialogVisible = ref(false)
const ocrFiles = ref([])
const ocrParsing = ref(false)
const ocrRematching = ref(false)
const ocrImporting = ref(false)
const ocrRecords = ref([])
const ocrError = ref('')

// 计算累计手续费
const totalFee = computed(() => {
  return tradeRecords.value.reduce((sum, r) => {
    return sum + (r.fee ? Number(r.fee) : 0)
  }, 0)
})

// 计算净收益 = 已实现收益 - 累计手续费
const netProfit = computed(() => {
  const profit = strategy.value?.realizedProfit || 0
  return Number(profit) - totalFee.value
})

const strategyTitle = computed(() => {
  if (!strategy.value) return '策略详情'
  if (strategy.value.name && strategy.value.symbol) {
    return `${strategy.value.name} (${strategy.value.symbol})`
  }
  return strategy.value.name || strategy.value.symbol || '策略详情'
})

onUnmounted(() => {
  isUnmounted = true
})

// 加载数据
const loadData = async () => {
  const id = strategyId.value
  if (!id || isUnmounted) {
    // ID无效或组件已销毁时静默返回
    loading.value = false
    return
  }
  
  console.log('开始加载策略详情, id:', id)
  loading.value = true
  try {
    // 分开请求，便于定位哪个失败
    const strategyRes = await getStrategyDetail(id)
    console.log('策略详情加载成功:', strategyRes.data)
    
    const gridRes = await getGridLines(id)
    console.log('网格计划加载成功:', gridRes.data)
    
    const recordRes = await getTradeRecords(id)
    console.log('成交记录加载成功:', recordRes.data)
    
    // 组件已销毁则不更新状态
    if (isUnmounted) return
    
    strategy.value = strategyRes.data
    gridLines.value = gridRes.data.gridPlans?.sort((a, b) => a.level - b.level) || []
    tradeRecords.value = recordRes.data || []
    
    // 设置默认价格
    if (!priceInput.value && strategy.value?.basePrice) {
      priceInput.value = Number(strategy.value.basePrice).toFixed(2)
    }
  } catch (error) {
    // 组件已销毁则不显示错误
    if (isUnmounted) return
    console.error('加载失败, 详细错误:', error)
    console.error('错误响应:', error.response)
    ElMessage.error('加载失败: ' + (error.response?.data?.message || error.message || '未知错误'))
  } finally {
    if (!isUnmounted) {
      loading.value = false
    }
  }
}

// 执行价格触发
const handleExecute = async () => {
  const price = parseFloat(priceInput.value)
  if (!price || price <= 0) {
    ElMessage.warning('请输入有效价格')
    return
  }

  executing.value = true
  try {
    const res = await executeTick(strategyId.value, price)
    const trades = res.data?.trades || []
    
    if (trades.length > 0) {
      // 有交易产生，弹窗录入费用
      tickTrades.value = trades
      tickFeeInputs.value = {}
      trades.forEach(t => {
        tickFeeInputs.value[t.id] = ''
      })
      tickFeeDialogVisible.value = true
      ElMessage.success(`成交 ${trades.length} 笔，请录入手续费`)
    } else {
      ElMessage.info('未触发任何交易')
    }
    
    await loadData()
  } catch (error) {
    console.error('执行失败:', error)
    ElMessage.error(error.response?.data?.message || '执行失败')
  } finally {
    executing.value = false
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
const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth()+1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2,'0')}`
}

// 打开费用编辑弹窗
const openFeeDialog = (record) => {
  editingRecord.value = record
  feeInput.value = record.fee ? Number(record.fee).toString() : ''
  feeDialogVisible.value = true
}

// 保存费用
const saveFee = async () => {
  const fee = parseFloat(feeInput.value)
  if (isNaN(fee) || fee < 0) {
    ElMessage.warning('请输入有效的费用')
    return
  }
  
  savingFee.value = true
  try {
    await updateTradeFee(editingRecord.value.id, fee)
    // 更新本地数据
    const record = tradeRecords.value.find(r => r.id === editingRecord.value.id)
    if (record) {
      record.fee = fee
    }
    feeDialogVisible.value = false
    ElMessage.success('保存成功')
  } catch (error) {
    console.error('保存费用失败:', error)
    ElMessage.error('保存失败')
  } finally {
    savingFee.value = false
  }
}

// 保存价格触发后的费用（批量）
const saveTickFees = async () => {
  savingTickFees.value = true
  try {
    const promises = []
    for (const trade of tickTrades.value) {
      const feeStr = tickFeeInputs.value[trade.id]
      if (feeStr && feeStr.trim() !== '') {
        const fee = parseFloat(feeStr)
        if (!isNaN(fee) && fee >= 0) {
          promises.push(updateTradeFee(trade.id, fee))
        }
      }
    }
    
    if (promises.length > 0) {
      await Promise.all(promises)
      ElMessage.success('费用保存成功')
    }
    
    tickFeeDialogVisible.value = false
    await loadData()  // 刷新数据
  } catch (error) {
    console.error('保存费用失败:', error)
    ElMessage.error('保存失败')
  } finally {
    savingTickFees.value = false
  }
}

// 跳过费用录入
const skipTickFees = () => {
  tickFeeDialogVisible.value = false
}

// 记录已加载的ID，避免重复加载
let loadedId = null

// 监听 strategyId 变化，确保在路由参数可用时加载数据
watch(strategyId, (newId) => {
  if (newId && newId !== loadedId) {
    loadedId = newId
    loadData()
  }
}, { immediate: true })

const openOcrDialog = () => {
  ocrDialogVisible.value = true
  ocrFiles.value = []
  ocrRecords.value = []
  ocrError.value = ''
}

const handleOcrFileChange = (file, fileList) => {
  const list = Array.isArray(fileList) ? fileList : []
  ocrFiles.value = list.map(item => item.raw).filter(Boolean)
}

const handleOcrFileExceed = () => {
  ElMessage.warning('一次最多上传5张截图')
}

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

    const records = (response.data.records || []).map((item) => ({
      ...item,
      tradeTime: item.tradeTime || ''
    }))
    ocrRecords.value = sortOcrRecords(records)
    await nextTick()
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
    const records = (response.data.records || []).map((item) => ({
      ...item,
      tradeTime: item.tradeTime || ''
    }))
    ocrRecords.value = sortOcrRecords(records)
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

const handleOcrImport = async () => {
  if (!ocrRecords.value.length) {
    ElMessage.warning('没有可导入的记录')
    return
  }

  ocrImporting.value = true
  ocrError.value = ''
  try {
    const response = await ocrImport({
      strategyId: strategyId.value,
      records: ocrRecords.value
    })

    const imported = response.data?.imported ?? 0
    const skipped = response.data?.skipped ?? 0
    ElMessage.success(`导入完成：成功 ${imported} 条，跳过 ${skipped} 条`)

    ocrDialogVisible.value = false
    await loadData()
  } catch (error) {
    console.error('导入失败:', error)
    ocrError.value = error.response?.data?.message || '导入失败'
    ElMessage.error(ocrError.value)
  } finally {
    ocrImporting.value = false
  }
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

const sortOcrRecords = (records) => {
  return [...records].sort((a, b) => {
    const aTime = a.tradeTime ? Date.parse(a.tradeTime.replace(' ', 'T')) : 0
    const bTime = b.tradeTime ? Date.parse(b.tradeTime.replace(' ', 'T')) : 0
    return aTime - bTime
  })
}
// ...existing code...
</script>

<style scoped>
.loading-container {
  display: flex;
  justify-content: center;
  padding: 60px;
  font-size: 32px;
  color: #667eea;
}

/* 概览卡片 */
.overview-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  padding: 20px;
  color: #fff;
  margin-bottom: 12px;
}

.overview-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.symbol-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.symbol {
  font-size: 22px;
  font-weight: 700;
}

.symbol-code {
  font-size: 13px;
  opacity: 0.85;
}

.overview-stats {
  text-align: center;
  margin-bottom: 20px;
}

.stat-item.main .stat-label {
  font-size: 13px;
  opacity: 0.85;
}

.stat-item.main .stat-value {
  font-size: 32px;
  font-weight: 700;
}

.stat-value.profit {
  color: #7dffb3;
}

.stat-value.profit.negative {
  color: #ffb3b3;
}

.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.overview-grid .stat-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.overview-grid .stat-label {
  font-size: 12px;
  opacity: 0.75;
}

.overview-grid .stat-value {
  font-size: 15px;
  font-weight: 600;
}

/* 执行区域 */
.execute-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.execute-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.execute-form {
  display: flex;
  gap: 10px;
}

.price-input {
  flex: 1;
}

.execute-btn {
  width: 80px;
}

.execute-hint {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

/* Tab切换 */
.tab-switcher {
  display: flex;
  background: #fff;
  border-radius: 12px;
  padding: 4px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.tab-item {
  flex: 1;
  padding: 10px;
  text-align: center;
  font-size: 14px;
  font-weight: 500;
  color: #909399;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-item.active {
  background: #667eea;
  color: #fff;
}

/* 成交记录 */
.record-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.empty-records {
  text-align: center;
  padding: 40px;
  color: #909399;
  font-size: 14px;
}

.record-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

.record-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.record-price {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.record-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.record-amount {
  font-size: 14px;
  color: #606266;
}

.record-time {
  font-size: 12px;
  color: #c0c4cc;
}

.record-fee {
  font-size: 12px;
  color: #e6a23c;
}

.record-fee-hint {
  font-size: 11px;
  color: #c0c4cc;
  font-style: italic;
}

/* 网格列表 */
.grid-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 费用栏位样式 */
.stat-value.fee {
  color: #ffcc80;
}

/* 费用弹窗样式 */
.fee-dialog-content {
  padding: 10px 0;
}

.fee-record-info {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
}

.fee-input {
  width: 100%;
}

/* 价格触发后费用弹窗样式 */
.tick-fee-content {
  padding: 0;
}

.tick-fee-hint {
  font-size: 13px;
  color: #909399;
  margin-bottom: 16px;
}

.tick-fee-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 10px;
}

.tick-fee-trade-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.tick-fee-price {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.tick-fee-amount {
  font-size: 12px;
  color: #909399;
}

.tick-fee-input {
  width: 100px;
  flex-shrink: 0;
}

/* OCR导入样式 */
.ocr-action {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.ocr-section {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ocr-list {
  margin-top: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ocr-item {
  background: #f9fafc;
  border-radius: 10px;
  padding: 10px;
}

.ocr-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.ocr-type {
  width: 90px;
}

.ocr-level {
  font-size: 12px;
  color: #909399;
}

.ocr-datetime {
  width: 100%;
  margin-bottom: 8px;
}

.ocr-field {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ocr-label {
  font-size: 12px;
  color: #606266;
}

.ocr-message {
  font-size: 12px;
  color: #909399;
}

.warn-icon {
  color: #e6a23c;
}
</style>
