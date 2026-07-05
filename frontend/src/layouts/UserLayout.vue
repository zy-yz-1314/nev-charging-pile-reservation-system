<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessageBox, ElMessage } from 'element-plus'
import { ws } from '@/utils/websocket'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

let unsubTurn = null
let unsubRemind = null
onMounted(() => {
  ws.connect()
  // === 全局 WS 监听(所有车主页面均生效) ===
  // 轮到你了(排队轮换)
  unsubTurn = ws.on('QUEUE_TURN', (msg) => {
    ElMessage({ message: msg.message || '轮到你了!请在10分钟内确认占位', type: 'success', duration: 10000, showClose: true })
  })
  // 充电计划提醒(自动预约结果/漏充提醒)
  unsubRemind = ws.on('PLAN_REMIND', (msg) => {
    ElMessage({ message: msg.message || '充电计划提醒', type: 'warning', duration: 8000, showClose: true })
  })
})
onUnmounted(() => {
  if (unsubTurn) unsubTurn()
  if (unsubRemind) unsubRemind()
})

const navs = [
  { path: '/smart-charge', label: '智能充电', icon: 'Cpu' },
  { path: '/stations', label: '充电地图', icon: 'LocationFilled' },
  { path: '/ai-assistant', label: 'AI 助手', icon: 'ChatDotRound' },
  { path: '/charge-plans', label: '充电计划', icon: 'Calendar' },
  { path: '/reservations', label: '我的预约', icon: 'Tickets' },
  { path: '/profile', label: '个人中心', icon: 'User' },
]

const isActive = (path) => route.path === path || route.path.startsWith(path + '/')

const logout = async () => {
  try {
    await ElMessageBox.confirm('确认退出登录?', '提示', { type: 'warning' })
    store.logout()
    router.push('/login')
  } catch (_) {
    /* 取消 */
  }
}
</script>

<template>
  <div class="user-layout">
    <header class="topbar">
      <div class="inner pq-glass">
        <div class="brand" @click="router.push('/stations')">
          <div class="logo"><el-icon :size="20"><Lightning /></el-icon></div>
          <span class="brand-name">Power<span class="pq-gradient-text">Queue</span></span>
        </div>

        <nav class="nav">
          <router-link
            v-for="n in navs"
            :key="n.path"
            :to="n.path"
            class="nav-item"
            :class="{ active: isActive(n.path) }"
          >
            <el-icon><component :is="n.icon" /></el-icon>
            <span>{{ n.label }}</span>
          </router-link>
        </nav>

        <div class="right">
          <el-button v-if="store.isAdmin" text class="admin-entry" @click="router.push('/admin')">
            <el-icon><Setting /></el-icon>&nbsp;管理后台
          </el-button>
          <el-dropdown trigger="click">
            <div class="user-chip">
              <el-avatar :size="30" class="avatar">{{ store.nickname.charAt(0) }}</el-avatar>
              <span class="uname">{{ store.nickname }}</span>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">
                  <el-icon><Wallet /></el-icon>&nbsp;余额 ¥{{ store.user?.balance ?? 0 }}
                </el-dropdown-item>
                <el-dropdown-item divided @click="logout">
                  <el-icon><SwitchButton /></el-icon>&nbsp;退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>

    <main class="content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.user-layout {
  min-height: 100vh;
}
.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  padding: 14px 24px 0;
}
.inner {
  max-width: 1280px;
  margin: 0 auto;
  height: 60px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  gap: 28px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}
.logo {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: #042018;
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  box-shadow: var(--pq-glow);
}
.brand-name {
  font-size: 19px;
  font-weight: 800;
  letter-spacing: 0.3px;
}
.nav {
  display: flex;
  gap: 6px;
  flex: 1;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 9px 16px;
  border-radius: 11px;
  color: var(--pq-text-dim);
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s var(--pq-ease);
}
.nav-item:hover {
  color: var(--pq-text);
  background: var(--pq-surface);
}
.nav-item.active {
  color: var(--pq-primary);
  background: rgba(20, 224, 160, 0.1);
  box-shadow: inset 0 0 0 1px rgba(20, 224, 160, 0.25);
}
.right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.admin-entry {
  color: var(--pq-text-dim);
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px 5px 5px;
  border-radius: 999px;
  cursor: pointer;
  background: var(--pq-surface);
  border: 1px solid var(--pq-border);
  transition: border-color 0.3s;
}
.user-chip:hover {
  border-color: var(--pq-border-strong);
}
.avatar {
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  color: #042018;
  font-weight: 800;
}
.uname {
  font-size: 13px;
  font-weight: 600;
}
.content {
  max-width: 1280px;
  margin: 0 auto;
  padding: 28px 24px 60px;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.28s var(--pq-ease), transform 0.28s var(--pq-ease);
}
.fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.fade-leave-to {
  opacity: 0;
}
@media (max-width: 720px) {
  .nav-item span {
    display: none;
  }
  .uname {
    display: none;
  }
}
</style>
