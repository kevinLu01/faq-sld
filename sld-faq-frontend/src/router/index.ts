import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/pages/LoginPage.vue'),
    },
    {
      path: '/',
      redirect: '/home',
    },
    {
      path: '/home',
      name: 'Home',
      component: () => import('@/pages/HomePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/upload',
      name: 'Upload',
      component: () => import('@/pages/UploadPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/review/list',
      name: 'ReviewList',
      component: () => import('@/pages/review/ReviewListPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/review/detail/:id',
      name: 'ReviewDetail',
      component: () => import('@/pages/review/ReviewDetailPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/faq/list',
      name: 'FaqList',
      component: () => import('@/pages/faq/FaqListPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/faq/detail/:id',
      name: 'FaqDetail',
      component: () => import('@/pages/faq/FaqDetailPage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/me',
      name: 'Me',
      component: () => import('@/pages/MePage.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/home',
    },
  ],
})

router.beforeEach((to) => {
  if (to.path === '/login') return true
  const userStore = useUserStore()
  if (!userStore.token) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})

export default router
