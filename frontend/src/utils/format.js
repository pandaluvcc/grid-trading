/**
 * 格式化价格，保留3位小数
 * @param {number|string} val 价格
 * @returns {string} 格式化后的价格
 */
export const formatPrice = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  const num = Number(val)
  return isNaN(num) ? '--' : num.toFixed(3)
}

/**
 * 格式化金额（盈亏、收益等），保留2位小数
 * @param {number|string} val 金额
 * @returns {string} 格式化后的金额
 */
export const formatAmount = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  const num = Number(val)
  return isNaN(num) ? '--' : num.toFixed(2)
}

/**
 * 格式化数量（股数、份数等），保留0位小数
 * @param {number|string} val 数量
 * @returns {string} 格式化后的数量
 */
export const formatQuantity = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  const num = Number(val)
  return isNaN(num) ? '--' : Math.round(num).toString()
}

/**
 * 格式化手续费，保留2位小数
 * @param {number|string} val 手续费
 * @returns {string} 格式化后的手续费
 */
export const formatFee = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  const num = Number(val)
  return isNaN(num) ? '--' : num.toFixed(2)
}

/**
 * 格式化收益率，保留2位小数，添加%
 * @param {number|string} val 收益率（小数形式，如0.05表示5%）
 * @returns {string} 格式化后的收益率
 */
export const formatProfitRate = (val) => {
  if (val === null || val === undefined || val === '') return '--'
  const num = Number(val)
  return isNaN(num) ? '--' : `${(num * 100).toFixed(2)}%`
}

/**
 * 格式化时间
 * @param {string|Date} time 时间
 * @param {string} format 格式，默认'YYYY-MM-DD HH:mm:ss'
 * @returns {string} 格式化后的时间
 */
export const formatTime = (time, format = 'YYYY-MM-DD HH:mm:ss') => {
  if (!time) return '--'
  const date = new Date(time)
  if (isNaN(date.getTime())) return '--'

  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 简化时间显示，今天的只显示时分，否则显示月日时分
 * @param {string|Date} time 时间
 * @returns {string} 简化后的时间
 */
export const formatTimeSimple = (time) => {
  if (!time) return '--'
  const date = new Date(time)
  if (isNaN(date.getTime())) return '--'

  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()

  if (isToday) {
    return formatTime(time, 'HH:mm')
  } else {
    return formatTime(time, 'MM-DD HH:mm')
  }
}
