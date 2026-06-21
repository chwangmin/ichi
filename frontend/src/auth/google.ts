// Google OAuth 동의 화면으로 보내는 authorization URL 생성.
// drive 스코프 + offline access(refresh_token) 요청.
const SCOPES = ['openid', 'email', 'profile', 'https://www.googleapis.com/auth/drive.file']

export function buildGoogleAuthUrl(state: string): string {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
  const redirectUri = import.meta.env.VITE_GOOGLE_REDIRECT_URI ?? `${location.origin}/auth/callback`

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: SCOPES.join(' '),
    access_type: 'offline', // refresh_token 받기
    include_granted_scopes: 'true',
    prompt: 'consent', // 매번 동의(개발 중 refresh_token 확보용)
    state,
  })
  return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`
}

/** CSRF 방지용 state 생성 + 보관. */
export function createState(): string {
  const state = crypto.randomUUID()
  sessionStorage.setItem('ichi_oauth_state', state)
  return state
}

export function consumeState(): string | null {
  const s = sessionStorage.getItem('ichi_oauth_state')
  sessionStorage.removeItem('ichi_oauth_state')
  return s
}
