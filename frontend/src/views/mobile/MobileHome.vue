<template>
  <MobileLayout title="我的网格" :show-back="false" :show-tab-bar="false">
    <!-- 头部区域 -->
    <HomeHeader
      :message-count="totalSuggestionsCount"
      :total-market-value="strategyStore.totalMarketValue"
      :total-position-profit="strategyStore.totalPositionProfit"
      :today-profit="strategyStore.todayProfit"
      @go-message="goToMessageCenter"
      @batch-update="showBatchUpdateDialog = true"
    />

    <!-- 布局切换按钮 -->
    <div class="layout-switcher">
      <div
        v-for="layout in layouts"
        :key="layout.id"
        class="layout-btn"
        :class="{ active: currentLayout === layout.id }"
        @click="currentLayout = layout.id"
      >
        {{ layout.name }}
      </div>
    </div>

    <!-- 策略列表 -->
    <div class="strategy-section">
      <div class="section-header">
        <span class="section-title">我的策略</span>
        <div class="section-actions">
          <span class="section-count">{{ strategyStore.strategies.length }}个</span>
          <span class="add-btn" @click="goToCreate">
            <el-icon><Plus /></el-icon>
          </span>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="strategyStore.strategies.length === 0" class="empty-state">
        <div class="empty-icon">📊</div>
        <div class="empty-text">暂无策略，点击右上角+创建</div>
      </div>

      <!-- 策略卡片列表 -->
      <div v-else class="strategy-list">
        <StrategyCard
          v-for="s in strategyStore.strategies"
          :key="s.id"
          :strategy="s"
          :layout="currentLayout"
          :suggestions="strategySuggestions[s.id]"
          :risks="getRisksForStrategy(s.id)"
          @click="goToDetail(s)"
        />
      </div>
    </div>

    <!-- 批量更新弹窗 -->
    <BatchUpdateDialog v-model="showBatchUpdateDialog" :strategies="strategyStore.strategies" @success="loadHomeData" />
  </MobileLayout>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useStrategyStore } from '@/stores/strategy'
import { useStrategySuggestions } from '@/composables/useStrategySuggestions'
import { useStrategyRisks } from '@/composables/useStrategyRisks'
import MobileLayout from './MobileLayout.vue'
import HomeHeader from './components/HomeHeader.vue'
import StrategyCard from './components/StrategyCard.vue'
import BatchUpdateDialog from './components/BatchUpdateDialog.vue'

const router = useRouter()
const strategyStore = useStrategyStore()
const { strategySuggestions, totalSuggestionsCount, fetchSuggestions } = useStrategySuggestions()
const { strategyRisks, getRisksForStrategy, fetchRisks } = useStrategyRisks()

const currentLayout = ref('compact')
const showBatchUpdateDialog = ref(false)

const layouts = [
  { id: 'compact', name: '紧凑' },
  { id: 'detailed', name: '详细' }
]

onMounted(() => {
  loadHomeData()
})

const loadHomeData = async () => {
  try {
    await strategyStore.fetchStrategies()
    const strategyIds = strategyStore.strategies.map((s) => s.id)
    // 同时获取建议和风险数据
    await Promise.all([fetchSuggestions(strategyIds), fetchRisks(strategyIds)])
  } catch (e) {
    ElMessage.error('加载失败：' + (e.response?.data?.message || e.message))
  }
}

const goToMessageCenter = () => {
  router.push('/m/messages')
}

const goToCreate = () => {
  router.push('/m/create')
}

const goToDetail = (strategy) => {
  router.push(`/m/strategy/${strategy.id}`)
}
</script>

<style scoped>
.layout-switcher {
  display: flex;
  background: white;
  margin: -30px 16px 16px;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  position: relative;
  z-index: 10;
}

.layout-btn {
  flex: 1;
  text-align: center;
  padding: 12px 0;
  font-size: 14px;
  color: #606266;
  cursor: pointer;
  transition: all 0.3s;
}

.layout-btn.active {
  background: #409eff;
  color: white;
  font-weight: 500;
}

.strategy-section {
  padding: 0 16px 20px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-count {
  font-size: 14px;
  color: #909399;
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: #409eff;
  color: white;
  border-radius: 50%;
  cursor: pointer;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
}

.empty-icon {
  font-size: 64px;
  margin-bottom: 16px;
}

.empty-text {
  font-size: 14px;
  color: #909399;
}
</style>
