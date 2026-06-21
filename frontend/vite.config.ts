import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 백엔드 프록시 대상: 도커에선 BACKEND_ORIGIN(http://backend:8080), 로컬은 localhost:8080
const backendOrigin = process.env.BACKEND_ORIGIN ?? 'http://localhost:8080'

// .env 는 모노레포 루트에 있다. 로컬 `npm run dev` 에서 루트 .env 의 VITE_* 를 읽도록 envDir 지정.
// (Docker 에선 environment: 로 주입되므로 무관)
const repoRoot = fileURLToPath(new URL('..', import.meta.url))

export default defineConfig({
  plugins: [vue()],
  envDir: repoRoot,
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: true, // 0.0.0.0 — 도커 컨테이너에서 외부 접근 허용
    port: 5173,
    proxy: {
      '/api': {
        target: backendOrigin,
        changeOrigin: true,
      },
    },
  },
})
