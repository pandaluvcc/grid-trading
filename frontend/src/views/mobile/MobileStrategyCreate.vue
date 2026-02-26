<template>
  <div class="mobile-create">
    <!-- 表单区域 -->
    <div class="form-area">
      <div class="import-section">
        <div class="section-title">成交截图导入</div>
        <div class="section-hint">上传成交记录截图，自动创建策略并匹配前 N 条网格</div>
        <input
          class="file-input"
          type="file"
          accept="image/*"
          multiple
          @change="handleImportFiles"
        />
        <div class="file-count" v-if="importFiles.length">
          已选择 {{ importFiles.length }} 张截图
        </div>
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
        <input 
          type="text" 
          class="form-input" 
          v-model="form.name" 
          placeholder="给策略起个名字"
        />
      </div>

      <div class="form-group">
        <label class="form-label">证券代码</label>
        <input 
          type="text" 
          class="form-input" 
          v-model="form.symbol" 
          placeholder="如 BTC/USDT"
        />
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
          <div 
            class="mode-item" 
            :class="{ active: mode === 'amount' }"
            @click="mode = 'amount'"
          >
            按金额
          </div>
          <div 
            class="mode-item" 
            :class="{ active: mode === 'quantity' }"
            @click="mode = 'quantity'"
          >
            按数量
          </div>
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
import { createStrategy, ocrCreateStrategy } from '../../api'

const router = useRouter()
const submitting = ref(false)
const importing = ref(false)
const mode = ref('amount')  // 'amount' 或 'quantity'
const importFiles = ref([])

const form = ref({
  name: '',
  symbol: '',
  basePrice: null,
  amountPerGrid: null,
  quantityPerGrid: null,
  gridCalculationMode: 'INDEPENDENT'  // 默认独立计算模式
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
  const baseValid = form.value.name && 
                    form.value.symbol && 
                    form.value.basePrice > 0
                    
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

const handleImportFiles = (event) => {
  const files = Array.from(event.target.files || [])
  importFiles.value = files
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
  background: #fff;
  padding: 24px 20px;
  padding-top: max(24px, env(safe-area-inset-top));
  padding-bottom: max(24px, env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.form-area {
  max-width: 400px;
  margin: 0 auto;
}

.form-group {
  margin-bottom: 24px;
}

.form-label {
  display: block;
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin-bottom: 10px;
}

.form-input {
  width: 100%;
  height: 50px;
  padding: 0 16px;
  font-size: 16px;
  color: #333;
  background: #f8f9fa;
  border: 1.5px solid transparent;
  border-radius: 12px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
  -webkit-appearance: none;
  appearance: none;
}

.form-input:focus {
  background: #fff;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-input::placeholder {
  color: #aaa;
}

.form-hint {
  font-size: 12px;
  color: #999;
  margin-top: 8px;
  padding-left: 4px;
}

/* 模式切换 */
.mode-switch {
  display: flex;
  background: #f5f6fa;
  border-radius: 10px;
  padding: 4px;
}

.mode-item {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: 14px;
  color: #666;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.mode-item.active {
  background: #fff;
  color: #667eea;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

/* 计算结果 */
.calc-section {
  background: linear-gradient(135deg, #667eea10 0%, #764ba210 100%);
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 24px;
}

.calc-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.calc-row:not(:last-child) {
  border-bottom: 1px dashed #e0e0e0;
}

.calc-label {
  font-size: 14px;
  color: #666;
}

.calc-value {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.calc-value.highlight {
  font-size: 18px;
  color: #667eea;
}

/* 导入区 */
.import-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
}

.section-hint {
  font-size: 12px;
  color: #999;
  margin-bottom: 12px;
}

.file-input {
  width: 100%;
  font-size: 14px;
  padding: 10px 12px;
  border: 1px dashed #d5d8e3;
  border-radius: 10px;
  background: #fafbff;
  box-sizing: border-box;
}

.file-count {
  font-size: 12px;
  color: #666;
  margin-top: 8px;
}

.import-btn {
  width: 100%;
  height: 48px;
  margin-top: 12px;
  font-size: 15px;
  font-weight: 600;
  color: #667eea;
  background: #eef1ff;
  border: none;
  border-radius: 24px;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.1s;
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
  background: #f0f1f5;
  margin-top: 20px;
}

/* 按钮组 */
.btn-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.submit-btn {
  width: 100%;
  height: 52px;
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  border-radius: 26px;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.1s;
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
  font-size: 16px;
  font-weight: 500;
  color: #666;
  background: transparent;
  border: none;
  cursor: pointer;
}

.cancel-btn:active {
  color: #333;
}
</style>
