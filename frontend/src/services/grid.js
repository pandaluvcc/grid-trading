import api from './index'

/**
 * 获取网格计划列表
 */
export function getGridLines(strategyId) {
  return api.get(`/strategies/${strategyId}/grid-plans`)
}

/**
 * 根据价格推荐网格和交易类型
 * @param {number} strategyId - 策略ID
 * @param {number} price - 输入的价格
 * @returns {Promise} 推荐结果
 */
export function suggestGridByPrice(strategyId, price) {
  return api.get(`/strategies/${strategyId}/suggest`, {
    params: { price }
  })
}

/**
 * 执行一次价格触发
 * 支持两种模式：
 * 1. 自动模式：executeTick(strategyId, price)
 * 2. 手动模式（新）：executeTick(strategyId, { gridLineId, type, price, quantity, fee, tradeTime })
 */
export function executeTick(strategyId, priceOrData) {
  // 兼容两种调用方式
  const data = typeof priceOrData === 'number' || typeof priceOrData === 'string' ? { price: priceOrData } : priceOrData

  return api.post(`/strategies/${strategyId}/tick`, data)
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
 * 手动补买暂缓网格
 * @param {number} strategyId - 策略ID
 * @param {number} gridId - 网格ID
 * @param {object} data - 交易数据 { price, quantity, fee, tradeTime }
 * @returns {Promise}
 */
export function resumeBuy(strategyId, gridId, data) {
  return api.post(`/strategies/${strategyId}/grids/${gridId}/resume-buy`, data).then((response) => response.data)
}

/**
 * 获取所有暂缓网格
 * @param {number} strategyId - 策略ID
 * @returns {Promise}
 */
export function getDeferredGrids(strategyId) {
  return api.get(`/strategies/${strategyId}/deferred-grids`).then((response) => response.data)
}
