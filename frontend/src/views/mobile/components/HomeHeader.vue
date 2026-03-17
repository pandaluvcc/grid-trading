<template>
  <div class="header-area">
    <div class="header-top">
      <div class="greeting">我的网格</div>
      <div class="header-right">
        <div class="icon-btn" @click="$emit('go-message')">
          <el-badge v-if="messageCount > 0" :value="messageCount" class="message-badge">
            <el-icon class="header-icon"><Bell /></el-icon>
          </el-badge>
          <el-icon v-else class="header-icon"><Bell /></el-icon>
        </div>
        <div class="icon-btn" @click="$emit('batch-update')">
          <el-icon class="header-icon"><RefreshRight /></el-icon>
        </div>
      </div>
    </div>

    <!-- 收益卡片 -->
    <ProfitCard
      :total-market-value="totalMarketValue"
      :total-position-profit="totalPositionProfit"
      :today-profit="todayProfit"
    />
  </div>
</template>

<script setup>
import { Bell, RefreshRight } from '@element-plus/icons-vue'
import ProfitCard from './ProfitCard.vue'

defineProps({
  messageCount: {
    type: Number,
    default: 0
  },
  totalMarketValue: {
    type: [Number, String],
    default: 0
  },
  totalPositionProfit: {
    type: [Number, String],
    default: 0
  },
  todayProfit: {
    type: [Number, String],
    default: 0
  }
})

defineEmits(['go-message', 'batch-update'])
</script>

<style scoped>
.header-area {
  background: var(--bg-card);
  padding: 16px 16px 24px;
  border-radius: 0 0 20px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  transition: background-color var(--transition-base);
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-top: env(safe-area-inset-top);
}

.greeting {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-light);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.icon-btn:active {
  transform: scale(0.95);
  background: var(--border-light);
}

.header-icon {
  font-size: 20px;
  color: var(--text-primary);
}

.message-badge {
  --el-badge-background-color: var(--danger-color);
}
</style>
