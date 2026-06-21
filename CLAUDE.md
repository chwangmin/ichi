# CLAUDE.md — 이치(一/Ichi) 프로젝트 가이드

이 파일은 Claude Code(및 후속 에이전트)가 이 저장소에서 작업할 때 참고하는 가이드다.
제품 전체 명세(개발 문서)는 `docs/DEVELOPMENT.md`에, 서비스 소개는 `README.md`에, 화면 디자인 레퍼런스는 `mock.html`에 있다.

## 제품 한 줄

Google Keep처럼 가볍게 하루를 끄적이는 일기장. **일기 본문/미디어는 사용자 본인의 Google Drive(appDataFolder)** 에 저장하고, PostgreSQL에는 정렬·검색용 **메타데이터(포인터)만** 둔다.

## 절대 원칙

- **본문은 DB에 저장하지 않는다.** 본문/미디어는 Drive, DB는 메타(`drive_file_id` 등)만.
- **본문은 리치텍스트(HTML).** 일기 안에 이미지를 *글 중간 아무 위치*에 복붙(클립보드 paste)할 수 있다. 본문은 plain text 가 아니다.
  - Drive 본문 JSON: `{ "version": 2, "html": "<p>…</p><img data-ref=\"<driveFileId>\">…", "createdAt", "updatedAt" }`
  - 이미지는 본문에 base64 로 박지 않는다. **Drive 별도 업로드 → `media` 행 → 본문엔 `<img data-ref=\"fileId\">` 참조**.
  - HTML 은 **저장 전/표시 전 모두 sanitize**(XSS 방지). 프론트 DOMPurify, 백엔드 OWASP Java HTML Sanitizer.
  - `entries.preview` 는 HTML 에서 **텍스트만 추출**한 발췌(카드/검색용).
- 저장/불러오기는 **반드시 `StorageService` 인터페이스 한 곳**(`backend/.../storage/service/StorageService.java`)을 통과한다. 나중에 `EncryptedStorage`로 감싸 E2E 암호화를 끼울 수 있어야 한다. (E2E 암호화는 이번 MVP 범위 밖, 자리만)
- **시크릿(OAuth secret, refresh_token, API 키)은 절대 커밋 금지.** 전부 환경변수(`.env` / `application.yml` 분리). `.env`는 `.gitignore`에 있음.
- **UI 텍스트는 한국어.** 빈 상태/에러에 명확한 한국어 카피.
- 접근성: 반응형(모바일까지), 키보드 포커스, `prefers-reduced-motion` 존중.

### 백엔드 코드 규약 (Lombok)

- **`@Setter` / setter 메서드 금지.** Lombok 이든 직접 작성이든 setter 를 두지 않는다.
- 객체 생성은 **Lombok `@Builder`**(+ `@AllArgsConstructor`)와 `@Getter` 로 한다.
- **엔티티**: `@Getter`, `@NoArgsConstructor(access = PROTECTED)`(JPA용), 생성자 또는 `@Builder`. 상태 변경은 setter 가 아니라 **의미 있는 도메인 메서드**(예: `updateProfile(...)`, `updateRefreshToken(...)`)로만 한다.
- **`@ConfigurationProperties`**: `@ConstructorBinding` + `@Getter` + `@Builder` (생성자 바인딩이라 setter 불필요). `@ConfigurationPropertiesScan` 은 메인 클래스에 이미 붙어 있음.
- DTO 는 가급적 `record`.

### Spring Boot 4 / Jackson 3 주의

- Spring Boot 4.1 은 **Jackson 3** 을 쓴다. 패키지가 바뀜:
  - `com.fasterxml.jackson.databind.*` → **`tools.jackson.databind.*`** (`JsonNode`, `ObjectMapper`)
  - `JsonNode.asText()` → **`asString()`** (asText 는 deprecated)
- "package com.fasterxml.jackson.databind does not exist" 컴파일 에러가 나면 이 이유다.

### 헬스체크 / 모니터링

- 커스텀 헬스 컨트롤러를 만들지 말 것. **Actuator `/actuator/health`** 로 일원화(앱 + DB 상태). 프로브는 `/actuator/health/liveness`·`/readiness`.
- 노출 엔드포인트는 `management.endpoints.web.exposure.include` 로 최소화(health, info).

### 인증 / 보안 규약 (M2 확정)

- 세션 = **JWT(stateless)**, **HttpOnly 쿠키 `ichi_session`** 으로 전달 (`AuthCookies`). SameSite=Lax. 운영 HTTPS 에서는 `ichi.cookie-secure=true`.
- 토큰 유틸: 발급/검증 = `JwtService`, 요청 필터 = `JwtAuthenticationFilter`(principal = google_sub).
- `JWT_SECRET` 은 **최소 32바이트**(HS256). 미달이면 부팅 시 예외.
- 공개 경로: `/api/auth/**`, `/actuator/health` 만. 나머지 `/api/**` 는 인증 필요.
- **CORS 는 코드에 하드코딩 금지.** `ichi.cors.allowed-origins`(env `CORS_ALLOWED_ORIGINS`, 쉼표 구분)로 설정. 쿠키 전송 위해 `allowCredentials=true`.
- Google OAuth 비밀(secret), `refresh_token` 은 백엔드만 다룬다. 프론트엔 노출하지 않음.

### 프론트 규약

- 백엔드 호출은 **`src/api/client.ts`** 헬퍼만 사용(`credentials: 'include'` 고정). 에러는 `ApiError`.
- 인증 상태는 Pinia `useAuthStore`(`fetchMe`/`loginWithCode`/`logout`). 라우터 가드가 앱 시작 시 `fetchMe` 1회로 세션 확인.
- 브라우저에 노출할 환경변수는 **`VITE_` 접두사**만 (예: `VITE_GOOGLE_CLIENT_ID`). secret 류는 절대 `VITE_` 로 두지 않는다.

## 기술 스택 (고정 버전)

| 영역     | 스택                                                                                    |
| -------- | --------------------------------------------------------------------------------------- |
| Backend  | Spring Boot **4.1.0**, Spring Security 7, **Java 25**(toolchain), Gradle(wrapper 9.5.1) |
| Frontend | Vue **3.5.x** + Vite + TypeScript, Pinia, Vue Router                                    |
| Mobile   | **Capacitor 8** (iOS/Android, dist 를 네이티브 웹뷰로 래핑)                             |
| DB       | PostgreSQL **17**                                                                       |
| 인증     | Google OAuth 2.0 / OIDC — 세션은 **JWT(stateless, HttpOnly 쿠키 지향)**                 |
| 저장     | Google Drive API v3 (`appDataFolder`)                                                   |
| 지도     | Google Maps JavaScript API                                                              |

> Java는 명세상 "21 LTS"였으나 개발자 환경에 맞춰 **toolchain 25로 통일**(로컬·빌드·Docker 동일). `eclipse-temurin:25`.

## 디렉터리

```
backend/    Spring Boot. com.ichi.*
              auth/     controller/, service/, filter/, exception/, dto/
              security/ service/TokenCipher (refresh_token AES-GCM)
              config/   SecurityConfig, IchiProperties
              storage/  service/StorageService, service/GoogleDriveStorage, drive/
              user/     domain/User, repository/UserRepository
              entry/    controller/, service/, repository/, domain/, exception/, dto/
              place/    controller/, service/, dto/ (VWorld 주소→좌표 프록시)
              settings/ controller/
frontend/   Vue 3.5. src/*
              api/client.ts     fetch 헬퍼(credentials: include, ApiError)
              auth/google.ts    OAuth authorize URL + state
              stores/auth.ts    Pinia 인증 상태
              layouts/ views/ components/ router/ styles/
            capacitor.config.ts — 모바일 래퍼 설정
docker-compose.yml   postgres:17 + backend + frontend
.env.example / .env  시크릿 placeholder
```

## 빌드 / 실행 — **개발자가 직접 한다**

> ⚠️ **에이전트는 앱을 직접 기동·실행·런타임 검증하지 못한다(=하지 않는다).**
>
> - 금지: `docker compose up`, `gradlew bootRun`, `npm run dev`, 그리고 **기동된 서버에 대한 curl/health 폴링·E2E 확인**.
> - 이유: 이 환경의 Bash 셸은 권한/네트워크 제약이 있고(`/etc/*` 심볼릭링크 실패 등), 장시간·포트 점유 명령은 부적절하다. **실제 기동과 동작 확인(브라우저 접속, `/actuator/health`, 로그인/저장 플로우)은 전적으로 개발자가 한다.**
> - 에이전트가 할 수 있는 것: **컴파일/타입체크/빌드 산출물 확인까지만** (`gradlew compileJava`, `npm run build`). 그 이상은 "이 명령으로 실행/확인하시면 됩니다"로 안내한다.
> - docker-compose 트러블슈팅(포트 충돌 등)으로 설정 파일을 고치는 것은 가능하나, **기동 자체는 개발자에게 맡긴다.**

개발자용 실행 명령(참고):

```bash
# 전체 (Docker)
cp .env.example .env      # 값 채우기
docker compose up --build

# 백엔드만 (로컬)
cd backend && ./gradlew bootRun

# 프론트만 (로컬)
cd frontend && npm install && npm run dev

# 모바일
cd frontend && npm run build && npx cap sync
```

확인: 프론트 http://localhost:5173 · 헬스 http://localhost:8080/actuator/health

## 마일스톤 진행

- **M1 스캐폴딩** ✅ — docker-compose, Actuator 헬스, Flyway V1 스키마, Vue 앱 셸(사이드바 5+1, 컴포저, 카드), Capacitor 설정.
- **M2 인증** ✅ — Google OAuth → users upsert(refresh_token AES-GCM) → JWT(HttpOnly 쿠키) → 보호 라우트. `POST /api/auth/google`·`GET /api/auth/me`·`POST /api/auth/logout`.
- **M3 저장소+노트** ✅ — `GoogleDriveStorage`(StorageService 구현, Drive v3 REST) + 노트 CRUD(작성/목록/상세/본문수정/핀/색/삭제). 본문은 리치텍스트 HTML JSON 으로 Drive 저장(`buildBody`, **version3**: html + 위치 lat/lng/placeName), `entries` 메타 INSERT, `preview`는 텍스트 추출. 인라인 이미지: `POST /api/media`(Drive 업로드→`media` 행)→본문 `<img data-ref>`, 표시는 `GET /api/media/{id}/raw`. HTML sanitize 양쪽(백 OWASP, 프론트 DOMPurify). V2 마이그레이션(media.user*id, entry_id nullable). **V3~V4**: 계층형 폴더 구조 `ichi/YYYY-MM-DD/<preview10>_<entryId>/` 내부에 `json/body.json`(앱이 읽는 원본 본문 HTML JSON, `drive_file_id` 가 가리킴), `<YYYY-MM-DD>_일기장.txt`(사람이 Drive 에서 바로 읽는 사본, 태그 제거 텍스트, 작성·수정 시 함께 갱신), `media/`(파일들). (이전엔 `body.html` 사본이었으나 txt 로 교체)

- **M4 미디어** ✅ — 갤러리 뷰(`MediaView` 그리드) + 서버 썸네일 생성(`Thumbnailer`, ImageIO → `thumb_file_id`) + 영상 업로드(`POST /api/media/video`) + 갤러리→노트 연결(`?entry=` 강조). 엔드포인트: `GET /api/media`(목록), `GET /api/media/{id}/thumb`. 썸네일 없으면 원본 폴백.
- **M5 캘린더** ✅ — `CalendarView` 월간 그리드(이전/다음/오늘, 일기 있는 날 점 표시) + 선택일 패널(그날 일기 목록→클릭 시 `?entry=` 로 노트 강조 이동). 백엔드는 `GET /api/entries?from&to`(기간 조회) 추가.
- **M6 아틀라스** ✅ — 컴포저 위치 첨부(Geolocation, lat/lng) + `AtlasView`(Google Maps, `VITE_GOOGLE_MAPS_API_KEY`, AdvancedMarkerElement). **핀이 그 일기의 첫 사진 썸네일**로 표시(`thumbMediaId`), 클릭 시 미리보기→노트 이동. 백엔드 `GET /api/entries/atlas`(위치 있는 일기+핀 썸네일). 키 없으면 안내 오버레이.
- M7 설정. (각 마일스톤 끝에서 동작 확인 후 커밋)
- **Drive 복원(re-index)** ✅ — `body.json` v3 에 위치 메타 포함 + `DriveRestoreService`(`ichi/` 스캔 → DB 누락 일기만 메타 INSERT). API `GET/POST /api/entries/restore`(`{scanned,restored,skipped}`), 설정→저장소 "일기 복원" 버튼. 한계: media 행·color·pin 은 복원 안 함, v2 본문은 위치 빠짐. (`GoogleDriveClient.listChildren` 추가) — 자세히는 `docs/DEVELOPMENT.md` "Drive 복원".

## 작업 규약

- 커밋은 **마일스톤 단위**, 의미 있는 메시지. 커밋/푸시는 개발자가 요청할 때만.
- 비가역 결정(DB 스키마, 인증 흐름, 저장소 구조)은 코드 쓰기 전에 짧게 확인받는다.
- 현재 결정 기록:
  - 세션 = **JWT(stateless)**, HttpOnly 쿠키 `ichi_session`, 운영 HTTPS 는 `ichi.cookie-secure=true`
  - refresh_token = `TOKEN_ENC_KEY` 있으면 AES-256-GCM, 없으면 개발 모드 평문+경고
  - 구조 = 모노레포 `backend/` + `frontend/`
  - Java = toolchain **25**, Gradle wrapper **9.5.1**
  - Lombok = setter 금지(도메인 메서드/`@Builder`), `@ConfigurationProperties` 는 `@ConstructorBinding`
  - 헬스 = Actuator `/actuator/health` (커스텀 컨트롤러 X)
  - Jackson = **3** (`tools.jackson.*`, `asString()`)
