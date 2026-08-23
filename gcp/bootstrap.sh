#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID="project-60a7cf7e-b36a-406b-b9e"
REGION="us-west1"
SERVICE="cityfarmerplus-api"
REPOSITORY="cityfarmerplus"
RUNTIME_SA_NAME="cityfarmerplus-runtime"
BUILD_SA_NAME="cityfarmerplus-build"
RUNTIME_SA="${RUNTIME_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
BUILD_SA="${BUILD_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
GCS_BUCKET="${PROJECT_ID}-cityfarmerplus-private"
BUILD_SOURCE_BUCKET="${PROJECT_ID}-cityfarmerplus-build-source"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

gcloud config set project "${PROJECT_ID}" >/dev/null
printf 'Account: %s\nProject: %s\nRegion: %s\n' \
  "$(gcloud config get-value account)" \
  "$(gcloud config get-value project)" \
  "${REGION}"

gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbilling.googleapis.com \
  cloudbuild.googleapis.com \
  cloudresourcemanager.googleapis.com \
  iam.googleapis.com \
  logging.googleapis.com \
  secretmanager.googleapis.com \
  storage.googleapis.com \
  --project="${PROJECT_ID}"

BILLING_ENABLED="$(gcloud billing projects describe "${PROJECT_ID}" --format='value(billingEnabled)' | tr '[:upper:]' '[:lower:]')"
if [[ "${BILLING_ENABLED}" != "true" ]]; then
  echo "Cloud Billing is not enabled for ${PROJECT_ID}." >&2
  exit 1
fi

gcloud artifacts repositories describe "${REPOSITORY}" \
  --location="${REGION}" \
  --project="${PROJECT_ID}" >/dev/null 2>&1 || \
gcloud artifacts repositories create "${REPOSITORY}" \
  --repository-format=docker \
  --location="${REGION}" \
  --description="CityFarmerPlus container images" \
  --project="${PROJECT_ID}"

gcloud artifacts repositories set-cleanup-policies "${REPOSITORY}" \
  --location="${REGION}" \
  --policy="${SCRIPT_DIR}/artifact-cleanup-policy.json" \
  --project="${PROJECT_ID}"

gcloud iam service-accounts describe "${RUNTIME_SA}" \
  --project="${PROJECT_ID}" >/dev/null 2>&1 || \
gcloud iam service-accounts create "${RUNTIME_SA_NAME}" \
  --display-name="CityFarmerPlus Cloud Run runtime" \
  --project="${PROJECT_ID}"

gcloud iam service-accounts describe "${BUILD_SA}" \
  --project="${PROJECT_ID}" >/dev/null 2>&1 || \
gcloud iam service-accounts create "${BUILD_SA_NAME}" \
  --display-name="CityFarmerPlus Cloud Build" \
  --project="${PROJECT_ID}"

gcloud storage buckets describe "gs://${GCS_BUCKET}" >/dev/null 2>&1 || \
gcloud storage buckets create "gs://${GCS_BUCKET}" \
  --location="${REGION}" \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access \
  --public-access-prevention \
  --soft-delete-duration=0 \
  --project="${PROJECT_ID}"

gcloud storage buckets describe "gs://${BUILD_SOURCE_BUCKET}" >/dev/null 2>&1 || \
gcloud storage buckets create "gs://${BUILD_SOURCE_BUCKET}" \
  --location="${REGION}" \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access \
  --public-access-prevention \
  --soft-delete-duration=0 \
  --project="${PROJECT_ID}"

gcloud storage buckets update "gs://${BUILD_SOURCE_BUCKET}" \
  --lifecycle-file="${SCRIPT_DIR}/build-source-lifecycle.json"

gcloud storage buckets add-iam-policy-binding "gs://${GCS_BUCKET}" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/storage.objectUser" >/dev/null

gcloud artifacts repositories add-iam-policy-binding "${REPOSITORY}" \
  --location="${REGION}" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/artifactregistry.writer" \
  --project="${PROJECT_ID}" >/dev/null

for role in roles/cloudbuild.builds.editor roles/logging.logWriter; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${BUILD_SA}" \
    --role="${role}" >/dev/null
done

for role in roles/storage.bucketViewer roles/storage.objectUser; do
  gcloud storage buckets add-iam-policy-binding "gs://${BUILD_SOURCE_BUCKET}" \
    --member="serviceAccount:${BUILD_SA}" \
    --role="${role}" >/dev/null
done

gcloud iam service-accounts add-iam-policy-binding "${BUILD_SA}" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/iam.serviceAccountUser" \
  --project="${PROJECT_ID}" >/dev/null

gcloud iam service-accounts add-iam-policy-binding "${RUNTIME_SA}" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/iam.serviceAccountUser" \
  --project="${PROJECT_ID}" >/dev/null

DEPLOYER_ACCOUNT="$(gcloud config get-value account)"
for service_account in "${BUILD_SA}" "${RUNTIME_SA}"; do
  gcloud iam service-accounts add-iam-policy-binding "${service_account}" \
    --member="user:${DEPLOYER_ACCOUNT}" \
    --role="roles/iam.serviceAccountUser" \
    --project="${PROJECT_ID}" >/dev/null
done

for secret in \
  cityfarmerplus-db-url \
  cityfarmerplus-db-username \
  cityfarmerplus-db-password \
  cityfarmerplus-jwt-secret; do
  gcloud secrets describe "${secret}" --project="${PROJECT_ID}" >/dev/null 2>&1 || \
  gcloud secrets create "${secret}" \
    --replication-policy=automatic \
    --project="${PROJECT_ID}"
  gcloud secrets add-iam-policy-binding "${secret}" \
    --member="serviceAccount:${RUNTIME_SA}" \
    --role="roles/secretmanager.secretAccessor" \
    --project="${PROJECT_ID}" >/dev/null
done

cat <<EOF
Bootstrap complete.
Project: ${PROJECT_ID}
Region: ${REGION}
Service: ${SERVICE}
Runtime account: ${RUNTIME_SA}
Build account: ${BUILD_SA}
Application bucket: gs://${GCS_BUCKET}
Build source bucket: gs://${BUILD_SOURCE_BUCKET}
EOF
