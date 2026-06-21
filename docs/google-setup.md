# Google Cloud 설정 가이드 — 이치(Ichi)

이치는 **Google 로그인(OAuth) + Google Drive(drive.file, "ichi" 폴더) + Google Maps**를 사용합니다.
이 문서는 Google Cloud Console에서 필요한 모든 설정을 **처음부터 끝까지** 정리한 것입니다.

> 결과물(키 4종)은 모두 프로젝트 루트 `.env`에 넣습니다. `.env`는 절대 커밋하지 마세요.

---

## 0. 한눈에 — 무엇을 발급하나

| 항목 | 들어가는 .env 키 | 용도 |
|---|---|---|
| OAuth 클라이언트 ID | `GOOGLE_CLIENT_ID`, `VITE_GOOGLE_CLIENT_ID` | Google 로그인 |
| OAuth 클라이언트 시크릿 | `GOOGLE_CLIENT_SECRET` | 백엔드 code↔token 교환 |
| 리디렉션 URI | `GOOGLE_REDIRECT_URI`, `VITE_GOOGLE_REDIRECT_URI` | 로그인 후 돌아올 주소 |
| Maps API 키 | `VITE_GOOGLE_MAPS_API_KEY` | 아틀라스 지도 |

활성화할 API: **Google Drive API**, **Maps JavaScript API**

---

## 1. 프로젝트 생성

1. https://console.cloud.google.com 접속
2. 상단 프로젝트 선택 드롭다운 → **새 프로젝트(New Project)**
3. 이름 예: `ichi-diary` → 만들기
4. 생성 후 그 프로젝트가 선택돼 있는지 확인 (이후 모든 설정은 이 프로젝트 안에서)

---

## 2. API 활성화

**API 및 서비스 → 라이브러리(Library)** 에서 아래를 각각 검색해 **사용 설정(Enable)**:

- **Google Drive API**
- **Maps JavaScript API** (아틀라스/지도용)

---

## 3. OAuth 동의 화면(OAuth consent screen)

**API 및 서비스 → OAuth 동의 화면**

1. **User Type: 외부(External)** → 만들기
2. 앱 정보
   - 앱 이름: `이치`
   - 사용자 지원 이메일: 본인 메일
   - 개발자 연락처 이메일: 본인 메일
3. **범위(Scopes)** — "ADD OR REMOVE SCOPES"에서 추가:
   - `openid`
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
   - `.../auth/drive.file`  ← **이 앱이 만든 파일/폴더만** 접근 (사용자의 다른 Drive 파일엔 접근 안 함)
4. 저장

> ⚠️ 프론트(`frontend/src/auth/google.ts`)와 백엔드 검증이 모두 **`drive.file`** 을 기준으로 합니다.
> 동의 화면 범위도 반드시 `drive.file` 로 등록하세요. (`drive.appdata` 로 등록하면 토큰에 `drive.file` 이 없어 로그인이 막힙니다.)
>
> `drive.file` 은 "민감(sensitive)" 스코프지만, **테스트 사용자(아래 4번)로 쓰는 개인/소수 사용에는 Google 검증(verification)이 필요 없습니다.**

---

## 4. ⭐ 테스트 사용자 추가 — "403 access_denied" 해결

> 동의 화면이 **"테스트(Testing)" 상태**면, **등록된 테스트 사용자만** 로그인할 수 있습니다.
> 로그인 시 아래 오류가 나면 이 단계를 안 한 것입니다:
>
> ```
> 403 오류: access_denied
> 이치에서 Google의 인증 절차를 완료하지 않았습니다.
> 앱은 현재 테스트 중이며 개발자가 승인한 테스터만 앱에 액세스할 수 있습니다.
> ```

**해결 (1분, 즉시 적용):**

1. **API 및 서비스 → OAuth 동의 화면**
2. **테스트 사용자(Test users)** 섹션 → **+ ADD USERS / 사용자 추가**
3. 로그인하려는 **본인 Google 이메일** 입력 (여러 명 가능, 최대 100명)
4. 저장 → 다시 로그인 시도

> 테스트 사용자는 무료이고 심사 없이 바로 됩니다. **개인 일기장은 이걸로 충분합니다.**

### (선택) 전체 공개로 바꾸려면 — 비권장

아무나 로그인하게 하려면 OAuth 동의 화면에서 **앱 게시(Publish App)** → 프로덕션 전환.
- `drive.file` 은 민감 스코프라, 전체 공개(프로덕션)로 게시하려면 Google 검증을 받기 전까지 "확인되지 않은 앱" 경고가 뜰 수 있음.
- 본격 공개는 Google 검증(개인정보처리방침·도메인 소유 확인 등)이 필요 → **개인/소수 사용이면 테스트 사용자(4번)로 충분**.

---

## 5. OAuth 2.0 클라이언트 ID 발급

**API 및 서비스 → 사용자 인증 정보(Credentials) → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**

1. 애플리케이션 유형: **웹 애플리케이션(Web application)**
2. **승인된 JavaScript 원본(Authorized JavaScript origins)**:
   - `http://localhost:5173`
3. **승인된 리디렉션 URI(Authorized redirect URIs)**:
   - `http://localhost:5173/auth/callback`
4. 만들기 → 표시되는 **클라이언트 ID / 클라이언트 보안 비밀** 복사

`.env`에 입력:
```bash
GOOGLE_CLIENT_ID=발급받은-클라이언트-ID
GOOGLE_CLIENT_SECRET=발급받은-시크릿
GOOGLE_REDIRECT_URI=http://localhost:5173/auth/callback

# 프론트(브라우저)도 같은 클라이언트 ID 가 필요 (Vite 는 VITE_ 접두사만 노출)
VITE_GOOGLE_CLIENT_ID=발급받은-클라이언트-ID
VITE_GOOGLE_REDIRECT_URI=http://localhost:5173/auth/callback
```

> ⚠️ `GOOGLE_CLIENT_ID`와 `VITE_GOOGLE_CLIENT_ID`는 **같은 값**입니다. 프론트는 `VITE_` 변수만 읽을 수 있어 둘 다 둡니다.

---

## 6. Maps JavaScript API 키

**사용자 인증 정보 → 사용자 인증 정보 만들기 → API 키**

1. 생성된 키 복사
2. (권장) 키 제한 설정:
   - **애플리케이션 제한**: HTTP 리퍼러 → `http://localhost:5173/*`
   - **API 제한**: Maps JavaScript API 만

`.env`에 입력:
```bash
VITE_GOOGLE_MAPS_API_KEY=발급받은-Maps-키
```

> 키가 없어도 앱은 뜨지만, 아틀라스(지도) 화면에 "지도 키가 필요해요" 안내가 표시됩니다.

---

## 7. .env 최종 점검

`.env`에 아래 키가 모두 채워졌는지 확인:

```bash
# OAuth
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GOOGLE_REDIRECT_URI=http://localhost:5173/auth/callback
VITE_GOOGLE_CLIENT_ID=...            # GOOGLE_CLIENT_ID 와 동일
VITE_GOOGLE_REDIRECT_URI=http://localhost:5173/auth/callback

# Maps
VITE_GOOGLE_MAPS_API_KEY=...
```

> `VITE_*` 값을 바꾼 뒤에는 **프론트 컨테이너를 재생성**해야 반영됩니다:
> ```bash
> docker compose up -d frontend
> ```
> (docker-compose 의 frontend `environment:` 블록에 해당 `VITE_*` 키가 있어야 컨테이너로 전달됩니다.)

---

## 8. 로그인 흐름 (참고)

```
[브라우저] http://localhost:5173 → 로그인 화면 → "Google로 계속하기"
   → Google 동의 화면 (scope: openid email profile drive.file)
   → http://localhost:5173/auth/callback?code=...
   → [프론트] POST /api/auth/google { code }
   → [백엔드] code ↔ token 교환 → users upsert → JWT 쿠키 발급
   → 로그인 완료
```

---

## 9. 자주 겪는 문제(Troubleshooting)

| 증상 | 원인 | 해결 |
|---|---|---|
| **403 access_denied** ("테스터만 액세스") | 동의 화면이 Testing 상태인데 테스트 사용자 미등록 | **4번** — 본인 이메일을 테스트 사용자로 추가 |
| **저장 시 403 `ACCESS_TOKEN_SCOPE_INSUFFICIENT`** (`DriveFiles.Create`) | 발급된 토큰에 `drive.file` 스코프 없음 (동의 화면에 스코프 미등록 또는 옛 동의 재사용) | ① 동의 화면 **범위에 `drive.file` 등록** 확인(3번) → ② https://myaccount.google.com/permissions 에서 **"이치" 앱 액세스 권한 삭제** → ③ 앱에서 로그아웃 후 재로그인(Drive 동의 화면이 떠야 정상) |
| 로그인 버튼 비활성 + "VITE_GOOGLE_CLIENT_ID 설정하세요" | 프론트 컨테이너에 값이 안 들어감 | `.env`에 `VITE_GOOGLE_CLIENT_ID` 넣고 `docker compose up -d frontend` |
| **redirect_uri_mismatch** | 등록한 리디렉션 URI 불일치 | **5번** 리디렉션 URI를 `http://localhost:5173/auth/callback` 정확히 등록 |
| 지도 화면 "지도 키가 필요해요" | Maps 키 없음/제한 | **6번** `VITE_GOOGLE_MAPS_API_KEY` 설정 |
| Drive 저장 실패/권한 오류 | `drive.file` 스코프 미동의 또는 재로그인 필요 | 동의 화면 스코프 확인 후 재로그인 |
