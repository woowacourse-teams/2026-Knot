#!/usr/bin/env bash
set -Eeuo pipefail

timestamp="$(( $(date +%s) - 86400 ))000000000"
payload=$(printf '{"streams":[{"stream":{"project":"knot","env":"dev","job":"observability-qa","scenario":"pre-schema-replay"},"values":[["%s","schema replay verification"]]}]}' "$timestamp")
status=$(curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}' \
    --header 'Content-Type: application/json' --data "$payload" \
    http://127.0.0.1:3100/loki/api/v1/push)
printf 'Yesterday log ingestion HTTP %s (expected 204)\n' "$status"
test "$status" = 204
