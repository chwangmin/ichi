<script setup lang="ts">
import { buildGoogleAuthUrl, createState } from '@/auth/google'

const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
const configured = !!clientId && !clientId.startsWith('your-')

function loginWithGoogle() {
  const state = createState()
  window.location.href = buildGoogleAuthUrl(state)
}
</script>

<template>
  <div class="login">
    <div class="card">
      <div class="seal">一</div>
      <h1>이치</h1>
      <p class="tag">하루를 적어두면, 이치에 닿는다.</p>

      <button class="google" :disabled="!configured" @click="loginWithGoogle">
        <span class="mi">login</span>
        Google로 계속하기
      </button>

      <p v-if="!configured" class="note">
        Google 로그인을 쓰려면 <code>.env</code>에 <code>VITE_GOOGLE_CLIENT_ID</code>를 설정하세요.
        (README의 "Google Cloud 준비" 참고)
      </p>
    </div>
  </div>
</template>

<style scoped>
.login {
  height: 100%;
  display: grid;
  place-items: center;
  background: var(--paper);
  padding: 20px;
}
.card {
  width: 100%;
  max-width: 360px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 40px 32px;
  text-align: center;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.05);
}
.seal {
  width: 56px;
  height: 56px;
  margin: 0 auto;
  background: var(--seal);
  border-radius: 13px;
  display: grid;
  place-items: center;
  color: #fff;
  font-family: 'Nanum Myeongjo', serif;
  font-weight: 800;
  font-size: 34px;
}
h1 {
  margin-top: 18px;
  font-family: 'Nanum Myeongjo', serif;
  font-size: 26px;
  font-weight: 800;
}
.tag {
  margin-top: 6px;
  font-size: 13px;
  color: var(--ink-faint);
}
.google {
  margin-top: 28px;
  width: 100%;
  height: 48px;
  border: 1px solid var(--line);
  background: #fff;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 14.5px;
  font-weight: 500;
  color: var(--ink);
  cursor: pointer;
}
.google:hover:not(:disabled) {
  background: var(--hover);
}
.google:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.note {
  margin-top: 14px;
  font-size: 11.5px;
  color: var(--ink-faint);
  line-height: 1.5;
}
.note code {
  background: var(--sidebar);
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 11px;
}
</style>
