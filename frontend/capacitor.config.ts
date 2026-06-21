import type { CapacitorConfig } from '@capacitor/cli'

/**
 * Capacitor — 이치를 iOS/Android 네이티브 앱으로 감싸는 설정.
 *
 * - webDir: Vite 빌드 산출물(dist)을 네이티브 웹뷰에 번들.
 * - 네이티브 앱은 capacitor:// (또는 http://localhost) origin 에서 동작하므로
 *   Vite 의 /api 프록시가 없다. 앱에서는 백엔드를 절대 URL 로 호출하고,
 *   백엔드는 해당 origin 에 대해 CORS 를 허용해야 한다. (M2에서 처리)
 * - 개발 중 라이브 리로드를 쓰려면 아래 server.url 주석을 풀어
 *   PC의 LAN IP:5173 을 가리키게 한다.
 */
const config: CapacitorConfig = {
  appId: 'com.ichi.diary',
  appName: '이치',
  webDir: 'dist',
  // server: {
  //   // 개발 라이브 리로드 (실기기/에뮬레이터가 PC에 접근 가능할 때)
  //   url: 'http://192.168.0.x:5173',
  //   cleartext: true,
  // },
}

export default config
