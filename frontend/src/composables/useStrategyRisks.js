import { ref } from 'vue'

export function useStrategyRisks() {
  const strategyRisks = ref({})
  const loading = ref(false)

  const fetchRisks = async (strategyIds) => {
    if (!strategyIds || strategyIds.length === 0) {
      strategyRisks.value = {}
      return
    }

    loading.value = true
    try {
      // 风险接口暂未实现，返回空数组
      strategyIds.forEach((id) => {
        strategyRisks.value[id] = []
      })
    } catch (error) {
      console.error('获取策略风险失败:', error)
      // 失败时返回空风险
      strategyIds.forEach((id) => {
        strategyRisks.value[id] = []
      })
    } finally {
      loading.value = false
    }
  }

  const getRisksForStrategy = (strategyId) => {
    return strategyRisks.value[strategyId] || []
  }

  const clearRisks = () => {
    strategyRisks.value = {}
  }

  return {
    strategyRisks,
    loading,
    fetchRisks,
    getRisksForStrategy,
    clearRisks
  }
}
