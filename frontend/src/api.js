import axios from 'axios'

// 创建 axios 实例
const api = axios.create({
  baseURL: 'http://localhost:8080/api',
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

export default api
