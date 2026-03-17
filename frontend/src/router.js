import { createRouter, createWebHistory } from 'vue-router'

// 移动端页面（全新设计）
import MobileHome from './views/mobile/MobileHome.vue'
import MobileRecord from './views/mobile/MobileRecord.vue'
import MobileHistory from './views/mobile/MobileHistory.vue'
import MobileStrategyCreate from './views/mobile/MobileStrategyCreate.vue'
import MobileStrategyDetail from './views/mobile/MobileStrategyDetail.vue'
import MobileMessageCenter from './views/mobile/MobileMessageCenter.vue'

// PC端页面
import ApkFolderGenerator from './views/pc/ApkFolderGenerator.vue'

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
    component: MobileHome,
    meta: { transition: 'page-fade' }
  },
  {
    path: '/m/messages',
    name: 'MobileMessageCenter',
    component: MobileMessageCenter,
    meta: { transition: 'page-slide' }
  },
  {
    path: '/m/record',
    name: 'MobileRecord',
    component: MobileRecord,
    meta: { transition: 'page-fade' }
  },
  {
    path: '/m/history',
    name: 'MobileHistory',
    component: MobileHistory,
    meta: { transition: 'page-fade' }
  },
  {
    path: '/m/create',
    name: 'MobileStrategyCreate',
    component: MobileStrategyCreate,
    meta: { transition: 'page-scale' }
  },
  {
    path: '/m/strategy/:id',
    name: 'MobileStrategyDetail',
    component: MobileStrategyDetail,
    meta: { transition: 'page-slide' }
  },

  // ========== PC端工具路由 ==========
  {
    path: '/pc/apk-generator',
    name: 'ApkFolderGenerator',
    component: ApkFolderGenerator,
    meta: { transition: 'page-fade' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
