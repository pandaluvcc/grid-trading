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
        <div class="empty-icon">
          <el-icon :size="64" color="#c0c4cc"><DataLine /></el-icon>
        </div>
        <div class="empty-text">暂无策略，点击右上角+创建</div>
      </div>

      <!-- 策略卡片列表 -->
      <div v-else class="strategy-list">
        <StrategyCard
          v-for="s in strategyStore.strategies"
          :key="s.id"
          :strategy="s"
          :suggestions="strategySuggestions[s.id]"
          :risks="getRisksForStrategy(s.id)"
          @click="goToDetail(s)"
          @deleted="handleStrategyDeleted"
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
import { Plus, DataLine } from '@element-plus/icons-vue'
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

const showBatchUpdateDialog = ref(false)

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

// 处理策略删除
const handleStrategyDeleted = (strategyId) => {
  // 从 store 中移除
  strategyStore.strategies = strategyStore.strategies.filter(s => s.id !== strategyId)
}
</script>

<style scoped>
.strategy-section {
  padding: 20px 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-count {
  font-size: 14px;
  color: var(--text-secondary);
}

.add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: var(--primary-color);
  color: white;
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.15s ease, opacity 0.15s ease;
}

.add-btn:active {
  transform: scale(0.95);
  opacity: 0.9;
}

.empty-state {
  text-align: center;
  padding: 60px 0;
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-text {
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
