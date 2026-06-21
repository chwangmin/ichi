import { ref } from 'vue'
import { useHomeLocation } from '@/composables/useHomeLocation'

/**
 * 현재 위치 날씨 (Open-Meteo, 무료·키 불필요).
 * navigator.geolocation 으로 좌표를 받아 현재 기온/날씨코드를 가져온다.
 *
 * 상단바 장식용이라 실패해도 조용히 비활성(표시 안 함)으로 둔다.
 */

export type WeatherState = 'idle' | 'loading' | 'ready' | 'error'

// 배경 애니메이션용 큰 분류
export type WeatherCategory = 'clear' | 'clouds' | 'rain' | 'snow' | 'fog' | 'thunder'

export interface Weather {
  tempC: number
  icon: string // Material Symbols 아이콘 이름
  label: string // 한국어 날씨 설명
  category: WeatherCategory // 배경 효과 분류
  intensity: number // 0~1, 비/눈 입자 밀도용 (약함~강함)
  isDay: boolean
  fallback: boolean // 위치를 못 받아 서울 기본값으로 표시 중인지
}

// 위치 권한 거부/실패 시 기본 좌표 (서울 시청)
const SEOUL = { lat: 37.5665, lng: 126.978 }

interface CodeMeta {
  label: string
  icon: string
  category: WeatherCategory
  intensity: number
}

// WMO weather code → (한국어 라벨, Material Symbols 아이콘, 배경 분류, 강도)
// https://open-meteo.com/en/docs (WMO Weather interpretation codes)
function fromCode(code: number, isDay: boolean): CodeMeta {
  const clear = isDay ? 'clear_day' : 'clear_night'
  const fewClouds = isDay ? 'partly_cloudy_day' : 'partly_cloudy_night'
  const table: Record<number, CodeMeta> = {
    0: { label: '맑음', icon: clear, category: 'clear', intensity: 0 },
    1: { label: '대체로 맑음', icon: fewClouds, category: 'clear', intensity: 0 },
    2: { label: '구름 조금', icon: fewClouds, category: 'clouds', intensity: 0.4 },
    3: { label: '흐림', icon: 'cloud', category: 'clouds', intensity: 0.8 },
    45: { label: '안개', icon: 'foggy', category: 'fog', intensity: 0.6 },
    48: { label: '서리 안개', icon: 'foggy', category: 'fog', intensity: 0.8 },
    51: { label: '약한 이슬비', icon: 'rainy', category: 'rain', intensity: 0.3 },
    53: { label: '이슬비', icon: 'rainy', category: 'rain', intensity: 0.4 },
    55: { label: '강한 이슬비', icon: 'rainy', category: 'rain', intensity: 0.55 },
    56: { label: '어는 이슬비', icon: 'rainy', category: 'rain', intensity: 0.4 },
    57: { label: '어는 이슬비', icon: 'rainy', category: 'rain', intensity: 0.55 },
    61: { label: '약한 비', icon: 'rainy', category: 'rain', intensity: 0.45 },
    63: { label: '비', icon: 'rainy', category: 'rain', intensity: 0.7 },
    65: { label: '강한 비', icon: 'rainy', category: 'rain', intensity: 1 },
    66: { label: '어는 비', icon: 'rainy', category: 'rain', intensity: 0.6 },
    67: { label: '어는 비', icon: 'rainy', category: 'rain', intensity: 0.85 },
    71: { label: '약한 눈', icon: 'weather_snowy', category: 'snow', intensity: 0.4 },
    73: { label: '눈', icon: 'weather_snowy', category: 'snow', intensity: 0.7 },
    75: { label: '강한 눈', icon: 'weather_snowy', category: 'snow', intensity: 1 },
    77: { label: '진눈깨비', icon: 'weather_snowy', category: 'snow', intensity: 0.5 },
    80: { label: '소나기', icon: 'rainy', category: 'rain', intensity: 0.6 },
    81: { label: '소나기', icon: 'rainy', category: 'rain', intensity: 0.8 },
    82: { label: '강한 소나기', icon: 'rainy', category: 'rain', intensity: 1 },
    85: { label: '소낙눈', icon: 'weather_snowy', category: 'snow', intensity: 0.6 },
    86: { label: '소낙눈', icon: 'weather_snowy', category: 'snow', intensity: 0.9 },
    95: { label: '뇌우', icon: 'thunderstorm', category: 'thunder', intensity: 0.8 },
    96: { label: '우박 뇌우', icon: 'thunderstorm', category: 'thunder', intensity: 1 },
    99: { label: '우박 뇌우', icon: 'thunderstorm', category: 'thunder', intensity: 1 },
  }
  return table[code] ?? { label: '날씨', icon: 'cloud', category: 'clouds', intensity: 0.5 }
}

function currentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new Error('NO_GEO'))
      return
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: false,
      timeout: 8000,
      maximumAge: 30 * 60 * 1000, // 30분 캐시 허용 (상단바라 정밀도 불필요)
    })
  })
}

// Open-Meteo forecast 호출 → current 블록 반환 (실패/HTTP 오류면 null).
interface CurrentWeather {
  temperature_2m?: number
  weather_code?: number
  is_day?: number
}
async function fetchCurrent(url: string): Promise<CurrentWeather | null> {
  try {
    const res = await fetch(url)
    if (!res.ok) return null
    const data = await res.json()
    return (data?.current as CurrentWeather) ?? null
  } catch {
    return null
  }
}

// 직전에 받은 날씨를 캐시해 둔다 → 새로고침 시 기본값(맑음) 깜빡임 방지.
const CACHE_KEY = 'ichi.weather.last'

function readCache(): Weather | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const v = JSON.parse(raw)
    if (typeof v?.tempC === 'number' && typeof v?.category === 'string') {
      return v as Weather
    }
  } catch {
    // 손상된 캐시는 무시
  }
  return null
}

function writeCache(w: Weather) {
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(w))
  } catch {
    // 저장 실패는 무시 (배경은 그냥 fetch 후 갱신)
  }
}

// 모듈 전역 상태: 상단바 칩과 배경 효과가 같은 날씨를 공유한다(중복 fetch 방지).
// 초기값은 직전 캐시 → 새로고침해도 마지막 날씨로 바로 시작한다.
const cached = readCache()
const state = ref<WeatherState>(cached ? 'ready' : 'idle')
const weather = ref<Weather | null>(cached)

export function useWeather() {
  const { home } = useHomeLocation()

  async function load() {
    if (state.value === 'loading') return
    // 캐시된 날씨가 이미 있으면 화면은 그대로 두고 조용히 갱신(깜빡임 방지).
    if (!weather.value) state.value = 'loading'

    // 좌표 우선순위: 설정에서 지정한 '내 장소' → 실시간 위치 → 서울 폴백.
    let lat = SEOUL.lat
    let lng = SEOUL.lng
    let fallback = true
    if (home.value) {
      lat = home.value.lat
      lng = home.value.lng
      fallback = false
    } else {
      try {
        const pos = await currentPosition()
        lat = pos.coords.latitude
        lng = pos.coords.longitude
        fallback = false
      } catch {
        // 위치 못 받음 → 서울로 진행
      }
    }

    try {
      // 온도: 기본(best_match) 모델은 서울에서 실측보다 몇 도 낮게 나와(격자 보간·열섬 미반영)
      //   ECMWF(ecmwf_ifs025) 모델을 쓴다 — 실측에 더 가깝다.
      // timezone=Asia/Seoul 로 낮/밤(is_day) 판정도 한국 기준이 되게 한다.
      // ECMWF 가 특정 시점에 값을 안 줄 때를 대비해, null 이면 기본 모델로 한 번 재시도.
      const base =
        `https://api.open-meteo.com/v1/forecast?latitude=${lat}` +
        `&longitude=${lng}&current=temperature_2m,weather_code,is_day&timezone=Asia%2FSeoul`

      let cur = await fetchCurrent(base + '&models=ecmwf_ifs025')
      if (!cur || typeof cur.temperature_2m !== 'number') {
        cur = await fetchCurrent(base) // ECMWF 누락 → 기본 모델 폴백
      }
      if (!cur || typeof cur.temperature_2m !== 'number') throw new Error('NO_DATA')

      const isDay = cur.is_day === 1
      const meta = fromCode(Number(cur.weather_code), isDay)
      const next: Weather = {
        tempC: Math.round(cur.temperature_2m),
        icon: meta.icon,
        label: meta.label,
        category: meta.category,
        intensity: meta.intensity,
        isDay,
        fallback,
      }
      weather.value = next
      writeCache(next)
      state.value = 'ready'
    } catch {
      // 갱신 실패: 캐시된 날씨가 있으면 그대로 유지, 없을 때만 에러 표시.
      state.value = weather.value ? 'ready' : 'error'
    }
  }

  return { state, weather, load }
}
