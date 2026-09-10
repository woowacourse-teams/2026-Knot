#!/usr/bin/env bash
#
# Knot 로컬 전체 기동 — postgres → backend(:8080) → frontend(:3000) → 데스크톱 셸(local)
#
# 데스크톱 셸의 local 환경은 빌드 시점에 웹 http://localhost:3000, API
# http://localhost:8080으로 고정돼 있다(desktop/src/shared/env.ts). 그래서 이 스크립트는
# 포트를 바꿀 수 있게 하지 않고, 세 개를 그 주소에 맞춰 순서대로 띄운다.
#
# 이미 떠 있는 것은 다시 띄우지 않고 그대로 쓴다. Ctrl+C 한 번으로 **이 스크립트가 띄운
# 것만** 정리한다(postgres 컨테이너와 원래 떠 있던 프로세스는 건드리지 않는다).
#
# 사용법: ./scripts/dev.sh [--skip-db] [--skip-backend] [--skip-web] [--skip-desktop] [--open]

set -euo pipefail
# 각 자식을 별도 프로세스 그룹으로 띄운다 — gradle이 포크한 java까지 그룹으로 끄기 위해서다.
set -m

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
LOG_DIR="$ROOT/.dev-logs"
WEB_URL="http://localhost:3000"
API_URL="http://localhost:8080"
# 3000을 다른 앱(예: 개인 블로그 dev 서버)이 잡고 있으면 잘못 붙는다. 웹 dev 서버가
# 내려주는 번들 태그로 Knot인지 확인한다(webpack output.filename=bundle.js).
WEB_MARKER='src="/bundle.js"'
JDK_FALLBACK="$HOME/Library/Java/JavaVirtualMachines/jdk-25.0.4.1+1/Contents/Home"

SKIP_DB=0
SKIP_BACKEND=0
SKIP_WEB=0
SKIP_DESKTOP=0
OPEN_BROWSER=0

# 이 스크립트가 띄운 자식들. 정리는 이 목록에 있는 것만 한다.
STARTED_PIDS=""

usage() {
  sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

info() { printf '\033[36m▸\033[0m %s\n' "$*"; }
ok() { printf '\033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '\033[33m!\033[0m %s\n' "$*" >&2; }
die() {
  printf '\033[31m✗\033[0m %s\n' "$*" >&2
  exit 1
}

for arg in "$@"; do
  case "$arg" in
    --skip-db) SKIP_DB=1 ;;
    --skip-backend) SKIP_BACKEND=1 ;;
    --skip-web) SKIP_WEB=1 ;;
    --skip-desktop) SKIP_DESKTOP=1 ;;
    --open) OPEN_BROWSER=1 ;;
    -h | --help)
      usage
      exit 0
      ;;
    *) die "모르는 옵션: $arg (--help로 사용법을 본다)" ;;
  esac
done

cleanup() {
  local pid
  for pid in $STARTED_PIDS; do
    # 프로세스 그룹째로 끈다(gradle → java, webpack → 자식).
    kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  done
  # 종료를 기다린다. 남으면 KILL.
  local waited=0
  while [ "$waited" -lt 10 ]; do
    local alive=0
    for pid in $STARTED_PIDS; do
      kill -0 "$pid" 2>/dev/null && alive=1
    done
    [ "$alive" -eq 0 ] && break
    sleep 1
    waited=$((waited + 1))
  done
  for pid in $STARTED_PIDS; do
    kill -KILL "-$pid" 2>/dev/null || true
  done
  [ -n "$STARTED_PIDS" ] && info "이 스크립트가 띄운 프로세스를 정리했다. postgres 컨테이너는 그대로 둔다."
  return 0
}
trap cleanup EXIT INT TERM

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "$1 이(가) 없다. $2"
}

# 조건이 참이 될 때까지 기다린다. wait_for <설명> <타임아웃초> <로그파일|-> <명령...>
wait_for() {
  local label="$1" timeout="$2" logfile="$3"
  shift 3
  local waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if "$@" >/dev/null 2>&1; then
      ok "$label 준비됨 (${waited}s)"
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
    # 점을 계속 찍는 대신 15초마다 한 줄만 남긴다.
    if [ $((waited % 15)) -eq 0 ]; then
      info "$label 대기 중… ${waited}s"
    fi
  done
  if [ "$logfile" != "-" ] && [ -f "$logfile" ]; then
    warn "$label 이(가) ${timeout}s 안에 뜨지 않았다. 로그 마지막 30줄:"
    tail -n 30 "$logfile" >&2
  fi
  die "$label 기동 실패"
}

backend_healthy() {
  curl -fsS -m 3 "$API_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'
}

web_up() {
  curl -fsS -m 3 "$WEB_URL" 2>/dev/null | grep -qF "$WEB_MARKER"
}

port_busy() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

# frontend·desktop은 Node 22 이상을 요구한다(각 package.json engines).
ensure_node() {
  local major
  major=$(node -v 2>/dev/null | sed 's/^v//; s/\..*//' || true)
  if [ -n "$major" ] && [ "$major" -ge 22 ]; then
    return
  fi
  if [ -s "$HOME/.nvm/nvm.sh" ]; then
    info "셸 Node가 v${major:-없음}이라 nvm으로 22를 쓴다"
    set +u
    # shellcheck disable=SC1091
    . "$HOME/.nvm/nvm.sh"
    nvm use 22 >/dev/null 2>&1 || true
    set -u
    major=$(node -v 2>/dev/null | sed 's/^v//; s/\..*//' || true)
  fi
  if [ -z "$major" ] || [ "$major" -lt 22 ]; then
    die "Node 22 이상이 필요하다(현재 v${major:-없음}). \`nvm install 22 && nvm use 22\` 후 다시 실행한다."
  fi
  ok "Node $(node -v)"
}

# backend 빌드는 JDK 25가 필요하다(toolchain 자동 다운로드가 없다).
ensure_java() {
  local candidate=""
  if [ -n "${JAVA_HOME:-}" ] && java_is_25 "$JAVA_HOME"; then
    candidate="$JAVA_HOME"
  fi
  if [ -z "$candidate" ] && [ -x /usr/libexec/java_home ]; then
    local from_libexec
    from_libexec=$(/usr/libexec/java_home -v 25 2>/dev/null || true)
    if [ -n "$from_libexec" ] && java_is_25 "$from_libexec"; then
      candidate="$from_libexec"
    fi
  fi
  if [ -z "$candidate" ] && java_is_25 "$JDK_FALLBACK"; then
    candidate="$JDK_FALLBACK"
  fi
  [ -n "$candidate" ] || die "JDK 25가 없다. Temurin 25를 설치하고(예: \`brew install --cask temurin@25\`) 다시 실행한다."
  export JAVA_HOME="$candidate"
  ok "JDK 25 — $JAVA_HOME"
}

java_is_25() {
  local home="$1"
  [ -x "$home/bin/java" ] || return 1
  "$home/bin/java" -version 2>&1 | head -1 | grep -qE '"2[5-9]|"[3-9][0-9]'
}

track() {
  STARTED_PIDS="$STARTED_PIDS $1"
}

mkdir -p "$LOG_DIR"

need_cmd curl "macOS 기본 제공이므로 PATH를 확인한다."
need_cmd lsof "macOS 기본 제공이므로 PATH를 확인한다."

# ── 1. postgres ────────────────────────────────────────────────────────────────
if [ "$SKIP_DB" -eq 1 ]; then
  info "postgres 건너뜀(--skip-db)"
else
  need_cmd docker "Docker Desktop을 켜고 다시 실행한다."
  docker info >/dev/null 2>&1 || die "Docker 데몬이 꺼져 있다. Docker Desktop을 켜고 다시 실행한다."
  info "postgres 기동(backend/compose.yml)"
  docker compose -f "$ROOT/backend/compose.yml" up -d postgres >/dev/null
  wait_for "postgres" 60 - bash -c \
    '[ "$(docker inspect -f "{{.State.Health.Status}}" knot-postgres 2>/dev/null)" = "healthy" ]'
fi

# ── 2. backend ─────────────────────────────────────────────────────────────────
BACKEND_LOG="$LOG_DIR/backend.log"
if [ "$SKIP_BACKEND" -eq 1 ]; then
  info "backend 건너뜀(--skip-backend)"
elif backend_healthy; then
  ok "backend 이미 떠 있음 — $API_URL 을 그대로 쓴다"
elif port_busy 8080; then
  die ":8080을 Knot 백엔드가 아닌 다른 프로세스가 쓰고 있다. 그 프로세스를 끄거나 --skip-backend로 실행한다."
else
  [ -f "$ROOT/backend/src/main/resources/application-local.properties" ] ||
    die "backend/src/main/resources/application-local.properties가 없다(gitignore 대상). DB·OAuth·JWT·초대 키를 채운 뒤 다시 실행한다."
  ensure_java
  # llm.chat.provider=anthropic은 키가 없으면 기동 시점에 LLM_CONFIGURATION_INVALID로 죽는다.
  # 키가 없으면 fake로 띄운다 — 서버 LLM을 안 쓰는 검증(앱 안 구독 경로)에서는 이게 기본이다.
  if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
    info "backend 기동 — 서버 채팅 LLM은 application-local.properties 설정대로(ANTHROPIC_API_KEY 있음)"
    (cd "$ROOT/backend" && SPRING_PROFILES_ACTIVE=dev,local ./gradlew bootRun --console=plain) \
      >"$BACKEND_LOG" 2>&1 &
  else
    info "backend 기동 — ANTHROPIC_API_KEY가 없어 서버 채팅 LLM은 fake로 띄운다"
    (cd "$ROOT/backend" && SPRING_PROFILES_ACTIVE=dev,local ./gradlew bootRun --console=plain \
      "--args=--llm.chat.provider=fake") >"$BACKEND_LOG" 2>&1 &
  fi
  track $!
  info "backend 로그: $BACKEND_LOG"
  wait_for "backend($API_URL)" 300 "$BACKEND_LOG" backend_healthy
fi

# ── 3. frontend ────────────────────────────────────────────────────────────────
WEB_LOG="$LOG_DIR/frontend.log"
if [ "$SKIP_WEB" -eq 1 ]; then
  info "frontend 건너뜀(--skip-web)"
elif web_up; then
  ok "frontend 이미 떠 있음 — $WEB_URL 을 그대로 쓴다"
elif port_busy 3000; then
  die ":3000을 Knot 웹 dev 서버가 아닌 다른 프로세스가 쓰고 있다. 데스크톱 local 빌드는 3000에 고정이므로 그 프로세스를 끄고 다시 실행한다."
else
  ensure_node
  need_cmd pnpm "\`npm i -g pnpm\` 또는 corepack으로 설치한다."
  [ -d "$ROOT/frontend/node_modules" ] || die "frontend 의존성이 없다. \`cd frontend && pnpm install\` 후 다시 실행한다."
  info "frontend 기동(webpack dev server :3000)"
  # devServer.open=true를 끈다 — 이 스크립트의 클라이언트는 데스크톱 셸이다(--open으로 되살린다).
  if [ "$OPEN_BROWSER" -eq 1 ]; then
    (cd "$ROOT/frontend" && pnpm dev) >"$WEB_LOG" 2>&1 &
  else
    (cd "$ROOT/frontend" && pnpm dev --no-open) >"$WEB_LOG" 2>&1 &
  fi
  track $!
  info "frontend 로그: $WEB_LOG"
  wait_for "frontend($WEB_URL)" 180 "$WEB_LOG" web_up
fi

# ── 4. 데스크톱 셸 ─────────────────────────────────────────────────────────────
if [ "$SKIP_DESKTOP" -eq 1 ]; then
  info "데스크톱 셸 건너뜀(--skip-desktop)"
  echo
  ok "웹 $WEB_URL · API $API_URL 준비됨. Ctrl+C로 이 스크립트가 띄운 것을 정리한다."
  # 자식이 있으면 그것들이 끝날 때까지 붙어 있는다.
  if [ -n "$STARTED_PIDS" ]; then
    wait
  fi
  exit 0
fi

ensure_node
need_cmd pnpm "\`npm i -g pnpm\` 또는 corepack으로 설치한다."
[ -d "$ROOT/desktop/node_modules" ] || die "desktop 의존성이 없다. \`cd desktop && pnpm install\` 후 다시 실행한다."
# Electron 단일 인스턴스 잠금 때문에 이미 켜져 있으면 새 창 없이 바로 끝난다(실측 2026-09-10).
# 개발 실행은 desktop/node_modules의 Electron, 패키징 앱은 Knot.app으로 뜬다.
if pgrep -qf "$ROOT/desktop/node_modules/electron/dist/Electron.app/Contents/MacOS/Electron" 2>/dev/null ||
  pgrep -qf "Knot.app/Contents/MacOS/Knot" 2>/dev/null; then
  warn "Knot 앱이 이미 실행 중이다(단일 인스턴스 잠금) — 새 창 없이 바로 종료되고, 이 스크립트도 함께 끝난다."
  warn "먼저 실행 중인 앱을 끄거나, 서버만 필요하면 --skip-desktop으로 실행한다."
fi

echo
info "데스크톱 셸 기동 — KNOT_DESKTOP_ENV=local (웹 $WEB_URL · API $API_URL)"
info "이 창을 Ctrl+C 하거나 앱을 종료하면 위에서 띄운 것도 함께 정리된다."
echo
cd "$ROOT/desktop"
KNOT_DESKTOP_ENV=local pnpm start
