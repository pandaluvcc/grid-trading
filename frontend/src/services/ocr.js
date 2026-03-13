import api from './index'

/**
 * OCR识别（上传截图）
 */
export function ocrRecognize({ files, strategyId, brokerType = 'EASTMONEY' }) {
  const formData = new FormData()
  const fileList = Array.isArray(files) ? files : files ? [files] : []
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
export function ocrCreateStrategy({
  files,
  brokerType = 'EASTMONEY',
  name,
  symbol,
  gridCalculationMode = 'INDEPENDENT'
}) {
  const formData = new FormData()
  const fileList = Array.isArray(files) ? files : files ? [files] : []
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
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000
  })
}

/**
 * OCR重新匹配
 */
export function ocrRematch({ strategyId, records }) {
  return api.post('/ocr/rematch', { strategyId, records })
}
