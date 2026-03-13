<template>
  <el-dialog v-model="visible" title="批量更新行情" width="90%" @close="handleClose">
    <div class="update-list">
      <div v-for="strategy in strategies" :key="strategy.id" class="update-item">
        <div class="strategy-info">
          <div class="strategy-name">{{ strategy.name || strategy.symbol }}</div>
          <div class="strategy-price">当前: ¥{{ formatPrice(strategy.lastPrice || strategy.basePrice) }}</div>
        </div>
        <div class="price-input-wrapper">
          <el-input
            v-model="priceMap[strategy.id]"
            type="number"
            placeholder="输入最新价格"
            size="small"
            style="width: 120px"
          >
            <template #prefix>¥</template>
          </el-input>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="updating" @click="handleUpdate"> 批量更新 </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { updateStrategyLastPrice } from '@/services/strategy'
import { formatPrice } from '@/utils/format'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  strategies: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'success'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const priceMap = ref({})
const updating = ref(false)

watch(
  () => props.strategies,
  (newVal) => {
    priceMap.value = {}
    newVal.forEach((s) => {
      priceMap.value[s.id] = ''
    })
  },
  { immediate: true }
)

const handleClose = () => {
  Object.keys(priceMap.value).forEach((key) => {
    priceMap.value[key] = ''
  })
  emit('update:modelValue', false)
}

const handleUpdate = async () => {
  const updates = []
  Object.entries(priceMap.value).forEach(([strategyId, price]) => {
    if (price && !isNaN(Number(price))) {
      updates.push({ id: Number(strategyId), price: Number(price) })
    }
  })

  if (updates.length === 0) {
    ElMessage.warning('请至少输入一个有效价格')
    return
  }

  updating.value = true
  try {
    await Promise.all(updates.map((item) => updateStrategyLastPrice(item.id, item.price)))
    ElMessage.success(`成功更新 ${updates.length} 个策略的行情`)
    emit('success')
    handleClose()
  } catch (e) {
    ElMessage.error('更新失败：' + (e.response?.data?.message || e.message))
  } finally {
    updating.value = false
  }
}
</script>

<style scoped>
.update-list {
  max-height: 400px;
  overflow-y: auto;
  padding: 16px 0;
}

.update-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.update-item:last-child {
  border-bottom: none;
}

.strategy-info {
  flex: 1;
}

.strategy-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
}

.strategy-price {
  font-size: 12px;
  color: #909399;
}
</style>
