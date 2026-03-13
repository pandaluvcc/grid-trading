<template>
  <div class="header-area">
    <div class="header-top">
      <div class="greeting">我的网格</div>
      <div class="header-right">
        <el-button text class="message-center-btn" @click="$emit('go-message')">
          <el-badge v-if="messageCount > 0" :value="messageCount" class="message-badge">
            <el-icon class="bell-icon"><Bell /></el-icon>
          </el-badge>
          <el-icon v-else class="bell-icon"><Bell /></el-icon>
        </el-button>
        <el-button type="primary" size="small" @click="$emit('batch-update')" class="update-price-btn">
          <el-icon><RefreshRight /></el-icon>
          更新行情
        </el-button>
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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px 16px 60px;
  color: white;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.greeting {
  font-size: 20px;
  font-weight: bold;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.message-center-btn {
  color: white;
  padding: 0;
}

.bell-icon {
  font-size: 24px;
}

.message-badge {
  --el-badge-background-color: #f56c6c;
}

.update-price-btn {
  font-size: 12px;
  height: 32px;
}
</style>
