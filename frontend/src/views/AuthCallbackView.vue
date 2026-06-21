<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { consumeState } from '@/auth/google'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const error = ref<string | null>(null)

onMounted(async () => {
  const code = route.query.code as string | undefined
  const returnedState = route.query.state as string | undefined
  const errParam = route.query.error as string | undefined

  if (errParam) {
    error.value = 'Google 로그인이 취소되었거나 거부되었습니다.'
    return
  }

  const savedState = consumeState()
  if (!code || !returnedState || returnedState !== savedState) {
    error.value = '로그인 요청이 올바르지 않습니다. 다시 시도해 주세요.'
    return
  }

  try {
    await auth.loginWithCode(code)
    router.replace({ name: 'notes' })
  } catch (e) {
    error.value = e instanceof Error ? e.message : '로그인에 실패했습니다.'
  }
})
</script>

<template>
  <div class="callback">
    <template v-if="error">
      <span class="mi err">error</span>
      <p class="msg">{{ error }}</p>
      <RouterLink class="back" :to="{ name: 'login' }">로그인으로 돌아가기</RouterLink>
    </template>
    <template v-else>
      <div class="spinner" aria-hidden="true"></div>
      <p class="msg">로그인 중…</p>
    </template>
  </div>
</template>

<style scoped>
.callback {
  height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 14px;
  color: var(--ink-soft);
  background: var(--paper);
}
.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--line);
  border-top-color: var(--seal);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.err {
  font-size: 40px;
  color: var(--seal);
}
.msg {
  font-size: 14px;
}
.back {
  font-size: 13px;
  color: var(--seal);
}
</style>
