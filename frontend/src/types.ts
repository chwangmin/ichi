// 프론트 공용 타입. 백엔드 entries 메타데이터(§4)와 1:1 대응.
export type CardColor = 'y' | 'g' | 'b' | 'p' | null

export interface NoteEntry {
  id: string
  entryDate: string // ISO date (YYYY-MM-DD)
  preview: string
  pinned: boolean
  color: CardColor
  lat?: number | null
  lng?: number | null
  placeName?: string | null
  thumbMediaId?: string | null
}
