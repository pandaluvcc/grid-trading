/**
 * 网格状态
 */
export const GRID_STATUS = {
  PENDING: 'PENDING',
  BUY_ORDERED: 'BUY_ORDERED',
  BOUGHT: 'BOUGHT',
  SELL_ORDERED: 'SELL_ORDERED',
  SOLD: 'SOLD',
  CANCELLED: 'CANCELLED',
  DEFERRED: 'DEFERRED'
}

/**
 * 网格状态中文映射
 */
export const GRID_STATUS_TEXT = {
  [GRID_STATUS.PENDING]: '待买入',
  [GRID_STATUS.BUY_ORDERED]: '买入挂单中',
  [GRID_STATUS.BOUGHT]: '已买入',
  [GRID_STATUS.SELL_ORDERED]: '卖出挂单中',
  [GRID_STATUS.SOLD]: '已卖出',
  [GRID_STATUS.CANCELLED]: '已取消',
  [GRID_STATUS.DEFERRED]: '暂缓'
}

/**
 * 网格状态标签颜色映射
 */
export const GRID_STATUS_COLOR = {
  [GRID_STATUS.PENDING]: '#909399',
  [GRID_STATUS.BUY_ORDERED]: '#409EFF',
  [GRID_STATUS.BOUGHT]: '#E6A23C',
  [GRID_STATUS.SELL_ORDERED]: '#F56C6C',
  [GRID_STATUS.SOLD]: '#67C23A',
  [GRID_STATUS.CANCELLED]: '#C0C4CC',
  [GRID_STATUS.DEFERRED]: '#F56C6C'
}

/**
 * 网格类型
 */
export const GRID_TYPE = {
  NORMAL: 'NORMAL',
  STOP_LOSS: 'STOP_LOSS',
  TAKE_PROFIT: 'TAKE_PROFIT'
}

/**
 * 网格类型中文映射
 */
export const GRID_TYPE_TEXT = {
  [GRID_TYPE.NORMAL]: '普通网格',
  [GRID_TYPE.STOP_LOSS]: '止损网格',
  [GRID_TYPE.TAKE_PROFIT]: '止盈网格'
}

/**
 * 交易类型
 */
export const TRADE_TYPE = {
  BUY: 'BUY',
  SELL: 'SELL'
}

/**
 * 交易类型中文映射
 */
export const TRADE_TYPE_TEXT = {
  [TRADE_TYPE.BUY]: '买入',
  [TRADE_TYPE.SELL]: '卖出'
}

/**
 * 交易类型颜色映射
 */
export const TRADE_TYPE_COLOR = {
  [TRADE_TYPE.BUY]: '#F56C6C',
  [TRADE_TYPE.SELL]: '#67C23A'
}

/**
 * 策略状态
 */
export const STRATEGY_STATUS = {
  ACTIVE: 'ACTIVE',
  PAUSED: 'PAUSED',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED'
}

/**
 * 策略状态中文映射
 */
export const STRATEGY_STATUS_TEXT = {
  [STRATEGY_STATUS.ACTIVE]: '运行中',
  [STRATEGY_STATUS.PAUSED]: '已暂停',
  [STRATEGY_STATUS.COMPLETED]: '已完成',
  [STRATEGY_STATUS.CANCELLED]: '已取消'
}

/**
 * 网格计算模式
 */
export const GRID_CALCULATION_MODE = {
  INDEPENDENT: 'INDEPENDENT',
  LINKED: 'LINKED'
}

/**
 * 网格计算模式中文映射
 */
export const GRID_CALCULATION_MODE_TEXT = {
  [GRID_CALCULATION_MODE.INDEPENDENT]: '独立计算',
  [GRID_CALCULATION_MODE.LINKED]: '关联计算'
}

/**
 * 券商类型
 */
export const BROKER_TYPE = {
  EASTMONEY: 'EASTMONEY',
  TONGHUASHUN: 'TONGHUASHUN',
  OTHER: 'OTHER'
}

/**
 * 券商类型中文映射
 */
export const BROKER_TYPE_TEXT = {
  [BROKER_TYPE.EASTMONEY]: '东方财富',
  [BROKER_TYPE.TONGHUASHUN]: '同花顺',
  [BROKER_TYPE.OTHER]: '其他'
}

/**
 * 建议操作类型
 */
export const SUGGESTION_ACTION = {
  BUY: 'BUY',
  SELL: 'SELL',
  HOLD: 'HOLD',
  ADJUST: 'ADJUST'
}

/**
 * 建议操作类型中文映射
 */
export const SUGGESTION_ACTION_TEXT = {
  [SUGGESTION_ACTION.BUY]: '建议买入',
  [SUGGESTION_ACTION.SELL]: '建议卖出',
  [SUGGESTION_ACTION.HOLD]: '建议持有',
  [SUGGESTION_ACTION.ADJUST]: '建议调整'
}

/**
 * 默认配置
 */
export const DEFAULT_CONFIG = {
  GRID_COUNT: 19,
  DEFAULT_QUANTITY: 1000,
  PRICE_TOLERANCE: 0.01,
  DEFAULT_GRID_MODE: GRID_CALCULATION_MODE.INDEPENDENT,
  DEFAULT_BROKER: BROKER_TYPE.EASTMONEY
}
