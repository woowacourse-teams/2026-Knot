#!/usr/bin/env bash
set -Eeuo pipefail

root=/opt/knot-observability
stage=$root/panel-stage
backup=$(mktemp -d "$root/panel-backup.XXXXXX")
cp -a "$root/dashboards" "$backup/dashboards"
cp -p "$root/provisioning/alerting/knot.json" "$backup/knot.json"

admin_api() {
    curl --silent --show-error --fail --max-time 30 \
        --config <(printf 'user = "knot-admin:%s"\n' "$(< "$root/secrets/grafana-admin-password")") \
        --request POST "http://127.0.0.1:3000/api/admin/provisioning/$1/reload"
}

rollback() {
    trap - ERR
    cp -p "$backup/dashboards/"*.json "$root/dashboards/"
    cp -p "$backup/knot.json" "$root/provisioning/alerting/knot.json"
    admin_api dashboards || true
    admin_api alerting || true
    printf 'Expansion rolled back. Backup retained: %s\n' "$backup" >&2
    exit 1
}
trap rollback ERR

for dashboard in knot-operations knot-spring knot-pools knot-infra knot-rds knot-compare; do
    install -m 0644 "$stage/$dashboard.json" "$root/dashboards/$dashboard.json"
done
install -m 0644 "$stage/knot.json" "$root/provisioning/alerting/knot.json"
admin_api dashboards
admin_api alerting
printf '\nDashboard expansion applied. Backup retained: %s\n' "$backup"
