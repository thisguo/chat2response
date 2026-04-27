import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '../api'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
  },
  {
    path: '/',
    name: 'Management',
    component: () => import('../views/Management.vue'),
    meta: { requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !isLoggedIn()) {
    return { name: 'Login' }
  }
  if (to.name === 'Login' && isLoggedIn()) {
    return { name: 'Management' }
  }
})

export default router
