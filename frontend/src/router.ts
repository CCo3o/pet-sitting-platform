import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from './stores/auth'
import AuthPage from './views/AuthPage.vue'
const WorkspacePage = () => import('./views/WorkspacePage.vue')
const OwnerDataPage = () => import('./views/OwnerDataPage.vue')
const SitterPage = () => import('./views/SitterPage.vue')
const BookingPage = () => import('./views/BookingPage.vue')
const OrdersPage = () => import('./views/OrdersPage.vue')
const ComplaintsPage = () => import('./views/ComplaintsPage.vue')
const AdminPage = () => import('./views/AdminPage.vue')
const AgentPage = () => import('./views/AgentPage.vue')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: AuthPage },
    { path: '/register', component: AuthPage },
    { path: '/workspace', component: WorkspacePage, meta: { requiresAuth: true } },
    { path: '/pets', component: OwnerDataPage, meta: { requiresAuth:true,roles:['OWNER'] } },
    { path: '/addresses', component: OwnerDataPage, meta: { requiresAuth:true,roles:['OWNER'] } },
    { path: '/book', component: BookingPage, meta: { requiresAuth:true,roles:['OWNER'] } },
    { path: '/agent', component: AgentPage, meta: { requiresAuth:true,roles:['OWNER'] } },
    { path: '/sitter', component: SitterPage, meta: { requiresAuth:true,roles:['SITTER'] } },
    { path: '/orders', component: OrdersPage, meta: { requiresAuth:true } },
    { path: '/complaints', component: ComplaintsPage, meta: { requiresAuth:true } },
    { path: '/admin', component: AdminPage, meta: { requiresAuth:true,roles:['ADMIN'] } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})
router.beforeEach(async to => {
  const auth = useAuthStore()
  try { await auth.restore() }
  catch { /* 页面提供连接失败提示；保留 token 以便重试。 */ }
  if (to.meta.requiresAuth && !auth.authenticated) return '/login'
  if(Array.isArray(to.meta.roles)&&!to.meta.roles.includes(auth.user?.role))return '/workspace'
  if ((to.path === '/login' || to.path === '/register') && auth.authenticated) return '/workspace'
})
