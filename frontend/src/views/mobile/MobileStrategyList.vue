<template>
  <MobileLayout title="我的网格" :show-tab-bar="true">
    <!-- 下拉刷新提示 -->
    <div v-if="loading" class="loading-tip">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 空状态 -->
    <div v-if="!loading && strategies.length === 0" class="empty-state">
      <el-icon class="empty-icon"><Folder /></el-icon>
      <p>暂无策略</p>
      <p class="empty-hint">请在电脑端创建策略</p>
    </div>

    <!-- 策略卡片列表 -->
    <div class="strategy-cards">
      <div 
        v-for="item in strategies" 
        :key="item.id" 
        class="strategy-card"
        @click="goToDetail(item.id)"
      >
        <!-- 卡片头部 -->
        <div class="card-header">
          <div class="card-title">
            <span class="symbol">{{ item.symbol }}</span>
            <el-tag 
              size="small" 
              :type="item.status === 'RUNNING' ? 'success' : 'info'"
            >
              {{ item.status === 'RUNNING' ? '运行中' : '已停止' }}
            </el-tag>
          </div>
          <el-icon class="arrow-icon"><ArrowRight /></el-icon>
        </div>

        <!-- 卡片内容 -->
        <div class="card-body">
          <div class="info-row">
            <div class="info-item">
              <span class="label">基准价</span>
              <span class="value">{{ formatPrice(item.basePrice) }}</span>
            </div>
            <div class="info-item">
              <span class="label">单格金额</span>
              <span class="value">{{ formatAmount(item.amountPerGrid) }}</span>
            </div>
          </div>
          <div class="info-row">
            <div class="info-item">
              <span class="label">已实现收益</span>
              <span class="value profit" :class="{ negative: item.realizedProfit < 0 }">
                {{ formatProfit(item.realizedProfit) }}
              </span>
            </div>
            <div class="info-item">
              <span class="label">网格</span>
              <span class="value">19条固定</span>
            </div>
          </div>
        </div>

        <!-- 卡片底部 -->
        <div class="card-footer">
          <span class="time">{{ formatDate(item.createdAt) }}</span>
        </div>
      </div>
    </div>
  </MobileLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Loading, Folder } from '@element-plus/icons-vue'
import { getAllStrategies } from '../../api'
import MobileLayout from './MobileLayout.vue'

const router = useRouter()
const strategies = ref([])
const loading = ref(false)

// 加载策略列表
const loadStrategies = async () => {
  loading.value = true
  try {
    const response = await getAllStrategies()
    strategies.value = response.data
  } catch (error) {
    console.error('加载失败:', error)
    ElMessage.error('加载失败，请检查网络')
  } finally {
    loading.value = false
  }
}

// 进入详情
const goToDetail = (id) => {
  router.push(`/m/strategy/${id}`)
}

// 格式化
const formatPrice = (val) => {
  if (val == null) return '-'
  return Number(val).toFixed(3)
}

const formatAmount = (val) => {
  if (val == null) return '-'
  return '¥' + Number(val).toFixed(0)
}

const formatProfit = (val) => {
  if (val == null) return '-'
  const num = Number(val)
  const prefix = num >= 0 ? '+' : ''
  return prefix + num.toFixed(2)
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

onMounted(() => {
  loadStrategies()
})
</script>

<style scoped>
.loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
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
  font-size: 60px;
  margin-bottom: 16px;
  color: #dcdfe6;
}

.empty-hint {
  font-size: 13px;
  margin-top: 8px;
}

.strategy-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.strategy-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.strategy-card:active {
  transform: scale(0.98);
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.symbol {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.arrow-icon {
  color: #c0c4cc;
  font-size: 18px;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.info-row {
  display: flex;
  justify-content: space-between;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-item .label {
  font-size: 12px;
  color: #909399;
}

.info-item .value {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

.info-item .value.profit {
  color: #67c23a;
}

.info-item .value.profit.negative {
  color: #f56c6c;
}

.card-footer {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.card-footer .time {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
