import { createRouter, createWebHistory } from 'vue-router'
import StrategyList from './views/StrategyList.vue'
import StrategyCreate from './views/StrategyCreate.vue'
import StrategyDetail from './views/StrategyDetail.vue'

// 移动端页面（全新设计）
import MobileHome from './views/mobile/MobileHome.vue'
import MobileRecord from './views/mobile/MobileRecord.vue'
import MobileHistory from './views/mobile/MobileHistory.vue'
import MobileStrategyCreate from './views/mobile/MobileStrategyCreate.vue'
import MobileStrategyDetail from './views/mobile/MobileStrategyDetail.vue'

// 检测是否是移动设备
const isMobile = () => {
  return /Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent) 
    || window.innerWidth < 768
}

const routes = [
  // 根路由 - 自动跳转
  {
    path: '/',
    name: 'Home',
    redirect: () => isMobile() ? '/m' : '/pc'
  },
  
  // PC端路由
  {
    path: '/pc',
    name: 'StrategyList',
    component: StrategyList
  },
  {
    path: '/create',
    name: 'StrategyCreate',
    component: StrategyCreate
  },
  {
    path: '/strategy/:id',
    name: 'StrategyDetail',
    component: StrategyDetail
  },

  // ========== 移动端 H5 路由（手机优化版） ==========
  {
    path: '/m',
    name: 'MobileHome',
    component: MobileHome
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
