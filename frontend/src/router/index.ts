import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    // Google 동의 후 돌아오는 곳. code → 백엔드 교환.
    path: '/auth/callback',
    name: 'auth-callback',
    component: () => import('@/views/AuthCallbackView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppShell.vue'),
    children: [
      { path: '', redirect: { name: 'notes' } },
      { path: 'notes', name: 'notes', component: () => import('@/views/NotesView.vue') },
      { path: 'calendar', name: 'calendar', component: () => import('@/views/CalendarView.vue') },
      { path: 'media', name: 'media', component: () => import('@/views/MediaView.vue') },
      { path: 'atlas', name: 'atlas', component: () => import('@/views/AtlasView.vue') },
      { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'notes' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 보호 라우트 가드. 앱 시작 시 한 번 세션을 확인(fetchMe)하고,
// 미인증이면 로그인 화면으로 보낸다.
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.ready) {
    await auth.fetchMe()
  }
  if (to.meta.public) return true
  if (auth.isAuthenticated) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
