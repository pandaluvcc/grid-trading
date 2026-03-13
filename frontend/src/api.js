/**
 * @deprecated 请使用 @/services/ 目录下的模块化服务
 * 此文件作为兼容层保留，所有函数已迁移到各服务模块
 */

// 导出axios实例
export { default } from '@/services/index'

// 策略相关
export {
  getAllStrategies,
  getStrategy,
  getStrategyDetail,
  createStrategy,
  updateStrategyLastPrice
} from '@/services/strategy'

// 网格相关
export {
  getGridLines,
  suggestGridByPrice,
  executeTick,
  updatePlanBuyPrice,
  updateActualBuyPrice,
  resumeBuy,
  getDeferredGrids
} from '@/services/grid'

// 交易相关
export { getTradeRecords, updateTradeFee } from '@/services/trade'

// OCR相关
export { ocrRecognize, ocrImport, ocrCreateStrategy, ocrRematch } from '@/services/ocr'

// 建议相关
export { getSuggestion, updateLastPrice, getSmartSuggestions } from '@/services/suggestion'
