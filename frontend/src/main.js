import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 组件由 unplugin 按需导入(见 vite.config.js)。
// 这里仅:全量样式(体积小,规避按需样式坑) + 暗色变量 + 服务式 API 指令 + 按需图标。
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import { ElLoading } from 'element-plus'
import {
  Lightning,
  LocationFilled,
  Tickets,
  User,
  Setting,
  ArrowDown,
  Wallet,
  SwitchButton,
  DataLine,
  OfficeBuilding,
  Cpu,
  UserFilled,
  Back,
  ArrowRightBold,
  ArrowLeftBold,
  Search,
  Plus,
  Refresh,
  Money,
  Phone,
  Van,
  TrendCharts,
  Calendar,
  Lock,
} from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElLoading) // 注册 v-loading 指令

// 按需注册用到的图标(供模板 <component :is> 与字符串 :icon 使用)
const icons = {
  Lightning, LocationFilled, Tickets, User, Setting, ArrowDown, Wallet, SwitchButton,
  DataLine, OfficeBuilding, Cpu, UserFilled, Back, ArrowRightBold, ArrowLeftBold,
  Search, Plus, Refresh, Money, Phone, Van, TrendCharts, Calendar, Lock,
}
for (const [key, comp] of Object.entries(icons)) {
  app.component(key, comp)
}

app.mount('#app')
