#!/usr/bin/env bash
set -e

chown ubuntu:ubuntu /opt/knot-backend/app.jar
chmod 500 /opt/knot-backend/app.jar