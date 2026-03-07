<template>
  <MobileLayout :title="strategyTitle" :show-back="true" :show-tab-bar="false">
    <div v-if="loading" class="loading-container">
      <el-icon class="is-loading"><Loading /></el-icon>
    </div>

    <div v-else-if="strategy">
      <div class="broker-header">
        <div class="header-top">
          <div class="symbol-name-big">{{ strategy.name || strategy.symbol }}</div>
          <div class="symbol-code">{{ strategy.symbol }}</div>
          <div class="risk-icon-wrapper" v-if="risks && risks.length > 0" @click="showRiskDialog = true">
            <el-icon class="risk-icon"><Warning /></el-icon>
          </div>
        </div>
        <div class="symbol-sub" v-if="strategy.name">{{ strategy.name }}</div>

        <div class="divider-line"></div>

        <div class="profit-section">
          <div class="profit-col">
            <div class="profit-label">持仓盈亏</div>
            <div class="profit-value" :class="{ negative: positionProfit < 0 }">
              {{ formatProfit(positionProfit) }}
            </div>
            <div class="profit-percent" :class="{ negative: positionProfitPercent < 0 }">
              {{ positionProfitPercent }}
            </div>
          </div>
          <div class="profit-col">
            <div class="profit-label">当日参考盈亏</div>
            <div class="profit-value">--</div>
            <div class="profit-percent">--</div>
          </div>
        </div>

        <div class="stats-grid">
          <div class="stat-row">
            <div class="stat-item">
              <span class="stat-label">持股天数</span>
              <span class="stat-value">{{ holdingDays }}</span>
            </div>
            <div class="stat-item price-item">
              <span class="stat-label">现价</span>
              <div class="price-input-wrapper">
                <el-input
                  v-model="priceInput"
                  type="number"
                  size="small"
                  class="inline-price-input"
                  @change="onPriceChange"
                />
              </div>
            </div>
          </div>
          <div class="stat-row">
            <div class="stat-item">
              <span class="stat-label">个股仓位</span>
              <span class="stat-value">{{ positionRatio }}%</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">成本价</span>
              <span class="stat-value">¥{{ formatPrice(costPrice) }}</span>
            </div>
          </div>
          <div class="stat-row">
            <div class="stat-item">
              <span class="stat-label">税费合计</span>
              <span class="stat-value fee">¥{{ totalFee.toFixed(2) }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">买入均价</span>
              <span class="stat-value">¥{{ formatPrice(averageBuyPrice) }}</span>
            </div>
          </div>
        </div>
      </div>

      <SmartSuggestion
        v-if="strategy"
        :strategy-id="Number(strategyId)"
        :initial-last-price="parseFloat(priceInput) || strategy.lastPrice"
        ref="smartSuggestionRef"
        @suggestion-updated="handleSuggestionUpdated"
      />

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

      <div class="tab-content">
        <div v-show="activeTab === 'grids'" class="grid-list">
          <MobileGridCard
            v-for="grid in gridLines"
            :key="grid.id"
            :grid="grid"
          />
        </div>

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
      </div>

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

      <el-dialog
        v-model="tickFeeDialogVisible"
        title="录入执行交易"
        width="90%"
        :close-on-click-modal="false"
        class="tick-fee-dialog"
      >
        <div class="tick-fee-content">
          <div v-if="suggestedGrid" class="suggested-grid-info">
            <div class="suggested-header">
              <el-icon><Promotion /></el-icon>
              <span>系统推荐</span>
            </div>
            <div class="suggested-details">
              <div class="suggested-item">
                <span class="label">网格编号:</span>
                <span class="value">第{{ suggestedGrid.level }}网</span>
              </div>
              <div class="suggested-item">
                <span class="label">网格类型:</span>
                <el-tag size="small" :type="getGridTypeTag(suggestedGrid.gridType)">
                  {{ getGridTypeName(suggestedGrid.gridType) }}
                </el-tag>
              </div>
              <div class="suggested-item">
                <span class="label">当前状态:</span>
                <el-tag size="small" :type="getStateTag(suggestedGrid.state)">
                  {{ getStateName(suggestedGrid.state) }}
                </el-tag>
              </div>
              <div class="suggested-item">
                <span class="label">建议操作:</span>
                <el-tag size="small" :type="suggestedGrid.suggestedType === 'BUY' ? 'danger' : 'success'">
                  {{ suggestedGrid.suggestedType === 'BUY' ? '买入' : '卖出' }}
                </el-tag>
              </div>
            </div>
          </div>

          <div class="grid-selector">
            <label>选择网格:</label>
            <el-select
              v-model="selectedGridLineId"
              placeholder="选择要操作的网格"
              size="default"
              style="width: 100%"
              @change="handleGridChange"
            >
              <el-option
                v-for="grid in availableGrids"
                :key="grid.id"
                :label="`第${grid.level}网 - ${getGridTypeName(grid.gridType)} - ${getStateName(grid.state)}`"
                :value="grid.id"
              >
                <div class="grid-option">
                  <span class="grid-option-label">第{{ grid.level }}网</span>
                  <el-tag size="small" :type="getGridTypeTag(grid.gridType)">
                    {{ getGridTypeName(grid.gridType) }}
                  </el-tag>
                  <el-tag size="small" :type="getStateTag(grid.state)">
                    {{ getStateName(grid.state) }}
                  </el-tag>
                </div>
              </el-option>
            </el-select>
          </div>

          <div class="tick-fee-hint">请录入交易信息：</div>
          <div class="tick-fee-item">
            <div class="tick-fee-trade-info">
              <el-select
                v-model="tradeType"
                size="default"
                style="width: 100px"
              >
                <el-option label="买入" value="BUY" />
                <el-option label="卖出" value="SELL" />
              </el-select>
              <span class="tick-fee-price">¥{{ priceInput }}</span>
              <span class="tick-fee-hint-text">基准价: ¥{{ formatPrice(strategy?.basePrice) }}</span>
            </div>

            <div class="tick-fee-inputs">
              <div class="input-group">
                <label>交易日期</label>
                <el-date-picker
                  v-model="tradeTime"
                  type="datetime"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  placeholder="选择交易日期"
                  size="default"
                  style="width: 100%"
                />
              </div>
              <div class="input-group">
                <label>交易数量</label>
                <el-input
                  v-model="tradeQuantity"
                  type="number"
                  placeholder="输入数量"
                  size="default"
                />
              </div>
              <div class="input-group">
                <label>手续费</label>
                <el-input
                  v-model="tradeFee"
                  type="number"
                  placeholder="输入手续费（可选）"
                  size="default"
                >
                  <template #prefix>¥</template>
                </el-input>
              </div>
            </div>
          </div>
        </div>
        <template #footer>
          <el-button @click="cancelTrade">取消</el-button>
          <el-button type="primary" :loading="savingTrade" @click="saveTrade">保存并执行</el-button>
        </template>
      </el-dialog>

      <el-dialog v-model="showRiskDialog" title="风险提示" width="90%">
        <div class="risk-dialog-content">
          <div v-for="(risk, index) in risks" :key="index" class="risk-dialog-item">
            <el-icon><Warning /></el-icon>
            <span>{{ risk.message }}</span>
          </div>
        </div>
        <template #footer>
          <el-button type="primary" @click="showRiskDialog = false">我知道了</el-button>
        </template>
      </el-dialog>
    </div>
  </MobileLayout>
</template>

<script setup>
import { ref, computed, watch, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading, Promotion, WarningFilled, RefreshRight, Warning } from '@element-plus/icons-vue'
import {
  getStrategyDetail,
  getGridLines,
  executeTick,
  getTradeRecords,
  updateTradeFee,
  suggestGridByPrice,
  ocrRecognize,
  ocrImport,
  ocrRematch
} from '../../api'
import MobileLayout from './MobileLayout.vue'
import MobileGridCard from './MobileGridCard.vue'
import SmartSuggestion from '../../components/SmartSuggestion.vue'

const route = useRoute()
const strategyId = computed(() => route.params.id)

const strategy = ref(null)
const gridLines = ref([])
const tradeRecords = ref([])
const priceInput = ref('')
const activeTab = ref('grids')
const smartSuggestionRef = ref(null)

const loading = ref(true)
const executing = ref(false)
let isUnmounted = false

const feeDialogVisible = ref(false)
const editingRecord = ref(null)
const feeInput = ref('')
const savingFee = ref(false)

const tickFeeDialogVisible = ref(false)
const selectedGridLineId = ref(null)
const suggestedGrid = ref(null)
const tradeType = ref('BUY')
const tradeTime = ref('')
const tradeQuantity = ref('')
const tradeFee = ref('')
const savingTrade = ref(false)

const ocrDialogVisible = ref(false)
const ocrFiles = ref([])
const ocrParsing = ref(false)
const ocrRematching = ref(false)
const ocrImporting = ref(false)
const ocrRecords = ref([])
const ocrError = ref('')

const showRiskDialog = ref(false)
const risks = ref([])

const totalFee = computed(() => {
  return tradeRecords.value.reduce((sum, r) => {
    return sum + (r.fee ? Number(r.fee) : 0)
  }, 0)
})

const calculatedInvestedAmount = computed(() => {
  return gridLines.value
    .filter(g => g.state === 'BOUGHT')
    .reduce((sum, g) => sum + (g.buyAmount ? Number(g.buyAmount) : 0), 0)
})

const realizedProfit = computed(() => {
  return gridLines.value
    .filter(g => g.actualProfit)
    .reduce((sum, g) => sum + Number(g.actualProfit), 0)
})

const completedGridIds = computed(() => {
  return new Set(gridLines.value.filter(g => g.actualSellPrice).map(g => g.id))
})

const completedTradeFee = computed(() => {
  return tradeRecords.value
    .filter(r => completedGridIds.value.has(r.gridLineId))
    .reduce((sum, r) => sum + (r.fee ? Number(r.fee) : 0), 0)
})

const netProfit = computed(() => {
  return realizedProfit.value - completedTradeFee.value
})

// 持仓盈亏计算
const positionProfit = computed(() => {
  if (!strategy.value || !gridLines.value.length) return 0
  const currentPrice = parseFloat(priceInput.value) || strategy.value.lastPrice || strategy.value.basePrice
  let totalProfit = 0
  
  gridLines.value.forEach(grid => {
    if (grid.state === 'BOUGHT' && grid.buyPrice) {
      const buyPrice = Number(grid.buyPrice)
      const quantity = grid.buyAmount ? Number(grid.buyAmount) / buyPrice : 0
      totalProfit += (currentPrice - buyPrice) * quantity
    }
  })
  
  return totalProfit
})

const positionProfitPercent = computed(() => {
  if (!calculatedInvestedAmount.value || calculatedInvestedAmount.value === 0) return '--'
  const percent = (positionProfit.value / calculatedInvestedAmount.value) * 100
  return (percent >= 0 ? '+' : '') + percent.toFixed(3) + '%'
})

// 持股天数
const holdingDays = computed(() => {
  if (!strategy.value || !strategy.value.createdAt) return 0
  const created = new Date(strategy.value.createdAt)
  const now = new Date()
  const diff = now - created
  return Math.floor(diff / (1000 * 60 * 60 * 24))
})

// 个股仓位
const positionRatio = computed(() => {
  if (!strategy.value || !strategy.value.maxCapital || strategy.value.maxCapital === 0) return '0.00'
  const maxCapital = Number(strategy.value.maxCapital)
  const invested = calculatedInvestedAmount.value
  const ratio = (invested / maxCapital) * 100
  return ratio.toFixed(2)
})

// 成本价和买入均价
const costPrice = computed(() => {
  if (!gridLines.value.length) return 0
  
  let totalAmount = 0
  let totalQuantity = 0
  
  gridLines.value.forEach(grid => {
    if (grid.state === 'BOUGHT' && grid.buyPrice && grid.buyAmount) {
      totalAmount += Number(grid.buyAmount)
      totalQuantity += Number(grid.buyAmount) / Number(grid.buyPrice)
    }
  })
  
  return totalQuantity > 0 ? totalAmount / totalQuantity : 0
})

const averageBuyPrice = computed(() => {
  if (!tradeRecords.value.length) return 0
  
  let totalAmount = 0
  let totalQuantity = 0
  
  tradeRecords.value.forEach(record => {
    if (record.type === 'BUY' && record.price && record.amount) {
      totalAmount += Number(record.amount)
      totalQuantity += Number(record.amount) / Number(record.price)
    }
  })
  
  return totalQuantity > 0 ? totalAmount / totalQuantity : 0
})

const strategyTitle = computed(() => {
  if (!strategy.value) return '策略详情'
  if (strategy.value.name && strategy.value.symbol) {
    return `${strategy.value.name} (${strategy.value.symbol})`
  }
  return strategy.value.name || strategy.value.symbol || '策略详情'
})

const getProfitPercent = () => {
  if (!strategy.value || !strategy.value.basePrice || calculatedInvestedAmount.value === 0) return '--'
  const percent = (realizedProfit.value / calculatedInvestedAmount.value) * 100
  return (percent >= 0 ? '+' : '') + percent.toFixed(2) + '%'
}

const availableGrids = computed(() => {
  return gridLines.value.filter(g =>
    g.state === 'WAIT_BUY' || g.state === 'BOUGHT'
  ).sort((a, b) => a.level - b.level)
})

const handleGridChange = (gridLineId) => {
  const selectedGrid = gridLines.value.find(g => g.id === gridLineId)
  if (selectedGrid) {
    if (selectedGrid.state === 'WAIT_BUY') {
      tradeType.value = 'BUY'
    } else if (selectedGrid.state === 'BOUGHT') {
      tradeType.value = 'SELL'
    }
  }
}

onUnmounted(() => {
  isUnmounted = true
})

const loadData = async () => {
  const id = strategyId.value
  if (!id || isUnmounted) {
    loading.value = false
    return
  }
  
  loading.value = true
  try {
    const strategyRes = await getStrategyDetail(id)
    const gridRes = await getGridLines(id)
    const recordRes = await getTradeRecords(id)
    
    if (isUnmounted) return
    
    strategy.value = strategyRes.data
    gridLines.value = gridRes.data.gridPlans?.sort((a, b) => a.level - b.level) || []
    tradeRecords.value = recordRes.data || []
    
    if (!priceInput.value && strategy.value?.lastPrice) {
      priceInput.value = Number(strategy.value.lastPrice).toFixed(2)
    } else if (!priceInput.value && strategy.value?.basePrice) {
      priceInput.value = Number(strategy.value.basePrice).toFixed(2)
    }
  } catch (error) {
    if (isUnmounted) return
    console.error('加载失败:', error)
    ElMessage.error('加载失败: ' + (error.response?.data?.message || error.message || '未知错误'))
  } finally {
    if (!isUnmounted) {
      loading.value = false
    }
  }
}

const onPriceChange = () => {
  if (smartSuggestionRef.value && priceInput.value) {
    smartSuggestionRef.value.fetchSuggestions(parseFloat(priceInput.value))
  }
}

const handleSuggestionUpdated = (data) => {
  risks.value = data.risks || []
}

const refreshPrice = () => {
  if (strategy.value?.lastPrice) {
    priceInput.value = Number(strategy.value.lastPrice).toFixed(2)
  } else if (strategy.value?.basePrice) {
    priceInput.value = Number(strategy.value.basePrice).toFixed(2)
  }
}

const formatPrice = (val) => val == null ? '-' : Number(val).toFixed(3)
const formatAmount = (val) => val == null ? '0' : Math.round(Number(val)).toString()
const formatQuantity = (val) => val == null ? '0' : Math.round(Number(val)).toString()
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

const openFeeDialog = (record) => {
  editingRecord.value = record
  feeInput.value = record.fee ? Number(record.fee).toString() : ''
  feeDialogVisible.value = true
}

const saveFee = async () => {
  const fee = parseFloat(feeInput.value)
  if (isNaN(fee) || fee < 0) {
    ElMessage.warning('请输入有效的费用')
    return
  }
  
  savingFee.value = true
  try {
    await updateTradeFee(editingRecord.value.id, fee)
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

const saveTrade = async () => {
  const price = parseFloat(priceInput.value)
  const quantity = parseFloat(tradeQuantity.value)
  const fee = tradeFee.value ? parseFloat(tradeFee.value) : null

  if (!quantity || quantity <= 0) {
    ElMessage.warning('请输入有效的交易数量')
    return
  }

  if (!tradeTime.value) {
    ElMessage.warning('请选择交易日期')
    return
  }

  savingTrade.value = true
  try {
    await executeTick(strategyId.value, {
      gridLineId: selectedGridLineId.value,
      type: tradeType.value,
      price: price,
      quantity: quantity,
      fee: fee,
      tradeTime: tradeTime.value
    })

    ElMessage.success('执行成功')
    tickFeeDialogVisible.value = false
    await loadData()
  } catch (error) {
    console.error('执行失败:', error)
    ElMessage.error(error.response?.data?.message || '执行失败')
  } finally {
    savingTrade.value = false
  }
}

const cancelTrade = () => {
  tickFeeDialogVisible.value = false
  ElMessage.info('已取消')
}

let loadedId = null

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

const getGridTypeName = (type) => {
  const map = {
    'SMALL': '小网',
    'MEDIUM': '中网',
    'LARGE': '大网'
  }
  return map[type] || type
}

const getGridTypeTag = (type) => {
  const map = {
    'SMALL': '',
    'MEDIUM': 'warning',
    'LARGE': 'danger'
  }
  return map[type] || ''
}

const calculateDeviation = () => {
  if (!strategy.value || !strategy.value.basePrice) return 0
  const currentPrice = parseFloat(priceInput.value) || strategy.value.lastPrice || strategy.value.basePrice
  const deviation = ((currentPrice - strategy.value.basePrice) / strategy.value.basePrice) * 100
  return deviation.toFixed(2)
}

const getDeviationClass = () => {
  const deviation = calculateDeviation()
  if (parseFloat(deviation) > 0) return 'profit'
  if (parseFloat(deviation) < 0) return 'negative'
  return ''
}

const getNearestGridInfo = () => {
  if (!gridLines.value || gridLines.value.length === 0) return '无'
  const firstGrid = gridLines.value[0]
  return `第${firstGrid.level}网 (${getGridTypeName(firstGrid.gridType)})`
}

const getStateName = (state) => {
  const map = {
    'WAIT_BUY': '等待买入',
    'BOUGHT': '已买入',
    'WAIT_SELL': '等待卖出',
    'SOLD': '已卖出'
  }
  return map[state] || state
}

const getStateTag = (state) => {
  const map = {
    'WAIT_BUY': 'info',
    'BOUGHT': 'success',
    'WAIT_SELL': 'warning',
    'SOLD': ''
  }
  return map[state] || ''
}
</script>

<style scoped>
.loading-container {
  display: flex;
  justify-content: center;
  padding: 60px;
  font-size: 32px;
  color: #667eea;
}

.broker-header {
  background: #fff;
  padding: 16px;
  margin-bottom: 8px;
}

.header-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}

.risk-icon-wrapper {
  margin-left: auto;
  display: flex;
  align-items: center;
  cursor: pointer;
}

.risk-icon {
  font-size: 24px;
  color: #e6a23c;
}

.symbol-name-big {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.symbol-code {
  font-size: 16px;
  color: #909399;
}

.symbol-sub {
  font-size: 16px;
  color: #909399;
  margin-bottom: 16px;
}

.divider-line {
  height: 8px;
  background: #f5f7fa;
  margin: 0 -16px;
  margin-bottom: 16px;
}

.profit-section {
  display: flex;
  gap: 24px;
  margin-bottom: 20px;
}

.profit-col {
  flex: 1;
}

.profit-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 6px;
}

.profit-value {
  font-size: 28px;
  font-weight: 600;
  color: #51cf66;
  margin-bottom: 4px;
}

.profit-value.negative {
  color: #ff6b6b;
}

.profit-percent {
  font-size: 18px;
  font-weight: 600;
  color: #51cf66;
}

.profit-percent.negative {
  color: #ff6b6b;
}

.stats-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.stat-value {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}

.stat-value.profit {
  color: #ff6b6b;
}

.stat-value.negative {
  color: #51cf66;
}

.stat-value.fee {
  color: #e6a23c;
}

.price-item {
  align-items: center;
}

.price-input-wrapper {
  display: flex;
  align-items: center;
}

.inline-price-input {
  width: 100px;
}

.inline-price-input :deep(.el-input__wrapper) {
  background: #f5f7fa;
  box-shadow: none;
  padding: 6px 8px;
  border-radius: 4px;
}

.inline-price-input :deep(.el-input__inner) {
  color: #303133;
  font-size: 16px;
  font-weight: 500;
  text-align: right;
  padding: 0;
}

.tab-switcher {
  display: flex;
  background: #fff;
  border-radius: 12px;
  padding: 4px;
  margin: 12px 16px;
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

.tab-content {
  min-height: 200px;
  padding: 0 16px;
}

.grid-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

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

.tick-fee-content {
  padding: 0;
}

.suggested-grid-info {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 16px;
  color: #fff;
}

.suggested-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
}

.suggested-details {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.suggested-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.suggested-item .label {
  font-size: 12px;
  opacity: 0.85;
}

.suggested-item .value {
  font-size: 15px;
  font-weight: 600;
}

.grid-selector {
  margin-bottom: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.grid-selector label {
  display: block;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
  margin-bottom: 8px;
}

.grid-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.grid-option-label {
  font-weight: 600;
  color: #303133;
}

.tick-fee-hint {
  font-size: 13px;
  color: #909399;
  margin-bottom: 16px;
}

.tick-fee-item {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 8px;
}

.tick-fee-trade-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tick-fee-price {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.tick-fee-hint-text {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.tick-fee-inputs {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.input-group label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
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

.risk-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-dialog-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  background: #fff7e6;
  border-radius: 8px;
  color: #d46b08;
  font-size: 14px;
}

.risk-dialog-item .el-icon {
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}
</style>
