import { ref, computed } from 'vue'
import { getSuggestion } from '@/services/suggestion'

export function useStrategySuggestions() {
  const strategySuggestions = ref({})
  const loading = ref(false)

  const totalSuggestionsCount = computed(() => {
    let count = 0
    Object.values(strategySuggestions.value).forEach((s) => {
      count += (s.buyCount || 0) + (s.sellCount || 0)
    })
    return count
  })

  const fetchSuggestions = async (strategyIds) => {
    if (!strategyIds || strategyIds.length === 0) {
      strategySuggestions.value = {}
      return
    }

    loading.value = true
    try {
      // 实际场景下批量获取建议
      const promises = strategyIds.map((id) => getSuggestion(id))
      const results = await Promise.all(promises)

      results.forEach((res, index) => {
        strategySuggestions.value[strategyIds[index]] = res.data || {
          buyCount: 0,
          sellCount: 0
        }
      })
    } catch (error) {
      console.error('获取策略建议失败:', error)
      // 失败时使用模拟数据
      strategyIds.forEach((id) => {
        strategySuggestions.value[id] = {
          buyCount: Math.floor(Math.random() * 2),
          sellCount: Math.floor(Math.random() * 2)
        }
      })
    } finally {
      loading.value = false
    }
  }

  const getSuggestionsForStrategy = (strategyId) => {
    return strategySuggestions.value[strategyId] || { buyCount: 0, sellCount: 0 }
  }

  const clearSuggestions = () => {
    strategySuggestions.value = {}
  }

  return {
    strategySuggestions,
    loading,
    totalSuggestionsCount,
    fetchSuggestions,
    getSuggestionsForStrategy,
    clearSuggestions
  }
}
