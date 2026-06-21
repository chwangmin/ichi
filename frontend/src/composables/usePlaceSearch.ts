import { api } from '@/api/client'

/**
 * 날씨 위치 검색. 브라우저에서 VWorld 키가 노출되지 않도록 백엔드 프록시를 호출한다.
 */

export interface PlaceResult {
  label: string // 보여줄 이름 (도시, 시/도, 국가)
  lat: number
  lng: number
}

export async function searchPlaces(query: string): Promise<PlaceResult[]> {
  const q = query.trim()
  if (q.length < 2) return []
  try {
    return await api.get<PlaceResult[]>(`/places/search?q=${encodeURIComponent(q)}`)
  } catch {
    return []
  }
}
