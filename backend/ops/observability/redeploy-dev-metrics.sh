#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
: "${KNOT_DEV_ORIGIN_URL:?Set private Dev origin URL including port}"
app=/opt/knot-backend/app.jar
candidate=/home/ubuntu/knot-observability-stage/metrics-app.jar
agent=/opt/knot-observability-agent
test "$(id -u)" = 0
test "$(sha256sum "$app" | cut -d ' ' -f1)" = 328ab142e1125d8df149773383258213ecd9a466898041a1dadcb1f65961e0ea
test "$(sha256sum "$candidate" | cut -d ' ' -f1)" = 4f6eb7881b22ecda0c2d32e2853fa5fc2dce3907bb21e177fbf80c74146fea97
systemctl is-active --quiet knot-backend.service
systemctl show knot-backend.service -p Environment --value | grep -Fq 'SPRING_PROFILES_INCLUDE=observability'
python3 - "$app" "$candidate" <<'PY'
import sys,zipfile,hashlib
with zipfile.ZipFile(sys.argv[1]) as old,zipfile.ZipFile(sys.argv[2]) as new:
    files=lambda jar,prefix:{name:hashlib.sha256(jar.read(name)).hexdigest() for name in jar.namelist() if name.startswith(prefix) and not name.endswith('/')}
    baseline=files(old,'BOOT-INF/classes/');candidate=files(new,'BOOT-INF/classes/')
    assert {name for name in baseline if baseline[name]!=candidate.get(name)}=={'BOOT-INF/classes/com/knot/backend/global/config/SecurityConfig.class'}
    assert set(candidate)-set(baseline)=={'BOOT-INF/classes/application-observability.properties'}
    assert not set(baseline)-set(candidate)
    libraries=files(old,'BOOT-INF/lib/');new_libraries=files(new,'BOOT-INF/lib/')
    assert all(value==new_libraries.get(name) for name,value in libraries.items())
    assert all('prometheus' in name for name in set(new_libraries)-set(libraries))
print('Current app parity verified; only metrics boundary, opt-in properties, Prometheus dependencies differ.')
PY
backup=$(mktemp -d "$agent/backup-dev-metrics-reapply.XXXXXX")
cp -p "$app" "$backup/app.jar"
cp -p /etc/systemd/system/knot-backend.service.d/20-observability.conf "$backup/20-observability.conf"
cp -p /etc/nginx/snippets/knot-private-metrics.conf "$backup/knot-private-metrics.conf"
cp -p "$agent/config.alloy" "$backup/config.alloy"
metrics=$(mktemp /tmp/knot-dev-metrics-check.XXXXXX)
rollback() {
    trap - ERR
    install -o ubuntu -g ubuntu -m 0500 "$backup/app.jar" "$app"
    systemctl restart knot-backend.service
    rm -f "$metrics"
    printf 'Dev JAR restored and restart requested. Backup: %s\n' "$backup" >&2
    exit 1
}
trap rollback ERR
printf 'AWS Dev backup: %s\n' "$backup"
install -o ubuntu -g ubuntu -m 0500 "$candidate" "$app"
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
curl -fsS --max-time 10 http://127.0.0.1:8080/actuator/prometheus -o "$metrics"
python3 - "$metrics" <<'PY'
import sys,json
lines=open(sys.argv[1]).read().splitlines()
counts={prefix:sum(line.startswith(prefix) for line in lines) for prefix in ['jvm_memory_used_bytes','tomcat_threads_busy_threads','hikaricp_connections','http_server_requests_seconds_bucket']}
assert all(counts.values()),counts
print(json.dumps({'localMetricFamilies':counts}))
PY
test "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' https://dev-api.knoted.kr/actuator/prometheus)" = 403
test "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' https://dev-api.knoted.kr/actuator/health)" = 200
test "$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "${KNOT_DEV_ORIGIN_URL}/actuator/prometheus")" = 401
systemctl is-active --quiet knot-backend.service
sha256sum "$app"
rm -f "$metrics"
trap - ERR
printf 'AWS Dev metrics JAR reapplied. External metrics remain blocked. Backup: %s\n' "$backup"
