<template>
  <div class="suggestion-card" :class="[suggestion.type.toLowerCase()]">
    <div class="card-header">
      <span class="action-icon">
        {{ suggestion.type === 'BUY' ? '📥' : '📤' }}
      </span>
      <span class="action-text">
        {{ suggestion.type === 'BUY' ? '买入' : '卖出' }}第{{ suggestion.gridLevel }}网
      </span>
      <el-tag size="small" :type="getGridTypeTag(suggestion.gridType)">
        {{ getGridTypeName(suggestion.gridType) }}
      </el-tag>
    </div>
    
    <div class="card-body">
      <div class="info-row">
        <span>价格：¥{{ formatPrice(suggestion.price) }}</span>
        <span>数量：{{ formatQuantity(suggestion.quantity) }}股</span>
      </div>
      <div class="info-row">
        <span>金额：¥{{ formatAmount(suggestion.amount) }}</span>
      </div>
      <div class="risk-warning" v-if="suggestion.riskWarning">
        ⚠️ {{ suggestion.riskWarning }}
      </div>
      <div class="reason" v-if="suggestion.reason">
        {{ suggestion.reason }}
      </div>
      <div class="suggestion-amount" v-if="suggestion.type === 'SELL' && suggestion.expectedProfit">
        预期收益: <span class="profit">+¥{{ formatPrice(suggestion.expectedProfit) }}</span>
      </div>
    </div>
    
    <div class="card-footer">
      <el-button 
        size="small" 
        :type="suggestion.type === 'BUY' ? 'danger' : 'success'"
        @click="$emit('execute', suggestion)"
      >
        {{ suggestion.type === 'BUY' ? '执行买入' : '执行卖出' }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  suggestion: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['viewGrid', 'execute'])

const formatPrice = (value) => {
  if (value === null || value === undefined) return '0.00'
  return parseFloat(value).toFixed(3)
}

const formatQuantity = (value) => {
  if (value === null || value === undefined) return '0'
  return Math.round(Number(value)).toString()
}

const formatAmount = (value) => {
  if (value === null || value === undefined) return '0'
  return Math.round(Number(value)).toString()
}

const getGridTypeTag = (type) => {
  const map = {
    'SMALL': '',
    'MEDIUM': 'warning',
    'LARGE': 'danger'
  }
  return map[type] || ''
}

const getGridTypeName = (type) => {
  const map = {
    'SMALL': '小网',
    'MEDIUM': '中网',
    'LARGE': '大网'
  }
  return map[type] || type
}
</script>

<style scoped>
.suggestion-card {
  background: #f8f9fa;
  border-radius: 12px;
  padding: 16px;
  border-left: 4px solid #409eff;
  transition: all 0.3s;
  overflow: hidden;
  position: relative;
  z-index: 1;
}

.suggestion-card.buy {
  border-left-color: #67c23a;
}

.suggestion-card.sell {
  border-left-color: #f56c6c;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.action-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.action-text {
  flex: 1;
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #606266;
}

.ratio-tag {
  padding: 2px 8px;
  background: #fff3cd;
  color: #856404;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.risk-warning {
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 6px 10px;
  border-radius: 4px;
  margin-top: 4px;
}

.reason {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

.suggestion-amount {
  font-size: 13px;
  color: #909399;
}

.suggestion-amount .profit {
  color: #67c23a;
  font-weight: bold;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
}
</style>
