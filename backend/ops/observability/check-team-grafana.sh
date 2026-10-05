#!/usr/bin/env bash
set -euo pipefail

grafana_url="${1:-https://grafana.knoted.kr}"
shift "$(( $# > 0 ? 1 : 0 ))"

check_status() {
    local path="$1" expected="$2" actual
    shift 2
    actual=$(curl --silent --show-error --max-time 15 --output /dev/null \
        --write-out '%{http_code}' "$@" "${grafana_url}${path}")
    if [[ "$actual" != "$expected" ]]; then
        printf 'FAIL %s: expected %s, got %s\n' "$path" "$expected" "$actual" >&2
        return 1
    fi
    printf 'PASS %s: %s\n' "$path" "$actual"
}

check_status /login 200 "$@"
check_status /api/user 401 "$@"
check_status /api/search 401 "$@"
check_status /api/datasources 401 "$@"
check_status /api/dashboards/uid/knot-operations 401 "$@"
check_status /api/user 401 "$@" --header 'X-Knot-Viewer: knot-viewer'
check_status /api/user 401 "$@" --header 'X-Knot-Viewer: knot-admin'
