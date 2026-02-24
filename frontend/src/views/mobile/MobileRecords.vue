<template>
  <MobileLayout title="成交记录" :show-tab-bar="true">
    <!-- 策略选择 -->
    <div class="strategy-selector">
      <el-select 
        v-model="selectedStrategyId" 
        placeholder="选择策略"
        size="large"
        @change="loadRecords"
      >
        <el-option
          v-for="s in strategies"
          :key="s.id"
          :label="s.symbol"
          :value="s.id"
        />
      </el-select>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="loading-tip">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 空状态 -->
    <div v-else-if="records.length === 0" class="empty-state">
      <el-icon class="empty-icon"><Document /></el-icon>
      <p>暂无成交记录</p>
    </div>

    <!-- 记录列表 -->
    <div v-else class="record-list">
      <div 
        v-for="record in records" 
        :key="record.id" 
        class="record-item"
      >
        <div class="record-main">
          <el-tag 
            size="small" 
            :type="record.type === 'BUY' ? 'danger' : 'success'"
          >
            {{ record.type === 'BUY' ? '买入' : '卖出' }}
          </el-tag>
          <span class="record-price">¥{{ formatPrice(record.price) }}</span>
          <span class="record-grid">第{{ record.gridLineLevel }}格</span>
        </div>
        <div class="record-detail">
          <span class="record-amount">{{ formatAmount(record.amount) }}元</span>
          <span class="record-qty">{{ formatQty(record.quantity) }}股</span>
          <span class="record-time">{{ formatTime(record.tradeTime) }}</span>
        </div>
      </div>
    </div>
  </MobileLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Document } from '@element-plus/icons-vue'
import { getAllStrategies, getTradeRecords } from '../../api'
import MobileLayout from './MobileLayout.vue'

const strategies = ref([])
const selectedStrategyId = ref(null)
const records = ref([])
const loading = ref(false)

// 加载策略列表
const loadStrategies = async () => {
  try {
    const res = await getAllStrategies()
    strategies.value = res.data
    if (strategies.value.length > 0) {
      selectedStrategyId.value = strategies.value[0].id
      await loadRecords()
    }
  } catch (error) {
    console.error('加载策略失败:', error)
  }
}

// 加载成交记录
const loadRecords = async () => {
  if (!selectedStrategyId.value) return
  
  loading.value = true
  try {
    const res = await getTradeRecords(selectedStrategyId.value)
    records.value = res.data || []
  } catch (error) {
    console.error('加载记录失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

// 格式化
const formatPrice = (val) => val == null ? '-' : Number(val).toFixed(3)
const formatAmount = (val) => val == null ? '0' : Math.round(Number(val)).toString()
const formatQty = (val) => val == null ? '0' : Math.round(Number(val)).toString()
const formatTime = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  return `${d.getMonth()+1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2,'0')}`
}

onMounted(() => {
  loadStrategies()
})
</script>

<style scoped>
.strategy-selector {
  margin-bottom: 16px;
}

.strategy-selector .el-select {
  width: 100%;
}

.loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #909399;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #909399;
}

.empty-icon {
  font-size: 50px;
  margin-bottom: 12px;
  color: #dcdfe6;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.record-item {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

.record-main {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.record-price {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.record-grid {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.record-detail {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #606266;
}

.record-time {
  margin-left: auto;
  color: #c0c4cc;
}
</style>
