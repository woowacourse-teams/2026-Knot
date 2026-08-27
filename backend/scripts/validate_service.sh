#!/usr/bin/env bash
set -e

for i in {1..30}; do
  if curl --fail --silent http://127.0.0.1:8080/actuator/health > /dev/null; then
    exit 0
  fi

  sleep 2
done

systemctl status knot-backend.service --no-pager
exit 1