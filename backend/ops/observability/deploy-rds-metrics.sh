#!/usr/bin/env bash
set -Eeuo pipefail
: "${KNOT_INGEST_BASE_URL:?Set private HTTPS ingestion endpoint}"
export KNOT_INGEST_BASE_URL

knot_env=${1:?Usage: deploy-rds-metrics.sh dev|prod}
case "$knot_env" in dev|prod) ;; *) exit 2 ;; esac
root=/opt/knot-observability-agent
stage=/home/ubuntu/knot-observability-stage/rds-config.alloy
image=$(docker inspect --format '{{.Config.Image}}' knot-observability-agent-alloy-1)
docker run --rm -e KNOT_ENV="$knot_env" -e KNOT_INGEST_BASE_URL="$KNOT_INGEST_BASE_URL" -v "$stage:/etc/alloy/config.alloy:ro" \
    "$image" validate /etc/alloy/config.alloy
backup=$(mktemp -d "$root/backup-rds.XXXXXX")
cp -p "$root/config.alloy" "$backup/config.alloy"

rollback() {
    trap - ERR
    install -m 0644 "$backup/config.alloy" "$root/config.alloy"
    env KNOT_ENV="$knot_env" docker compose -f "$root/compose.yml" up -d --force-recreate alloy
    printf 'RDS collector rolled back. Backup retained: %s\n' "$backup" >&2
    exit 1
}
trap rollback ERR
install -m 0644 "$stage" "$root/config.alloy"
env KNOT_ENV="$knot_env" docker compose -f "$root/compose.yml" up -d --force-recreate alloy
ready=false
for attempt in {1..15}; do
    if curl --silent --show-error --fail --max-time 3 http://127.0.0.1:12345/-/ready >/dev/null; then
        ready=true
        break
    fi
    sleep 2
done
test "$ready" = true
printf 'RDS collector configured for %s. Backup retained: %s\n' "$knot_env" "$backup"
