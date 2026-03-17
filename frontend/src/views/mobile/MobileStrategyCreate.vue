<template>
  <div class="mobile-create">
    <!-- 表单区域 -->
    <div class="form-area">
      <div class="import-section">
        <div class="section-title">成交截图导入</div>
        <div class="section-hint">上传成交记录截图，自动创建策略并匹配前 N 条网格</div>
        <el-upload
          ref="uploadRef"
          class="file-uploader"
          :auto-upload="false"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          :file-list="uploadFileList"
          multiple
          accept="image/*"
          drag
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">将截图拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 jpg/png/jpeg 格式，最多上传 20 张截图</div>
          </template>
        </el-upload>
        <div class="file-count" v-if="importFiles.length">已选择 {{ importFiles.length }} 张截图</div>
        <button
          class="import-btn"
          :class="{ disabled: importing || !importFiles.length }"
          :disabled="importing || !importFiles.length"
          @click="submitImport"
        >
          {{ importing ? '导入中...' : '导入成交截图创建策略' }}
        </button>
        <div class="section-divider"></div>
      </div>
      <div class="form-group">
        <label class="form-label">策略名称</label>
        <input type="text" class="form-input" v-model="form.name" placeholder="给策略起个名字" />
      </div>

      <div class="form-group">
        <label class="form-label">证券代码</label>
        <input type="text" class="form-input" v-model="form.symbol" placeholder="如 BTC/USDT" />
      </div>

      <div class="form-group">
        <label class="form-label">网格计算模式</label>
        <div class="mode-switch">
          <div
            class="mode-item"
            :class="{ active: form.gridCalculationMode === 'INDEPENDENT' }"
            @click="form.gridCalculationMode = 'INDEPENDENT'"
          >
            独立计算
          </div>
          <div
            class="mode-item"
            :class="{ active: form.gridCalculationMode === 'PRICE_LOCK' }"
            @click="form.gridCalculationMode = 'PRICE_LOCK'"
          >
            价格锁定
          </div>
        </div>
        <div class="form-hint" v-if="form.gridCalculationMode === 'INDEPENDENT'">
          每个网格独立计算：卖价 = 买价 × (1 + 比例)
        </div>
        <div class="form-hint" v-if="form.gridCalculationMode === 'PRICE_LOCK'">
          价格锁定模式：卖价关联其他网格（原有逻辑）
        </div>
      </div>

      <div class="form-group">
        <label class="form-label">基准价</label>
        <input
          type="number"
          class="form-input"
          v-model.number="form.basePrice"
          placeholder="第1格买入价格"
          step="0.01"
        />
        <div class="form-hint">第1条网格的买入价格</div>
      </div>

      <!-- 模式切换 -->
      <div class="form-group">
        <label class="form-label">单格设置方式</label>
        <div class="mode-switch">
          <div class="mode-item" :class="{ active: mode === 'amount' }" @click="mode = 'amount'">按金额</div>
          <div class="mode-item" :class="{ active: mode === 'quantity' }" @click="mode = 'quantity'">按数量</div>
        </div>
      </div>

      <!-- 按金额模式 -->
      <div class="form-group" v-if="mode === 'amount'">
        <label class="form-label">单格金额</label>
        <input
          type="number"
          class="form-input"
          v-model.number="form.amountPerGrid"
          placeholder="每格投入金额"
          step="1"
        />
        <div class="form-hint">每条网格投入的金额（固定19条）</div>
      </div>

      <!-- 按数量模式 -->
      <div class="form-group" v-if="mode === 'quantity'">
        <label class="form-label">单格数量</label>
        <input
          type="number"
          class="form-input"
          v-model.number="form.quantityPerGrid"
          placeholder="每格买入数量"
          step="0.0001"
        />
        <div class="form-hint">每条网格买入的数量（固定19条）</div>
      </div>

      <!-- 计算结果 -->
      <div class="calc-section" v-if="showCalcResult">
        <div class="calc-row">
          <span class="calc-label">单格金额</span>
          <span class="calc-value">¥{{ calcAmountPerGrid.toFixed(2) }}</span>
        </div>
        <div class="calc-row">
          <span class="calc-label">最大投入</span>
          <span class="calc-value highlight">¥{{ (calcAmountPerGrid * 19).toFixed(2) }}</span>
        </div>
        <div class="calc-row" v-if="mode === 'quantity'">
          <span class="calc-label">总数量</span>
          <span class="calc-value">{{ (form.quantityPerGrid * 19).toFixed(4) }}</span>
        </div>
      </div>

      <!-- 按钮组 -->
      <div class="btn-group">
        <button
          class="submit-btn"
          :class="{ disabled: !isFormValid }"
          :disabled="submitting || !isFormValid"
          @click="handleSubmit"
        >
          {{ submitting ? '创建中...' : '创建策略' }}
        </button>
        <button class="cancel-btn" @click="goBack">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { createStrategy } from '@/services/strategy'
import { ocrCreateStrategy } from '@/services/ocr'

const router = useRouter()
const submitting = ref(false)
const importing = ref(false)
const mode = ref('amount')
const importFiles = ref([])
const uploadFileList = ref([])
const uploadRef = ref(null)

const form = ref({
  name: '',
  symbol: '',
  basePrice: null,
  amountPerGrid: null,
  quantityPerGrid: null,
  gridCalculationMode: 'INDEPENDENT' // 默认独立计算模式
})

// 计算单格金额
const calcAmountPerGrid = computed(() => {
  if (mode.value === 'amount') {
    return form.value.amountPerGrid || 0
  } else {
    // 按数量：金额 = 基准价 × 数量
    return (form.value.basePrice || 0) * (form.value.quantityPerGrid || 0)
  }
})

// 是否显示计算结果
const showCalcResult = computed(() => {
  return calcAmountPerGrid.value > 0
})

// 表单验证
const isFormValid = computed(() => {
  const baseValid = form.value.name && form.value.symbol && form.value.basePrice > 0

  if (mode.value === 'amount') {
    return baseValid && form.value.amountPerGrid > 0
  } else {
    return baseValid && form.value.quantityPerGrid > 0
  }
})

// 返回
const goBack = () => {
  router.back()
}

const handleFileChange = (file, fileList) => {
  uploadFileList.value = fileList
  importFiles.value = uploadFileList.value.map((f) => f.raw)
}

const handleFileRemove = (file) => {
  const index = uploadFileList.value.findIndex((f) => f.uid === file.uid)
  if (index > -1) {
    uploadFileList.value.splice(index, 1)
    importFiles.value = uploadFileList.value.map((f) => f.raw)
  }
}

const submitImport = async () => {
  if (!importFiles.value.length || importing.value) return
  importing.value = true
  try {
    const response = await ocrCreateStrategy({
      files: importFiles.value,
      name: form.value.name,
      symbol: form.value.symbol,
      gridCalculationMode: form.value.gridCalculationMode
    })
    const strategyId = response.data?.id
    if (!strategyId) {
      ElMessage.error('导入失败：无法获取策略ID')
      return
    }
    ElMessage.success('导入成功')
    setTimeout(() => {
      router.replace(`/m/strategy/${strategyId}`)
    }, 300)
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error(error.response?.data?.message || '导入失败')
  } finally {
    importing.value = false
  }
}

// 提交
const handleSubmit = async () => {
  if (!isFormValid.value) return

  // 根据模式构建请求数据
  const requestData = {
    name: form.value.name,
    symbol: form.value.symbol,
    basePrice: form.value.basePrice
  }

  if (mode.value === 'amount') {
    requestData.amountPerGrid = form.value.amountPerGrid
  } else {
    requestData.quantityPerGrid = form.value.quantityPerGrid
  }

  submitting.value = true
  try {
    const response = await createStrategy(requestData)
    console.log('创建策略响应:', response)
    const strategyId = response.data?.id
    if (!strategyId) {
      console.error('创建响应中没有id:', response)
      ElMessage.error('创建失败：无法获取策略ID')
      return
    }
    ElMessage.success('创建成功')
    // 使用 replace 跳转，这样返回时直接回到首页而不是创建页
    setTimeout(() => {
      router.replace(`/m/strategy/${strategyId}`)
    }, 300)
  } catch (error) {
    console.error('创建失败:', error)
    ElMessage.error(error.response?.data?.message || '创建失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.mobile-create {
  min-height: 100vh;
  background: var(--bg-card);
  padding: var(--spacing-xxl) var(--spacing-xl);
  padding-top: max(24px, env(safe-area-inset-top));
  padding-bottom: max(24px, env(safe-area-inset-bottom));
  box-sizing: border-box;
  transition: background-color var(--transition-base);
}

.form-area {
  max-width: 400px;
  margin: 0 auto;
}

.form-group {
  margin-bottom: var(--spacing-xxl);
}

.form-label {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.form-input {
  width: 100%;
  height: 50px;
  padding: 0 var(--spacing-lg);
  font-size: var(--font-size-lg);
  color: var(--text-primary);
  background: var(--bg-light);
  border: 1.5px solid transparent;
  border-radius: var(--border-radius-lg);
  outline: none;
  transition: all var(--transition-base);
  box-sizing: border-box;
  -webkit-appearance: none;
  appearance: none;
}

.form-input:focus {
  background: var(--bg-card);
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-input::placeholder {
  color: var(--text-placeholder);
}

.form-hint {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  margin-top: var(--spacing-sm);
  padding-left: var(--spacing-xs);
}

/* 模式切换 */
.mode-switch {
  display: flex;
  background: var(--bg-color);
  border-radius: 10px;
  padding: var(--spacing-xs);
}

.mode-item {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: var(--font-size-base);
  color: var(--text-regular);
  border-radius: var(--border-radius-md);
  cursor: pointer;
  transition: all var(--transition-base);
}

.mode-item.active {
  background: var(--bg-card);
  color: var(--primary-color);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

/* 计算结果 */
.calc-section {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.06) 0%, rgba(118, 75, 162, 0.06) 100%);
  border-radius: var(--border-radius-lg);
  padding: var(--spacing-md) var(--spacing-lg);
  margin-bottom: var(--spacing-xxl);
}

.calc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm) 0;
}

.calc-row:not(:last-child) {
  border-bottom: 1px dashed var(--border-light);
}

.calc-label {
  font-size: var(--font-size-base);
  color: var(--text-regular);
}

.calc-value {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
}

.calc-value.highlight {
  font-size: var(--font-size-xl);
  color: var(--primary-color);
}

/* 导入区 */
.import-section {
  margin-bottom: var(--spacing-xxl);
}

.section-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
}

.section-hint {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
}

.file-uploader {
  margin-bottom: var(--spacing-md);
}

.file-uploader :deep(.el-upload-dragger) {
  width: 100%;
  height: 180px;
  background: linear-gradient(135deg, var(--bg-light) 0%, rgba(102, 126, 234, 0.05) 100%);
  border: 2px dashed var(--border-light);
  border-radius: var(--border-radius-xl);
  transition: all var(--transition-slow);
}

.file-uploader :deep(.el-upload-dragger:hover) {
  border-color: var(--primary-color);
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.05) 0%, rgba(102, 126, 234, 0.1) 100%);
}

.file-uploader :deep(.el-icon--upload) {
  font-size: 56px;
  color: var(--primary-color);
  margin-bottom: var(--spacing-sm);
}

.file-uploader :deep(.el-upload__text) {
  font-size: 15px;
  color: var(--text-regular);
}

.file-uploader :deep(.el-upload__text em) {
  color: var(--primary-color);
  font-style: normal;
  font-weight: 600;
}

.file-uploader :deep(.el-upload__tip) {
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  margin-top: var(--spacing-sm);
}

.file-count {
  font-size: 13px;
  color: var(--primary-color);
  font-weight: 500;
  margin-bottom: var(--spacing-md);
}

.import-btn {
  width: 100%;
  height: 48px;
  margin-top: var(--spacing-md);
  font-size: 15px;
  font-weight: 600;
  color: var(--primary-color);
  background: rgba(102, 126, 234, 0.1);
  border: none;
  border-radius: 24px;
  cursor: pointer;
  transition: opacity var(--transition-base), transform var(--transition-fast);
}

.import-btn:active {
  transform: scale(0.98);
}

.import-btn.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.import-btn.disabled:active {
  transform: none;
}

.section-divider {
  height: 1px;
  background: var(--border-lighter);
  margin-top: var(--spacing-xl);
}

/* 按钮组 */
.btn-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  margin-top: var(--spacing-lg);
}

.submit-btn {
  width: 100%;
  height: 52px;
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  background: var(--primary-gradient);
  border: none;
  border-radius: 26px;
  cursor: pointer;
  transition: opacity var(--transition-base), transform var(--transition-fast);
}

.submit-btn:active {
  transform: scale(0.98);
}

.submit-btn.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.submit-btn.disabled:active {
  transform: none;
}

.cancel-btn {
  width: 100%;
  height: 48px;
  font-size: var(--font-size-lg);
  font-weight: 500;
  color: var(--text-regular);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color var(--transition-fast);
}

.cancel-btn:active {
  color: var(--text-primary);
}
</style>
