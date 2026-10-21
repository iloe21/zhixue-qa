import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由配置 + 全局守卫
 * meta.requiresAuth：未登录跳转登录页（带 redirect 回跳）
 * meta.guestOnly：已登录访问登录/注册页时跳回首页
 */
const routes = [
  { path: '/', redirect: '/home' },
  {
    path: '/home',
    name: 'home',
    component: () => import('../views/Home.vue'),
    meta: { title: '首页', requiresAuth: true }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册', guestOnly: true }
  },
  {
    path: '/kbs',
    name: 'kbs',
    component: () => import('../views/KbList.vue'),
    meta: { title: '知识库', requiresAuth: true }
  },
  {
    path: '/kbs/:id',
    name: 'kbDetail',
    component: () => import('../views/KbDetail.vue'),
    meta: { title: '知识库详情', requiresAuth: true }
  },
  {
    path: '/chat',
    name: 'chat',
    component: () => import('../views/Chat.vue'),
    meta: { title: '智能问答', requiresAuth: true }
  },
  {
    path: '/exercises',
    name: 'exercises',
    component: () => import('../views/ExerciseList.vue'),
    meta: { title: '练习题', requiresAuth: true }
  },
  {
    path: '/exercises/:id',
    name: 'exerciseDo',
    component: () => import('../views/ExerciseDo.vue'),
    meta: { title: '练习作答', requiresAuth: true }
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, role: 'admin' },
    children: [
      {
        path: '',
        name: 'adminDashboard',
        component: () => import('../views/admin/Dashboard.vue'),
        meta: { title: '管理后台', requiresAuth: true, role: 'admin' }
      },
      {
        path: 'users',
        name: 'adminUsers',
        component: () => import('../views/admin/UserManage.vue'),
        meta: { title: '用户管理', requiresAuth: true, role: 'admin' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.guestOnly && token) {
    return '/home'
  }
  // 角色守卫：meta.role 不匹配则回首页（后端接口另有 @PreAuthorize 兜底）
  if (to.meta.role) {
    const user = JSON.parse(localStorage.getItem('user') || 'null')
    if (user?.role !== to.meta.role) {
      return '/home'
    }
  }
  document.title = to.meta.title ? `${to.meta.title} - 智学问答` : '智学问答'
})

export default router
