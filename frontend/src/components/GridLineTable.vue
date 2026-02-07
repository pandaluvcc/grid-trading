<template>
  <div class="grid-line-table">
    <div class="table-header">
      <h3>网格计划表（固定19条）- v2.1 增强版</h3>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #title>
          <strong>风控提示：</strong>本策略固定生成 19 条网格。当价格触达最下方网格后，将不再继续买入，仅等待卖出信号。
        </template>
      </el-alert>
    </div>

    <el-table
      :data="displayGridLines"
      border
      stripe
      :row-class-name="getRowClassName"
      v-loading="loading"
      empty-text="请先创建策略并生成网格计划"
    >
      <!-- 1. 序号 -->
      <el-table-column prop="level" label="序号" width="70" align="center" fixed>
        <template #default="scope">
          <span style="font-weight: 500">{{ scope.row.level }}</span>
        </template>
      </el-table-column>

      <!-- 2. 网格类型 -->
      <el-table-column prop="gridType" label="网格类型" width="100" align="center">
        <template #default="scope">
          <el-tag
            :type="getGridTypeTag(scope.row.gridType)"
            size="default"
          >
            {{ formatGridType(scope.row.gridType) }}
          </el-tag>
        </template>
      </el-table-column>

      <!-- 3. 买入触发价（可编辑） -->
      <el-table-column prop="buyTriggerPrice" label="买入触发价" width="130" align="right">
        <template #header>
          <el-tooltip content="触发价用于策略监听，不等于实际成交价" placement="top">
            <span>买入触发价 <el-icon><InfoFilled /></el-icon></span>
          </el-tooltip>
        </template>
        <template #default="scope">
          <el-input-number
            v-model="scope.row.buyTriggerPrice"
            :precision="3"
            :step="0.001"
            :min="0.001"
            size="small"
            :class="{ 'edited-field': scope.row._buyTriggerEdited }"
            @change="handleFieldEdit(scope.row, 'buyTrigger')"
            style="width: 110px"
          />
        </template>
      </el-table-column>

      <!-- 4. 买入价（可编辑） -->
      <el-table-column prop="buyPrice" label="买入价" width="130" align="right">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.buyPrice"
            :precision="3"
            :step="0.001"
            :min="0.001"
            size="small"
            :class="{ 'edited-field': scope.row._buyPriceEdited }"
            @change="handleFieldEdit(scope.row, 'buyPrice')"
            style="width: 110px"
          />
        </template>
      </el-table-column>

      <!-- 5. 卖出触发价（可编辑） -->
      <el-table-column prop="sellTriggerPrice" label="卖出触发价" width="130" align="right">
        <template #header>
          <el-tooltip content="触发价用于策略监听，不等于实际成交价" placement="top">
            <span>卖出触发价 <el-icon><InfoFilled /></el-icon></span>
          </el-tooltip>
        </template>
        <template #default="scope">
          <el-input-number
            v-model="scope.row.sellTriggerPrice"
            :precision="3"
            :step="0.001"
            :min="0.001"
            size="small"
            :class="{ 'edited-field': scope.row._sellTriggerEdited }"
            @change="handleFieldEdit(scope.row, 'sellTrigger')"
            style="width: 110px"
          />
        </template>
      </el-table-column>

      <!-- 6. 卖出价（可编辑） -->
      <el-table-column prop="sellPrice" label="卖出价" width="130" align="right">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.sellPrice"
            :precision="3"
            :step="0.001"
            :min="0.001"
            size="small"
            :class="{ 'edited-field': scope.row._sellPriceEdited }"
            @change="handleFieldEdit(scope.row, 'sellPrice')"
            style="width: 110px"
          />
        </template>
      </el-table-column>

      <!-- 7. 买入金额（只读） -->
      <el-table-column prop="buyAmount" label="买入金额" width="110" align="right">
        <template #default="scope">
          <span class="readonly-field">{{ formatAmount(scope.row.buyAmount) }}</span>
        </template>
      </el-table-column>

      <!-- 8. 买入数量（只读） -->
      <el-table-column prop="quantity" label="买入数量" width="110" align="right">
        <template #default="scope">
          <span class="readonly-field quantity-text">{{ formatQuantity(scope.row.quantity) }}</span>
        </template>
      </el-table-column>

      <!-- 9. 卖出金额（只读） -->
      <el-table-column prop="sellAmount" label="卖出金额" width="110" align="right">
        <template #default="scope">
          <span class="readonly-field">{{ formatAmount(scope.row.sellAmount) }}</span>
        </template>
      </el-table-column>

      <!-- 10. 预期收益（只读） -->
      <el-table-column prop="profit" label="预期收益" width="110" align="right">
        <template #default="scope">
          <span class="readonly-field profit-text">{{ formatAmount(scope.row.profit) }}</span>
        </template>
      </el-table-column>

      <!-- 11. 收益率（只读） -->
      <el-table-column prop="profitRate" label="收益率" width="100" align="right">
        <template #default="scope">
          <span class="readonly-field profit-text">{{ formatPercent(scope.row.profitRate) }}</span>
        </template>
      </el-table-column>

      <!-- 12. 当前状态（只读） -->
      <el-table-column prop="state" label="当前状态" width="110" align="center">
        <template #default="scope">
          <el-tag :type="getStateTag(scope.row.state)" size="small">
            {{ formatState(scope.row.state) }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-footer">
      <el-text type="info" size="small">
        <el-icon><InfoFilled /></el-icon>
        v2.1 增强版：支持手动编辑价格字段，前端实时计算展示。被编辑的字段将显示浅黄色背景。
      </el-text>
    </div>
  </div>
</template>

<script setup>
import { defineProps, computed, ref } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  gridLines: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

// 创建可编辑的展示数据（深拷贝）
const displayGridLines = computed(() => {
  return props.gridLines.map(line => ({
    ...line,
    // 初始化触发价（如果后端没有，使用计算值）
    buyTriggerPrice: line.buyTriggerPrice || (line.buyPrice + 0.002),
    sellTriggerPrice: line.sellTriggerPrice || (line.sellPrice - 0.002),
    // 编辑标记
    _buyTriggerEdited: false,
    _buyPriceEdited: false,
    _sellTriggerEdited: false,
    _sellPriceEdited: false
  }))
})

// 处理字段编辑（前端实时计算）
const handleFieldEdit = (row, fieldType) => {
  // 标记字段被编辑过
  if (fieldType === 'buyTrigger') {
    row._buyTriggerEdited = true
  } else if (fieldType === 'buyPrice') {
    row._buyPriceEdited = true
    // 买入价变化，重新计算相关字段
    recalculateRow(row)
  } else if (fieldType === 'sellTrigger') {
    row._sellTriggerEdited = true
  } else if (fieldType === 'sellPrice') {
    row._sellPriceEdited = true
    // 卖出价变化，重新计算相关字段
    recalculateRow(row)
  }
}

// 重新计算单行数据（前端计算）
const recalculateRow = (row) => {
  if (!row.buyPrice || !row.sellPrice || !row.buyAmount) {
    return
  }
  
  // 买入数量 = 买入金额 ÷ 买入价
  row.quantity = (row.buyAmount / row.buyPrice).toFixed(2)
  
  // 卖出金额 = 买入数量 × 卖出价
  row.sellAmount = (row.quantity * row.sellPrice).toFixed(2)
  
  // 预期收益 = 卖出金额 - 买入金额
  row.profit = (row.sellAmount - row.buyAmount).toFixed(2)
  
  // 收益率 = 预期收益 ÷ 买入金额
  row.profitRate = (row.profit / row.buyAmount).toFixed(6)
}

// 格式化网格类型
const formatGridType = (type) => {
  const typeMap = {
    SMALL: '小网 (5%)',
    MEDIUM: '中网 (15%)',
    LARGE: '大网 (30%)'
  }
  return typeMap[type] || type
}

// 网格类型标签颜色
const getGridTypeTag = (type) => {
  const tagMap = {
    SMALL: 'info',      // 蓝色
    MEDIUM: 'warning',  // 橙色
    LARGE: 'danger'     // 红色
  }
  return tagMap[type] || ''
}

// 格式化状态
const formatState = (state) => {
  const stateMap = {
    WAIT_BUY: '等待买入',
    BOUGHT: '已买入',
    WAIT_SELL: '等待卖出',
    SOLD: '已卖出'
  }
  return stateMap[state] || state
}

// 状态标签颜色
const getStateTag = (state) => {
  const tagMap = {
    WAIT_BUY: 'info',
    BOUGHT: 'primary',
    WAIT_SELL: 'warning',
    SOLD: 'success'
  }
  return tagMap[state] || ''
}

// 行样式（根据网格类型）
const getRowClassName = ({ row }) => {
  if (row.gridType === 'LARGE') {
    return 'large-grid-row'
  } else if (row.gridType === 'MEDIUM') {
    return 'medium-grid-row'
  }
  return 'small-grid-row'
}

// 格式化金额（整数）
const formatAmount = (value) => {
  if (value == null) return '-'
  return Math.round(Number(value)).toString()
}

// 格式化数量（整数）
const formatQuantity = (value) => {
  if (value == null) return '-'
  return Math.round(Number(value)).toString()
}

// 格式化百分比
const formatPercent = (value) => {
  if (value == null) return '-'
  return (Number(value) * 100).toFixed(2) + '%'
}
</script>

<style scoped>
.grid-line-table {
  margin-bottom: 30px;
}

.table-header {
  margin-bottom: 16px;
}

.table-header h3 {
  margin: 0 0 12px 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.table-footer {
  margin-top: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 只读字段样式 */
.readonly-field {
  font-family: 'Courier New', monospace;
  font-weight: 500;
  color: #606266;
}

.quantity-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}

.profit-text {
  color: #67c23a;
  font-weight: 600;
}

/* 被编辑过的字段高亮 */
:deep(.edited-field .el-input-number__decrease),
:deep(.edited-field .el-input-number__increase),
:deep(.edited-field .el-input__inner) {
  background-color: #fffbea !important;
  border-color: #f0c040 !important;
}

/* 可编辑输入框样式 */
:deep(.el-input-number) {
  width: 100%;
}

:deep(.el-input-number .el-input__inner) {
  text-align: right;
  font-family: 'Courier New', monospace;
  font-weight: 500;
}

/* 小网行样式 */
:deep(.small-grid-row) {
  background-color: #f0f9ff !important;
}

:deep(.small-grid-row:hover > td) {
  background-color: #e0f2fe !important;
}

/* 中网行样式 */
:deep(.medium-grid-row) {
  background-color: #fff9e6 !important;
}

:deep(.medium-grid-row:hover > td) {
  background-color: #fff3d1 !important;
}

/* 大网行样式 */
:deep(.large-grid-row) {
  background-color: #ffe6e6 !important;
}

:deep(.large-grid-row:hover > td) {
  background-color: #ffd1d1 !important;
}

/* 表格样式优化 */
:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th) {
  background-color: #f5f7fa;
  color: #606266;
  font-weight: 600;
}

:deep(.el-table td) {
  padding: 8px 0;
}

/* 表头 tooltip 图标 */
:deep(.el-table th .el-icon) {
  margin-left: 4px;
  color: #909399;
  cursor: help;
}
</style>
