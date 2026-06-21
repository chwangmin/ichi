import { ref } from 'vue'

/**
 * 화면 테마 설정 (localStorage).
 *
 * - 'auto'  : 날씨가 '밤'이면 어둡게, 낮이면 밝게 (기본값)
 * - 'light' : 날씨와 무관하게 항상 밝게
 * - 'dark'  : 날씨와 무관하게 항상 어둡게
 *
 * 우선순위는 사용자가 고른 모드(다크/라이트)가 날씨보다 위.
 * 'auto' 일 때만 날씨의 낮/밤을 따른다.
 * 실제 어둡게 적용할지(.night 클래스)는 AppShell 이 이 값 + 날씨로 계산한다.
 */

export type ThemeMode = 'auto' | 'light' | 'dark'

const KEY = 'ichi.theme'

function read(): ThemeMode {
  const v = localStorage.getItem(KEY)
  return v === 'light' || v === 'dark' ? v : 'auto'
}

// 모듈 전역 상태 (설정 화면과 AppShell 이 공유)
const mode = ref<ThemeMode>(read())

export function useTheme() {
  function setMode(v: ThemeMode) {
    mode.value = v
    localStorage.setItem(KEY, v)
  }
  return { mode, setMode }
}
