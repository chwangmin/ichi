# 이치 (一 / Ichi) — 개발 문서

> 📌 이 문서는 **개발자용 명세서**입니다. 서비스 소개는 [루트 README](../README.md)를 보세요.

> Google Keep처럼 가볍게 하루를 끄적이는 일기장.
> **일기 본문과 미디어는 사용자 본인의 Google Drive("ichi" 폴더, drive.file 스코프)에 저장**되고,
> PostgreSQL에는 정렬·검색용 가벼운 메타데이터(포인터)만 둡니다.

---

## 구조 (모노레포)

```
4_ichi/
├── docker-compose.yml      # postgres:17 + backend + frontend 한 번에 실행
├── .env.example            # 시크릿 placeholder (복사해서 .env 작성)
├── backend/                # Spring Boot 4.1.0 / Java 25 / Gradle
└── frontend/               # Vue 3.5 + Vite + TypeScript + Pinia + Vue Router
```

### 아키텍처 핵심

```
[Vue] 작성
   ├─→ [Backend] StorageService.save(json) → Google Drive("ichi" 폴더) → drive_file_id
   └─→ [Backend] entries 메타 INSERT (drive_file_id, 날짜, 미리보기, 위치…) → PostgreSQL
```

- **PostgreSQL**: 계정 + 일기/미디어 **메타데이터**
- **Google Drive ("ichi" 폴더, drive.file)**: 실제 일기 콘텐츠(JSON) + 사진/영상
- 저장/불러오기는 전부 `StorageService` 인터페이스 한 곳을 통과합니다. (나중에 `EncryptedStorage`로 감싸 E2E 암호화를 끼울 수 있도록)

---

## 빠른 실행

> 사전 준비: Docker Desktop, 그리고 아래 **Google Cloud 준비**를 끝내고 `.env`를 채워야 로그인/Drive 기능이 동작합니다.
> (OAuth 값 없이도 앱 셸과 헬스체크는 뜹니다. 로그인 버튼은 키가 없으면 비활성됩니다.)

```bash
cp .env.example .env      # 값 채우기
docker compose up --build
```

- 프론트: http://localhost:5173
- 백엔드 헬스체크: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5432

### 헬스체크

Spring Boot **Actuator**가 제공합니다(별도 컨트롤러 없음). DB 연결 상태까지 포함해 검사합니다.

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"db":{"status":"UP",...},...}}
```

- `GET /actuator/health` — 앱 + DB 등 구성요소 종합 상태
- `GET /actuator/health/liveness` · `/readiness` — 프로브용
- docker-compose 의 backend healthcheck 도 이 엔드포인트를 사용합니다.

### 로컬에서 따로 실행 (Docker 없이)

```bash
# 백엔드 (Java 25 권장, Gradle toolchain이 25을 자동 확보)
cd backend && ./gradlew bootRun

# 프론트
cd frontend && npm install && npm run dev
```

---

## Google Cloud 준비 (개발자가 먼저 할 일)

이치는 Google 로그인 + Google Drive + Google Maps를 사용합니다. 아래를 본인 계정으로 발급하세요.

### 1. 프로젝트 생성

1. https://console.cloud.google.com 접속
2. 상단 프로젝트 선택 → **새 프로젝트** → 이름 예: `ichi-diary`

### 2. API 활성화

**API 및 서비스 > 라이브러리**에서 다음을 각각 검색해 **사용 설정**:

- **Google Drive API**
- **Maps JavaScript API** (M6 아틀라스 지도 표시용)
- **Geocoding API** (위치 → 장소명 변환용 — 일기 위치 칩, 아틀라스 새 핀/이동의 장소명에 사용)

### 3. OAuth 동의 화면

**API 및 서비스 > OAuth 동의 화면**

1. User Type: **외부(External)** → 만들기
2. 앱 이름 `이치`, 지원 이메일 본인 메일 입력
3. **범위(Scopes)** 추가:
   - `openid`
   - `.../auth/userinfo.email`
   - `.../auth/userinfo.profile`
   - `.../auth/drive.file` ← 이 앱이 만든 파일/폴더만 접근 (사용자의 다른 Drive 파일엔 접근 안 함)
4. **테스트 사용자**에 본인 Google 계정 추가 (테스트 모드로 시작)

### 4. OAuth 2.0 클라이언트 ID 발급

**API 및 서비스 > 사용자 인증 정보 > 사용자 인증 정보 만들기 > OAuth 클라이언트 ID**

1. 애플리케이션 유형: **웹 애플리케이션**
2. **승인된 JavaScript 원본**:
   - `http://localhost:5173`
3. **승인된 리디렉션 URI**:
   - `http://localhost:5173/auth/callback`
4. 생성 후 **클라이언트 ID / 클라이언트 보안 비밀**을 복사 → `.env`의
   `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`에 입력

### 5. Maps JavaScript API 키

**사용자 인증 정보 > API 키 만들기**

1. 생성된 키를 `.env`의 `VITE_GOOGLE_MAPS_API_KEY`에 입력
2. (권장) 키 제한: HTTP 리퍼러 `http://localhost:5173/*`, Maps JavaScript API로 제한

### 6. JWT / 암호화 키

```bash
# 세션 JWT 서명 키
openssl rand -base64 48     # → JWT_SECRET

# (선택) refresh_token 암호화 키 — 운영에선 반드시 설정
openssl rand -base64 32     # → TOKEN_ENC_KEY
```

> `.env`는 절대 커밋하지 마세요. 시크릿은 모두 환경변수로만 관리합니다.

### 7. VWorld Geocoder API 키

날씨 위치에서 한국 도로명/지번 주소를 좌표로 바꿀 때 사용합니다.

1. VWorld에서 인증키를 발급
2. `.env`의 `VWORLD_API_KEY`에 입력
3. 프론트에는 노출하지 않고 백엔드 `/api/places/search` 프록시가 사용

---

## 인증 흐름 (M2)

세션은 **JWT(stateless)** 이며 **HttpOnly 쿠키**(`ichi_session`)로 전달됩니다.

```
[Vue] LoginView → Google 동의 화면 (scope: openid email profile drive.file, access_type=offline)
   → /auth/callback?code=...&state=...
   → [Front] POST /api/auth/google { code }
      → [Back] Google 토큰 교환 → id_token 파싱(sub/email/name/picture) + refresh_token
      → users UPSERT (refresh_token 은 TokenCipher 로 암호화)   ← 회원 정보 저장
      → JWT 발급 → Set-Cookie: ichi_session (HttpOnly)
   → 이후 /api/** 요청은 쿠키의 JWT 로 인증 (JwtAuthenticationFilter)
```

엔드포인트:
- `POST /api/auth/google` — code 로 로그인, 세션 쿠키 발급, 사용자 정보 반환
- `GET /api/auth/me` — 현재 로그인 사용자 (미인증 401)
- `POST /api/auth/logout` — 세션 쿠키 만료

보호: `/api/auth/**` 와 `/actuator/health` 만 공개, 나머지 `/api/**` 는 인증 필요. CORS 허용 origin 은 `CORS_ALLOWED_ORIGINS`(쉼표 구분, 모바일 origin 포함 가능).

> 키를 아직 안 넣었다면: 코드/빌드는 모두 동작하지만 실제 Google 로그인은 `.env`에 `GOOGLE_CLIENT_ID/SECRET`, `VITE_GOOGLE_CLIENT_ID`를 채워야 됩니다.

---

## 모바일 앱 (Capacitor)

프론트(`frontend/`)는 **Capacitor**로 iOS/Android 네이티브 앱으로도 빌드됩니다. Vite 빌드 산출물(`dist/`)을 네이티브 웹뷰에 감싸는 방식이라 같은 코드베이스를 씁니다.

### 네이티브 플랫폼 추가 (최초 1회)

> 사전 준비: Android는 **Android Studio**, iOS는 **macOS + Xcode** 필요.

```bash
cd frontend
npm install
npm run build           # dist/ 생성

npx cap add android     # android/ 프로젝트 생성
npx cap add ios         # ios/ 프로젝트 생성 (macOS 전용)
```

### 빌드/실행

```bash
npm run cap:android     # build → sync → Android Studio 열기
npm run cap:ios         # build → sync → Xcode 열기  (macOS)
```

웹 코드를 고친 뒤에는 `npm run build && npx cap sync` 로 네이티브에 반영합니다.

> **주의**: 네이티브 앱은 `capacitor://`(또는 `http://localhost`) origin 에서 동작해 Vite의 `/api` 프록시가 없습니다. 앱에서는 백엔드를 **절대 URL**로 호출하고, 백엔드가 그 origin에 대해 **CORS를 허용**해야 합니다(M2에서 OAuth와 함께 처리). OAuth 리다이렉트도 모바일에서는 커스텀 스킴/딥링크 설정이 추가로 필요합니다.

---

## 보안 / 저장 정책 메모

- **refresh_token**은 우리 서버 DB에 저장됩니다(사용자 Drive 아님). `TOKEN_ENC_KEY`가 설정되면 AES-256-GCM으로 암호화 저장하고, 미설정 시 개발 모드에서 평문 + 경고 로그로 동작합니다. **운영에서는 반드시 키를 설정**하세요.
- 일기 **본문**은 사용자 Drive에만 저장됩니다. DB의 `preview`는 카드/검색용 평문 발췌이며, 향후 E2E 암호화 도입 시 암호화/제거 대상입니다.

### Drive 파일 구조

일기 하나당 Drive 에 폴더(`ichi/YYYY-MM-DD/<preview10>_<entryId>/`)가 생기고 그 안에:

- `json/body.json` — **앱이 읽고 쓰는 원본** 본문(리치텍스트 HTML JSON). `entries.drive_file_id` 가 이 파일을 가리킨다.
- `<YYYY-MM-DD>_일기장.txt` — **사람이 Drive 에서 바로 읽는 사본**(태그 제거한 순수 텍스트, 줄바꿈 보존). 작성·수정 때마다 함께 갱신.
- `media/` — 인라인 이미지·영상 파일들.

### Drive 복원 (re-index) — 구현됨

본문의 **원본은 Drive**, DB(`entries`)는 정렬·검색용 **포인터**일 뿐이다. 그래서 DB가 비거나(재설치·기기 이전·메타 행 유실) 사용자가 돌아왔을 때, **Drive `ichi/` 폴더를 스캔해 DB 메타를 다시 만드는 복원**이 가능하다. 본문을 DB로 가져오는 게 아니라 **포인터만 재생성**하므로 "본문은 DB에 저장 안 한다" 원칙을 깨지 않는다.

**범위: 누락분만 채우기.** Drive 엔 있는데 `entries` 에 없는 일기만 찾아 INSERT 한다. 기존 행은 건드리지 않는다(덮어쓰기 위험 회피). 전체 재동기화·미디어 재구성은 후속 과제.

**구현:**
- `DriveRestoreService.restore(userId, apply)` — `ichi/` → 날짜 폴더 → 일기 폴더 순으로 `GoogleDriveClient.listChildren()` 로 훑고, 폴더명 끝 UUID 가 DB 에 없으면 `json/body.json` 을 읽어 메타 INSERT. `apply=false` 면 미리보기(복원 대상 수만 계산).
- API: `GET /api/entries/restore`(미리보기), `POST /api/entries/restore`(실행). 응답 `{ scanned, restored, skipped }`.
- 프론트: 설정 → 저장소 → **"일기 복원"** 버튼. 미리보기로 대상 수 확인 후 실행, 결과 메시지 표시.

**폴더 구조에서 바로 복원되는 것:**

| `entries` 컬럼  | 출처                                              |
| --------------- | ------------------------------------------------- |
| `entry_date`    | 날짜 폴더명 `YYYY-MM-DD`                           |
| `id` (entryId)  | 일기 폴더명 끝의 UUID (`<preview10>_<entryId>`)    |
| `drive_file_id` | `json/body.json` 파일 ID                          |
| `preview`       | `body.json` 의 html → `HtmlSanitizer.toPreview()` |
| `lat`/`lng`/`place_name` | `body.json`(**version 3+**) 의 위치 메타            |

**`body.json` 버전:** version 3 부터 `lat`/`lng`/`placeName` 을 본문 JSON 에 함께 적는다(위치가 있을 때만). 그 전 버전(v2)으로 저장된 일기는 위치가 본문에 없어, **복원 시 위치가 빠진다**(본문·날짜·preview 는 정상). v3 이후 작성/수정된 일기는 위치까지 온전히 복원된다.

**한계:**

- **`media` 행**(사진/영상)은 복원하지 않는다. `media/` 폴더 파일을 훑어 재구성(+썸네일 재생성)하는 건 후속 과제.
- **색(`color`)** 은 Drive 에 없어 복원 시 기본값(null). **핀** 도 false 로 초기화.

---

## 마일스톤

|     | 내용                                                                  | 상태    |
| --- | --------------------------------------------------------------------- | ------- |
| M1  | 스캐폴딩 (Gradle + Vite + PostgreSQL + docker-compose, `/api/health`) | 완료    |
| M2  | Google OAuth 로그인 → `users` 저장 → JWT 세션 → 보호 라우트           | 완료    |
| M3  | `StorageService`(GoogleDriveStorage) + 노트 CRUD + 인라인 이미지       | 완료    |
| M4  | 미디어 갤러리(썸네일·영상) + 갤러리↔노트 연결                          | 완료    |
| M5  | 캘린더 월간 뷰 (일기 있는 날 표시, 날짜→일기)                          | 완료    |
| M6  | 아틀라스 (Google Maps + 사진 썸네일 핀)                               | 완료    |
| M7  | 설정 (계정/로그아웃/저장소 상태)                                      | 완료    |

---

## 추가 기능 (M7 이후 개선)

핵심 마일스톤 이후에 붙인 사용성·분위기 개선들입니다.

### 일기 / 저장

- **사람이 읽는 본문 사본**: Drive 일기 폴더에 `<날짜>_일기장.txt`(태그 제거 텍스트)를 함께 저장해, Drive 에서 바로 열어볼 수 있습니다. 앱이 읽는 원본은 `json/body.json` (위 "Drive 파일 구조" 참고).
- **삭제 확인 모달**: 노트 카드 삭제 시 바로 지우지 않고 확인 대화상자(`ConfirmDialog`)를 띄웁니다. (Esc·배경 클릭 = 취소)
- **저장/열기 로딩 표시**: 완료 → 목록 반영까지 **스켈레톤 카드**, 기록 열 때 **스피너**로 텀을 메웁니다.
- 노트 카드에 **위치 칩**(요일 옆), 본문 영역은 **반투명**(날씨 배경이 은은히 비침).

### 날씨 (Open-Meteo, API 키 불필요)

- **상단바 날씨 칩**: 현재 위치 기온 + 한국어 날씨. 직전 날씨를 캐시해 새로고침 깜빡임 방지. 위치 거부/실패 시 **서울**로 폴백.
  - 기온은 **ECMWF 모델**(`ecmwf_ifs025`)로 받습니다. 기본(`best_match`) 모델이 도심에서 실측보다 몇 도 낮게 나오는 격자 보간 한계가 있어, 실측에 더 가까운 ECMWF 를 쓰고 ECMWF 값이 비면 기본 모델로 자동 폴백합니다. `timezone=Asia/Seoul` 로 낮/밤(테마·아이콘) 판정도 한국 기준입니다.
- **움직이는 날씨 배경**(기본 ON): 실제 날씨에 맞춰 비/눈 입자, 흐름 구름, 해·달·별, 번개, 하단 잔디·나무가 애니메이션됩니다. 흐림(강한 구름)이면 상단을 구름 띠 + 빽빽한 구름으로 덮어 "꽉 찬" 느낌을 줍니다. `prefers-reduced-motion` 존중. **설정 > 날씨 배경**에서 끌 수 있습니다.
- **날씨 위치 설정**: "현재 위치로 설정"(GPS) 또는 **도시 검색**(영문, 예: `Seoul`). 검색은 Open-Meteo geocoding 이라 **구글 키가 없어도** 됩니다. (지도 표시·일기 위치 장소명은 여전히 Google Maps 키 사용)

> 상단바 날씨/검색은 **Open-Meteo**(무료·키 불필요)를 씁니다. 결제·키 등록이 필요 없습니다.

### 테마 (다크/라이트)

- **설정 > 테마**에서 `자동`/`라이트`/`다크` 선택(`useTheme`, localStorage). 사용자가 고른 모드가 날씨보다 우선하며, `자동`은 날씨 배경이 켜진 상태에서 '밤'일 때만 어둡게 합니다. 모든 화면이 토큰(`--ink`/`--card`…) 기반이라 한 곳에서 일괄 전환됩니다.

### 아틀라스

- **지도에서 새 일기 쓰기**: 오른쪽 아래 **＋ 버튼**(FAB) → 핀이 **화면 중앙에 고정**되고 **지도를 움직여** 위치를 맞춥니다(드래그 마커가 아니라 카카오/우버식). `여기에 쓰기` → 좌표 역지오코딩 후 작성 모달이 그 위치로 채워진 채 열리고, 저장하면 그 자리에 새 핀이 생깁니다. 위치를 못 구하면 **문정역**으로 폴백.
- **핀 위치 이동**: 미리보기에서 기록별 위치 이동 버튼 → 핀을 드래그해 옮기고 저장(본문 유지, 좌표/장소명만 갱신).
- **근접 핀 클러스터링**: 5m 이내 핀들을 하나로 묶어 개수 표시(`useAtlasClusters`). 묶여 있어도 목록에서 개별 핀을 골라 이동할 수 있습니다.

### 캘린더

- **날짜 골라 일기 쓰기**: 선택한 날짜 패널의 **＋ 일기 쓰기** 버튼 → 그 날짜로 새 일기를 작성합니다(과거 날짜도 가능, 백엔드 `entryDate` 지원). 같은 작성/수정 모달(`EditNoteModal`)을 신규 모드로 재사용합니다.

### 화면 이동 / 작성 UX

- **위치 칩 → 아틀라스**: 일기 작성/수정 모달의 위치 칩(📍 장소명)을 누르면 아틀라스로 이동해 **그 기록 위치를 지도 한가운데**에 띄우고 미리보기를 엽니다(`?entry=<id>`).
- **저장 중 표시**: 저장을 누르면 모달이 **열린 채로** 그 위에 "저장 중…" 오버레이가 뜨고(Drive 업로드 완료까지), 성공해야 모달이 닫힙니다. 실패 시 모달이 유지돼 작성 내용이 보존됩니다.
- **앱 아이콘**: 사이드바 인장(一)과 같은 디자인의 SVG favicon(브라우저 탭/모바일 홈 아이콘).

### 설정 / 기타

- 설정 **계정 섹션 오른쪽에 로그아웃** 버튼.
- 상단바 **검색창 제거**(본문이 Drive 에 있어 전체 검색이 반쪽짜리라 일단 뺌).
- 캘린더를 **반투명 카드 패널**로 감싸 날씨 배경 위에서도 읽히게.

---

## 블루그린 무중단 배포 (백엔드 blue/green — 구현됨)

> **목표: 로컬 한 머신에서 연습용** 블루그린. 백엔드만 두 색으로 띄우고 엣지 nginx 가
> active 색으로 트래픽을 보냅니다. 구성/실행법은 **[`deploy/README.md`](../deploy/README.md)** 참고.
> (Jenkins CI/CD 파이프라인은 아직 미구현 — 아래 "향후" 참고)

```bash
cd deploy
cp ../.env .env                                  # 운영 값 채우기
docker compose -f docker-compose.app.yml up -d --build db frontend edge backend-blue
./switch.sh green                                # 유휴 색 배포 → health 확인 → 무중단 전환
./switch.sh blue                                 # 롤백
```

### 설계 개요

```
[Jenkins]  checkout → 백 gradlew test/bootJar → 프론트 npm build
           → docker build → (blue/green 중 유휴 색에 배포)
           → /actuator/health 통과 확인 → nginx upstream 스위치 → 구버전 down

[Nginx]    backend-blue / backend-green 두 세트 중 active 색만 upstream.
           무중단 = 유휴 색에 새 버전 띄우고 health OK 후 트래픽 전환.
```

### 파일 구조

- `deploy/docker-compose.app.yml` — 앱(backend blue/green + frontend + edge nginx + db). ✅ 구현
- `deploy/nginx/edge.conf` · `upstream.{blue,green}.conf` · `active-upstream.conf` — 엣지 라우팅 + active 색 전환용. ✅ 구현
- `deploy/switch.sh` — 무중단 전환/롤백 스크립트. ✅ 구현
- `deploy/docker-compose.ci.yml` — Jenkins(별도). 앱 compose 와 **절대 섞지 않는다**. (예정)
- `Jenkinsfile` — 파이프라인 정의. (예정)

### 향후 (CI 미구현)

Jenkins 파이프라인(checkout → test/build → docker build → `switch.sh` 호출)은 아직
없습니다. 지금은 `deploy/switch.sh` 를 **수동 실행**해 전환합니다.

### 주의

- 백엔드는 stateless(JWT)라 블루그린에 적합. 단 **Flyway 마이그레이션**은 blue/green 이 같은 DB 를 보므로, 파괴적 스키마 변경(V_x) 시 두 버전 호환성을 지킬 것(expand→contract 패턴).
- health 전환 게이트는 기존 **`/actuator/health`** 를 그대로 사용.
- 로컬 단일 머신 한정. 실서버/클라우드 배포는 도메인·TLS·시크릿 관리가 추가로 필요(범위 밖).
