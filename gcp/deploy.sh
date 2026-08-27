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
JWT_ISSUER="${JWT_ISSUER:-https://api.cityfarmerplus.local}"
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

latest_enabled_version() {
  local secret="$1"
  local version
  version="$(gcloud secrets versions list "${secret}" \
    --filter='state=ENABLED' \
    --sort-by='~createTime' \
    --format='value(name)' \
    --limit=1 \
    --project="${PROJECT_ID}")"
  if [[ -z "${version}" ]]; then
    echo "Secret ${secret} has no enabled version." >&2
    return 1
  fi
  printf '%s' "${version##*/}"
}

DB_URL_VERSION="$(latest_enabled_version cityfarmerplus-db-url)"
DB_USERNAME_VERSION="$(latest_enabled_version cityfarmerplus-db-username)"
DB_PASSWORD_VERSION="$(latest_enabled_version cityfarmerplus-db-password)"
JWT_SECRET_VERSION="$(latest_enabled_version cityfarmerplus-jwt-secret)"
KAMIS_API_KEY_VERSION="$(latest_enabled_version cityfarmerplus-kamis-api-key)"

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
  --env-vars-file="${ENV_FILE}" \
  --set-secrets="DB_URL=cityfarmerplus-db-url:${DB_URL_VERSION},DB_USERNAME=cityfarmerplus-db-username:${DB_USERNAME_VERSION},DB_PASSWORD=cityfarmerplus-db-password:${DB_PASSWORD_VERSION},JWT_SECRET=cityfarmerplus-jwt-secret:${JWT_SECRET_VERSION},KAMIS_API_KEY=cityfarmerplus-kamis-api-key:${KAMIS_API_KEY_VERSION}" \
  --startup-probe="httpGet.path=/health,httpGet.port=8080,initialDelaySeconds=0,failureThreshold=24,timeoutSeconds=2,periodSeconds=10" \
  --liveness-probe="httpGet.path=/health/live,httpGet.port=8080,initialDelaySeconds=0,failureThreshold=3,timeoutSeconds=2,periodSeconds=30"

gcloud run services add-iam-policy-binding "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/run.developer" >/dev/null

SERVICE_URL="$(gcloud run services describe "${SERVICE}" \
  --project="${PROJECT_ID}" \
  --region="${REGION}" \
  --format='value(status.url)')"

curl --fail --show-error --silent "${SERVICE_URL}/health"
printf '\nService URL: %s\nRevision source: %s\n' "${SERVICE_URL}" "${COMMIT_SHA}"
