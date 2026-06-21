// Google Maps JavaScript API 동적 로더. 한 번만 로드하고 Promise 캐시.
let loadPromise: Promise<typeof google> | null = null

export function loadGoogleMaps(): Promise<typeof google> {
  if (loadPromise) return loadPromise

  const key = import.meta.env.VITE_GOOGLE_MAPS_API_KEY
  if (!key || key.startsWith('your-')) {
    return Promise.reject(new Error('NO_KEY'))
  }

  loadPromise = new Promise((resolve, reject) => {
    if (window.google?.maps) {
      resolve(window.google)
      return
    }
    const script = document.createElement('script')
    // marker 라이브러리 = AdvancedMarkerElement(사진 핀용)
    script.src = `https://maps.googleapis.com/maps/api/js?key=${key}&libraries=marker&v=weekly`
    script.async = true
    script.onload = () => resolve(window.google)
    script.onerror = () => {
      loadPromise = null
      reject(new Error('LOAD_FAILED'))
    }
    document.head.appendChild(script)
  })
  return loadPromise
}
