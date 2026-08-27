# CityFarmerPlus 교육 과정·이수증 인증 API

- 기준일: 2026-08-28
- 기준 소스: 통합 코드의 교육 사용자·관리자·진도 연동 Controller, DTO, Service, Entity, Repository, 서명·파일 Validator
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 구현 API: 7개

> 이 문서는 도시농부용 교육 API를 노션에 단독으로 복사할 수 있게 정리한다. 중개센터의 과정 관리와 이수증 심사 API는 통합돼 있으며 요청·응답 상세는 `ADMIN_EDUCATION_COURSE_API_SPEC.md`, `ADMIN_EDUCATION_SUBMISSION_API_SPEC.md`를 따른다.
> 교육기관 HMAC 서명과 수강률 응답의 독립 명세는 `EDUCATION_PROGRESS_API_SPEC.md`를 따른다.

---

## 1. 기능 개요

교육 인증은 모집 공고 지원 자격을 결정한다.

- 활성 교육 과정만 사용자에게 노출한다.
- 교육 과정 중 `mandatory=true`인 활성 필수 과정을 모두 승인받아야 공고 지원이 가능하다.
- 활성 필수 과정이 0개이면 교육 인증 완료로 처리하지 않는다.
- 선택 과정(`mandatory=false`)의 승인 여부는 공고 지원 자격에 영향을 주지 않는다.
- 각 과정의 최신 제출 상태를 기준으로 진행 상태를 계산한다.
- 한 과정은 미제출 상태이거나 최신 제출이 `REJECTED`일 때만 새 이수증을 제출할 수 있다.
- 제출된 회차와 파일은 덮어쓰지 않는다. 반려 후 재제출하면 새 회차가 추가되고 과거 제출은 유지된다.
- 제출 직후 상태는 `PENDING_REVIEW`다.
- 승인·반려 결과는 통합된 중개센터 담당자 API가 같은 도메인 모델에 저장한다.
- 교육기관이 서명한 진도 이벤트를 보내면 과정별 수강 시간과 수강률을 갱신한다.
- 실시간 수강 진도와 이수증 심사 결과는 별개다. 수강률 100%만으로 공고 지원 자격을 자동 부여하지 않는다.

공고 지원 API에서는 다음 조건을 다시 검사한다.

```text
활성 필수 과정 수 > 0
AND
활성 필수 과정마다 APPROVED 제출이 존재
AND
각 인정 시간이 max(8, 과정 requiredHours) 이상
```

조건을 충족하지 못하면 공고 지원 API가 `403 EDUCATION_CERTIFICATION_REQUIRED`를 반환한다.

---

## 2. 인증과 권한

### 공개 API

다음 API는 JWT 없이 호출할 수 있다.

```http
GET /api/education/courses
```

### 도시농부 전용 API

나머지 교육 인증 API는 `URBAN_FARMER` JWT가 필요하다.

```http
Authorization: Bearer {{urbanFarmerAccessToken}}
```

| 상황 | HTTP | 코드 |
|---|---:|---|
| JWT 누락·만료·위조 | 401 | `UNAUTHORIZED` |
| JWT의 회원 ID가 숫자가 아님, 탈퇴·정지 계정, 토큰 역할과 DB 역할 불일치 | 401 | `INVALID_ACCOUNT` |
| `FARM`, `CENTER_ADMIN` 역할로 도시농부 API 호출 | 403 | `ACCESS_DENIED` |
| 보안 필터 통과 직후 계정 역할이 바뀐 경쟁 상황을 서비스가 다시 방어 | 403 | `URBAN_FARMER_ROLE_REQUIRED` |
| 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 | 404 | `USER_NOT_FOUND` |

### 교육기관 서버 연동 API

`POST /api/integrations/education/progress-events`는 사용자 JWT 대신
`X-Education-Event-Timestamp`와 `X-Education-Signature` HMAC 헤더를 검증한다.
공유 비밀키는 32바이트 이상 난수로 생성해 서버의 `EDUCATION_PROGRESS_WEBHOOK_SECRET`에만 저장하며 브라우저나
모바일 앱에 포함하면 안 된다. 비밀키가 비어 있으면 이 API는 `503`으로 비활성화된다.

오류 본문은 공통적으로 다음 형식이다.

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
| 활성 교육 과정 목록 | `GET` | `/api/education/courses` | 공개 | 200 |
| 내 교육 인증 진행 상태 | `GET` | `/api/urban-farmers/me/education-certification` | `URBAN_FARMER` | 200 |
| 내 이수증 제출 이력 | `GET` | `/api/urban-farmers/me/education-certification/submissions` | `URBAN_FARMER` | 200 |
| 이수증 제출·재제출 | `POST` | `/api/urban-farmers/me/education-certification/submissions` | `URBAN_FARMER` | 201 |
| 내 이수증 제출 상세 | `GET` | `/api/urban-farmers/me/education-certification/submissions/{submissionId}` | `URBAN_FARMER` | 200 |
| 내 이수증 파일 다운로드 | `GET` | `/api/urban-farmers/me/education-certification/submissions/{submissionId}/documents/{documentId}` | `URBAN_FARMER` 소유자 | 200 |
| 교육기관 진도 이벤트 | `POST` | `/api/integrations/education/progress-events` | HMAC 서명 | 200 |

---

## 4. 활성 교육 과정 목록

### `GET /api/education/courses`

활성 상태인 교육 과정만 `displayOrder` 오름차순, 같은 순서에서는 `title` 오름차순으로 반환한다.

### 요청

```http
GET {{baseUrl}}/api/education/courses
```

Query Parameter와 Request Body는 없다.

### 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
[
  {
    "id": 1,
    "title": "농업안전 기초",
    "description": "농작업 안전수칙과 보호구 사용법을 학습합니다.",
    "requiredHours": 8,
    "externalApplicationUrl": "https://example.org/courses/1",
    "mandatory": true,
    "active": true,
    "displayOrder": 1,
    "version": 0,
    "createdAt": "2026-08-01T00:00:00Z",
    "updatedAt": "2026-08-01T00:00:00Z"
  }
]
```

활성 과정이 없으면 `200 OK`와 빈 배열 `[]`을 반환한다.

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `id` | Long | N | 과정 ID |
| `title` | String | N | 과정명 |
| `description` | String | N | 과정 설명 |
| `requiredHours` | Integer | N | 과정에서 요구하는 최소 시간 |
| `externalApplicationUrl` | String | Y | 외부 교육 신청 페이지 주소 |
| `mandatory` | Boolean | N | 공고 지원 자격에 필요한 필수 과정 여부 |
| `active` | Boolean | N | 이 API에서는 항상 `true` |
| `displayOrder` | Integer | N | 화면 표시 순서 |
| `version` | Long | N | 낙관적 잠금 버전 |
| `createdAt` | Instant | N | 생성 시각 |
| `updatedAt` | Instant | N | 수정 시각 |

---

## 5. 내 교육 인증 진행 상태

### `GET /api/urban-farmers/me/education-certification`

현재 활성 과정과 각 과정의 최신 제출을 조합해 진행 상태를 실시간으로 계산한다. 제출 이력이 없어도 오류가 아니라 `NOT_SUBMITTED` 응답을 반환한다.

### 요청

```http
GET {{baseUrl}}/api/urban-farmers/me/education-certification
Authorization: Bearer {{urbanFarmerAccessToken}}
```

### 성공 응답

```json
{
  "id": 10,
  "urbanFarmerId": 25,
  "status": "PARTIALLY_APPROVED",
  "approvedSubmissionId": null,
  "recognizedHours": 8,
  "approvedAt": null,
  "version": 2,
  "createdAt": "2026-08-05T02:00:00Z",
  "updatedAt": "2026-08-10T03:00:00Z",
  "eligibleToApply": false,
  "requiredCourseCount": 2,
  "approvedRequiredCourseCount": 1,
  "courses": [
    {
      "courseId": 1,
      "title": "농업안전 기초",
      "description": "농작업 안전수칙 교육",
      "requiredHours": 8,
      "externalApplicationUrl": "https://example.org/courses/1",
      "mandatory": true,
      "latestSubmissionStatus": "APPROVED",
      "latestSubmissionId": 101,
      "attemptNumber": 1,
      "recognizedHours": 8,
      "rejectionReason": null,
      "submittedAt": "2026-08-05T02:00:00Z",
      "progressStatus": "IN_PROGRESS",
      "totalMinutes": 480,
      "completedMinutes": 240,
      "remainingMinutes": 240,
      "progressPercentage": 50,
      "startedAt": "2026-08-28T00:00:00Z",
      "completedAt": null,
      "progressUpdatedAt": "2026-08-28T00:30:00Z",
      "lastSyncedAt": "2026-08-28T00:30:01Z"
    },
    {
      "courseId": 2,
      "title": "도시농업 이해와 기초",
      "description": "도시농업 기본 교육",
      "requiredHours": 8,
      "externalApplicationUrl": null,
      "mandatory": true,
      "latestSubmissionStatus": null,
      "latestSubmissionId": null,
      "attemptNumber": null,
      "recognizedHours": null,
      "rejectionReason": null,
      "submittedAt": null,
      "progressStatus": "NOT_STARTED",
      "totalMinutes": 480,
      "completedMinutes": 0,
      "remainingMinutes": 480,
      "progressPercentage": 0,
      "startedAt": null,
      "completedAt": null,
      "progressUpdatedAt": null,
      "lastSyncedAt": null
    }
  ]
}
```

### 최상위 필드

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `id` | Long | Y | 교육 인증 ID. 제출 이력이 없으면 `null` |
| `urbanFarmerId` | Long | N | JWT 본인의 회원 ID |
| `status` | Enum | N | 전체 교육 진행 상태 |
| `approvedSubmissionId` | Long | Y | 모든 필수 과정 승인 시 가장 최근에 심사된 승인 제출 ID |
| `recognizedHours` | Integer | Y | 승인된 필수 과정 인정 시간의 합. 합계가 0이면 `null` |
| `approvedAt` | Instant | Y | 자격 완성 시 가장 최근 승인 심사 시각 |
| `version` | Long | N | 인증 데이터가 없으면 0 |
| `createdAt` | Instant | Y | 인증 데이터 생성 시각 |
| `updatedAt` | Instant | Y | 인증 데이터 수정 시각 |
| `eligibleToApply` | Boolean | N | 모집 공고 지원 가능 여부 |
| `requiredCourseCount` | Long | N | 현재 활성 필수 과정 수 |
| `approvedRequiredCourseCount` | Long | N | 기준을 충족해 승인된 활성 필수 과정 수 |
| `courses` | Array | N | 현재 활성 과정별 최신 제출 상태 |

### 전체 진행 상태 `status`

| 상태 | 계산 기준 |
|---|---|
| `NOT_SUBMITTED` | 필수 과정의 최신 제출이 하나도 없음 |
| `PENDING_REVIEW` | 필수 과정 최신 제출 중 하나 이상이 심사 대기 중 |
| `REJECTED` | 심사 대기 건은 없고, 필수 과정 최신 제출 중 하나 이상이 반려됨 |
| `PARTIALLY_APPROVED` | 일부 필수 과정은 승인됐지만 전체 자격은 미완성 |
| `APPROVED` | 활성 필수 과정을 모두 기준 시간 이상 승인받음 |

판정 우선순위는 `APPROVED → PENDING_REVIEW → REJECTED → PARTIALLY_APPROVED → NOT_SUBMITTED`다.

### 과정별 실시간 수강 진도 필드

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `progressStatus` | Enum | N | `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` |
| `totalMinutes` | Integer | N | 교육기관이 보낸 전체 과정 시간. 연동 전에는 `requiredHours * 60` |
| `completedMinutes` | Integer | N | 현재까지 수강한 시간 |
| `remainingMinutes` | Integer | N | `max(0, totalMinutes - completedMinutes)` |
| `progressPercentage` | Integer | N | 완료 전 100%가 표시되지 않도록 소수점을 버린 0~100 정수 수강률 |
| `startedAt` | Instant | Y | 최초 1분 이상 수강한 이벤트 발생 시각 |
| `completedAt` | Instant | Y | 최초 100% 완료 이벤트 발생 시각 |
| `progressUpdatedAt` | Instant | Y | 현재 값으로 반영된 교육기관 이벤트 발생 시각 |
| `lastSyncedAt` | Instant | Y | 서버가 마지막 이벤트를 수신한 시각 |

프론트 화면은 이 조회 API를 화면 진입 시 호출하고, 수강 화면에서 돌아온 뒤 다시
호출한다. 교육기관이 웹훅을 지원해 수강 중 갱신을 보여줘야 한다면 5~10초 간격으로
재조회할 수 있다. 서버는 이벤트 수신 즉시 DB를 갱신하지만 현재 계약은 WebSocket이나
SSE 푸시를 제공하지 않는다.

### 교육기관 진도 이벤트

```http
POST {{baseUrl}}/api/integrations/education/progress-events
Content-Type: application/json
X-Education-Event-Timestamp: 1787875200
X-Education-Signature: sha256={{hmacHex}}
```

```json
{
  "provider": "CHUNGBUK_LMS",
  "eventId": "evt-20260828-0001",
  "externalEnrollmentId": "enrollment-21-1",
  "urbanFarmerId": 21,
  "courseId": 1,
  "totalMinutes": 480,
  "completedMinutes": 240,
  "occurredAt": "2026-08-28T00:30:00Z"
}
```

서명 대상 바이트는 `timestamp + "." + rawJsonBody`이고 알고리즘은
`HMAC-SHA256`이다. JSON을 다시 직렬화하지 말고 HTTP로 보내는 원본 바이트를 그대로
서명해야 한다. 기본 허용 시각 오차는 5분이다.

- `(provider, eventId)`는 멱등 키다. 같은 원문 재전송은 현재 등록 상태를 `200`으로 반환한다.
- 같은 멱등 키에 다른 본문을 보내면 `409 EDUCATION_PROGRESS_EVENT_CONFLICT`다.
- 과거 시각의 새 이벤트는 감사 이력에는 저장하지만 현재 수강 시간을 덮어쓰지 않는다.
- 더 최신 이벤트가 수강 시간을 감소시키거나 완료 과정을 다시 미완료로 만들면 `409 EDUCATION_PROGRESS_REGRESSION`이다.
- 도시농부 계정과 교육 과정은 모두 활성 상태여야 한다.
- `completedMinutes`는 `0 <= completedMinutes <= totalMinutes`여야 한다.
- `totalMinutes`는 서버에 설정된 과정의 `requiredHours * 60`보다 짧을 수 없다.
- 이 API 성공 응답은 등록 ID와 현재 수강 상태·시간·수강률을 담은 `EducationEnrollmentResponse`다.
- 실제 자동 갱신을 시작하려면 교육기관 LMS가 이 계약으로 이벤트를 전송하도록 별도 설정해야 한다.

---

## 6. 내 이수증 제출 이력

### `GET /api/urban-farmers/me/education-certification/submissions`

모든 과정의 본인 제출을 `attemptNumber` 내림차순으로 반환한다. 페이지네이션은 없다.

### 요청

```http
GET {{baseUrl}}/api/urban-farmers/me/education-certification/submissions
Authorization: Bearer {{urbanFarmerAccessToken}}
```

### 성공 응답

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "id": 102,
    "certificationId": 10,
    "urbanFarmerId": 25,
    "urbanFarmerName": "김도시",
    "courseId": 1,
    "courseTitle": "농업안전 기초",
    "attemptNumber": 2,
    "completionDate": "2026-08-09",
    "completionHours": 8,
    "status": "PENDING_REVIEW",
    "reviewedByUserId": null,
    "reviewedAt": null,
    "recognizedHours": null,
    "rejectionReason": null,
    "documents": [
      {
        "id": 1002,
        "displayOrder": 0,
        "originalFilename": "교육이수증.pdf",
        "contentType": "application/pdf",
        "sizeBytes": 245781,
        "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "createdAt": "2026-08-10T03:00:00Z"
      }
    ],
    "version": 0,
    "submittedAt": "2026-08-10T03:00:00Z"
  }
]
```

제출 이력이 없으면 `200 OK`와 `[]`을 반환한다.

> `attemptNumber`는 과정별 번호가 아니라 해당 회원의 교육 인증 전체 제출에 대해 증가한다. 예를 들어 1번 과정 제출 후 2번 과정을 제출하면 서로 다른 과정이어도 회차가 1, 2로 기록될 수 있다.

---

## 7. 이수증 제출·재제출

### `POST /api/urban-farmers/me/education-certification/submissions`

### 요청 형식

```http
POST {{baseUrl}}/api/urban-farmers/me/education-certification/submissions
Authorization: Bearer {{urbanFarmerAccessToken}}
Content-Type: multipart/form-data
```

| Part 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `request` | JSON Part | O | 과정·이수일·이수 시간 |
| `documents` | File Array | O | 같은 Key로 1~5개 첨부 |

`request` JSON:

```json
{
  "courseId": 1,
  "completionDate": "2026-08-09",
  "completionHours": 8
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `courseId` | Long | O | 존재하고 `active=true`인 과정 ID |
| `completionDate` | LocalDate | O | `yyyy-MM-dd`, 오늘 또는 과거 날짜 |
| `completionHours` | Integer | O | 8~1000, 동시에 `max(8, course.requiredHours)` 이상 |

### 성공 응답

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

```json
{
  "id": 102,
  "certificationId": 10,
  "urbanFarmerId": 25,
  "urbanFarmerName": "김도시",
  "courseId": 1,
  "courseTitle": "농업안전 기초",
  "attemptNumber": 2,
  "completionDate": "2026-08-09",
  "completionHours": 8,
  "status": "PENDING_REVIEW",
  "reviewedByUserId": null,
  "reviewedAt": null,
  "recognizedHours": null,
  "rejectionReason": null,
  "documents": [
    {
      "id": 1002,
      "displayOrder": 0,
      "originalFilename": "교육이수증.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 245781,
      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "createdAt": "2026-08-10T03:00:00Z"
    }
  ],
  "version": 0,
  "submittedAt": "2026-08-10T03:00:00Z"
}
```

과정명은 제출 시점의 값이 `courseTitle` 스냅샷으로 보존된다.

### 제출 가능 상태

```text
과정 미제출 ──제출──> PENDING_REVIEW
PENDING_REVIEW ──CENTER_ADMIN 승인──> APPROVED
PENDING_REVIEW ──CENTER_ADMIN 반려──> REJECTED
REJECTED ──재제출──> 새 PENDING_REVIEW 회차
```

- 같은 과정의 최신 제출이 `PENDING_REVIEW` 또는 `APPROVED`이면 추가 제출할 수 없다.
- `REJECTED`일 때만 새 회차로 재제출한다.
- 과거 제출과 과거 파일 메타데이터는 수정하거나 덮어쓰지 않는다.

---

## 8. 내 이수증 제출 상세

### `GET /api/urban-farmers/me/education-certification/submissions/{submissionId}`

### 요청

```http
GET {{baseUrl}}/api/urban-farmers/me/education-certification/submissions/102
Authorization: Bearer {{urbanFarmerAccessToken}}
```

### Path Variable

| 이름 | 타입 | 설명 |
|---|---|---|
| `submissionId` | Long | 조회할 제출 ID |

### 성공 응답

`200 OK`와 7절의 `EducationSubmissionResponse` 한 건을 반환한다.

다른 회원의 제출 ID도 본인 소유 조건에 맞지 않으므로 다음과 같이 처리한다.

```http
HTTP/1.1 404 Not Found
```

```json
{
  "code": "EDUCATION_SUBMISSION_NOT_FOUND",
  "message": "교육 이수증 제출 내역을 찾을 수 없습니다."
}
```

---

## 9. 내 이수증 파일 다운로드

### `GET /api/urban-farmers/me/education-certification/submissions/{submissionId}/documents/{documentId}`

제출 ID, 문서 ID, JWT 회원 ID가 모두 일치하는 파일만 내려받는다.

### 요청

```http
GET {{baseUrl}}/api/urban-farmers/me/education-certification/submissions/102/documents/1002
Authorization: Bearer {{urbanFarmerAccessToken}}
```

### 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Length: 245781
Content-Disposition: attachment; filename*=UTF-8''...
```

응답 본문은 JSON이 아니라 파일 바이트다. 저장 당시 서버가 판별한 MIME 타입과 원본 파일명을 사용한다.

| 상황 | HTTP | 코드 |
|---|---:|---|
| 제출·문서·본인 소유 관계가 일치하지 않음 | 404 | `EDUCATION_DOCUMENT_NOT_FOUND` |
| 문서 메타데이터는 있으나 저장 파일이 유실되었거나 읽을 수 없음 | 410 | `EDUCATION_DOCUMENT_FILE_UNAVAILABLE` |

---

## 10. 파일 업로드 제한

| 항목 | 제한 |
|---|---|
| 파일 개수 | 1~5개 |
| 허용 확장자 | `pdf`, `jpg`, `jpeg`, `png` |
| 개별 파일 최대 크기 | 10 MiB (`10,485,760` bytes) |
| 한 요청의 파일 총합 | 30 MiB (`31,457,280` bytes) |
| 원본 파일명 | 경로를 제거한 이름, UTF-8 최대 255 bytes, 제어문자 불가 |
| Spring 수신 한도 | 파일당 12MB, 요청 전체 31MB |

서버는 확장자만 보지 않고 실제 파일 시그니처를 함께 검사한다.

| 판별 형식 | 저장 확장자 | 저장 Content-Type | 허용 선언 MIME |
|---|---|---|---|
| PDF | `pdf` | `application/pdf` | `application/pdf`, `application/x-pdf` |
| JPEG | `jpg` | `image/jpeg` | `image/jpeg`, `image/jpg`, `image/pjpeg` |
| PNG | `png` | `image/png` | `image/png` |

- 선언 MIME이 없거나 빈 문자열 또는 `application/octet-stream`이면 실제 시그니처와 확장자로 판별한다.
- 선언 MIME이 있다면 실제 형식과 일치해야 한다.
- 서버는 실제 바이트 기준 크기와 SHA-256을 다시 계산한다.
- 파일은 `education-certification/{userId}/{batchUuid}/...` 아래 UUID 기반 이름으로 저장한다.
- DB 저장 실패 시 이번 요청에서 저장한 파일을 보상 삭제한다.
- 회원이 활성 상태인 동안 별도의 기간 만료 삭제 규칙은 없다.
- 회원 탈퇴가 완료되면 실제 저장 파일을 삭제 작업에 등록한다. 제출·문서 DB 메타데이터를 이 API에서 물리 삭제하지는 않는다.
- 제출 상태에 별도 탈퇴 enum을 추가하지 않는다. 담당자 심사 목록·건수·심사 잠금 Repository 쿼리가 회원 상태 `ACTIVE`를 함께 검사하므로 탈퇴·정지 계정의 `PENDING_REVIEW` 제출은 심사 대상에서 제외된다.

---

## 11. Postman multipart 설정

1. Method를 `POST`로 선택한다.
2. URL에 `{{baseUrl}}/api/urban-farmers/me/education-certification/submissions`를 입력한다.
3. `Authorization > Bearer Token`에 `{{urbanFarmerAccessToken}}`을 입력한다.
4. `Body > form-data`를 선택한다.
5. `request` Key를 `Text`로 추가하고 JSON 문자열을 입력한다.
6. `request` 행의 Part Content-Type을 `application/json`으로 설정한다. Postman 버전에 따라 행 우측 메뉴의 `Content Type`에서 지정한다.
7. `documents` Key를 `File`로 추가하고 파일을 선택한다.
8. 여러 파일이면 같은 `documents` Key의 File 행을 반복해서 추가한다.
9. 요청 전체 `Content-Type` 헤더는 직접 작성하지 않는다. Postman이 boundary를 포함한 값을 자동 생성해야 한다.

Postman form-data 예시:

| Key | Type | Value | Part Content-Type |
|---|---|---|---|
| `request` | Text | `{"courseId":1,"completionDate":"2026-08-09","completionHours":8}` | `application/json` |
| `documents` | File | `교육이수증.pdf` | 파일에 따라 자동 |
| `documents` | File | `교육확인서.png` | 파일에 따라 자동 |

`request` Part를 단순 `text/plain`으로 전송하면 JSON DTO 변환이 실패해 `415 UNSUPPORTED_MEDIA_TYPE`이 발생할 수 있다.

---

## 12. 교육 도메인 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | `courseId`·이수일·시간 누락, 미래 이수일, 시간 범위 8~1000 위반 |
| 400 | `INVALID_REQUEST` | 잘못된 JSON 또는 날짜 형식 |
| 400 | `MISSING_MULTIPART_PART` | 필수 `request` Part 누락 |
| 400 | `EDUCATION_DOCUMENTS_REQUIRED` | 파일이 없거나 빈 파일 포함 |
| 400 | `TOO_MANY_EDUCATION_DOCUMENTS` | 파일 5개 초과 |
| 400 | `INVALID_EDUCATION_FILENAME` | 파일명이 없거나 255 UTF-8 bytes 초과, 제어문자 포함 |
| 400 | `INVALID_EDUCATION_DOCUMENT` | 파일 읽기 실패, 시그니처 판별 실패, 확장자·MIME·실제 내용 불일치 |
| 400 | `INSUFFICIENT_EDUCATION_HOURS` | 제출 시간이 `max(8, requiredHours)`보다 작음 |
| 400 | `INVALID_EDUCATION_PROGRESS_EVENT` | 진도 이벤트 JSON을 해석할 수 없음 |
| 400 | `INVALID_EDUCATION_PROGRESS` | 수강 시간이 전체 시간을 초과함 |
| 400 | `INVALID_EDUCATION_PROGRESS_TIME` | 이벤트 발생 시각이 현재보다 5분 넘게 미래임 |
| 400 | `INSUFFICIENT_EDUCATION_PROGRESS_DURATION` | 전체 교육 시간이 과정 필수 시간보다 짧음 |
| 401 | `INVALID_EDUCATION_PROGRESS_SIGNATURE` | HMAC 서명·서명 형식·서명 시각이 유효하지 않음 |
| 403 | `EDUCATION_CERTIFICATION_REQUIRED` | 공고 지원 시 활성 필수 과정을 모두 승인받지 못함 |
| 404 | `ACTIVE_URBAN_FARMER_NOT_FOUND` | 진도 이벤트 대상이 활성 도시농부가 아님 |
| 404 | `ACTIVE_EDUCATION_COURSE_NOT_FOUND` | 과정이 없거나 비활성 상태 |
| 404 | `EDUCATION_SUBMISSION_NOT_FOUND` | 본인 소유 제출을 찾을 수 없음 |
| 404 | `EDUCATION_DOCUMENT_NOT_FOUND` | 제출·문서·본인 소유 관계가 일치하지 않음 |
| 409 | `EDUCATION_SUBMISSION_NOT_ALLOWED` | 같은 과정 최신 제출이 심사 대기 또는 승인 상태 |
| 409 | `EDUCATION_SUBMISSION_DATA_CONFLICT` | 동시 제출로 회차 DB 제약 충돌 |
| 409 | `EDUCATION_PROGRESS_EVENT_CONFLICT` | 같은 진도 이벤트 ID에 다른 본문을 사용함 |
| 409 | `EDUCATION_ENROLLMENT_CONFLICT` | 회원·과정·외부 등록 ID 연결이 기존 값과 충돌함 |
| 409 | `EDUCATION_PROGRESS_REGRESSION` | 더 최신 이벤트가 진도를 감소시키거나 완료를 취소함 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 잠금 경합 또는 동시 갱신 충돌 |
| 410 | `EDUCATION_DOCUMENT_FILE_UNAVAILABLE` | 실제 파일이 삭제됐거나 읽을 수 없음 |
| 413 | `EDUCATION_DOCUMENT_TOO_LARGE` | 개별 10 MiB 또는 총합 30 MiB 초과 |
| 413 | `UPLOAD_REQUEST_TOO_LARGE` | Spring 수신 한도인 파일 12MB 또는 요청 31MB 초과 |
| 415 | `UNSUPPORTED_EDUCATION_DOCUMENT_TYPE` | 허용하지 않는 확장자 또는 확장자 없음 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | multipart가 아니거나 `request` Part 형식이 지원되지 않음 |
| 500 | `EDUCATION_DOCUMENT_STORAGE_FAILED` | 검증 후 파일 저장 실패 또는 저장 무결성 불일치 |
| 503 | `EDUCATION_PROGRESS_WEBHOOK_DISABLED` | 서버에 웹훅 비밀키가 설정되지 않음 |

---

## 13. 중개센터 담당자 기능 연동

현재 통합 코드에는 다음 HTTP API가 구현돼 있다.

- 교육 과정 생성·수정·비활성화 API
- 심사 대기 이수증 목록·상세 API
- 이수증 승인 API
- 이수증 반려 API
- 담당자용 이수증 다운로드 API

사용자와 담당자 API는 다음 데이터 계약을 공유한다.

- 담당자 역할 `CENTER_ADMIN`
- 제출 상태 `PENDING_REVIEW`, `APPROVED`, `REJECTED`
- `reviewedByUserId`, `reviewedAt`, `recognizedHours`, `rejectionReason`
- 전체 인증 상태 `NOT_SUBMITTED`, `PENDING_REVIEW`, `PARTIALLY_APPROVED`, `APPROVED`, `REJECTED`

담당자 서비스는 심사 목록과 건수에 `EducationCertificateSubmissionRepository.findAllByStatus`와 `countByStatus`, 심사 변경용 잠금 조회에 `findByIdForUpdate`를 사용한다. 이 공용 Repository 계약은 모두 제출 회원의 `accountStatus=ACTIVE`를 조건으로 포함한다. 따라서 탈퇴 직전 화면에 보였던 건도 심사 시 다시 잠금 조회하며, 비활성 계정이면 처리하지 않는다.

도시농부가 이수증을 제출해 `PENDING_REVIEW`로 만들고, `CENTER_ADMIN`이 담당자 API로 `APPROVED` 또는 `REJECTED`로 심사한다.

---

## 14. Postman 권장 확인 순서

1. `GET /api/education/courses`로 활성 과정 ID와 최소 시간을 확인한다.
2. 도시농부 계정으로 로그인하고 토큰을 저장한다.
3. `GET /api/urban-farmers/me/education-certification`에서 최초 상태를 확인한다.
4. multipart 요청으로 필수 과정의 이수증을 제출한다.
5. 응답이 `201`, 제출 상태가 `PENDING_REVIEW`인지 확인한다.
6. 이력과 상세 API에서 회차·과정 스냅샷·파일 순서를 확인한다.
7. 파일 다운로드 응답의 `Content-Type`, `Content-Length`, `Content-Disposition`을 확인한다.
8. 같은 과정에 즉시 재제출해 `409 EDUCATION_SUBMISSION_NOT_ALLOWED`를 확인한다.
9. 중개센터 심사 API로 반려 처리하고 재제출 회차가 증가하는지 확인한다.
10. 모든 활성 필수 과정 승인 후 `eligibleToApply=true`인지 확인한다.
