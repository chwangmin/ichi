// 백엔드 API 호출 헬퍼. 세션은 HttpOnly 쿠키라 credentials: 'include' 필수.
const BASE = import.meta.env.VITE_API_BASE ?? '/api'

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string, // 백엔드 error 코드 (예: drive_scope_missing)
  ) {
    super(message)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(init.headers ?? {}) },
    ...init,
  })

  if (!res.ok) {
    let message = `요청 실패 (${res.status})`
    let code: string | undefined
    try {
      const body = await res.json()
      if (body?.message) message = body.message
      if (body?.error) code = body.error
    } catch {
      /* 응답 본문 없음 */
    }
    throw new ApiError(res.status, message, code)
  }

  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

async function upload<T>(path: string, form: FormData): Promise<T> {
  // multipart: Content-Type 를 브라우저가 boundary 와 함께 설정하도록 두어야 함
  const res = await fetch(`${BASE}${path}`, {
    method: 'POST',
    credentials: 'include',
    body: form,
  })
  if (!res.ok) {
    let message = `업로드 실패 (${res.status})`
    try {
      const body = await res.json()
      if (body?.message) message = body.message
    } catch {
      /* noop */
    }
    throw new ApiError(res.status, message)
  }
  return res.json() as Promise<T>
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PATCH', body: body ? JSON.stringify(body) : undefined }),
  del: (path: string) => request<void>(path, { method: 'DELETE' }),
  upload,
}

/** 인라인 이미지/영상 원본 URL (백엔드가 Drive 에서 스트리밍). */
export function mediaUrl(id: string): string {
  return `${BASE}/media/${id}/raw`
}

/** 갤러리 그리드용 썸네일 URL. */
export function thumbUrl(id: string): string {
  return `${BASE}/media/${id}/thumb`
}
