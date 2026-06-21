// Google Analytics (gtag.js) 로더.
// 측정 ID(VITE_GA_MEASUREMENT_ID)가 있을 때만 스크립트를 주입한다.
// 키 컨벤션은 Maps/OAuth 키와 동일하게 VITE_ 접두사(브라우저 노출 허용)만 사용.
//
// SPA 라 정적 gtag 스니펫은 최초 1회 page_view 만 보낸다.
// 라우터 이동마다 trackPageView() 로 수동 전송한다.
import type { Router } from 'vue-router'

const MEASUREMENT_ID = import.meta.env.VITE_GA_MEASUREMENT_ID

// gtag 는 인자 묶음(arguments-like)을 dataLayer 에 그대로 push 한다.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function gtag(...args: any[]) {
  window.dataLayer = window.dataLayer || []
  window.dataLayer.push(args)
}

let initialized = false

/** gtag.js 스크립트를 주입하고 초기화한다. 측정 ID 없으면 no-op. */
export function setupAnalytics(router: Router) {
  if (initialized) return
  if (!MEASUREMENT_ID) return // 로컬/테스트 등 미설정 환경은 추적하지 않음
  initialized = true

  const script = document.createElement('script')
  script.async = true
  script.src = `https://www.googletagmanager.com/gtag/js?id=${MEASUREMENT_ID}`
  document.head.appendChild(script)

  gtag('js', new Date())
  // SPA 라우팅은 직접 page_view 를 보내므로 자동 페이지뷰는 끈다(중복 방지).
  gtag('config', MEASUREMENT_ID, { send_page_view: false })

  // 라우트 이동마다 페이지뷰 전송.
  router.afterEach((to) => {
    gtag('event', 'page_view', {
      page_path: to.fullPath,
      page_location: window.location.href,
      page_title: document.title,
    })
  })
}
