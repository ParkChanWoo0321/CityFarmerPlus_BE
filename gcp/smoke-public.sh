#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 1 || -z "${1:-}" ]]; then
  echo 'Usage: bash gcp/smoke-public.sh <service-base-url>' >&2
  exit 1
fi

BASE_URL="${1%/}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

bash "${SCRIPT_DIR}/smoke-health.sh" "${BASE_URL}"

WEBHOOK_RESPONSE_FILE="$(mktemp)"
cleanup() {
  rm -f -- "${WEBHOOK_RESPONSE_FILE}"
}
trap cleanup EXIT
WEBHOOK_TIMESTAMP="$(date -u +'%s')"
WEBHOOK_STATUS="$(curl \
  --show-error \
  --silent \
  --connect-timeout 10 \
  --max-time 30 \
  --output "${WEBHOOK_RESPONSE_FILE}" \
  --write-out '%{http_code}' \
  --request POST \
  --header 'Content-Type: application/json' \
  --header "X-Education-Event-Timestamp: ${WEBHOOK_TIMESTAMP}" \
  --header "X-Education-Signature: sha256=$(printf '0%.0s' {1..64})" \
  --data '{}' \
  "${BASE_URL}/api/integrations/education/progress-events")"
if [[ "${WEBHOOK_STATUS}" != "401" ]] || \
  ! grep -Eq '"code"[[:space:]]*:[[:space:]]*"INVALID_EDUCATION_PROGRESS_SIGNATURE"' \
    "${WEBHOOK_RESPONSE_FILE}"; then
  printf 'Education progress webhook security smoke failed (HTTP %s).\n' \
    "${WEBHOOK_STATUS}" >&2
  exit 1
fi

for attempt in 1 2 3; do
  if KAMIS_RESPONSE="$(curl \
      --fail \
      --show-error \
      --silent \
      --connect-timeout 10 \
      --max-time 90 \
      "${BASE_URL}/api/market-prices/latest?page=0&size=1")" \
      && grep -Eq '"provider"[[:space:]]*:[[:space:]]*"KAMIS"' <<<"${KAMIS_RESPONSE}" \
      && grep -Eq '"observedDate"[[:space:]]*:[[:space:]]*"[0-9]{4}-[0-9]{2}-[0-9]{2}"' <<<"${KAMIS_RESPONSE}" \
      && grep -Eq '"items"[[:space:]]*:[[:space:]]*\[[[:space:]]*\{' <<<"${KAMIS_RESPONSE}"; then
    echo 'Public smoke tests: health, education webhook security, and KAMIS ok'
    exit 0
  fi
  if [[ "${attempt}" -lt 3 ]]; then
    sleep 5
  fi
done

echo 'KAMIS smoke test did not return a dated non-empty KAMIS response.' >&2
exit 1
