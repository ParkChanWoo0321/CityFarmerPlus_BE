#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ID="${PROJECT_ID:-project-60a7cf7e-b36a-406b-b9e}"
APP_BUCKET="${APP_BUCKET:-${PROJECT_ID}-cityfarmerplus-private}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
MIGRATION_FILE="${SCRIPT_DIR}/migrations/20260827_backend2_tables.sql"
TEMP_DIR=""

cleanup() {
  unset MYSQL_PWD DB_URL DB_USER DB_PASS DB_CONNECTION DB_AUTHORITY
  unset DB_PATH_AND_QUERY DB_HOST DB_PORT DB_NAME
  if [[ -n "${TEMP_DIR}" && -d "${TEMP_DIR}" ]]; then
    rm -rf -- "${TEMP_DIR}"
  fi
}
trap cleanup EXIT

for required_command in gcloud mysql mysqldump gzip sha256sum git timeout; do
  if ! command -v "${required_command}" >/dev/null 2>&1; then
    printf 'Required command is missing: %s\n' "${required_command}" >&2
    exit 1
  fi
done

if [[ ! -f "${MIGRATION_FILE}" ]]; then
  printf 'Migration file not found: %s\n' "${MIGRATION_FILE}" >&2
  exit 1
fi

gcloud storage buckets describe "gs://${APP_BUCKET}" \
  --project="${PROJECT_ID}" \
  --format='value(name)' >/dev/null

DB_URL="$(gcloud secrets versions access latest \
  --secret=cityfarmerplus-db-url \
  --project="${PROJECT_ID}")"
DB_USER="$(gcloud secrets versions access latest \
  --secret=cityfarmerplus-db-username \
  --project="${PROJECT_ID}")"
DB_PASS="$(gcloud secrets versions access latest \
  --secret=cityfarmerplus-db-password \
  --project="${PROJECT_ID}")"

if [[ "${DB_URL}" != jdbc:mysql://* ]]; then
  echo 'DB URL must start with jdbc:mysql://.' >&2
  exit 1
fi
if [[ -z "${DB_USER}" || -z "${DB_PASS}" ]]; then
  echo 'DB username and password secrets must not be empty.' >&2
  exit 1
fi

DB_CONNECTION="${DB_URL#jdbc:mysql://}"
DB_AUTHORITY="${DB_CONNECTION%%/*}"
DB_PATH_AND_QUERY="${DB_CONNECTION#*/}"
DB_NAME="${DB_PATH_AND_QUERY%%\?*}"
if [[ "${DB_AUTHORITY}" == *:* ]]; then
  DB_HOST="${DB_AUTHORITY%:*}"
  DB_PORT="${DB_AUTHORITY##*:}"
else
  DB_HOST="${DB_AUTHORITY}"
  DB_PORT="3306"
fi

if [[ -z "${DB_HOST}" || -z "${DB_NAME}" || ! "${DB_PORT}" =~ ^[0-9]+$ ]]; then
  echo 'DB URL host, port, or database name could not be parsed.' >&2
  exit 1
fi

MYSQL_BASE_CONNECTION_ARGS=(
  --protocol=TCP
  -h "${DB_HOST}"
  -P "${DB_PORT}"
  -u "${DB_USER}"
)
MYSQL_CONNECTION_ARGS=("${MYSQL_BASE_CONNECTION_ARGS[@]}")
if mysql --help 2>&1 | grep -q -- '--connect-timeout'; then
  MYSQL_CONNECTION_ARGS+=(--connect-timeout=15)
fi
if mysql --help 2>&1 | grep -q -- '--ssl-mode'; then
  MYSQL_CONNECTION_ARGS+=(--ssl-mode=REQUIRED)
elif mysql --help 2>&1 | grep -Eq '(^|[[:space:]])--ssl([=[:space:]]|$)'; then
  MYSQL_CONNECTION_ARGS+=(--ssl)
fi

MYSQLDUMP_CONNECTION_ARGS=("${MYSQL_BASE_CONNECTION_ARGS[@]}")
if mysqldump --help 2>&1 | grep -q -- '--ssl-mode'; then
  MYSQLDUMP_CONNECTION_ARGS+=(--ssl-mode=REQUIRED)
elif mysqldump --help 2>&1 | grep -Eq '(^|[[:space:]])--ssl([=[:space:]]|$)'; then
  MYSQLDUMP_CONNECTION_ARGS+=(--ssl)
fi

TABLE_COUNT="$(MYSQL_PWD="${DB_PASS}" mysql \
  "${MYSQL_CONNECTION_ARGS[@]}" \
  --batch --skip-column-names \
  --execute='SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE();' \
  "${DB_NAME}")"
if [[ ! "${TABLE_COUNT}" =~ ^[0-9]+$ ]]; then
  echo 'Read-only database preflight did not return a table count.' >&2
  exit 1
fi
printf 'Read-only database preflight: ok (%s tables)\n' "${TABLE_COUNT}"

TEMP_DIR="$(mktemp -d)"
TIMESTAMP="$(date -u +'%Y%m%dT%H%M%SZ')"
BACKUP_BASENAME="cityfarmerplus-before-backend2-${TIMESTAMP}.sql.gz"
BACKUP_FILE="${TEMP_DIR}/${BACKUP_BASENAME}"
BACKUP_SHA_FILE="${BACKUP_FILE}.sha256"
MIGRATION_SHA_FILE="${TEMP_DIR}/20260827_backend2_tables.sql.sha256"
METADATA_FILE="${TEMP_DIR}/migration-metadata.txt"
REMOTE_BACKUP_DIR="gs://${APP_BUCKET}/backups/mysql/${TIMESTAMP}"

DUMP_ARGS=(--single-transaction --quick --triggers --hex-blob)
if mysqldump --help 2>&1 | grep -q -- '--set-gtid-purged'; then
  DUMP_ARGS+=(--set-gtid-purged=OFF)
fi
if mysqldump --help 2>&1 | grep -q -- '--column-statistics'; then
  DUMP_ARGS+=(--column-statistics=0)
fi
if mysqldump --help 2>&1 | grep -q -- '--no-tablespaces'; then
  DUMP_ARGS+=(--no-tablespaces)
fi

MYSQL_PWD="${DB_PASS}" timeout --preserve-status 300 mysqldump \
  "${MYSQLDUMP_CONNECTION_ARGS[@]}" \
  "${DUMP_ARGS[@]}" \
  "${DB_NAME}" | gzip -9 >"${BACKUP_FILE}"

if [[ ! -s "${BACKUP_FILE}" ]]; then
  echo 'Database backup is empty; migration was not applied.' >&2
  exit 1
fi

BACKUP_SHA="$(sha256sum "${BACKUP_FILE}" | awk '{print $1}')"
MIGRATION_SHA="$(sha256sum "${MIGRATION_FILE}" | awk '{print $1}')"
printf '%s  %s\n' "${BACKUP_SHA}" "${BACKUP_BASENAME}" >"${BACKUP_SHA_FILE}"
printf '%s  %s\n' "${MIGRATION_SHA}" "$(basename "${MIGRATION_FILE}")" >"${MIGRATION_SHA_FILE}"
COMMIT_SHA="$(git -C "${REPOSITORY_DIR}" rev-parse HEAD 2>/dev/null || printf 'unknown')"
printf 'project=%s\ncommit=%s\ntimestamp_utc=%s\ntables_before=%s\nbackup_sha256=%s\nmigration_sha256=%s\n' \
  "${PROJECT_ID}" \
  "${COMMIT_SHA}" \
  "${TIMESTAMP}" \
  "${TABLE_COUNT}" \
  "${BACKUP_SHA}" \
  "${MIGRATION_SHA}" >"${METADATA_FILE}"

gcloud storage cp \
  "${BACKUP_FILE}" \
  "${BACKUP_SHA_FILE}" \
  "${MIGRATION_FILE}" \
  "${MIGRATION_SHA_FILE}" \
  "${METADATA_FILE}" \
  "${REMOTE_BACKUP_DIR}/" \
  --project="${PROJECT_ID}" >/dev/null
gcloud storage objects describe \
  "${REMOTE_BACKUP_DIR}/${BACKUP_BASENAME}" \
  --project="${PROJECT_ID}" \
  --format='value(name)' >/dev/null
printf 'Private backup uploaded: %s/%s\n' \
  "${REMOTE_BACKUP_DIR}" \
  "${BACKUP_BASENAME}"
printf 'Backup SHA-256: %s\n' "${BACKUP_SHA}"

MYSQL_PWD="${DB_PASS}" mysql \
  "${MYSQL_CONNECTION_ARGS[@]}" "${DB_NAME}" <"${MIGRATION_FILE}"

VERIFY_SQL="$(cat <<'SQL'
SELECT CONCAT_WS('|',
  (SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('proxy_registration_logs', 'work_assignment_corrections')),
  (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'education_certificate_submissions'
      AND column_name = 'required_hours_snapshot'
      AND data_type = 'int'
      AND is_nullable = 'NO'),
  (SELECT character_maximum_length FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'proxy_registration_logs'
      AND column_name = 'action_type'),
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'proxy_registration_logs'
      AND index_name = 'idx_proxy_registration_logs_target_user'),
  (SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'work_assignment_corrections'
      AND index_name = 'idx_work_assignment_corrections_assignment'),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'proxy_registration_logs'),
  (SELECT COUNT(*) FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'work_assignment_corrections'),
  (SELECT COUNT(*) FROM education_certificate_submissions
    WHERE required_hours_snapshot IS NULL)
);
SQL
)"

SCHEMA_RESULT="$(MYSQL_PWD="${DB_PASS}" mysql \
  "${MYSQL_CONNECTION_ARGS[@]}" \
  --batch --skip-column-names \
  --execute="${VERIFY_SQL}" \
  "${DB_NAME}")"
if [[ "${SCHEMA_RESULT}" != '2|1|64|1|1|2|2|0' ]]; then
  printf 'Schema verification failed: %s\n' "${SCHEMA_RESULT}" >&2
  printf 'Backup remains available at: %s/%s\n' \
    "${REMOTE_BACKUP_DIR}" \
    "${BACKUP_BASENAME}" >&2
  exit 1
fi

echo 'Backend-2 schema migration verification: ok'
printf 'Migration commit: %s\n' "${COMMIT_SHA}"
