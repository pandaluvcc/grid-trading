import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getAllStrategies } from '@/services/strategy'

export const useStrategyStore = defineStore('strategy', () => {
  const strategies = ref([])
  const loading = ref(false)

  // 计算属性
  const totalMarketValue = computed(() => {
    return strategies.value.reduce((sum, s) => {
      return sum + (s.marketValue || s.position * (s.lastPrice || s.basePrice || 0))
    }, 0)
  })

  const totalPositionProfit = computed(() => {
    return strategies.value.reduce((sum, s) => sum + (s.positionProfit || 0), 0)
  })

  const todayProfit = computed(() => {
    return strategies.value.reduce((sum, s) => sum + (s.todayProfit || 0), 0)
  })

  const runningStrategies = computed(() => {
    return strategies.value.filter((s) => s.status === 'RUNNING')
  })

  const stoppedStrategies = computed(() => {
    return strategies.value.filter((s) => s.status !== 'RUNNING')
  })

  // 获取策略列表
  const fetchStrategies = async () => {
    loading.value = true
    try {
      const res = await getAllStrategies()
      strategies.value = res.data || []
      return res.data
    } catch (error) {
      console.error('获取策略列表失败:', error)
      throw error
    } finally {
      loading.value = false
    }
  }

  // 更新单个策略价格
  const updateStrategyPrice = (strategyId, price) => {
    const strategy = strategies.value.find((s) => s.id === strategyId)
    if (strategy) {
      strategy.lastPrice = price
      strategy.marketValue = strategy.position * price
    }
  }

  // 重置状态
  const reset = () => {
    strategies.value = []
    loading.value = false
  }

  return {
    strategies,
    loading,
    totalMarketValue,
    totalPositionProfit,
    todayProfit,
    runningStrategies,
    stoppedStrategies,
    fetchStrategies,
    updateStrategyPrice,
    reset
  }
})
