#!/bin/sh
set -e

APP_USERNAME="${APP_USERNAME:-admin}"
APP_PASSWORD="${APP_PASSWORD:-admin123456}"

java ${JAVA_OPTS} -jar /app/app.jar \
  --management.user.username="${APP_USERNAME}" \
  --management.user.password="${APP_PASSWORD}" &
JAVA_PID=$!

nginx -g 'daemon off;' &
NGINX_PID=$!

cleanup() {
  kill -TERM "$JAVA_PID" "$NGINX_PID" 2>/dev/null || true
  wait "$JAVA_PID" 2>/dev/null || true
  wait "$NGINX_PID" 2>/dev/null || true
}

trap cleanup INT TERM

while true; do
  if ! kill -0 "$JAVA_PID" 2>/dev/null; then
    wait "$JAVA_PID"
    EXIT_CODE=$?
    cleanup
    exit "$EXIT_CODE"
  fi

  if ! kill -0 "$NGINX_PID" 2>/dev/null; then
    wait "$NGINX_PID"
    EXIT_CODE=$?
    cleanup
    exit "$EXIT_CODE"
  fi

  sleep 1
done
