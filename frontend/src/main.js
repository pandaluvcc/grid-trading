import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Vant from 'vant'
import 'vant/lib/index.css'
import './styles/variables.css'
import './styles/reset.css'
import './styles/profit.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import CommonComponents from './components/common'

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(ElementPlus)
app.use(Vant)
app.use(router)
app.use(CommonComponents)

app.mount('#app')
