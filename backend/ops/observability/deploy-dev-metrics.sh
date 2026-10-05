#!/usr/bin/env bash
set -Eeuo pipefail
: "${KNOT_INGEST_BASE_URL:?Set private HTTPS ingestion endpoint}"
export KNOT_INGEST_BASE_URL

stage=/home/ubuntu/knot-observability-stage
backup=/opt/knot-observability-agent/backup-dev-metrics-20261001
app=/opt/knot-backend/app.jar
nginx=/etc/nginx/conf.d/knot-backend-dev.conf
dropin=/etc/systemd/system/knot-backend.service.d/20-observability.conf
snippet=/etc/nginx/snippets/knot-private-metrics.conf

test "$(sha256sum "$app" | cut -d ' ' -f1)" = 328ab142e1125d8df149773383258213ecd9a466898041a1dadcb1f65961e0ea
test "$(sha256sum "$nginx" | cut -d ' ' -f1)" = 335d260f800761e7c23678e1e1f3c2f6a44caeafd0ceedc7f3060892c09bc005
test ! -e "$backup"
test ! -e "$dropin"
test ! -e "$snippet"
env KNOT_ENV=dev docker compose -f /opt/knot-observability-agent/compose.yml run --rm --no-deps -v "$stage/config.alloy:/etc/alloy/config.alloy:ro" alloy validate /etc/alloy/config.alloy
install -d -m 0700 "$backup"
cp -p "$app" "$backup/app.jar"
cp -p "$nginx" "$backup/nginx.conf"
cp -p /opt/knot-observability-agent/config.alloy "$backup/config.alloy"

rollback() {
    trap - ERR
    install -o ubuntu -g ubuntu -m 0500 "$backup/app.jar" "$app"
    install -m 0644 "$backup/nginx.conf" "$nginx"
    install -m 0644 "$backup/config.alloy" /opt/knot-observability-agent/config.alloy
    rm -f "$dropin" "$snippet"
    systemctl daemon-reload
    nginx -t && systemctl reload nginx
    systemctl restart knot-backend.service
    env KNOT_ENV=dev docker compose -f /opt/knot-observability-agent/compose.yml up -d --force-recreate alloy
    echo 'Observability deployment rolled back; backups retained.' >&2
    exit 1
}
trap rollback ERR

install -d -m 0755 /etc/nginx/snippets
install -m 0644 "$stage/private-metrics.conf" "$snippet"
install -m 0644 "$stage/knot-backend-dev.conf" "$nginx"
nginx -t
systemctl reload nginx
install -m 0644 "$stage/systemd-observability.conf" "$dropin"
install -o ubuntu -g ubuntu -m 0500 "$stage/metrics-app.jar" "$app"
systemctl daemon-reload
systemctl restart knot-backend.service

healthy=false
for attempt in {1..30}; do
    if curl -fsS --max-time 2 http://127.0.0.1:8080/actuator/health >/dev/null; then
        healthy=true
        break
    fi
    sleep 2
done
test "$healthy" = true
curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/prometheus | grep '^jvm_memory_used_bytes' >/dev/null
test "$(curl -sS --max-time 5 -o /dev/null -w '%{http_code}' --resolve dev-api.knoted.kr:443:127.0.0.1 https://dev-api.knoted.kr/actuator/prometheus)" = 403

install -m 0644 "$stage/config.alloy" /opt/knot-observability-agent/config.alloy
env KNOT_ENV=dev docker compose -f /opt/knot-observability-agent/compose.yml run --rm --no-deps alloy validate /etc/alloy/config.alloy
env KNOT_ENV=dev docker compose -f /opt/knot-observability-agent/compose.yml up -d --force-recreate alloy
systemctl is-active knot-backend.service
sha256sum "$app"
echo 'Dev metrics deployment completed; previous JAR and configs retained in backup-dev-metrics-20261001.'
