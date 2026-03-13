import api from './index'

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
