<template>
  <el-dialog v-model="visible" title="录入手续费" width="90%" @close="handleClose">
    <div class="fee-edit-form">
      <el-form label-width="80px">
        <el-form-item label="交易类型">
          <span>{{ currentRecord.type === 'SELL' ? '卖出' : '买入' }}</span>
        </el-form-item>
        <el-form-item label="交易价格">
          <span>¥{{ formatPrice(currentRecord.price) }}</span>
        </el-form-item>
        <el-form-item label="交易数量">
          <span>{{ formatQuantity(currentRecord.quantity) }}股</span>
        </el-form-item>
        <el-form-item label="手续费" prop="fee">
          <el-input v-model="feeInput" type="number" placeholder="请输入手续费" size="large">
            <template #prefix>¥</template>
          </el-input>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave"> 保存 </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { formatPrice, formatQuantity } from '@/utils/format'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  currentRecord: {
    type: Object,
    default: null
  },
  saving: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'save'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const feeInput = ref('')

watch(
  () => props.currentRecord,
  (newVal) => {
    if (newVal) {
      feeInput.value = newVal.fee ? String(newVal.fee) : ''
    }
  },
  { immediate: true }
)

const handleClose = () => {
  feeInput.value = ''
  emit('update:modelValue', false)
}

const handleSave = () => {
  if (!feeInput.value || isNaN(Number(feeInput.value))) {
    ElMessage.warning('请输入有效的手续费')
    return
  }
  emit('save', Number(feeInput.value))
}
</script>

<style scoped>
.fee-edit-form {
  padding: 16px 0;
}
</style>
