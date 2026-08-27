# CityFarmerPlus 농가 소유 증빙 제출 API 명세서

- 문서 버전: 2.0
- 갱신일: 2026-08-20
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 제출, 반려 후 재제출 이력, 본인 목록·상세·파일 다운로드
- 관련 문서: `FARM_PROFILE_API_SPEC.md`, `API_SPEC_INDEX.md`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 로컬 Base URL: `http://localhost:8080`

## 1. 핵심 규칙

- `FARM` 역할의 활성 계정만 본인 농가 프로필에 증빙을 제출할 수 있다.
- 제출 대상 농가 프로필은 요청값이 아니라 JWT `sub` 회원 ID로 결정한다.
- 농가 프로필이 `DRAFT` 또는 `REJECTED`일 때만 제출할 수 있다.
- 제출이 완료되면 농가 프로필은 `PENDING_REVIEW`로 변경된다.
- 한 번 제출된 회차와 파일 메타데이터는 수정하거나 덮어쓰지 않는다.
- 중개센터 심사로 프로필이 `REJECTED`가 되면 같은 API가 기존 회차를 보존하고 `attemptNumber`를 1 증가시킨다.
- `PENDING_REVIEW`, `APPROVED`, `INACTIVE` 상태에서는 새 제출을 허용하지 않는다.
- 본인 제출 이력·상세·파일 다운로드와 회원 탈퇴 후 파일 삭제 작업 등록이 구현되어 있다.
- `CENTER_ADMIN`은 담당자용 제출 조회·파일 다운로드·승인·반려 API를 사용할 수 있다. 상세 계약은 `ADMIN_FARM_OWNERSHIP_API_SPEC.md`를 따른다.

통합된 중개센터 심사 API로 `REJECTED` 상태를 만든 뒤 농가가 동일 제출 API를 호출하면 새 회차를 만들 수 있다.

## 2. API

| 기능 | Method | URL | 권한 | 성공 상태 |
|---|---|---|---|---|
| 내 제출 이력 | `GET` | `/api/farm-profiles/me/ownership-submissions` | `ROLE_FARM` | `200 OK` |
| 내 제출 상세 | `GET` | `/api/farm-profiles/me/ownership-submissions/{submissionId}` | `ROLE_FARM` | `200 OK` |
| 농가 소유 증빙 제출 | `POST` | `/api/farm-profiles/me/ownership-submissions` | `ROLE_FARM` | `201 Created` |
| 본인 문서 다운로드 | `GET` | `/api/farm-ownership-documents/{documentId}/file` | `ROLE_FARM` 소유자 | `200 OK` |

## 3. 요청

```http
POST /api/farm-profiles/me/ownership-submissions
Authorization: Bearer {{farmAccessToken}}
Content-Type: multipart/form-data
```

`multipart/form-data`에서 같은 이름의 파일 파트를 반복해 전송한다.

| 파트명 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `documents` | File Array | O | 1~5개 |

파일 정책은 다음과 같다.

| 항목 | 정책 |
|---|---|
| 허용 확장자 | `pdf`, `jpg`, `jpeg`, `png` |
| 개별 파일 크기 | 최대 10 MiB (`10,485,760`바이트) |
| 한 요청의 파일 총크기 | 최대 30 MiB (`31,457,280`바이트) |
| 서버 multipart 수신 한도 | 파일당 `12MB`, 요청당 `31MB` |
| 원본 파일명 | UTF-8 기준 최대 255바이트, 경로 부분 제거 후 표시용으로만 저장 |
| 형식 검증 | 확장자, 확인 가능한 선언 MIME 타입, 실제 파일 시그니처를 교차 검증 |
| 무결성 | 저장된 파일의 크기와 SHA-256을 다시 계산해 검증 결과와 비교 |

선언 MIME 타입이 없거나 빈 문자열 또는 `application/octet-stream`이면 MIME 비교는 생략하고 확장자와 실제 파일 시그니처로 검증한다. 서버 multipart 한도를 먼저 넘으면 도메인 크기 검사 전에 `UPLOAD_REQUEST_TOO_LARGE`가 반환될 수 있다.

Postman에서는 `Body → form-data`를 선택하고 `documents` 키의 타입을 `File`로 바꾼다. 여러 파일은 같은 `documents` 키를 여러 줄 추가하거나 다중 선택한다.

서버는 클라이언트가 전달한 파일명을 실제 저장 경로로 사용하지 않는다. UUID 파일명으로 저장하여 경로 조작과 이름 충돌을 방지한다.

## 4. 성공 응답

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

```json
{
  "id": 200,
  "attemptNumber": 1,
  "status": "PENDING_REVIEW",
  "farmProfileStatus": "PENDING_REVIEW",
  "submittedAt": "2026-08-04T00:00:00Z",
  "reviewerId": null,
  "reviewerName": null,
  "reviewedAt": null,
  "rejectionReason": null,
  "documents": [
    {
      "id": 300,
      "originalFilename": "토지대장.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 1048576
    }
  ],
  "farmNameSnapshot": "충주 사과농원",
  "representativeNameSnapshot": "홍길동",
  "farmAddressSnapshot": "충청북도 충주시 예시로 1",
  "cityCountySnapshot": "CHUNGJU",
  "businessRegistrationNumberSnapshot": "1234567890",
  "farmAreaPyeongSnapshot": 1500
}
```

보안을 위해 응답에는 서버의 `storageKey`와 SHA-256 값을 노출하지 않는다.

## 5. 상태 전이와 제출 회차

```text
DRAFT ── 첫 제출(attempt 1) ──> PENDING_REVIEW
REJECTED ── 재제출(attempt N+1) ──> PENDING_REVIEW
```

동일 농가의 두 요청이 동시에 제출 회차를 만들지 못하도록 농가 프로필 행을 비관적 쓰기 잠금으로 조회한다. DB에도 `(farm_profile_id, attempt_number)` UNIQUE 제약을 두어 중복 회차를 이중 방어한다.

중개센터가 심사할 때 최신 `PENDING_REVIEW` 회차와 농가 프로필 상태를 함께 변경한다. 반려된 회차와 파일은 그대로 남기고, 재제출은 별도의 새 회차로 저장한다.

## 6. 오류 응답

아래 표에 정의한 도메인 오류와 업로드 크기 오류는 다음 형식이다.

```json
{
  "code": "OWNERSHIP_DOCUMENTS_REQUIRED",
  "message": "농가 소유 증빙 파일을 한 개 이상 첨부해야 합니다."
}
```

| HTTP | 코드 | 발생 조건 |
|---|---|---|
| `400` | `OWNERSHIP_DOCUMENTS_REQUIRED` | 파일 파트가 없거나 빈 파일 포함 |
| `400` | `TOO_MANY_OWNERSHIP_DOCUMENTS` | 파일이 5개를 초과함 |
| `400` | `INVALID_OWNERSHIP_DOCUMENT_FILENAME` | 파일명이 없거나 허용 길이·문자 규칙 위반 |
| `400` | `INVALID_OWNERSHIP_DOCUMENT_CONTENT` | 업로드 스트림을 읽지 못했거나 실제 파일 시그니처가 확장자 또는 MIME 타입과 불일치 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `401` | `INVALID_AUTHENTICATION` | JWT `sub`가 숫자 회원 ID가 아님 |
| `403` | `ACCESS_DENIED` | 도시농부 또는 담당자 JWT로 접근 |
| `403` | `FARM_ROLE_REQUIRED` | JWT 역할과 DB의 실제 역할이 불일치 |
| `403` | `INACTIVE_ACCOUNT` | 정지 또는 탈퇴한 계정 |
| `404` | `USER_NOT_FOUND` | JWT 회원 ID에 해당하는 사용자가 없음 |
| `404` | `FARM_PROFILE_NOT_FOUND` | 농가 프로필을 아직 등록하지 않음 |
| `404` | `OWNERSHIP_SUBMISSION_NOT_FOUND` | 본인 소유가 아닌 제출 또는 존재하지 않는 제출 상세 조회 |
| `404` | `OWNERSHIP_DOCUMENT_NOT_FOUND` | 문서 ID가 없음 |
| `409` | `OWNERSHIP_SUBMISSION_NOT_ALLOWED` | 현재 프로필 상태에서 제출할 수 없음 |
| `409` | `OWNERSHIP_SUBMISSION_DATA_CONFLICT` | 동시 처리 등으로 DB 제약 충돌 |
| `413` | `OWNERSHIP_DOCUMENT_TOO_LARGE` | 개별 파일이 10 MiB 초과 |
| `413` | `OWNERSHIP_DOCUMENTS_TOTAL_SIZE_TOO_LARGE` | 파일 총크기가 30 MiB 초과 |
| `413` | `UPLOAD_REQUEST_TOO_LARGE` | multipart 파일 12MB 또는 요청 31MB의 서버 수신 한도 초과 |
| `415` | `UNSUPPORTED_OWNERSHIP_DOCUMENT_TYPE` | 확장자가 없거나 점으로 끝나거나 허용되지 않은 확장자 |
| `403` | `OWNERSHIP_DOCUMENT_ACCESS_DENIED` | 다른 농가의 문서 다운로드 시도 |
| `500` | `OWNERSHIP_DOCUMENT_STORAGE_ERROR` | 파일 저장 실패 또는 저장 중 파일 내용 불일치 |
| `500` | `OWNERSHIP_DOCUMENT_READ_ERROR` | 저장 파일이 없거나 읽을 수 없음 |

잘못된 `Content-Type`이나 파싱할 수 없는 multipart 자체의 오류는 아직 이 기능의 공통 오류 계약에 포함하지 않는다. 전역 multipart 오류 형식은 공통 예외 처리 기능에서 별도로 통일한다.

## 7. 저장 구조

### 7.1 로컬 파일 저장소

현재 파일 저장 루트는 다음 환경 변수로 설정한다.

```properties
FILE_STORAGE_ROOT=./data/uploads
```

저장 키 형식은 다음과 같다.

```text
farm-ownership/{farmProfileId}/{requestBatchUuid}/{storedFileUuid}.{extension}
```

`FileStorage` 인터페이스 뒤에 로컬 구현을 두어 이후 S3 같은 외부 저장소로 교체할 수 있다. `data/uploads`는 Git 추적 대상에서 제외한다.

### 7.2 `farm_ownership_submissions`

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `id` | PK, Auto Increment | 제출 식별자 |
| `farm_profile_id` | FK, NOT NULL | 농가 프로필 ID |
| `attempt_number` | NOT NULL | 농가별 제출 회차 |
| `status` | NOT NULL | `PENDING_REVIEW`, `APPROVED`, `REJECTED` |
| `submitted_at` | NOT NULL | 제출 시각 |
| `reviewer_user_id` | FK, NULL | 공통 심사자 계약 |
| `reviewed_at` | NULL | 심사 시각 |
| `rejection_reason` | NULL, 최대 1,000자 | 반려 사유 |

각 회차에는 농가명, 대표자명, 주소, 시·군, 사업자번호, 농지 면적의 제출 시점 스냅샷도 저장한다.

`(farm_profile_id, attempt_number)`는 UNIQUE다.

### 7.3 `farm_ownership_documents`

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `id` | PK, Auto Increment | 문서 식별자 |
| `submission_id` | FK, NOT NULL | 제출 회차 ID |
| `display_order` | NOT NULL | 요청 파일 순서 |
| `original_filename` | NOT NULL, 최대 255바이트 | 표시용 원본 파일명 |
| `storage_key` | NOT NULL, UNIQUE | 실제 파일 저장 키 |
| `content_type` | NOT NULL | 서버가 판별한 MIME 타입 |
| `size_bytes` | NOT NULL | 실제 저장 크기 |
| `sha256` | NOT NULL, 64자 | 파일 무결성 해시 |
| `created_at` | NOT NULL | 파일 메타데이터 생성 시각 |

`(submission_id, display_order)`는 UNIQUE다. 제출에서 문서로의 cascade는 생성에만 적용하여 제출 이력을 실수로 삭제할 때 문서까지 연쇄 삭제되지 않도록 한다.

## 8. 파일과 DB의 실패 처리

1. 계정·농가 프로필·제출 가능 상태를 먼저 확인한다.
2. 모든 파일의 형식과 크기를 검증한다.
3. 파일을 저장하고 실제 크기와 SHA-256을 확인한다.
4. 별도의 짧은 DB 트랜잭션에서 농가 프로필 잠금, 제출 회차 저장, 상태 전이를 수행한다.
5. 파일 저장 도중 또는 DB 트랜잭션이 실패하면 이번 요청에서 저장한 파일을 역순으로 보상 삭제한다.

파일시스템과 MySQL은 하나의 원자적 트랜잭션으로 묶이지 않는다. 보상 삭제도 실패할 수 있으므로 실패한 저장 키는 서버 로그에 기록한다. 운영 환경에서는 고아 파일과 누락 파일을 점검하는 배치 작업 또는 외부 오브젝트 스토리지 전환이 추가로 필요하다.

## 9. 보안 한계

- 파일 시그니처 검증은 확장자만 신뢰하는 것보다 안전하지만 파일 전체의 정상성이나 악성 코드 부재를 보장하지 않는다.
- 본인 다운로드는 문서의 농가 소유자와 현재 로그인 회원 ID를 비교하고 `Content-Disposition: attachment`를 사용한다.
- 운영 배포 전 악성 코드 검사와 업로드 저장소 접근 권한 분리를 추가하는 것이 좋다.
- 회원 탈퇴 트랜잭션 커밋 후 파일을 삭제하며 실패한 키는 삭제 작업 테이블에 기록해 백그라운드에서 재시도한다.

## 10. Postman 확인 순서

1. `FARM` 계정으로 로그인하고 `accessToken`을 `farmAccessToken` 환경 변수에 저장한다.
2. 농가 프로필이 없다면 `POST /api/farm-profiles`로 먼저 생성한다.
3. `Body → form-data`에서 `documents` 키로 PDF 또는 이미지를 첨부한다.
4. 제출 후 `201`, `attemptNumber=1`, 두 상태가 모두 `PENDING_REVIEW`인지 확인한다.
5. 목록과 상세 API에서 제출 회차·스냅샷·문서 ID를 확인한다.
6. 문서 ID로 파일을 다운로드하고 다른 농가 토큰으로는 거절되는지 확인한다.
7. 같은 제출 요청을 다시 보내 `409 OWNERSHIP_SUBMISSION_NOT_ALLOWED`인지 확인한다.
8. 확장자를 위조한 파일로 `400 INVALID_OWNERSHIP_DOCUMENT_CONTENT`인지 확인한다.
9. 허용되지 않은 확장자로 `415 UNSUPPORTED_OWNERSHIP_DOCUMENT_TYPE`인지 확인한다.
10. 도시농부 토큰으로 호출해 `403 ACCESS_DENIED`인지 확인한다.

반려와 재제출 전체 흐름은 통합된 중개센터 심사 API와 함께 검증한다.

## 11. 로컬 MySQL 통합 테스트

일반 `gradlew test`는 격리된 H2(MySQL 호환 모드)에서 새 테이블 매핑, 문서 cascade 저장, 비관적 잠금 쿼리와 제출 회차 UNIQUE 제약을 자동 검증하며 별도 환경변수가 필요하지 않다.

PowerShell 실행 예시:

```powershell
.\gradlew.bat test --tests "chungbuk.cityfarmerplus.farm.ownership.repository.FarmOwnershipPersistenceIntegrationTest" --no-daemon
```

테스트 DB와 JWT 설정은 `src/test/resources/application-test.properties`의 테스트 전용 값만 사용한다. 테스트 데이터는 트랜잭션으로 롤백되고 개발·운영 MySQL에는 연결하지 않는다.
