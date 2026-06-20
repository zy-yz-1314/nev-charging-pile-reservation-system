import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { public: true },
  },
  {
    path: '/register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { public: true },
  },
  // 车主端
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    redirect: '/stations',
    children: [
      {
        path: 'stations',
        name: 'stations',
        component: () => import('@/views/user/Stations.vue'),
        meta: { title: '充电地图' },
      },
      {
        path: 'stations/:id',
        name: 'stationDetail',
        component: () => import('@/views/user/StationDetail.vue'),
        meta: { title: '站点详情' },
      },
      {
        path: 'reservations',
        name: 'myReservations',
        component: () => import('@/views/user/MyReservations.vue'),
        meta: { title: '我的预约' },
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/user/Profile.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },
  // 管理后台
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/admin/dashboard',
    meta: { admin: true },
    children: [
      {
        path: 'dashboard',
        component: () => import('@/views/admin/Dashboard.vue'),
        meta: { title: '数据看板' },
      },
      {
        path: 'stations',
        component: () => import('@/views/admin/StationManage.vue'),
        meta: { title: '充电站管理' },
      },
      {
        path: 'piles',
        component: () => import('@/views/admin/PileManage.vue'),
        meta: { title: '充电桩管理' },
      },
      {
        path: 'reservations',
        component: () => import('@/views/admin/ReservationManage.vue'),
        meta: { title: '订单管理' },
      },
      {
        path: 'users',
        component: () => import('@/views/admin/UserManage.vue'),
        meta: { title: '用户管理' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!store.isLogin) {
    return { path: '/login' }
  }
  if (to.path.startsWith('/admin') && !store.isAdmin) {
    return { path: '/stations' }
  }
  return true
})

export default router
