/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string
  readonly VITE_GOOGLE_MAPS_API_KEY: string
  readonly VITE_GOOGLE_CLIENT_ID: string
  readonly VITE_GOOGLE_REDIRECT_URI: string
  // Google Analytics 측정 ID (예: G-XXXXXXXXXX). 없으면 추적 비활성.
  readonly VITE_GA_MEASUREMENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// Google Maps JS API 최소 타입 (@types/google.maps 미사용). 느슨하게 선언.
// env.d.ts 는 모듈이 아닌 전역 스크립트라 declare global 없이 최상위에 선언.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const google: any
interface Window {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  google: any
  // gtag.js (Google Analytics) 가 사용하는 전역 큐.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  dataLayer: any[]
}
