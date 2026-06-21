import { ref } from 'vue'
import { resolvePlaceName } from '@/composables/usePlaceName'

/**
 * 날씨용 "내 장소" 설정 (localStorage).
 *
 * 설정돼 있으면 상단바 날씨가 실시간 위치 대신 이 좌표를 쓴다.
 * 백엔드 없이 브라우저에만 저장한다(개인 기기 기준).
 */

export interface HomeLocation {
  lat: number
  lng: number
  placeName: string | null
}

const KEY = 'ichi.weather.home'

function read(): HomeLocation | null {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return null
    const v = JSON.parse(raw)
    if (typeof v?.lat === 'number' && typeof v?.lng === 'number') {
      return { lat: v.lat, lng: v.lng, placeName: v.placeName ?? null }
    }
  } catch {
    // 손상된 값은 무시
  }
  return null
}

// 모듈 전역 상태 (여러 컴포넌트가 같은 값을 공유)
const home = ref<HomeLocation | null>(read())

function set(loc: HomeLocation) {
  home.value = loc
  localStorage.setItem(KEY, JSON.stringify(loc))
}

function clear() {
  home.value = null
  localStorage.removeItem(KEY)
}

function currentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('NO_GEO'))
      return
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 8000,
    })
  })
}

export function useHomeLocation() {
  const saving = ref(false)
  const error = ref<string | null>(null)

  /** 현재 GPS 위치를 받아 '내 장소'로 저장. */
  async function captureCurrent() {
    if (saving.value) return
    saving.value = true
    error.value = null
    try {
      const pos = await currentPosition()
      const lat = pos.coords.latitude
      const lng = pos.coords.longitude
      const placeName = await resolvePlaceName(lat, lng)
      set({ lat, lng, placeName })
    } catch (e) {
      const denied =
        typeof GeolocationPositionError !== 'undefined' &&
        e instanceof GeolocationPositionError &&
        e.code === e.PERMISSION_DENIED
      error.value = denied
        ? '위치 권한이 거부됐어요. 브라우저 설정에서 허용해 주세요.'
        : '현재 위치를 가져오지 못했어요.'
    } finally {
      saving.value = false
    }
  }

  /** 검색 등으로 고른 좌표를 '내 장소'로 저장. label 을 그대로 장소명으로 쓴다. */
  function setHome(loc: HomeLocation) {
    set(loc)
    error.value = null
  }

  return { home, saving, error, captureCurrent, setHome, clearHome: clear }
}
