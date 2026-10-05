#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
    printf 'Run as root on the NCP observability server.\n' >&2
    exit 1
fi

stage_dir="/opt/knot-observability/team-stage"
stack_dir="/opt/knot-observability"
public_vhost="/etc/nginx/conf.d/knot-grafana-team.conf"
allow_file="/etc/nginx/snippets/knot-grafana-cloudflare-allow.conf"
certificate="/etc/nginx/ssl/grafana-origin.pem"
private_key="/etc/nginx/ssl/grafana-origin.key"
ca_file="/etc/nginx/ssl/cloudflare-origin-ca.pem"
backup_dir="$(mktemp -d /opt/knot-observability/team-backup.XXXXXX)"
had_override=false
had_vhost=false
had_allow=false
activated=false

for file in "$stage_dir/compose.team.yml" "$stage_dir/grafana-team.conf" \
    "$stage_dir/cloudflare-allow.conf" "$stage_dir/check-team-grafana.sh" \
    "$certificate" "$private_key" "$ca_file"; do
    test -s "$file" || { printf 'Missing prerequisite: %s\n' "$file" >&2; exit 1; }
done

openssl verify -CAfile "$ca_file" -verify_hostname grafana.knoted.kr "$certificate"
cert_public=$(openssl x509 -in "$certificate" -pubkey -noout | openssl pkey -pubin -outform DER | sha256sum)
key_public=$(openssl pkey -in "$private_key" -pubout -outform DER | sha256sum)
test "$cert_public" = "$key_public" || { printf 'Certificate and key do not match.\n' >&2; exit 1; }

[[ ! -e "$stack_dir/compose.team.yml" ]] || { cp -p "$stack_dir/compose.team.yml" "$backup_dir/compose.team.yml"; had_override=true; }
[[ ! -e "$public_vhost" ]] || { cp -p "$public_vhost" "$backup_dir/grafana-team.conf"; had_vhost=true; }
[[ ! -e "$allow_file" ]] || { cp -p "$allow_file" "$backup_dir/cloudflare-allow.conf"; had_allow=true; }

restore_file() {
    local destination="$1" previous="$2" existed="$3"
    if [[ "$existed" = true ]]; then
        cp -p "$previous" "$destination"
    elif [[ -e "$destination" ]]; then
        mv "$destination" "$backup_dir/failed-$(basename "$destination")"
    fi
}

rollback() {
    local status=$?
    trap - EXIT
    if [[ "$status" -ne 0 && "$activated" = true ]]; then
        printf 'Deployment failed; restoring previous configuration. Backup: %s\n' "$backup_dir" >&2
        restore_file "$stack_dir/compose.team.yml" "$backup_dir/compose.team.yml" "$had_override"
        restore_file "$public_vhost" "$backup_dir/grafana-team.conf" "$had_vhost"
        restore_file "$allow_file" "$backup_dir/cloudflare-allow.conf" "$had_allow"
        if [[ "$had_override" = true ]]; then
            docker compose -f "$stack_dir/compose.yml" -f "$stack_dir/compose.team.yml" up -d --no-deps grafana || true
        else
            docker compose -f "$stack_dir/compose.yml" up -d --no-deps grafana || true
        fi
        nginx -t && systemctl reload nginx || true
    fi
    exit "$status"
}
trap rollback EXIT

activated=true
install -m 0644 "$stage_dir/compose.team.yml" "$stack_dir/compose.team.yml"
install -d -m 0755 /etc/nginx/snippets
install -m 0644 "$stage_dir/cloudflare-allow.conf" "$allow_file"
install -m 0644 "$stage_dir/grafana-team.conf" "$public_vhost"
docker compose -f "$stack_dir/compose.yml" -f "$stack_dir/compose.team.yml" config --quiet
nginx -t
docker compose -f "$stack_dir/compose.yml" -f "$stack_dir/compose.team.yml" up -d --no-deps grafana

ready=false
for attempt in {1..40}; do
    if curl --silent --fail --max-time 2 http://127.0.0.1:3000/api/health >/dev/null; then
        ready=true
        break
    fi
    sleep 1
done
test "$ready" = true || { printf 'Grafana did not become healthy.\n' >&2; exit 1; }

bash "$stage_dir/check-team-grafana.sh" http://127.0.0.1:3000
systemctl reload nginx
tls_ready=false
for attempt in {1..20}; do
    if curl --silent --fail --max-time 2 --cacert "$ca_file" \
        --resolve grafana.knoted.kr:443:127.0.0.1 \
        https://grafana.knoted.kr/login >/dev/null; then
        tls_ready=true
        break
    fi
    sleep 1
done
test "$tls_ready" = true || { printf 'Nginx did not serve the verified Grafana certificate.\n' >&2; exit 1; }
bash "$stage_dir/check-team-grafana.sh" https://grafana.knoted.kr \
    --cacert "$ca_file" --resolve grafana.knoted.kr:443:127.0.0.1
printf 'NCP deployment verified. Backup: %s\n' "$backup_dir"
