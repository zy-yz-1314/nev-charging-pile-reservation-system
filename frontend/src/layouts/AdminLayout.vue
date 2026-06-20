<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/user'
import { ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const menus = [
  { path: '/admin/dashboard', label: '数据看板', icon: 'DataLine' },
  { path: '/admin/stations', label: '充电站管理', icon: 'OfficeBuilding' },
  { path: '/admin/piles', label: '充电桩管理', icon: 'Cpu' },
  { path: '/admin/reservations', label: '订单管理', icon: 'Tickets' },
  { path: '/admin/users', label: '用户管理', icon: 'UserFilled' },
]

const currentTitle = computed(() => route.meta.title || '管理后台')

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
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="brand">
        <div class="logo"><el-icon :size="20"><Lightning /></el-icon></div>
        <div class="brand-text">
          <div class="brand-name">Power<span class="pq-gradient-text">Queue</span></div>
          <div class="brand-sub">管理控制台</div>
        </div>
      </div>

      <nav class="menu">
        <router-link
          v-for="m in menus"
          :key="m.path"
          :to="m.path"
          class="menu-item"
          :class="{ active: route.path === m.path }"
        >
          <el-icon :size="18"><component :is="m.icon" /></el-icon>
          <span>{{ m.label }}</span>
          <span class="bar" />
        </router-link>
      </nav>

      <div class="side-foot">
        <el-button text class="back-user" @click="router.push('/stations')">
          <el-icon><Back /></el-icon>&nbsp;返回车主端
        </el-button>
      </div>
    </aside>

    <div class="main">
      <header class="header pq-glass">
        <div class="title">
          <span class="dot" />
          {{ currentTitle }}
        </div>
        <el-dropdown trigger="click">
          <div class="user-chip">
            <el-avatar :size="30" class="avatar">{{ store.nickname.charAt(0) }}</el-avatar>
            <span>{{ store.nickname }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">
                <el-icon><SwitchButton /></el-icon>&nbsp;退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <section class="page">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </section>
    </div>
  </div>
</template>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
}
.sidebar {
  width: 234px;
  flex-shrink: 0;
  padding: 22px 16px;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--pq-border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent);
  position: sticky;
  top: 0;
  height: 100vh;
}
.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 4px 8px 22px;
}
.logo {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: #042018;
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  box-shadow: var(--pq-glow);
}
.brand-name {
  font-size: 18px;
  font-weight: 800;
}
.brand-sub {
  font-size: 11px;
  color: var(--pq-text-dim);
  letter-spacing: 2px;
}
.menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}
.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 12px;
  color: var(--pq-text-dim);
  font-size: 14px;
  font-weight: 600;
  transition: all 0.3s var(--pq-ease);
}
.menu-item:hover {
  color: var(--pq-text);
  background: var(--pq-surface);
}
.menu-item.active {
  color: var(--pq-primary);
  background: rgba(20, 224, 160, 0.1);
}
.menu-item .bar {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%) scaleY(0);
  width: 3px;
  height: 60%;
  border-radius: 3px;
  background: linear-gradient(var(--pq-primary), var(--pq-primary-2));
  transition: transform 0.3s var(--pq-ease);
}
.menu-item.active .bar {
  transform: translateY(-50%) scaleY(1);
}
.side-foot {
  padding-top: 14px;
  border-top: 1px solid var(--pq-border);
}
.back-user {
  color: var(--pq-text-dim);
  width: 100%;
  justify-content: flex-start;
}
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.header {
  margin: 16px 20px 0;
  height: 58px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--pq-primary);
  box-shadow: var(--pq-glow);
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
}
.avatar {
  background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2));
  color: #042018;
  font-weight: 800;
}
.page {
  padding: 22px 20px 50px;
  flex: 1;
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
</style>
