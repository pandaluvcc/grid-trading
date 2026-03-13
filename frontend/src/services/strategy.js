import api from './index'

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
 * 更新策略最新价格
 */
export function updateStrategyLastPrice(strategyId, lastPrice) {
  return api.put(`/strategies/${strategyId}/last-price`, { lastPrice })
}
