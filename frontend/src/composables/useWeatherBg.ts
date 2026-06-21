import { ref } from 'vue'

/**
 * 날씨 배경 효과 on/off 설정 (localStorage).
 * 기본값은 켜짐. 설정 화면에서 토글하고, AppShell 의 배경이 이 값을 본다.
 */

const KEY = 'ichi.weather.bg'

function read(): boolean {
  // 저장값이 없으면 기본 켜짐 — 명시적으로 'off' 일 때만 꺼진다
  return localStorage.getItem(KEY) !== 'off'
}

// 모듈 전역 상태 (설정 화면과 배경이 공유)
const enabled = ref(read())

export function useWeatherBg() {
  function setEnabled(v: boolean) {
    enabled.value = v
    localStorage.setItem(KEY, v ? 'on' : 'off')
  }
  function toggle() {
    setEnabled(!enabled.value)
  }
  return { enabled, setEnabled, toggle }
}
