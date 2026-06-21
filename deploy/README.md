# 배포 (블루그린 무중단) — 백엔드 blue/green

자가 호스팅(자택 SFF/Ubuntu) 블루그린 무중단 배포 구성입니다. 진입점은 외부의
**Nginx Proxy Manager(NPM)** 가 맡아 도메인·TLS 를 처리하고, 컨테이너명으로 프록시합니다.
백엔드만 두 색(blue/green)으로 띄우고, **API 엣지 nginx(`api-edge`)** 가 active 색으로
트래픽을 보냅니다(블루그린 스위치). **프론트는 api-edge 를 거치지 않습니다** — NPM 이
frontend 컨테이너로 직접 보냅니다(프론트는 단일, 블루그린 대상 아님). DB·프론트는 단일입니다.

```
        Cloudflare → 공유기 → ┌──────── NPM (도메인·TLS) ────────┐
                              │  도메인 /     → ichi-frontend:80  │  (정적 SPA, 단일)
                              │  도메인 /api/ → ichi-api-edge:80  │
                              └──────────────────┬───────────────┘
                                                 │ /api/  /healthz
                              ┌──── api-edge(nginx :80) ────┐
                              │  /api/  → backend_active     │  ← active-upstream.conf
                              └──────────┬─────────┬────────┘     (switch.sh 가 교체)
                                         │         │
                              ichi-backend-blue  ichi-backend-green  ← 같은 DB 공유(stateless)
                                         └──── db(postgres 17) ────┘

  frontend/api-edge 는 NPM 과 같은 npm-network 에 join. 내부 컨테이너는 포트 외부 노출 없음.
```

## 파일

| 파일 | 역할 |
| --- | --- |
| `docker-compose.app.yml` | db + backend-blue + backend-green + frontend + api-edge |
| `nginx/api-edge.conf` | API 엣지 라우팅(`/api/` → active 백엔드, `/healthz`). 프론트는 다루지 않음 |
| `nginx/upstream.blue.conf` · `upstream.green.conf` | 각 색 upstream 정의(전환 소스) |
| `nginx/active-upstream.conf` | **현재 active** upstream(api-edge 에 마운트, switch.sh 가 교체) |
| `switch.sh` | 유휴 색 배포 → health 확인 → 트래픽 전환 → 직전 색 정지 |

> 진입점은 NPM(별도 운영). 이 compose 는 포트를 외부로 노출하지 않고 `frontend`·`api-edge`
> 를 NPM 의 외부 네트워크(`NPM_NETWORK`, 기본 `npm-network`)에 연결합니다.
> CI(Jenkins) compose 는 이 파일과 **절대 섞지 않습니다**(별도 파일/네트워크).

## 준비

```bash
cd deploy
cp ../.env .env        # 운영 값으로 채움 (아래 필수 키 확인)
```

`docker-compose.app.yml` 은 운영 안전을 위해 다음 키가 비어 있으면 **기동을 거부**합니다(`:?`):
`POSTGRES_PASSWORD`, `JWT_SECRET`(≥32바이트), `TOKEN_ENC_KEY`, `GOOGLE_REDIRECT_URI`,
`CORS_ALLOWED_ORIGINS`, `VITE_GOOGLE_REDIRECT_URI`.

운영 추가 키:
- `NPM_NETWORK` — NPM 이 쓰는 외부 도커 네트워크 이름(기본 `npm-network`). `docker network ls` 로 확인.
- `ICHI_COOKIE_SECURE` — HTTPS 운영 시 `true`(기본 true)
- `IMAGE_TAG` — 이미지 태그(기본 latest)

> 프론트의 `VITE_*` 값은 **빌드 시점에 번들로 박힙니다**(compose `build.args` 로 주입).
> 값을 바꾸면 frontend 를 **재빌드**해야 반영됩니다.

> ⚠️ `npm-network` 는 **NPM 이 먼저 만들어 둔** 외부 네트워크여야 합니다(`external: true`).
> 없으면 `up` 이 실패합니다. NPM 컨테이너를 먼저 기동하거나 네트워크명을 `NPM_NETWORK` 로 맞추세요.

## 최초 기동

```bash
cd deploy
docker compose -f docker-compose.app.yml up -d --build db frontend api-edge backend-blue
# 초기 active = blue (active-upstream.conf 기본값)
```

NPM 설정(웹 UI):
- 도메인 `ichilog.com` → Forward `http://ichi-frontend:80` (정적 SPA)
- 같은 도메인의 `/api/` 경로 → Forward `http://ichi-api-edge:80` (Custom Location 으로 추가)
- TLS 인증서는 NPM 에서 발급/적용

확인 (NPM 도메인 기준):
- 앱: `https://<도메인>/`
- 헬스: `https://<도메인>/healthz` → `{"status":"UP",...}`
- (내부 점검) `docker exec ichi-api-edge wget -qO- http://localhost/healthz`

## 무중단 배포(전환)

새 버전을 유휴 색에 띄우고, health 통과 후 트래픽을 넘깁니다.

```bash
cd deploy
./switch.sh green     # 현재 blue 면 green 으로 (인자 생략 시 반대 색 자동)
```

`switch.sh` 가 하는 일:
1. `backend-green` 빌드/기동
2. 컨테이너 healthcheck 가 `healthy` 될 때까지 대기(실패 시 전환 중단, 트래픽은 기존 색 유지)
3. `active-upstream.conf` 를 green 으로 교체 → `nginx -t` → `nginx -s reload`(무중단)
4. 직전 색(`backend-blue`) 정지(롤백 대비 컨테이너는 보존)

### 롤백

```bash
./switch.sh blue      # 직전 색으로 즉시 되돌림 (이미 빌드돼 있어 빠름)
```

## 주의 (Flyway 스키마)

blue/green 이 **같은 DB** 를 봅니다. 파괴적 스키마 변경은 두 버전이 동시에 떠도 깨지지
않도록 **expand → contract** 패턴으로 나눠 적용하세요(컬럼 추가는 nullable 로 먼저,
구버전 내린 뒤 정리 마이그레이션). health 게이트는 `/actuator/health` 를 그대로 씁니다.

> 자택 단일 머신(SFF/Ubuntu) 자가 호스팅 기준. 도메인·TLS 는 앞단 NPM 이, 외부 노출은
> Cloudflare + 공유기 포트포워딩이 담당합니다. 시크릿은 `deploy/.env` 로만 주입(커밋 금지).
