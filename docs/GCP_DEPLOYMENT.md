# GCP Cloud Run 배포 가이드

## 1. 배포 기준

이 문서는 `main` 브랜치의 CityFarmerPlus API를 Google Cloud에 배포하는 계약이다.

기본안은 비용을 최대한 무료 한도 안에 유지하기 위해 다음 구조를 사용한다.

```text
GitHub main
  -> Cloud Build
  -> Artifact Registry
  -> Cloud Run (us-west1, min 0, max 1)
       -> 기존 외부 MySQL
       -> Cloud Storage (us-west1, 비공개 bucket)
       -> Secret Manager
```

- 애플리케이션: Cloud Run
- 이미지: Artifact Registry
- 자동 배포: Cloud Build의 `main` push trigger
- 데이터베이스: 현재 사용 중인 외부 MySQL 재사용
- 파일: Google Cloud Storage와 Application Default Credentials(ADC)
- 비밀값: Secret Manager
- Readiness: `GET /health` (애플리케이션과 DB `SELECT 1` 준비 상태 확인)
- Liveness: `GET /health/live` (프로세스 생존 확인, DB 장애와 분리)

Cloud SQL은 장기 상시 무료가 아니므로 기본안에서 생성하지 않는다. 이후 유료 운영 환경이 필요할 때 별도 마이그레이션으로 전환한다.

## 2. 비용과 무료 한도

GCP 무료 한도를 사용하려면 활성 결제 계정이 필요하다. 결제수단 등록 자체가 사용료를 발생시키지는 않지만, 무료 한도를 넘거나 무료 대상이 아닌 리소스를 만들면 등록한 결제수단으로 청구된다.

이 구성은 다음 조건을 전제로 한다.

- Cloud Run은 요청 기반 과금, `min instances=0`, `max instances=1`을 사용한다. 월 무료 한도는 요청 200만 건, 메모리 360,000 GiB-초, vCPU 180,000초다.
- Cloud Storage bucket은 Always Free 대상인 `us-west1`에 만든다. Standard Storage 무료 한도는 월 5 GiB이며 세 개의 미국 무료 리전 사용량을 합산한다.
- Cloud Build는 무료 대상인 기본 풀 `e2-standard-2`를 사용한다. 결제 계정당 월 2,500 build-minutes까지 무료다.
- Artifact Registry에는 전용 repository 정리 정책을 적용해 최근 이미지 한 개만 남긴다. 무료 저장공간은 결제 계정당 0.5 GiB다.
- Secret Manager는 DB/JWT/KAMIS용 다섯 개의 활성 secret version만 유지한다. 무료 한도는 활성 version 6개와 월 10,000회 access다.
- Cloud SQL, Serverless VPC Access connector, 최소 인스턴스 1 이상은 만들지 않는다.
- 외부 MySQL 제공자의 무료 한도와 만료 정책은 별도로 확인한다.

Cloud Run, Cloud Storage, Artifact Registry에는 각각 무료 사용량 제한이 있다. 한도를 넘으면 자동으로 차단되는 것이 아니라 과금된다. Cloud Billing 예산은 알림 기능이며 하드 지출 상한이 아니다.

미국 리전을 사용하므로 한국 사용자의 네트워크 지연은 서울 리전보다 커질 수 있다. 비용보다 응답 지연이 중요해지면 `asia-northeast3`로 옮기되 Cloud Storage와 네트워크 비용을 다시 계산한다.

## 3. 현재 애플리케이션 계약

Cloud Run은 `PORT`를 주입한다. 애플리케이션은 `0.0.0.0:${PORT}`에 바인딩하며 Docker 기본 포트는 `8080`이다.

배포 시 필요한 주요 환경 변수는 다음과 같다.

| 변수 | 저장 위치 | 설명 |
|---|---|---|
| `DB_URL` | Secret Manager | 현재 외부 MySQL JDBC URL |
| `DB_USERNAME` | Secret Manager | 전용 DB 사용자 |
| `DB_PASSWORD` | Secret Manager | DB 비밀번호 |
| `JWT_SECRET` | Secret Manager | 32바이트 이상 난수 |
| `KAMIS_API_KEY` | Secret Manager | KAMIS Open API 인증 키. 저장과 배포 연결만 담당하며, 실제 시세 호출에는 별도 요청자 ID와 클라이언트 구현이 필요 |
| `JWT_ISSUER` | Cloud Run env | 고정 issuer. 예: `urn:cityfarmerplus:api` |
| `CORS_ALLOWED_ORIGINS` | Cloud Run env | 실제 프론트 Origin 목록 |
| `FILE_STORAGE_TYPE` | Cloud Run env | `gcs` |
| `GCS_PROJECT_ID` | Cloud Run env | GCP 프로젝트 ID |
| `GCS_BUCKET` | Cloud Run env | 비공개 bucket 이름 |
| `GCS_PREFIX` | Cloud Run env | 예: `cityfarmerplus/prod` |
| `DB_POOL_MAX_SIZE` | Cloud Run env | 기본 `5` |
| `DB_POOL_MIN_IDLE` | Cloud Run env | 기본 `0` |
| `DB_CONNECTION_TIMEOUT` | Cloud Run env | DB 연결 1회 제한 `10000`ms |
| `DB_POOL_INITIALIZATION_FAIL_TIMEOUT` | Cloud Run env | 기동 시 DB/DNS 복구를 최대 `60000`ms 재시도 |
| `JPA_DDL_AUTO` | Cloud Run env | 운영 외부 DB에서는 `validate` |

서비스 계정 JSON 키를 만들거나 `GOOGLE_APPLICATION_CREDENTIALS` 파일을 배포하지 않는다. Cloud Run 런타임 서비스 계정에 bucket 권한을 부여하면 Google Cloud Storage Java client가 ADC로 인증한다.

## 4. 사전 조건

1. GCP 결제 계정을 활성화한다.
2. 배포할 프로젝트를 선택한다.
3. 현재 외부 MySQL이 GCP Cloud Run의 인터넷 연결을 허용하는지 확인한다.
4. `main`에 배포할 변경이 커밋·푸시되어 있어야 한다.
5. 실제 DB/JWT 값은 채팅, Git, 문서에 붙여 넣지 않고 사용자가 Cloud Shell 또는 GCP Console에 직접 입력한다.
6. 외부 MySQL을 백업하고 현재 스키마와 애플리케이션 엔티티가 맞는지 확인한다. 운영 DB에는 `ddl-auto=update`를 사용하지 않는다.

backend-2를 처음 배포할 때는 백업 후 `gcp/migrations/20260827_backend2_tables.sql`을 외부 MySQL에 한 번 적용한다. 이 migration은 대리 접수 감사 로그와 출결 정정 이력 테이블을 생성한다. 두 테이블이 조회되는 것을 확인한 뒤에만 `JPA_DDL_AUTO=validate` revision을 배포한다.

로컬 PC에 `gcloud`가 없다면 무료 Cloud Shell에서 아래 명령을 실행할 수 있다.

```bash
export PROJECT_ID="YOUR_PROJECT_ID"
export REGION="us-west1"
export SERVICE="cityfarmerplus-api"
export REPOSITORY="cityfarmerplus"
export RUNTIME_SA_NAME="cityfarmerplus-runtime"
export RUNTIME_SA="${RUNTIME_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
export BUILD_SA_NAME="cityfarmerplus-build"
export BUILD_SA="${BUILD_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
export BUILD_SA_RESOURCE="projects/${PROJECT_ID}/serviceAccounts/${BUILD_SA}"
export GCS_BUCKET="${PROJECT_ID}-cityfarmerplus-private"
export BUILD_SOURCE_BUCKET="${PROJECT_ID}-cityfarmerplus-build-source"

gcloud config set project "$PROJECT_ID"
```

## 5. API와 기본 리소스

필요한 API를 활성화한다.

```bash
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudbuild.googleapis.com \
  cloudresourcemanager.googleapis.com \
  iam.googleapis.com \
  secretmanager.googleapis.com \
  storage.googleapis.com
```

Artifact Registry, 런타임 서비스 계정, 빌드 서비스 계정을 만든다.

```bash
gcloud artifacts repositories create "$REPOSITORY" \
  --repository-format=docker \
  --location="$REGION" \
  --description="CityFarmerPlus Cloud Run images"

gcloud iam service-accounts create "$RUNTIME_SA_NAME" \
  --display-name="CityFarmerPlus Cloud Run runtime"

gcloud iam service-accounts create "$BUILD_SA_NAME" \
  --display-name="CityFarmerPlus Cloud Build"
```

비공개 애플리케이션 bucket과 수동 build source 전용 bucket을 만든다. bucket 이름은 전 세계에서 고유해야 한다. 비용과 개인정보 삭제 의미를 명확히 하기 위해 soft delete는 끈다. 삭제된 객체는 복구할 수 없다.

```bash
gcloud storage buckets create "gs://${GCS_BUCKET}" \
  --project="$PROJECT_ID" \
  --location="$REGION" \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access \
  --public-access-prevention \
  --soft-delete-duration=0

gcloud storage buckets create "gs://${BUILD_SOURCE_BUCKET}" \
  --project="$PROJECT_ID" \
  --location="$REGION" \
  --default-storage-class=STANDARD \
  --uniform-bucket-level-access \
  --public-access-prevention \
  --soft-delete-duration=0

gcloud storage buckets update "gs://${BUILD_SOURCE_BUCKET}" \
  --lifecycle-file=gcp/build-source-lifecycle.json

gcloud storage buckets add-iam-policy-binding "gs://${GCS_BUCKET}" \
  --member="serviceAccount:${RUNTIME_SA}" \
  --role="roles/storage.objectUser"
```

## 6. Cloud Build 최소 권한

전용 build 서비스 계정은 앱의 GCS 파일이나 Secret Manager 값을 읽지 않는다. Artifact Registry repository에 쓰고 로그를 남기는 권한을 주며, Cloud Run 권한은 최초 service 생성 후 해당 service 하나에만 추가한다.

```bash
gcloud artifacts repositories add-iam-policy-binding "$REPOSITORY" \
  --location="$REGION" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/artifactregistry.writer"

for ROLE in \
  roles/logging.logWriter \
  roles/cloudbuild.builds.editor
do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${BUILD_SA}" \
    --role="$ROLE"
done

gcloud storage buckets add-iam-policy-binding "gs://${BUILD_SOURCE_BUCKET}" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/storage.bucketViewer"

gcloud storage buckets add-iam-policy-binding "gs://${BUILD_SOURCE_BUCKET}" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/storage.objectUser"

gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/iam.serviceAccountUser"

gcloud iam service-accounts add-iam-policy-binding "$BUILD_SA" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/iam.serviceAccountUser"
```

현재 로그인한 배포 사용자가 build와 runtime 서비스 계정을 사용할 수 있도록 `actAs` 권한을 준다.

```bash
export DEPLOYER_ACCOUNT="$(gcloud config get-value account)"

for SERVICE_ACCOUNT in "$BUILD_SA" "$RUNTIME_SA"
do
  gcloud iam service-accounts add-iam-policy-binding "$SERVICE_ACCOUNT" \
    --member="user:${DEPLOYER_ACCOUNT}" \
    --role="roles/iam.serviceAccountUser"
done
```

## 7. Secret Manager

다섯 개의 secret을 만든다. 이미 존재하면 그대로 재사용한다.

```bash
for SECRET in \
  cityfarmerplus-db-url \
  cityfarmerplus-db-username \
  cityfarmerplus-db-password \
  cityfarmerplus-jwt-secret \
  cityfarmerplus-kamis-api-key
do
  gcloud secrets describe "$SECRET" >/dev/null 2>&1 || \
    gcloud secrets create "$SECRET" --replication-policy=automatic
done
```

값은 사용자가 Cloud Shell에서 직접 입력한다. 다음 방식은 백슬래시와 앞뒤 공백을 보존하고 입력값을 화면과 shell history에 남기지 않는다. 출력된 version 번호를 변수에 저장해 Cloud Run이 정확한 version을 참조하게 한다.

```bash
IFS= read -r -s -p "DB URL: " VALUE; echo
DB_URL_VERSION="$(printf %s "$VALUE" | gcloud secrets versions add \
  cityfarmerplus-db-url --data-file=- --format='value(name)')"
DB_URL_VERSION="${DB_URL_VERSION##*/}"
unset VALUE

IFS= read -r -s -p "DB username: " VALUE; echo
DB_USERNAME_VERSION="$(printf %s "$VALUE" | gcloud secrets versions add \
  cityfarmerplus-db-username --data-file=- --format='value(name)')"
DB_USERNAME_VERSION="${DB_USERNAME_VERSION##*/}"
unset VALUE

IFS= read -r -s -p "DB password: " VALUE; echo
DB_PASSWORD_VERSION="$(printf %s "$VALUE" | gcloud secrets versions add \
  cityfarmerplus-db-password --data-file=- --format='value(name)')"
DB_PASSWORD_VERSION="${DB_PASSWORD_VERSION##*/}"
unset VALUE

IFS= read -r -s -p "JWT secret (32 bytes or more): " VALUE; echo
JWT_SECRET_VERSION="$(printf %s "$VALUE" | gcloud secrets versions add \
  cityfarmerplus-jwt-secret --data-file=- --format='value(name)')"
JWT_SECRET_VERSION="${JWT_SECRET_VERSION##*/}"
unset VALUE

IFS= read -r -s -p "KAMIS API key: " VALUE; echo
KAMIS_API_KEY_VERSION="$(printf %s "$VALUE" | gcloud secrets versions add \
  cityfarmerplus-kamis-api-key --data-file=- --format='value(name)')"
KAMIS_API_KEY_VERSION="${KAMIS_API_KEY_VERSION##*/}"
unset VALUE
```

기존 로그인 token을 유지해야 한다면 운영 환경에서 사용 중인 JWT secret을 그대로 입력한다. 새 값으로 바꾸면 기존 로그인 token이 모두 무효가 된다. 새 version으로 회전할 때는 `versions add` → Cloud Run 참조 변경 → smoke test → 구 version disable 순서를 지킨다.

런타임 서비스 계정에는 필요한 secret만 읽을 수 있게 부여한다.

```bash
for SECRET in \
  cityfarmerplus-db-url \
  cityfarmerplus-db-username \
  cityfarmerplus-db-password \
  cityfarmerplus-jwt-secret \
  cityfarmerplus-kamis-api-key
do
  gcloud secrets add-iam-policy-binding "$SECRET" \
    --member="serviceAccount:${RUNTIME_SA}" \
    --role="roles/secretmanager.secretAccessor"
done
```

## 8. 최초 이미지와 Cloud Run 서비스

GitHub `main`을 가져와 테스트한 뒤 최초 이미지를 만든다.

```bash
git clone https://github.com/ParkChanWoo0321/CityFarmerPlus_BE.git
cd CityFarmerPlus_BE
git checkout main
git pull --ff-only origin main

chmod +x gradlew
./gradlew test --no-daemon --max-workers=1

gcloud artifacts repositories set-cleanup-policies "$REPOSITORY" \
  --location="$REGION" \
  --policy=gcp/artifact-cleanup-policy.json

gcloud builds submit . \
  --config=cloudbuild.yaml \
  --region="$REGION" \
  --service-account="$BUILD_SA_RESOURCE" \
  --gcs-source-staging-dir="gs://${BUILD_SOURCE_BUCKET}/source"
```

정리 정책은 전용 repository에서 최신 version 하나만 보존하고 나머지를 삭제한다. build source bucket의 업로드 파일도 1일 후 삭제한다. 정책 실행에는 최대 하루 정도 걸릴 수 있으므로 짧은 시간에는 이미지가 둘 이상 보일 수 있다.

수동 build에서는 `cloudbuild.yaml`의 기본값인 `_TAG=bootstrap`, `_DEPLOY=false`가 적용되어 이미지만 push하고 아직 존재하지 않는 Cloud Run service 갱신은 건너뛴다.

비밀이 아닌 설정은 YAML 파일로 전달한다. 쉼표로 구분된 여러 CORS Origin도 안전하게 전달할 수 있다. 예제 파일을 `/tmp`에 복사하고 `YOUR_...` 값을 실제 값으로 직접 바꾼다. 기존 token을 유지해야 한다면 운영 중인 `JWT_ISSUER` 값을 유지한다. `CORS_ALLOWED_ORIGINS`에는 경로가 아닌 `https://host` 형식만 넣는다.

```bash
cp gcp/cloud-run.env.yaml.example /tmp/cityfarmerplus-cloud-run.env.yaml
nano /tmp/cityfarmerplus-cloud-run.env.yaml

export IMAGE="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPOSITORY}/${SERVICE}:bootstrap"

gcloud run deploy "$SERVICE" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --platform=managed \
  --image="$IMAGE" \
  --allow-unauthenticated \
  --service-account="$RUNTIME_SA" \
  --port=8080 \
  --cpu=1 \
  --memory=2Gi \
  --concurrency=1 \
  --min=0 \
  --max=1 \
  --timeout=300 \
  --cpu-throttling \
  --env-vars-file=/tmp/cityfarmerplus-cloud-run.env.yaml \
  --set-secrets="DB_URL=cityfarmerplus-db-url:${DB_URL_VERSION},DB_USERNAME=cityfarmerplus-db-username:${DB_USERNAME_VERSION},DB_PASSWORD=cityfarmerplus-db-password:${DB_PASSWORD_VERSION},JWT_SECRET=cityfarmerplus-jwt-secret:${JWT_SECRET_VERSION},KAMIS_API_KEY=cityfarmerplus-kamis-api-key:${KAMIS_API_KEY_VERSION}" \
  --startup-probe="httpGet.path=/health,httpGet.port=8080,initialDelaySeconds=0,failureThreshold=24,timeoutSeconds=2,periodSeconds=10" \
  --liveness-probe="httpGet.path=/health/live,httpGet.port=8080,initialDelaySeconds=0,failureThreshold=3,timeoutSeconds=2,periodSeconds=30"

gcloud run services add-iam-policy-binding "$SERVICE" \
  --project="$PROJECT_ID" \
  --region="$REGION" \
  --member="serviceAccount:${BUILD_SA}" \
  --role="roles/run.developer"
```

서비스 URL과 health를 확인한다.

```bash
export SERVICE_URL="$(gcloud run services describe "$SERVICE" \
  --region="$REGION" \
  --format='value(status.url)')"

curl -i "${SERVICE_URL}/health"
```

정상 응답은 `200`과 `{"status":"UP"}`이다. `/health`는 HTTP 프로세스만 확인하며 DB와 GCS 연결 성공을 증명하지 않는다.

512 MiB는 비용 최소화를 위한 시작값이다. 배포 로그에서 메모리 종료가 확인되면 기능을 줄이거나 1 GiB로 올려야 한다. 저트래픽에서는 1 GiB도 무료 한도 안에 머물 수 있지만 무료 한도 소진 속도는 더 빨라진다.

저장소에 포함된 `gcp/bootstrap.sh`는 이 문서의 API·서비스 계정·전용 bucket·Secret 껍데기 생성을 멱등 실행한다. `gcp/deploy.sh`는 clean `main`이 최신 `origin/main`과 같은지 확인하고 secret의 숫자 version을 고정해 최초 수동 배포를 수행한다.

```bash
bash gcp/bootstrap.sh
# 네 개 secret version을 안전하게 입력한 다음
bash gcp/deploy.sh
```

두 스크립트는 이 프로젝트 ID와 `us-west1` 전용이다. 최초 배포자는 프로젝트 Owner이거나 API/Artifact Registry/Storage/IAM/Secret Manager/Cloud Build/Cloud Run을 만들고 공개 서비스 IAM을 설정할 동등한 권한이 있어야 한다. 이후 `main` trigger가 구성되면 `deploy.sh`를 동시에 실행해 같은 커밋을 중복 빌드하지 않는다.

## 9. main 자동 배포

`cloudbuild.yaml`은 다음 순서로 실행된다.

1. 브랜치가 `main`인지 확인한다.
2. 전체 Gradle 테스트를 실행한다.
3. Docker 이미지를 빌드하고 Artifact Registry에 push한다.
4. 이미 만들어진 Cloud Run 서비스의 이미지와 허용 프론트 Origin을 갱신한다.

따라서 최초 Cloud Run 서비스와 환경 변수·secret·서비스 계정은 8절에서 먼저 만들어야 한다.

Cloud Build trigger는 GCP Console에서 다음 값으로 만든다.

| 항목 | 값 |
|---|---|
| Trigger region | `global` (무료 기본 build pool 사용) |
| Event | Push to a branch |
| Repository | `ParkChanWoo0321/CityFarmerPlus_BE` |
| Branch regex | `^main$` |
| Ignored files | `docs/**`, `**/*.md` |
| Configuration | Cloud Build configuration file |
| Location | `/cloudbuild.yaml` |
| Service account | `cityfarmerplus-build@PROJECT_ID.iam.gserviceaccount.com` |
| `_REGION` | `us-west1` |
| `_REPOSITORY` | `cityfarmerplus` |
| `_IMAGE` | `cityfarmerplus-api` |
| `_SERVICE` | `cityfarmerplus-api` |
| `_TAG` | `$SHORT_SHA` |
| `_DEPLOY` | `true` |
| `_CORS_ALLOWED_ORIGINS` | `https://cityfarmerplus.site,https://www.cityfarmerplus.site`와 필요한 로컬 개발 Origin 목록 |

Trigger 위치와 배포 위치는 서로 다르다. Trigger는 별도 private pool이 필요 없는 `global` 기본 pool에서 실행하고, `_REGION=us-west1`을 통해 Artifact Registry와 Cloud Run은 `us-west1`에 배포한다.

trigger가 사용하는 build 서비스 계정은 6절에서 만든 전용 계정을 반드시 선택한다. Trigger가 실행될 때 YAML 안의 서비스 계정보다 trigger에 지정한 계정이 우선한다.

- Artifact Registry Writer
- Cloud Run Developer
- Logs Writer
- Cloud Build Editor
- 런타임 서비스 계정에 대한 Service Account User

GitHub 연결과 IAM 변경을 마친 뒤 `main` push로 한 번 실행하고, 테스트·이미지 push·Cloud Run revision 배포가 모두 성공했는지 확인한다. `_CORS_ALLOWED_ORIGINS`는 경로가 없는 정확한 Origin만 넣으며, 삭제된 프론트 주소를 남겨 두지 않는다.

## 10. 운영 Smoke Test

운영 배포를 완료한 뒤 다음을 모두 통과해야 한다.

1. `GET /health`가 `200`과 `{ "status": "UP", "database": "UP" }`을 반환한다.
2. 회원가입·로그인·JWT 인증 요청이 성공한다.
3. DB 쓰기 후 다시 조회해 외부 MySQL 연결을 확인한다.
4. 교육 또는 농가 증빙 파일을 업로드하고 다운로드한다.
5. 해당 파일이 비공개 GCS bucket의 `cityfarmerplus/prod` 아래에 생성된다.
6. 새 revision 배포 또는 scale-to-zero 이후에도 파일을 다시 다운로드할 수 있다.
7. 허용 Origin에는 CORS 헤더가 있고, 허용하지 않은 Origin에는 없다.
8. 로그에 DB 비밀번호, JWT secret, access token이 노출되지 않는다.
9. 프론트엔드 API base URL을 새 `run.app` 주소로 바꾼다.

KAMIS secret이 Cloud Run에 연결돼 있다는 사실만으로 시세 API가 동작하는 것은 아니다. KAMIS가 요구하는 요청자 ID를 확보하고, 애플리케이션의 HTTP client·응답 DTO·오류/timeout 정책·프론트용 API와 테스트를 구현한 뒤 별도 smoke test를 통과해야 완료로 판정한다.

JWT secret 또는 issuer를 바꾸면 기존 토큰은 무효가 되므로 사용자는 다시 로그인해야 한다.

## 11. 운영 주의사항

- Cloud Run request-based CPU와 `min=0`에서는 요청이 없을 때 `@Scheduled` 작업 실행이 보장되지 않는다. 현재 파일 삭제 재시도 worker는 best-effort다.
- 무료 운영에서는 keep-alive용 주기 ping을 두지 않는다. 인위적인 요청은 scale-to-zero를 방해하고 무료 사용량을 소비한다.
- Cloud Run HTTP/1 요청 한도에 맞춰 multipart 전체 요청 크기는 31MB로 제한한다.
- 현재 Spring Boot 런타임은 512MiB에서 메모리 한도를 초과했으므로 운영 기준을 2GiB로 유지한다. 이 설정은 1 vCPU의 요청 기반 무료 CPU·RAM 할당량을 약 50시간의 활성 시간까지 균형 있게 사용한다.
- 외부 MySQL의 IP allowlist가 고정 IP만 허용하면 Cloud Run에서 연결되지 않을 수 있다.
- 현재 Aiven 무료 MySQL은 장기간 활동이 없으면 자동으로 꺼질 수 있다. 이 경우 GCP 리소스가 정상이어도 애플리케이션 기동 시 DB DNS/연결 오류로 `5xx`가 발생한다.
- 장애 확인 순서는 `/health`의 `database` 상태 확인 → Aiven 서비스 `Running` 확인 → 공개 DNS/포트 확인 → `GET /api/education/courses`처럼 실제 도메인 조회 API 확인이다. `/health/live`는 DB 연결을 증명하지 않는다.
- `DB_POOL_INITIALIZATION_FAIL_TIMEOUT=60000`은 DB를 켠 직후의 짧은 DNS·연결 지연을 흡수하지만, 꺼진 Aiven 서비스를 대신 켜 주지는 않는다.
- 운영 외부 MySQL은 백업과 스키마 비교 후 처음부터 `JPA_DDL_AUTO=validate`로 연결한다. 변경이 필요하면 복제 DB에서 검증한 migration을 명시적으로 적용한다.
- 전용 Artifact Registry cleanup policy는 최신 version 하나만 보존하고 나머지를 비동기로 삭제한다.
- build source bucket은 lifecycle rule로 업로드 파일을 1일 후 삭제한다.
- 비용 알림을 설정해도 리소스는 자동 중지되지 않는다.
- Cloud Storage soft delete를 껐으므로 앱에서 삭제한 파일은 복구할 수 없다.

## 12. 공식 문서

- [Cloud Run container contract](https://docs.cloud.google.com/run/docs/container-contract)
- [Cloud Run pricing](https://cloud.google.com/run/pricing)
- [Google Cloud Free Tier](https://docs.cloud.google.com/free/docs/free-cloud-features)
- [Cloud Storage pricing and Always Free regions](https://cloud.google.com/storage/pricing)
- [Cloud Storage ADC](https://docs.cloud.google.com/storage/docs/authentication)
- [Cloud Run secrets](https://docs.cloud.google.com/run/docs/configuring/services/secrets)
- [Cloud Build trigger](https://docs.cloud.google.com/build/docs/automate-builds)
- [Cloud Build custom service account](https://docs.cloud.google.com/build/docs/securing-builds/configure-user-specified-service-accounts)
- [Cloud Build pricing](https://cloud.google.com/build/pricing)
- [Artifact Registry cleanup policy](https://docs.cloud.google.com/artifact-registry/docs/repositories/cleanup-policy)
- [Artifact Registry pricing](https://cloud.google.com/artifact-registry/pricing)
- [Secret Manager pricing](https://cloud.google.com/secret-manager/pricing)
- [Aiven for MySQL free tier](https://aiven.io/docs/products/mysql/concepts/mysql-free-tier)
- [Aiven MySQL power cycle](https://aiven.io/docs/products/mysql/howto/power-cycle-service)
