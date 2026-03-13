<template>
  <el-dialog v-model="visible" title="确认交易执行" width="90%" @close="handleClose">
    <div v-if="suggestion" class="execute-confirm">
      <el-alert
        type="warning"
        :title="alertTitle"
        :description="alertDescription"
        show-icon
        style="margin-bottom: 16px"
      />
      <el-form label-width="100px">
        <el-form-item label="交易类型">
          <el-tag :type="suggestion.type === 'SELL' ? 'success' : 'danger'">
            {{ suggestion.type === 'BUY' ? '买入' : '卖出' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="交易价格">
          <span>¥{{ formatPrice(suggestion.price) }}</span>
        </el-form-item>
        <el-form-item label="交易数量">
          <span>{{ formatQuantity(suggestion.quantity) }}股</span>
        </el-form-item>
        <el-form-item label="预计金额">
          <span>¥{{ formatAmount(suggestion.amount) }}元</span>
        </el-form-item>
        <el-form-item label="手续费">
          <el-input v-model="feeInput" type="number" placeholder="可选，录入实际手续费">
            <template #prefix>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="交易时间">
          <el-date-picker
            v-model="tradeTime"
            type="datetime"
            placeholder="可选，默认当前时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="executing" @click="handleConfirm"> 确认执行 </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { formatPrice, formatQuantity, formatAmount, formatTime } from '@/utils/format'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  suggestion: {
    type: Object,
    default: null
  },
  executing: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const feeInput = ref('')
const tradeTime = ref('')

const alertTitle = computed(() => {
  return props.suggestion?.type === 'BUY' ? '确认买入' : '确认卖出'
})

const alertDescription = computed(() => {
  if (!props.suggestion) return ''
  return `将${props.suggestion.type === 'BUY' ? '买入' : '卖出'} ${formatQuantity(props.suggestion.quantity)} 股，价格 ${formatPrice(props.suggestion.price)}元`
})

const handleClose = () => {
  feeInput.value = ''
  tradeTime.value = ''
  emit('update:modelValue', false)
}

const handleConfirm = () => {
  const data = {
    gridLineId: props.suggestion?.gridLineId,
    type: props.suggestion?.type,
    price: props.suggestion?.price,
    quantity: props.suggestion?.quantity
  }
  if (feeInput.value) {
    data.fee = Number(feeInput.value)
  }
  if (tradeTime.value) {
    data.tradeTime = tradeTime.value
  }
  emit('confirm', data)
}
</script>

<style scoped>
.execute-confirm {
  padding: 16px 0;
}
</style>
