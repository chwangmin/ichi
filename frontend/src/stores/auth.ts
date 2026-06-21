import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, ApiError } from '@/api/client'

export interface IchiUser {
  email: string
  name: string | null
  pictureUrl: string | null
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<IchiUser | null>(null)
  // 앱 시작 시 세션 확인(fetchMe)이 끝났는지. 가드가 깜빡임 없이 판단하도록.
  const ready = ref(false)

  const isAuthenticated = computed(() => user.value !== null)

  /** authorization code 를 백엔드로 보내 로그인. 성공 시 사용자 정보 세팅. */
  async function loginWithCode(code: string) {
    const u = await api.post<IchiUser>('/auth/google', { code })
    user.value = u
    return u
  }

  /** 세션 쿠키로 현재 사용자 조회. 미인증이면 user=null. */
  async function fetchMe() {
    try {
      user.value = await api.get<IchiUser>('/auth/me')
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        user.value = null
      } else {
        // 네트워크 등 기타 오류도 미인증으로 처리 (로그인 화면으로)
        user.value = null
      }
    } finally {
      ready.value = true
    }
  }

  async function logout() {
    try {
      await api.post('/auth/logout')
    } finally {
      user.value = null
    }
  }

  return { user, ready, isAuthenticated, loginWithCode, fetchMe, logout }
})
