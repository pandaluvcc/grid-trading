<template>
  <el-dialog v-model="visible" title="OCR导入成交记录" width="95%" @close="handleClose">
    <div class="ocr-section">
      <el-upload
        class="ocr-uploader"
        :auto-upload="false"
        :show-file-list="true"
        :limit="5"
        accept="image/*"
        multiple
        :on-change="handleFileChange"
        :on-remove="handleFileChange"
        :on-exceed="handleFileExceed"
      >
        <el-button type="primary" size="small">选择截图（最多5张）</el-button>
      </el-upload>

      <el-button
        type="success"
        size="small"
        :disabled="!ocrFiles.length"
        :loading="parsing"
        @click="handleParse"
        style="margin-left: 12px"
      >
        解析截图
      </el-button>

      <div v-if="parsedRecords.length > 0" class="ocr-result">
        <h4>识别结果（共{{ parsedRecords.length }}条）</h4>
        <div class="ocr-table">
          <div class="ocr-table-header">
            <div class="ocr-col">时间</div>
            <div class="ocr-col">类型</div>
            <div class="ocr-col">价格</div>
            <div class="ocr-col">数量</div>
            <div class="ocr-col">金额</div>
            <div class="ocr-col">费用</div>
          </div>
          <div
            v-for="(record, index) in parsedRecords"
            :key="index"
            class="ocr-table-row"
            :class="{ error: record.error }"
          >
            <div class="ocr-col">{{ record.tradeTime }}</div>
            <div class="ocr-col">
              <el-tag :type="record.type === 'SELL' ? 'success' : 'danger'" size="small">
                {{ record.type === 'BUY' ? '买入' : '卖出' }}
              </el-tag>
            </div>
            <div class="ocr-col">{{ formatPrice(record.price) }}</div>
            <div class="ocr-col">{{ formatQuantity(record.quantity) }}股</div>
            <div class="ocr-col">{{ formatAmount(record.amount) }}元</div>
            <div class="ocr-col">
              <el-input v-model="record.fee" type="number" size="mini" placeholder="费用" style="width: 80px" />
            </div>
          </div>
        </div>

        <div class="ocr-actions" style="margin-top: 16px">
          <el-button type="warning" size="small" @click="handleRematch" :loading="rematching"> 重新匹配 </el-button>
          <el-button type="primary" size="small" @click="handleImport" :loading="importing"> 导入选中记录 </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { formatPrice, formatQuantity, formatAmount } from '@/utils/format'
import { ocrRecognize, ocrImport, ocrRematch } from '@/services/ocr'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  strategyId: {
    type: [Number, String],
    required: true
  }
})

const emit = defineEmits(['update:modelValue', 'import-success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const ocrFiles = ref([])
const parsing = ref(false)
const rematching = ref(false)
const importing = ref(false)
const parsedRecords = ref([])

const handleFileChange = (file, fileList) => {
  ocrFiles.value = fileList.map((item) => item.raw)
}

const handleFileExceed = () => {
  ElMessage.warning('最多只能上传5张截图')
}

const handleParse = async () => {
  if (ocrFiles.value.length === 0) {
    ElMessage.warning('请先选择截图')
    return
  }

  parsing.value = true
  try {
    const res = await ocrRecognize({
      files: ocrFiles.value,
      strategyId: props.strategyId
    })
    parsedRecords.value = res.data.records || []
    ElMessage.success(`识别成功，共${parsedRecords.value.length}条记录`)
  } catch (e) {
    ElMessage.error('识别失败：' + (e.response?.data?.message || e.message))
  } finally {
    parsing.value = false
  }
}

const handleRematch = async () => {
  if (parsedRecords.value.length === 0) {
    ElMessage.warning('没有可匹配的记录')
    return
  }

  rematching.value = true
  try {
    const res = await ocrRematch({
      strategyId: props.strategyId,
      records: parsedRecords.value
    })
    parsedRecords.value = res.data.records || []
    ElMessage.success('重新匹配完成')
  } catch (e) {
    ElMessage.error('匹配失败：' + (e.response?.data?.message || e.message))
  } finally {
    rematching.value = false
  }
}

const handleImport = async () => {
  if (parsedRecords.value.length === 0) {
    ElMessage.warning('没有可导入的记录')
    return
  }

  importing.value = true
  try {
    await ocrImport({
      strategyId: props.strategyId,
      records: parsedRecords.value
    })
    ElMessage.success('导入成功')
    emit('import-success')
    handleClose()
  } catch (e) {
    ElMessage.error('导入失败：' + (e.response?.data?.message || e.message))
  } finally {
    importing.value = false
  }
}

const handleClose = () => {
  ocrFiles.value = []
  parsedRecords.value = []
  emit('update:modelValue', false)
}
</script>

<style scoped>
.ocr-section {
  padding: 16px 0;
}

.ocr-result {
  margin-top: 20px;
}

.ocr-result h4 {
  margin-bottom: 12px;
  color: #303133;
}

.ocr-table {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow-x: auto;
}

.ocr-table-header {
  display: flex;
  background: #f5f7fa;
  font-weight: 500;
  font-size: 12px;
}

.ocr-table-row {
  display: flex;
  border-top: 1px solid #ebeef5;
  font-size: 12px;
}

.ocr-table-row.error {
  background: #fef0f0;
}

.ocr-col {
  flex: 1;
  padding: 8px;
  text-align: center;
  min-width: 80px;
}
</style>
