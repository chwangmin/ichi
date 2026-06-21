import { api } from './client'
import type { CardColor } from '@/types'

export interface EntryListItem {
  id: string
  entryDate: string
  preview: string
  pinned: boolean
  color: CardColor
  lat?: number | null
  lng?: number | null
  placeName?: string | null
  thumbMediaId?: string | null
}

export interface EntryDetail extends EntryListItem {
  html: string
}

export interface CreateEntryPayload {
  html: string
  entryDate?: string
  color?: CardColor
  lat?: number | null
  lng?: number | null
  placeName?: string | null
  mediaIds?: string[]
}

export interface UpdateEntryPayload {
  html: string
  lat?: number | null
  lng?: number | null
  placeName?: string | null
  mediaIds?: string[]
}

export interface AtlasPin {
  id: string
  lat: number
  lng: number
  placeName: string | null
  preview: string
  color: CardColor
  thumbMediaId: string | null
}

export interface RestoreResult {
  scanned: number
  restored: number
  skipped: number
}

export const entriesApi = {
  list: () => api.get<EntryListItem[]>('/entries'),
  listBetween: (from: string, to: string) =>
    api.get<EntryListItem[]>(`/entries?from=${from}&to=${to}`),
  atlas: () => api.get<AtlasPin[]>('/entries/atlas'),
  get: (id: string) => api.get<EntryDetail>(`/entries/${id}`),
  create: (payload: CreateEntryPayload) => api.post<EntryListItem>('/entries', payload),
  updateContent: (id: string, payload: UpdateEntryPayload) =>
    api.patch<EntryListItem>(`/entries/${id}`, payload),
  togglePin: (id: string) => api.post<EntryListItem>(`/entries/${id}/pin`),
  changeColor: (id: string, color: CardColor) =>
    api.patch<EntryListItem>(`/entries/${id}/color`, { color }),
  remove: (id: string) => api.del(`/entries/${id}`),
  // 복원: Drive 스캔 미리보기(restored=복원 대상 수) / 실행
  restorePreview: () => api.get<RestoreResult>('/entries/restore'),
  restore: () => api.post<RestoreResult>('/entries/restore'),
}

export interface MediaItem {
  id: string
  type: 'image' | 'video'
  entryId: string | null
}

export const mediaApi = {
  uploadImage: (file: File | Blob) => {
    const form = new FormData()
    form.append('file', file)
    return api.upload<MediaItem>('/media', form)
  },
  uploadVideo: (file: File | Blob) => {
    const form = new FormData()
    form.append('file', file)
    return api.upload<MediaItem>('/media/video', form)
  },
  gallery: () => api.get<MediaItem[]>('/media'),
}
