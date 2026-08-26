# CityFarmerPlus 농가 소유 증빙 제출 API

- 기준일: 2026-08-20
- 기준 소스: 현재 `backend-1` 작업 폴더의 FarmOwnership Controller, DTO, Service, Entity, Repository, 파일 Validator
- 로컬 Base URL: `http://localhost:8080`
- 구현 API: 4개

> 이 문서는 다른 문서를 보지 않아도 노션에 단독으로 복사할 수 있는 농가 소유 증빙 명세다. 현재 backend-1은 농가 본인의 제출·재제출·이력 조회·상세 조회·본인 파일 다운로드만 제공한다. 담당자의 승인·반려 및 담당자용 파일 다운로드 API는 backend-2 범위다.

---

## 1. 기능 개요

농가 계정이 토지대장, 농지원부 등 농가 소유를 증명할 파일을 제출하면 담당자가 별도로 심사하는 구조다.

- `FARM` 역할의 활성 계정만 사용할 수 있다.
- 먼저 농가 프로필이 존재해야 한다.
- 농가 프로필 상태가 `DRAFT` 또는 `REJECTED`일 때만 제출할 수 있다.
- 제출이 완료되면 새 제출 회차가 `PENDING_REVIEW`로 생성되고 농가 프로필도 `PENDING_REVIEW`가 된다.
- 제출 시점의 농가 핵심 정보를 스냅샷으로 보존한다.
- 제출 회차와 첨부 문서는 수정·삭제·덮어쓰기 하지 않는다.
- 담당자가 반려한 뒤 재제출하면 `attemptNumber`가 증가하고 과거 제출·파일 이력이 유지된다.
- 담당자의 승인·반려 HTTP API는 backend-1에 없다.

---

## 2. 인증과 권한

모든 API에 `FARM` JWT가 필요하다.

```http
Authorization: Bearer {{farmAccessToken}}
```

소유권 판정은 요청값이 아니라 JWT `sub` 회원 ID와 농가 프로필 소유자를 비교한다.

| 상황 | HTTP | 코드 |
|---|---:|---|
| JWT 누락·만료·위조 | 401 | `UNAUTHORIZED` |
| 탈퇴·정지 계정 또는 토큰 역할과 DB 역할 불일치 | 401 | `INVALID_ACCOUNT` |
| `URBAN_FARMER`, `CENTER_ADMIN` JWT로 호출 | 403 | `ACCESS_DENIED` |
| 보안 필터 통과 직후 계정 역할이 바뀐 경쟁 상황을 서비스가 다시 방어 | 403 | `FARM_ROLE_REQUIRED` |
| 다른 농가가 파일 다운로드 시도 | 403 | `OWNERSHIP_DOCUMENT_ACCESS_DENIED` |
| 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 | 404 | `USER_NOT_FOUND` |

오류 응답 형식:

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

---

## 3. API 목록

| 기능 | Method | URL | 권한 | 성공 상태 |
|---|---|---|---|---:|
| 내 제출 이력 | `GET` | `/api/farm-profiles/me/ownership-submissions` | `FARM` | 200 |
| 내 제출 상세 | `GET` | `/api/farm-profiles/me/ownership-submissions/{submissionId}` | `FARM` 소유자 | 200 |
| 소유 증빙 제출·재제출 | `POST` | `/api/farm-profiles/me/ownership-submissions` | `FARM` | 201 |
| 본인 소유 증빙 파일 다운로드 | `GET` | `/api/farm-ownership-documents/{documentId}/file` | `FARM` 소유자 | 200 |

---

## 4. 내 제출 이력

### `GET /api/farm-profiles/me/ownership-submissions`

본인 농가 프로필의 모든 제출 회차를 `attemptNumber` 내림차순으로 반환한다. 페이지네이션은 없다.

### 요청

```http
GET {{baseUrl}}/api/farm-profiles/me/ownership-submissions
Authorization: Bearer {{farmAccessToken}}
```

### 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
[
  {
    "id": 202,
    "attemptNumber": 2,
    "status": "PENDING_REVIEW",
    "farmProfileStatus": "PENDING_REVIEW",
    "submittedAt": "2026-08-11T02:00:00Z",
    "reviewerId": null,
    "reviewerName": null,
    "reviewedAt": null,
    "rejectionReason": null,
    "documents": [
      {
        "id": 302,
        "originalFilename": "토지대장.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 245781
      }
    ],
    "farmNameSnapshot": "충주 사과농원",
    "representativeNameSnapshot": "홍길동",
    "farmAddressSnapshot": "충청북도 충주시 예시로 1",
    "cityCountySnapshot": "CHUNGJU",
    "businessRegistrationNumberSnapshot": "1234567890",
    "farmAreaPyeongSnapshot": 1200
  },
  {
    "id": 201,
    "attemptNumber": 1,
    "status": "REJECTED",
    "farmProfileStatus": "PENDING_REVIEW",
    "submittedAt": "2026-08-05T01:00:00Z",
    "reviewerId": 900,
    "reviewerName": "충북 담당자",
    "reviewedAt": "2026-08-06T01:00:00Z",
    "rejectionReason": "주소가 표시된 서류를 다시 제출해 주세요.",
    "documents": [
      {
        "id": 301,
        "originalFilename": "이전_토지대장.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 210400
      }
    ],
    "farmNameSnapshot": "충주 사과농원",
    "representativeNameSnapshot": "홍길동",
    "farmAddressSnapshot": "충청북도 충주시 예시로 1",
    "cityCountySnapshot": "CHUNGJU",
    "businessRegistrationNumberSnapshot": "1234567890",
    "farmAreaPyeongSnapshot": 1200
  }
]
```

제출 이력이 없으면 `200 OK`와 빈 배열 `[]`을 반환한다. 단, 농가 프로필 자체가 없으면 `404 FARM_PROFILE_NOT_FOUND`가 아니라 현재 목록 조회 구현상 빈 배열이 반환될 수 있다. 제출 API는 프로필이 없으면 명확하게 `404 FARM_PROFILE_NOT_FOUND`를 반환한다.

> `farmProfileStatus`는 각 과거 회차 당시 상태가 아니라 조회 시점의 현재 농가 프로필 상태다. 과거 심사 결과는 각 항목의 `status`, `reviewedAt`, `rejectionReason`으로 확인한다.

---

## 5. 내 제출 상세

### `GET /api/farm-profiles/me/ownership-submissions/{submissionId}`

### 요청

```http
GET {{baseUrl}}/api/farm-profiles/me/ownership-submissions/202
Authorization: Bearer {{farmAccessToken}}
```

### Path Variable

| 이름 | 타입 | 설명 |
|---|---|---|
| `submissionId` | Long | 조회할 제출 회차 ID |

### 성공 응답

`200 OK`와 4절의 제출 응답 한 건을 반환한다.

다른 농가의 제출 ID 또는 존재하지 않는 ID는 소유 여부를 노출하지 않고 동일하게 처리한다.

```http
HTTP/1.1 404 Not Found
```

```json
{
  "code": "OWNERSHIP_SUBMISSION_NOT_FOUND",
  "message": "농가 소유 증빙 제출을 찾을 수 없습니다."
}
```

---

## 6. 소유 증빙 제출·재제출

### `POST /api/farm-profiles/me/ownership-submissions`

### 선행 조건

- 활성 `FARM` 계정이어야 한다.
- 본인 농가 프로필이 존재해야 한다.
- 프로필 상태가 `DRAFT` 또는 `REJECTED`여야 한다.
- 별도 JSON 요청 Part는 없다.

### 요청

```http
POST {{baseUrl}}/api/farm-profiles/me/ownership-submissions
Authorization: Bearer {{farmAccessToken}}
Content-Type: multipart/form-data
```

| Part 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `documents` | File Array | O | 같은 Key로 1~5개 첨부 |

### 성공 응답

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

```json
{
  "id": 202,
  "attemptNumber": 2,
  "status": "PENDING_REVIEW",
  "farmProfileStatus": "PENDING_REVIEW",
  "submittedAt": "2026-08-11T02:00:00Z",
  "reviewerId": null,
  "reviewerName": null,
  "reviewedAt": null,
  "rejectionReason": null,
  "documents": [
    {
      "id": 302,
      "originalFilename": "토지대장.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 245781
    },
    {
      "id": 303,
      "originalFilename": "농지원부.png",
      "contentType": "image/png",
      "sizeBytes": 120400
    }
  ],
  "farmNameSnapshot": "충주 사과농원",
  "representativeNameSnapshot": "홍길동",
  "farmAddressSnapshot": "충청북도 충주시 예시로 1",
  "cityCountySnapshot": "CHUNGJU",
  "businessRegistrationNumberSnapshot": "1234567890",
  "farmAreaPyeongSnapshot": 1200
}
```

보안을 위해 응답에는 내부 `storageKey`와 SHA-256을 노출하지 않는다.

### 제출 회차와 상태 전이

```text
DRAFT ── 최초 제출(attempt 1) ──> PENDING_REVIEW
PENDING_REVIEW ── backend-2 승인 ──> APPROVED
PENDING_REVIEW ── backend-2 반려 ──> REJECTED
REJECTED ── 재제출(attempt N+1) ──> PENDING_REVIEW
```

- `PENDING_REVIEW`, `APPROVED`, `INACTIVE`에서는 제출할 수 없다.
- 회차는 해당 농가 프로필의 기존 최대 회차에 1을 더한다.
- 동시 제출 방지를 위해 사용자·프로필 행을 잠그고 `(farm_profile_id, attempt_number)` DB 고유 제약을 둔다.
- 반려 후 재제출해도 과거 회차와 과거 문서 메타데이터는 유지된다.
- 각 회차에는 제출 시점의 농가명·대표자명·주소·시군·사업자등록번호·농지 면적을 스냅샷으로 저장한다.

---

## 7. 본인 소유 증빙 파일 다운로드

### `GET /api/farm-ownership-documents/{documentId}/file`

현재 로그인한 농가가 소유한 문서만 다운로드한다.

### 요청

```http
GET {{baseUrl}}/api/farm-ownership-documents/302/file
Authorization: Bearer {{farmAccessToken}}
```

### Path Variable

| 이름 | 타입 | 설명 |
|---|---|---|
| `documentId` | Long | 다운로드할 문서 ID |

### 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Length: 245781
Content-Disposition: attachment; filename*=UTF-8''...
```

응답 본문은 JSON이 아니라 파일 바이트다.

- 저장 당시 서버가 판별한 Content-Type을 사용한다.
- Content-Type 문자열이 잘못 저장된 경우 `application/octet-stream`으로 응답한다.
- UTF-8 원본 파일명을 `Content-Disposition: attachment`에 포함한다.
- 문서 ID가 존재하지 않으면 `404 OWNERSHIP_DOCUMENT_NOT_FOUND`다.
- 다른 농가의 문서이면 `403 OWNERSHIP_DOCUMENT_ACCESS_DENIED`다.
- 문서 메타데이터는 있지만 실제 저장 파일을 읽을 수 없으면 `500 OWNERSHIP_DOCUMENT_READ_ERROR`다.

---

## 8. 응답 필드

### 제출 응답

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `id` | Long | N | 제출 ID |
| `attemptNumber` | Integer | N | 농가별 제출 회차 |
| `status` | Enum | N | 해당 회차 심사 상태 |
| `farmProfileStatus` | Enum | N | 조회 시점의 현재 농가 프로필 상태 |
| `submittedAt` | Instant | N | 제출 시각 |
| `reviewerId` | Long | Y | 담당자 회원 ID |
| `reviewerName` | String | Y | 담당자 이름 |
| `reviewedAt` | Instant | Y | 승인·반려 시각 |
| `rejectionReason` | String | Y | 반려 사유 |
| `documents` | Array | N | 제출 파일 메타데이터, 요청 순서 유지 |
| `farmNameSnapshot` | String | Y | 제출 시점 농가명 |
| `representativeNameSnapshot` | String | Y | 제출 시점 대표자명 |
| `farmAddressSnapshot` | String | Y | 제출 시점 주소 |
| `cityCountySnapshot` | Enum | Y | 제출 시점 시·군 |
| `businessRegistrationNumberSnapshot` | String | Y | 제출 시점 사업자등록번호 |
| `farmAreaPyeongSnapshot` | Integer | Y | 제출 시점 농지 면적 |

스냅샷 필드가 DB상 nullable인 이유는 이전 스키마 데이터와의 호환을 위한 것이다. 현재 제출 로직은 프로필의 값을 복사한다. 선택 필드인 사업자등록번호는 정상 신규 제출에서도 `null`일 수 있다.

### 문서 응답

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `id` | Long | N | 문서 ID |
| `originalFilename` | String | N | 경로 부분을 제거한 원본 파일명 |
| `contentType` | String | N | 서버가 실제 내용으로 판별한 MIME |
| `sizeBytes` | Long | N | 실제 파일 크기 |

---

## 9. 파일 업로드 제한

| 항목 | 제한 |
|---|---|
| 파일 개수 | 1~5개 |
| 허용 확장자 | `pdf`, `jpg`, `jpeg`, `png` |
| 개별 파일 최대 크기 | 10 MiB (`10,485,760` bytes) |
| 한 요청의 파일 총합 | 30 MiB (`31,457,280` bytes) |
| 원본 파일명 | 경로 제거 후 UTF-8 최대 255 bytes, 제어문자 불가 |
| Spring 수신 한도 | 파일당 12MB, 요청 전체 31MB |

서버는 다음 세 정보를 교차 검증한다.

1. 원본 파일명의 확장자
2. 요청 Part의 선언 Content-Type
3. 파일 바이트의 실제 시그니처

| 실제 형식 | 저장 확장자 | 저장 Content-Type | 허용 선언 MIME |
|---|---|---|---|
| PDF | `pdf` | `application/pdf` | `application/pdf`, `application/x-pdf` |
| JPEG | `jpg` | `image/jpeg` | `image/jpeg`, `image/jpg`, `image/pjpeg` |
| PNG | `png` | `image/png` | `image/png` |

- 선언 Content-Type이 없거나 빈 문자열 또는 `application/octet-stream`이면 MIME 비교를 생략하고 실제 시그니처와 확장자로 판단한다.
- `.png` 이름으로 PDF 내용을 보내는 식의 위장 파일은 거절한다.
- 실제 바이트 기준 크기와 SHA-256을 계산한 뒤 저장 결과와 다시 비교한다.
- 저장 경로는 `farm-ownership/{farmProfileId}/{requestBatchUuid}/{storedFileUuid}.{extension}` 형태다.
- 내부 저장 이름은 UUID를 사용하고 원본 파일명은 메타데이터로만 보관한다.
- 파일 저장 중 일부 실패 또는 DB 트랜잭션 실패 시 이번 요청 파일을 보상 삭제한다.
- 회원이 활성 상태인 동안 별도의 기간 만료 삭제 규칙은 없다.
- 회원 탈퇴가 완료되면 실제 저장 파일을 삭제 작업에 등록한다. 제출·문서 DB 메타데이터를 이 API에서 물리 삭제하지는 않는다.
- 회원 탈퇴 시 농가 프로필이 `INACTIVE`로 바뀌므로 backend-2의 `PENDING_REVIEW` 프로필 목록에서 제외된다. 소유자 계정도 비활성화되어 물리 파일 삭제가 재시도 중인 동안에도 이 다운로드 API를 사용할 수 없다.

---

## 10. Postman multipart 설정

1. Method를 `POST`로 선택한다.
2. URL에 `{{baseUrl}}/api/farm-profiles/me/ownership-submissions`를 입력한다.
3. `Authorization > Bearer Token`에 `{{farmAccessToken}}`을 입력한다.
4. `Body > form-data`를 선택한다.
5. Key를 `documents`로 입력하고 타입을 `File`로 바꾼다.
6. PDF 또는 이미지 파일을 선택한다.
7. 여러 파일이면 동일한 `documents` Key의 File 행을 반복해서 추가한다.
8. 요청 전체 `Content-Type`은 직접 입력하지 않는다. Postman이 multipart boundary를 자동 생성해야 한다.

예시:

| Key | Type | Value |
|---|---|---|
| `documents` | File | `토지대장.pdf` |
| `documents` | File | `농지원부.png` |

이 API에는 `request` JSON Part가 없다.

---

## 11. 오류 코드

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `OWNERSHIP_DOCUMENTS_REQUIRED` | 파일이 없거나 빈 파일 포함 |
| 400 | `TOO_MANY_OWNERSHIP_DOCUMENTS` | 파일 5개 초과 |
| 400 | `INVALID_OWNERSHIP_DOCUMENT_FILENAME` | 파일명 누락, UTF-8 255 bytes 초과, 제어문자 포함 |
| 400 | `INVALID_OWNERSHIP_DOCUMENT_CONTENT` | 파일 읽기 실패, 실제 시그니처 판별 실패, 확장자·MIME·실제 내용 불일치 |
| 400 | `INVALID_REQUEST_PARAMETER` | Path ID가 숫자가 아님 |
| 401 | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| 401 | `INVALID_ACCOUNT` | 탈퇴·정지 계정 또는 토큰 역할 불일치 |
| 403 | `ACCESS_DENIED` | 농가가 아닌 JWT 역할 |
| 403 | `FARM_ROLE_REQUIRED` | 보안 필터 통과 직후 계정 역할이 바뀐 경쟁 상황을 서비스가 다시 방어 |
| 403 | `OWNERSHIP_DOCUMENT_ACCESS_DENIED` | 다른 농가의 파일 다운로드 |
| 404 | `USER_NOT_FOUND` | 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 |
| 404 | `FARM_PROFILE_NOT_FOUND` | 제출할 본인 농가 프로필이 없음 |
| 404 | `OWNERSHIP_SUBMISSION_NOT_FOUND` | 본인 소유 제출을 찾을 수 없음 |
| 404 | `OWNERSHIP_DOCUMENT_NOT_FOUND` | 문서 ID가 없음 |
| 409 | `OWNERSHIP_SUBMISSION_NOT_ALLOWED` | 프로필이 `DRAFT`, `REJECTED` 이외 상태 |
| 409 | `OWNERSHIP_SUBMISSION_DATA_CONFLICT` | 동시 제출로 회차 DB 고유 제약 충돌 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 사용자·프로필 잠금 또는 동시 갱신 충돌 |
| 413 | `OWNERSHIP_DOCUMENT_TOO_LARGE` | 개별 파일 10 MiB 초과 |
| 413 | `OWNERSHIP_DOCUMENTS_TOTAL_SIZE_TOO_LARGE` | 파일 총합 30 MiB 초과 |
| 413 | `UPLOAD_REQUEST_TOO_LARGE` | Spring 수신 한도인 파일 12MB 또는 요청 31MB 초과 |
| 415 | `UNSUPPORTED_OWNERSHIP_DOCUMENT_TYPE` | 확장자가 없거나 허용 확장자가 아님 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 요청이 multipart/form-data가 아님 |
| 500 | `OWNERSHIP_DOCUMENT_STORAGE_ERROR` | 저장 실패 또는 검증한 파일과 저장 결과 불일치 |
| 500 | `OWNERSHIP_DOCUMENT_READ_ERROR` | 문서 메타데이터는 있으나 저장 파일이 유실되었거나 읽기 실패 |

`OWNERSHIP_REVIEW_NOT_ALLOWED`, `OWNERSHIP_REJECTION_REASON_REQUIRED` 코드도 도메인에는 준비돼 있지만 backend-1에 이를 반환하는 담당자 HTTP API는 없다.

---

## 12. backend-2 담당자 기능과의 경계

현재 backend-1에는 다음 HTTP API가 없다.

- 심사 대기 농가·제출 목록 API
- 담당자용 제출 상세 API
- 담당자용 증빙 파일 다운로드 API
- 농가 소유 승인 API
- 농가 소유 반려 API

다만 병합을 위해 다음 계약은 이미 존재한다.

- 담당자 역할 `CENTER_ADMIN`
- 제출 상태 `PENDING_REVIEW`, `APPROVED`, `REJECTED`
- 농가 프로필 상태 `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `INACTIVE`
- `reviewerId`, `reviewerName`, `reviewedAt`, `rejectionReason`
- 최신 심사 대기 회차만 담당자가 처리하는 Entity·Repository 구조

backend-2의 한 심사 작업은 최신 제출 회차의 상태와 농가 프로필 상태를 함께 변경해야 한다.

- 승인: 제출 `APPROVED`, 프로필 `APPROVED`, 반려 사유 제거
- 반려: 제출 `REJECTED`, 프로필 `REJECTED`, 반려 사유 저장

심사 대기 목록은 제출 상태만으로 조회하지 않고 농가 프로필 상태가 `PENDING_REVIEW`인 건만 대상으로 해야 한다. 탈퇴 처리로 프로필이 `INACTIVE`가 된 제출의 과거 상태와 심사자 이력은 감사 이력으로 보존하되 신규 심사에서는 제외한다.

따라서 backend-1만 실행하면 `PENDING_REVIEW`까지 만들 수 있지만 정상 HTTP 흐름만으로 승인·반려하거나 재제출 조건인 `REJECTED`를 만들 수 없다.

---

## 13. Postman 권장 확인 순서

1. `FARM` 계정으로 로그인하고 `farmAccessToken`을 저장한다.
2. 농가 프로필이 없으면 `POST /api/farm-profiles`로 먼저 생성한다.
3. `Body > form-data`에서 `documents` File을 1~5개 첨부해 제출한다.
4. 응답이 `201`, `attemptNumber=1`, 제출·프로필 상태가 모두 `PENDING_REVIEW`인지 확인한다.
5. 목록과 상세 API에서 스냅샷과 파일 순서를 확인한다.
6. 문서 ID로 다운로드하고 `Content-Type`, `Content-Length`, `Content-Disposition`을 확인한다.
7. 동일 상태에서 다시 제출해 `409 OWNERSHIP_SUBMISSION_NOT_ALLOWED`를 확인한다.
8. 확장자만 바꾼 위장 파일로 `400 INVALID_OWNERSHIP_DOCUMENT_CONTENT`를 확인한다.
9. 허용하지 않는 확장자로 `415 UNSUPPORTED_OWNERSHIP_DOCUMENT_TYPE`을 확인한다.
10. 다른 농가 토큰으로 문서 다운로드를 시도해 `403 OWNERSHIP_DOCUMENT_ACCESS_DENIED`를 확인한다.
11. backend-2 심사 기능 병합 후 반려하고 재제출 회차가 2로 증가하며 1회차가 유지되는지 확인한다.
