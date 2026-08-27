#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 || -z "${1:-}" ]]; then
  echo 'Usage: bash gcp/smoke-health.sh <service-base-url>' >&2
  exit 1
fi

BASE_URL="${1%/}"

for attempt in 1 2 3 4 5 6; do
  if HEALTH_RESPONSE="$(curl \
      --fail \
      --show-error \
      --silent \
      --connect-timeout 10 \
      --max-time 90 \
      "${BASE_URL}/health")" \
      && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_RESPONSE}" \
      && grep -Eq '"database"[[:space:]]*:[[:space:]]*"UP"' <<<"${HEALTH_RESPONSE}"; then
    echo 'Health smoke test: application and database UP'
    exit 0
  fi
  if [[ "${attempt}" -lt 6 ]]; then
    sleep 5
  fi
done

echo 'Health smoke test did not report application and database UP.' >&2
exit 1
