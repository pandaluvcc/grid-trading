import { createRouter, createWebHistory } from 'vue-router'

// 移动端页面（全新设计）
import MobileHome from './views/mobile/MobileHome.vue'
import MobileRecord from './views/mobile/MobileRecord.vue'
import MobileHistory from './views/mobile/MobileHistory.vue'
import MobileStrategyCreate from './views/mobile/MobileStrategyCreate.vue'
import MobileStrategyDetail from './views/mobile/MobileStrategyDetail.vue'
import MobileMessageCenter from './views/mobile/MobileMessageCenter.vue'

const routes = [
  // 根路由 - 直接跳转到移动端首页
  {
    path: '/',
    redirect: '/m'
  },

  // ========== 移动端 H5 路由（手机优化版） ==========
  {
    path: '/m',
    name: 'MobileHome',
    component: MobileHome
  },
  {
    path: '/m/messages',
    name: 'MobileMessageCenter',
    component: MobileMessageCenter
  },
  {
    path: '/m/record',
    name: 'MobileRecord',
    component: MobileRecord
  },
  {
    path: '/m/history',
    name: 'MobileHistory',
    component: MobileHistory
  },
  {
    path: '/m/create',
    name: 'MobileStrategyCreate',
    component: MobileStrategyCreate
  },
  {
    path: '/m/strategy/:id',
    name: 'MobileStrategyDetail',
    component: MobileStrategyDetail
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
