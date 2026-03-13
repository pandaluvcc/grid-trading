import api from './index'

/**
 * 获取智能建议操作
 * @param {number} strategyId - 策略ID
 * @returns {Promise} 智能建议结果
 */
export function getSuggestion(strategyId) {
  return api.get(`/strategies/${strategyId}/suggestion`).then((response) => response.data)
}

/**
 * 更新最新价格
 * @param {number} strategyId - 策略ID
 * @param {number} lastPrice - 最新价格
 * @returns {Promise}
 */
export function updateLastPrice(strategyId, lastPrice) {
  return api.put(`/strategies/${strategyId}/last-price`, { lastPrice }).then((response) => response.data)
}

/**
 * 获取智能建议
 * @param {number} strategyId - 策略ID
 * @param {number} currentPrice - 当前价格
 * @returns {Promise}
 */
export function getSmartSuggestions(strategyId, currentPrice) {
  return api
    .get(`/suggestions/${strategyId}`, {
      params: { currentPrice }
    })
    .then((response) => response.data)
}
