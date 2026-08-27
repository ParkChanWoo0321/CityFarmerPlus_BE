#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID="project-60a7cf7e-b36a-406b-b9e"
REGION="us-west1"
SERVICE="cityfarmerplus-api"
REPOSITORY="cityfarmerplus"
BUILD_SA_NAME="cityfarmerplus-build"
RUNTIME_SA_NAME="cityfarmerplus-runtime"
BUILD_SA="${BUILD_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
RUNTIME_SA="${RUNTIME_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
BUILD_SA_RESOURCE="projects/${PROJECT_ID}/serviceAccounts/${BUILD_SA}"
GCS_BUCKET="${PROJECT_ID}-cityfarmerplus-private"
BUILD_SOURCE_BUCKET="${PROJECT_ID}-cityfarmerplus-build-source"
JWT_ISSUER="${JWT_ISSUER:?Set JWT_ISSUER explicitly to the current production issuer.}"
CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-https://cityfarmerplus.site,https://www.cityfarmerplus.site,http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

cd "${REPO_ROOT}"
git fetch origin main --quiet

if [[ "$(git branch --show-current)" != "main" ]]; then
  echo "Deployments are allowed only from main." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "The working tree must be clean before deployment." >&2
  exit 1
fi

if [[ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
  echo "HEAD must match the latest GitHub origin/main." >&2
  exit 1
fi

resolve_enabled_version() {
  local secret="$1"
  local requested_version="$2"
  local state
  if [[ ! "${requested_version}" =~ ^[0-9]+$ ]]; then
    echo "Secret ${secret} version must be numeric." >&2
    return 1
  fi
  state="$(gcloud secrets versions describe "${requested_version}" \
    --secret="${secret}" \
    --format='value(state)' \
    --project="${PROJECT_ID}")"
  if [[ "${state}" != "ENABLED" ]]; then
    echo "Secret ${secret} version ${requested_version} is not enabled." >&2
    return 1
  fi
  printf '%s' "${requested_version}"
}

for required_version_variable in \
  CFP_DB_URL_VERSION \
  CFP_DB_USERNAME_VERSION \
  CFP_DB_PASSWORD_VERSION \
  CFP_JWT_SECRET_VERSION \
  CFP_KAMIS_API_KEY_VERSION \
  CFP_KAMIS_CERT_ID_VERSION; do
  if [[ -z "${!required_version_variable:-}" ]]; then
    echo "${required_version_variable} must be set to an explicit numeric secret version." >&2
    exit 1
  fi
done
unset required_version_variable

DB_URL_VERSION="$(resolve_enabled_version cityfarmerplus-db-url "${CFP_DB_URL_VERSION}")"
DB_USERNAME_VERSION="$(resolve_enabled_version cityfarmerplus-db-username "${CFP_DB_USERNAME_VERSION}")"
DB_PASSWORD_VERSION="$(resolve_enabled_version cityfarmerplus-db-password "${CFP_DB_PASSWORD_VERSION}")"
JWT_SECRET_VERSION="$(resolve_enabled_version cityfarmerplus-jwt-secret "${CFP_JWT_SECRET_VERSION}")"
KAMIS_API_KEY_VERSION="$(resolve_enabled_version cityfarmerplus-kamis-api-key "${CFP_KAMIS_API_KEY_VERSION}")"
KAMIS_CERT_ID_VERSION="$(resolve_enabled_version cityfarmerplus-kamis-cert-id "${CFP_KAMIS_CERT_ID_VERSION}")"

COMMIT_SHA="$(git rev-parse HEAD)"
SHORT_SHA="$(git rev-parse --short=7 HEAD)"
ENV_FILE="$(mktemp)"
trap 'rm -f "${ENV_FILE}"' EXIT

cat >"${ENV_FILE}" <<EOF
DB_POOL_MAX_SIZE: "5"
DB_POOL_MIN_IDLE: "0"
DB_CONNECTION_TIMEOUT: "10000"
DB_POOL_INITIALIZATION_FAIL_TIMEOUT: "60000"
JPA_DDL_AUTO: "validate"
JPA_SHOW_SQL: "false"
JWT_ISSUER: "${JWT_ISSUER}"
CORS_ALLOWED_ORIGINS: "${CORS_ALLOWED_ORIGINS}"
FILE_STORAGE_TYPE: "gcs"
GCS_PROJECT_ID: "${PROJECT_ID}"
GCS_BUCKET: "${GCS_BUCKET}"
GCS_PREFIX: "cityfarmerplus/prod"
EOF

gcloud builds submit . \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --config=cloudbuild.yaml \
  --service-account="${BUILD_SA_RESOURCE}" \
  --gcs-source-staging-dir="gs://${BUILD_SOURCE_BUCKET}/source" \
  --substitutions="BRANCH_NAME=main,_REGION=${REGION},_REPOSITORY=${REPOSITORY},_IMAGE=${SERVICE},_SERVICE=${SERVICE},_TAG=${SHORT_SHA},_DEPLOY=false"

IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}/${SERVICE}:${SHORT_SHA}"
DEPLOY_LOCK_URI="gs://${BUILD_SOURCE_BUCKET}/deploy-locks/${SERVICE}.lock"
DEPLOY_LOCK_OWNER="manual:${COMMIT_SHA}:$(date -u +'%s%N'):$$"
DEPLOY_LOCK_GENERATION=""

release_deploy_lock() {
  if [[ -n "${DEPLOY_LOCK_GENERATION}" ]]; then
    gcloud storage rm "${DEPLOY_LOCK_URI}" \
      --if-generation-match="${DEPLOY_LOCK_GENERATION}" \
      --project="${PROJECT_ID}" \
      --quiet || echo "Deployment lock ${DEPLOY_LOCK_URI} could not be released." >&2
  fi
}

acquire_deploy_lock() {
  if ! printf '%s\n' "${DEPLOY_LOCK_OWNER}" | gcloud storage cp - "${DEPLOY_LOCK_URI}" \
    --if-generation-match=0 \
    --project="${PROJECT_ID}" \
    --quiet; then
    echo "Another deployment holds ${DEPLOY_LOCK_URI}; production traffic was not changed." >&2
    return 1
  fi
  if ! DEPLOY_LOCK_GENERATION="$(gcloud storage objects describe "${DEPLOY_LOCK_URI}" \
    --project="${PROJECT_ID}" \
    --format='value(generation)')" || \
    [[ ! "${DEPLOY_LOCK_GENERATION}" =~ ^[0-9]+$ ]]; then
    echo "Deployment lock generation could not be resolved; the lock was left in place and production traffic was not changed." >&2
    DEPLOY_LOCK_GENERATION=""
    return 1
  fi
  trap 'rm -f "${ENV_FILE}"; release_deploy_lock' EXIT
}

service_traffic_json() {
  gcloud run services describe "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --format=json
}

current_traffic_revision() {
  service_traffic_json | python3 -c '
import json
import sys

traffic = json.load(sys.stdin).get("status", {}).get("traffic", [])
matches = [entry.get("revisionName", "") for entry in traffic
           if entry.get("percent") == 100]
print(matches[0] if len(matches) == 1 else "")
'
}

resolve_current_traffic_revision() {
  local attempt
  local revision
  for attempt in 1 2 3; do
    revision=""
    if revision="$(current_traffic_revision)" && [[ -n "${revision}" ]]; then
      printf '%s' "${revision}"
      return 0
    fi
    if [[ "${attempt}" -lt 3 ]]; then
      sleep 2
    fi
  done
  return 1
}

tagged_traffic_pair() {
  local tag="$1"
  service_traffic_json | python3 -c '
import json
import sys

tag = sys.argv[1]
traffic = json.load(sys.stdin).get("status", {}).get("traffic", [])
matches = [(entry.get("revisionName", ""), entry.get("url", ""))
           for entry in traffic
           if entry.get("tag") == tag]
if len(matches) == 1 and all(matches[0]):
    print(*matches[0], sep="\t")
' "${tag}"
}

acquire_deploy_lock
PREVIOUS_REVISION=""
if ! PREVIOUS_REVISION="$(resolve_current_traffic_revision)"; then
  PREVIOUS_REVISION=""
fi
if [[ -z "${PREVIOUS_REVISION}" ]]; then
  echo 'An existing Cloud Run service with one 100% production revision is required.' >&2
  exit 1
fi
CANDIDATE_NONCE="$(printf '%04x%04x' "${RANDOM}" "${RANDOM}")"
CANDIDATE_TAG="c-${SHORT_SHA}-${CANDIDATE_NONCE}"

gcloud run deploy "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --platform=managed \
  --image="${IMAGE}" \
  --allow-unauthenticated \
  --service-account="${RUNTIME_SA}" \
  --port=8080 \
  --cpu=1 \
  --memory=2Gi \
  --concurrency=1 \
  --min=0 \
  --max=1 \
  --timeout=300 \
  --cpu-throttling \
  --no-traffic \
  --tag="${CANDIDATE_TAG}" \
  --env-vars-file="${ENV_FILE}" \
  --set-secrets="DB_URL=cityfarmerplus-db-url:${DB_URL_VERSION},DB_USERNAME=cityfarmerplus-db-username:${DB_USERNAME_VERSION},DB_PASSWORD=cityfarmerplus-db-password:${DB_PASSWORD_VERSION},JWT_SECRET=cityfarmerplus-jwt-secret:${JWT_SECRET_VERSION},KAMIS_API_KEY=cityfarmerplus-kamis-api-key:${KAMIS_API_KEY_VERSION},KAMIS_CERT_ID=cityfarmerplus-kamis-cert-id:${KAMIS_CERT_ID_VERSION}" \
  --startup-probe="httpGet.path=/health,httpGet.port=8080,initialDelaySeconds=0,failureThreshold=24,timeoutSeconds=2,periodSeconds=10" \
  --liveness-probe="httpGet.path=/health/live,httpGet.port=8080,initialDelaySeconds=0,failureThreshold=3,timeoutSeconds=2,periodSeconds=30"

CANDIDATE_TRAFFIC=""
if ! CANDIDATE_TRAFFIC="$(tagged_traffic_pair "${CANDIDATE_TAG}")"; then
  CANDIDATE_TRAFFIC=""
fi
IFS=$'\t' read -r CANDIDATE_REVISION CANDIDATE_URL <<<"${CANDIDATE_TRAFFIC}"

if [[ -z "${CANDIDATE_REVISION}" || -z "${CANDIDATE_URL}" ]]; then
  echo 'Candidate revision or tagged URL could not be resolved; production traffic was not changed.' >&2
  gcloud run services update-traffic "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --remove-tags="${CANDIDATE_TAG}" \
    --quiet || true
  exit 1
fi

if ! bash "${SCRIPT_DIR}/smoke-public.sh" "${CANDIDATE_URL}"; then
  gcloud run services update-traffic "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --remove-tags="${CANDIDATE_TAG}" \
    --quiet || true
  exit 1
fi

git fetch origin main --quiet
if [[ "$(git rev-parse HEAD)" != "$(git rev-parse origin/main)" ]]; then
  echo 'origin/main advanced while the candidate was building; production traffic was not changed.' >&2
  gcloud run services update-traffic "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --remove-tags="${CANDIDATE_TAG}" \
    --quiet || true
  exit 1
fi

gcloud run services add-iam-policy-binding "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/run.developer" >/dev/null

SERVICE_URL="$(gcloud run services describe "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --format='value(status.url)')"

TRAFFIC_SWITCHED=false
rollback_and_exit() {
  local exit_code="$1"
  local current_revision
  local restored_revision
  local rollback_succeeded=false
  local rollback_owner_changed=false
  trap - ERR INT TERM
  set +e
  if ! current_revision="$(resolve_current_traffic_revision)"; then
    current_revision=""
  fi
  if [[ "${current_revision}" == "${CANDIDATE_REVISION}" ]]; then
    echo "Deployment failed after traffic change; rolling back to ${PREVIOUS_REVISION}." >&2
    for attempt in 1 2 3; do
      if ! current_revision="$(resolve_current_traffic_revision)"; then
        current_revision=""
      fi
      if [[ "${current_revision}" != "${CANDIDATE_REVISION}" ]]; then
        if [[ "${current_revision}" == "${PREVIOUS_REVISION}" ]]; then
          rollback_succeeded=true
        elif [[ -n "${current_revision}" ]]; then
          echo "Rollback stopped because traffic is now owned by ${current_revision}." >&2
          rollback_owner_changed=true
        else
          echo 'CRITICAL: current production revision could not be resolved during rollback.' >&2
        fi
        break
      fi
      gcloud run services update-traffic "${SERVICE}" \
        --project="${PROJECT_ID}" \
        --region="${REGION}" \
        --to-revisions="${PREVIOUS_REVISION}=100" \
        --quiet
      restored_revision=""
      if restored_revision="$(resolve_current_traffic_revision)" && \
        [[ "${restored_revision}" == "${PREVIOUS_REVISION}" ]]; then
        rollback_succeeded=true
        break
      fi
      if [[ "${attempt}" -lt 3 ]]; then
        sleep 5
      fi
    done
    if [[ "${rollback_succeeded}" == true ]]; then
      bash "${SCRIPT_DIR}/smoke-health.sh" "${SERVICE_URL}" || \
        echo 'Rollback revision was restored, but rollback health verification failed.' >&2
    elif [[ "${rollback_owner_changed}" != true ]]; then
      echo "CRITICAL: traffic rollback to ${PREVIOUS_REVISION} failed." >&2
    fi
  elif [[ -n "${current_revision}" ]]; then
    echo "Traffic is now owned by ${current_revision}; this failed deployment did not overwrite it." >&2
  else
    echo 'CRITICAL: current production revision could not be resolved; rollback was not attempted.' >&2
  fi
  gcloud run services update-traffic "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --remove-tags="${CANDIDATE_TAG}" \
    --quiet || true
  exit "${exit_code}"
}
CURRENT_REVISION=""
if ! CURRENT_REVISION="$(resolve_current_traffic_revision)"; then
  CURRENT_REVISION=""
fi
if [[ "${CURRENT_REVISION}" != "${PREVIOUS_REVISION}" ]]; then
  echo "Production traffic changed from ${PREVIOUS_REVISION} to ${CURRENT_REVISION}; this candidate was not promoted." >&2
  gcloud run services update-traffic "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --remove-tags="${CANDIDATE_TAG}" \
    --quiet || true
  exit 1
fi
CURRENT_CANDIDATE_TRAFFIC=""
if ! CURRENT_CANDIDATE_TRAFFIC="$(tagged_traffic_pair "${CANDIDATE_TAG}")" || \
  [[ "${CURRENT_CANDIDATE_TRAFFIC}" != "${CANDIDATE_TRAFFIC}" ]]; then
  echo 'Candidate tag mapping changed before promotion; production traffic was not changed.' >&2
  gcloud run services update-traffic "${SERVICE}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --remove-tags="${CANDIDATE_TAG}" \
    --quiet || true
  exit 1
fi
trap 'rollback_and_exit $?' ERR
trap 'rollback_and_exit 130' INT
trap 'rollback_and_exit 143' TERM

TRAFFIC_SWITCHED=true
gcloud run services update-traffic "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --to-revisions="${CANDIDATE_REVISION}=100" \
  --quiet

bash "${SCRIPT_DIR}/smoke-public.sh" "${SERVICE_URL}"
TRAFFIC_SWITCHED=false
trap - ERR INT TERM
gcloud run services update-traffic "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --remove-tags="${CANDIDATE_TAG}" \
  --quiet || echo "Candidate tag ${CANDIDATE_TAG} could not be removed." >&2
printf 'Service URL: %s\nRevision: %s\nPrevious revision: %s\nRevision source: %s\n' \
  "${SERVICE_URL}" "${CANDIDATE_REVISION}" "${PREVIOUS_REVISION}" "${COMMIT_SHA}"
