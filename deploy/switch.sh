#!/usr/bin/env bash
# =========================================================================
#  블루그린 전환 스크립트 (백엔드만 blue/green).
#
#  하는 일:
#   1) 대상(유휴) 색 백엔드를 새로 빌드/기동
#   2) 그 색이 /actuator/health 로 UP 될 때까지 대기
#   3) api-edge nginx 의 active upstream 을 대상 색으로 교체 → reload (무중단 전환)
#   4) 직전 active 색 백엔드를 정지(롤백 대비로 컨테이너는 남겨둠)
#
#  사용:
#    cd deploy
#    ./switch.sh green     # green 으로 전환 (현재 blue 면)
#    ./switch.sh blue      # blue 로 전환
#    ./switch.sh           # 인자 없으면 현재 active 의 반대 색으로 자동 전환
#
#  전제: deploy/.env 준비됨. db/frontend/api-edge 는 이미 up 상태.
# =========================================================================
set -euo pipefail

cd "$(dirname "$0")"

COMPOSE="docker compose -f docker-compose.app.yml"
EDGE_CONTAINER="ichi-api-edge"
ACTIVE_FILE="nginx/active-upstream.conf"

# --- 현재 active 색 판별 (active-upstream.conf 내용 기준) ---
current_color() {
  if grep -q "ichi-backend-green" "$ACTIVE_FILE" 2>/dev/null; then
    echo green
  else
    echo blue
  fi
}

CURRENT="$(current_color)"
TARGET="${1:-}"
if [[ -z "$TARGET" ]]; then
  TARGET=$([[ "$CURRENT" == "blue" ]] && echo green || echo blue)
fi

if [[ "$TARGET" != "blue" && "$TARGET" != "green" ]]; then
  echo "사용법: $0 [blue|green]" >&2
  exit 1
fi

echo "▶ 현재 active: $CURRENT  →  전환 대상: $TARGET"

if [[ "$TARGET" == "$CURRENT" ]]; then
  echo "이미 $TARGET 이 active 입니다. (그래도 재빌드/기동은 진행)"
fi

TARGET_SVC="backend-$TARGET"
TARGET_CONTAINER="ichi-backend-$TARGET"

# --- 1) 대상 색 빌드 + 기동 ---
echo "▶ $TARGET_SVC 빌드/기동…"
$COMPOSE up -d --build "$TARGET_SVC"

# --- 2) health 대기 (컨테이너 healthcheck 상태를 폴링) ---
echo "▶ $TARGET_CONTAINER health 대기…"
for i in $(seq 1 60); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$TARGET_CONTAINER" 2>/dev/null || echo starting)"
  if [[ "$status" == "healthy" ]]; then
    echo "  ✓ healthy"
    break
  fi
  if [[ "$i" == "60" ]]; then
    echo "  ✗ health 실패 — 전환 중단(트래픽은 $CURRENT 유지)" >&2
    exit 1
  fi
  sleep 3
done

# --- 3) upstream 교체 + edge reload (무중단 전환) ---
echo "▶ 트래픽 전환: active upstream → $TARGET"
cp "nginx/upstream.$TARGET.conf" "$ACTIVE_FILE"
docker exec "$EDGE_CONTAINER" nginx -t
docker exec "$EDGE_CONTAINER" nginx -s reload
echo "  ✓ 전환 완료"

# --- 4) 직전 색 정지 (롤백 대비 컨테이너는 보존) ---
if [[ "$TARGET" != "$CURRENT" ]]; then
  echo "▶ 직전 색 backend-$CURRENT 정지(보존)…"
  $COMPOSE stop "backend-$CURRENT" || true
fi

echo "✅ $TARGET 로 전환 완료. (롤백: ./switch.sh $CURRENT)"
