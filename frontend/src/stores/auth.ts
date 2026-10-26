import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ApiError, request } from '../api'
import type { AuthResponse, User } from '../types'

// 只保存到当前标签页的 sessionStorage，页面刷新时向后端重新核实身份。
export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem('pet-access-token') ?? '')
  const user = ref<User | null>(null)
  const authenticated = computed(() => Boolean(token.value && user.value))
  let pending: Promise<void> | null = null
  function clear() { token.value = ''; user.value = null; sessionStorage.removeItem('pet-access-token') }
  function apply(result: AuthResponse) {
    token.value = result.accessToken
    user.value = result.user
    sessionStorage.setItem('pet-access-token', result.accessToken)
  }
  async function login(username: string, password: string) {
    apply(await request<AuthResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }))
  }
  async function register(data: { username: string; password: string; displayName: string; role: 'OWNER' | 'SITTER' }) {
    apply(await request<AuthResponse>('/auth/register', { method: 'POST', body: JSON.stringify(data) }))
  }
  async function restore() {
    if (!token.value || user.value) return
    if (!pending) pending = request<User>('/auth/me', {}, token.value)
      .then(result => { user.value = result })
      .catch(error => { if (error instanceof ApiError && error.status === 401) clear(); else throw error })
      .finally(() => { pending = null })
    return pending
  }
  async function logout() {
    // 服务端撤销成功后再清除本地凭证；失败时允许重试，避免误报退出成功。
    try { if (token.value) await request<void>('/auth/logout', { method: 'POST' }, token.value) }
    catch (error) { if (!(error instanceof ApiError && error.status === 401)) throw error }
    clear()
  }
  return { token, user, authenticated, login, register, restore, logout, clear }
})
