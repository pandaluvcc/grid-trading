<template>
  <div class="mobile-layout">
    <!-- 顶部导航栏 -->
    <header class="mobile-header">
      <div class="header-left">
        <el-icon v-if="showBack" class="back-btn" @click.stop="goBack"><ArrowLeft /></el-icon>
      </div>
      <h1 class="header-title">{{ title }}</h1>
      <div class="header-right">
        <slot name="header-right"></slot>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="mobile-main">
      <slot></slot>
    </main>

    <!-- 底部Tab栏（仅首页显示） -->
    <nav v-if="showTabBar" class="mobile-tabbar">
      <div 
        class="tab-item" 
        :class="{ active: activeTab === 'strategies' }"
        @click="switchTab('strategies')"
      >
        <el-icon><Grid /></el-icon>
        <span>我的网格</span>
      </div>
      <div 
        class="tab-item" 
        :class="{ active: activeTab === 'records' }"
        @click="switchTab('records')"
      >
        <el-icon><Document /></el-icon>
        <span>成交记录</span>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, Grid, Document } from '@element-plus/icons-vue'

const props = defineProps({
  title: {
    type: String,
    default: '网格交易'
  },
  showBack: {
    type: Boolean,
    default: false
  },
  showTabBar: {
    type: Boolean,
    default: true
  }
})

const router = useRouter()
const route = useRoute()

const activeTab = computed(() => {
  if (route.path.includes('/m/records')) return 'records'
  return 'strategies'
})

const goBack = () => {
  router.push('/m')
}

const switchTab = (tab) => {
  if (tab === 'strategies') {
    router.push('/m')
  } else if (tab === 'records') {
    router.push('/m/records')
  }
}
</script>

<style scoped>
.mobile-layout {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f5f6fa;
}

/* 顶部导航 */
.mobile-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 50px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.header-left, .header-right {
  width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-btn {
  font-size: 22px;
  color: #fff;
  cursor: pointer;
}

.header-title {
  flex: 1;
  text-align: center;
  font-size: 17px;
  font-weight: 600;
  color: #fff;
  margin: 0;
}

/* 主内容区 */
.mobile-main {
  flex: 1;
  margin-top: 50px;
  margin-bottom: 60px;
  padding: 12px;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

/* 底部Tab栏 */
.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: #fff;
  display: flex;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.08);
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #909399;
  font-size: 11px;
  cursor: pointer;
  transition: color 0.2s;
}

.tab-item .el-icon {
  font-size: 22px;
}

.tab-item.active {
  color: #667eea;
}

.tab-item.active .el-icon {
  color: #667eea;
}
</style>
