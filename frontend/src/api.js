import axios from 'axios'

// 动态获取后端地址：与前端同主机，端口8080
const getBaseURL = () => {
  const host = window.location.hostname || 'localhost'
  return `http://${host}:8080/api`
}

// 创建 axios 实例
const api = axios.create({
  baseURL: getBaseURL(),
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// ==================== 策略相关 ====================

/**
 * 获取所有策略列表
 */
export function getAllStrategies() {
  return api.get('/strategies')
}

/**
 * 根据 ID 获取策略详情
 */
export function getStrategy(id) {
  return api.get(`/strategies/${id}`)
}

/**
 * 获取策略详细信息（完整）
 */
export function getStrategyDetail(id) {
  return api.get(`/strategies/${id}/detail`)
}

/**
 * 创建策略
 */
export function createStrategy(data) {
  return api.post('/strategies', data)
}

/**
 * 获取网格计划列表
 */
export function getGridLines(strategyId) {
  return api.get(`/strategies/${strategyId}/grid-plans`)
}

/**
 * 执行一次价格触发
 */
export function executeTick(strategyId, price) {
  return api.post(`/strategies/${strategyId}/tick`, { price })
}

/**
 * 更新网格计划买入价（计划阶段调整）
 */
export function updatePlanBuyPrice(gridLineId, newBuyPrice) {
  return api.put(`/strategies/grid-lines/${gridLineId}/update-plan-buy-price`, null, {
    params: { newBuyPrice }
  })
}

/**
 * 更新网格实际买入价
 */
export function updateActualBuyPrice(gridLineId, actualBuyPrice) {
  return api.put('/strategies/grid-lines/actual-buy-price', {
    gridLineId,
    actualBuyPrice
  })
}

/**
 * 获取成交记录
 */
export function getTradeRecords(strategyId) {
  return api.get(`/strategies/${strategyId}/trades`)
}

/**
 * 更新成交记录的手续费
 */
export function updateTradeFee(tradeId, fee) {
  return api.put(`/trades/${tradeId}/fee`, { fee })
}

/**
 * 获取策略的累计手续费
 */
export function getStrategyTotalFee(strategyId) {
  return api.get(`/strategies/${strategyId}/total-fee`)
}

/**
 * OCR识别（上传截图）
 */
export function ocrRecognize({ files, strategyId, brokerType = 'EASTMONEY' }) {
  const formData = new FormData()
  const fileList = Array.isArray(files) ? files : (files ? [files] : [])
  fileList.forEach((file) => {
    formData.append('files', file)
  })
  formData.append('strategyId', strategyId)
  formData.append('brokerType', brokerType)

  return api.post('/ocr/recognize', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * OCR批量导入
 */
export function ocrImport(data) {
  return api.post('/ocr/import', data)
}

/**
 * OCR导入并创建策略
 */
export function ocrCreateStrategy({ files, brokerType = 'EASTMONEY', name, symbol, gridCalculationMode = 'INDEPENDENT' }) {
  const formData = new FormData()
  const fileList = Array.isArray(files) ? files : (files ? [files] : [])
  fileList.forEach((file) => {
    formData.append('files', file)
  })
  formData.append('brokerType', brokerType)
  if (name) {
    formData.append('name', name)
  }
  if (symbol) {
    formData.append('symbol', symbol)
  }
  if (gridCalculationMode) {
    formData.append('gridCalculationMode', gridCalculationMode)
  }

  return api.post('/ocr/import-create', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * OCR重新匹配
 */
export function ocrRematch({ strategyId, records }) {
  return api.post('/ocr/rematch', { strategyId, records })
}

export default api
