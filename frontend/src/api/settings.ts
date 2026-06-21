import { api } from './client'

export interface StorageStatus {
  connected: boolean
  usageBytes: number | null
  limitBytes: number | null
  entryCount: number
}

export const settingsApi = {
  storage: () => api.get<StorageStatus>('/settings/storage'),
}
