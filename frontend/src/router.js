import { createRouter, createWebHistory } from 'vue-router'
import StrategyList from './views/StrategyList.vue'
import StrategyCreate from './views/StrategyCreate.vue'
import StrategyDetail from './views/StrategyDetail.vue'

const routes = [
  {
    path: '/',
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
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
