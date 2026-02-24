<template>
  <div class="trade-record-table">
    <h3>成交记录</h3>
    <el-table
      :data="tradeRecords"
      border
      stripe
      v-loading="loading"
    >
      <el-table-column prop="tradeTime" label="成交时间" width="180">
        <template #default="scope">
          {{ formatDate(scope.row.tradeTime) }}
        </template>
      </el-table-column>

      <el-table-column prop="type" label="类型" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.type === 'BUY' ? 'danger' : 'success'" size="small">
            {{ scope.row.type === 'BUY' ? '买入' : '卖出' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="price" label="成交价" width="120" align="right">
        <template #default="scope">
          {{ formatPrice(scope.row.price) }}
        </template>
      </el-table-column>

      <el-table-column prop="quantity" label="成交数量" width="140" align="right">
        <template #default="scope">
          {{ formatQuantity(scope.row.quantity) }}
        </template>
      </el-table-column>

      <el-table-column prop="amount" label="成交金额" width="120" align="right">
        <template #default="scope">
          {{ formatAmount(scope.row.amount) }}
        </template>
      </el-table-column>

      <el-table-column label="所属网格" min-width="180">
        <template #default="scope">
          {{ formatGridInfo(scope.row) }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!tradeRecords || tradeRecords.length === 0"
      description="暂无成交记录"
      :image-size="80"
    />
  </div>
</template>

<script setup>
import { defineProps } from 'vue'

defineProps({
  tradeRecords: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN')
}

// 格式化价格
const formatPrice = (value) => {
  if (value == null) return '-'
  return Number(value).toFixed(3)
}

// 格式化金额
const formatAmount = (value) => {
  if (value == null) return '-'
  return Number(value).toFixed(2)
}

// 格式化数量
const formatQuantity = (value) => {
  if (value == null) return '-'
  return Number(value).toFixed(4)
}

// 格式化网格信息
const formatGridInfo = (record) => {
  // 这里只展示网格层级或 ID，不进行价格计算
  if (record.gridLineId) {
    return `网格 #${record.gridLineId}`
  }
  return '-'
}
</script>

<style scoped>
.trade-record-table {
  margin-top: 30px;
}

h3 {
  margin-bottom: 15px;
  font-size: 18px;
}
</style>
