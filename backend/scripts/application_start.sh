#!/usr/bin/env bash
set -e

systemctl daemon-reload
systemctl enable knot-backend.service
systemctl start knot-backend.service