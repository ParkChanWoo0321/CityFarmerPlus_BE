# CityFarmerPlus 관리자 모집 공고 승인/반려 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-25
- 구현 기준 브랜치: `backend-2`
- 적용 범위: 승인 대기 모집 공고 목록 조회, 승인, 반려 (수정·마감·취소·이력 조회는 범위 밖)

## 1. 공통 사항

### 1.1 기본 URL

```text
http://localhost:8080
```

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{adminAccessToken}}
```

### 1.3 인증 및 권한 규칙

- `/api/admin/**`는 `SecurityConfig`에서 `hasRole("CENTER_ADMIN")`으로 URL 패턴 단위로 이미 보호된다. 이 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('CENTER_ADMIN')")`을 추가로 걸어 이중으로 확인한다.
- 서비스 레이어에서 JWT와 무관하게 **DB에 저장된 실제 역할을 다시 조회**한다(`AdminJobPostingService.requireCenterAdmin`).
- 심사자 ID는 요청 body로 받지 않는다. 항상 JWT의 `sub`(`AuthenticatedUser.id(authentication)`)에서 추출한다.
- 이 API들은 [`jobposting`](../src/main/java/chungbuk/cityfarmerplus/jobposting) 패키지의 `JobPosting`/`JobPostingReview` 엔티티와 응답 DTO(`JobPostingResponse`, `JobPostingReviewResponse`), 조립기(`JobPostingResponseAssembler`)를 그대로 재사용한다.
- **승인·반려는 두 엔티티를 함께 다룬다.** `JobPosting`은 상태만 전이하고(`PENDING_REVIEW → OPEN` 또는 `PENDING_REVIEW → DRAFT`), **누가·왜 심사했는지는 `JobPosting`이 아니라 별도의 append-only 감사 로그 엔티티 `JobPostingReview`에 기록된다.** 이 API는 `posting.approve()`/`reject()`와 `JobPostingReview.record(...)` 저장을 같은 트랜잭션 안에서 함께 처리한다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`JobPostingException`은 `common.exception.DomainException`을 상속하므로 전역 `GlobalExceptionHandler`가 컨트롤러 종류와 무관하게 처리한다(농가 소유 증빙 심사 때와 달리, 이 도메인은 컨트롤러 범위를 지정하는 별도 어드바이스가 없어 새 컨트롤러를 추가 등록할 필요가 없었다). 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다. 자세한 내용은 6장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 승인 대기 공고 목록 조회 | `GET` | `/api/admin/job-postings` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 모집 공고 승인 | `POST` | `/api/admin/job-postings/{postingId}/approve` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 모집 공고 반려 | `POST` | `/api/admin/job-postings/{postingId}/reject` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

## 3. 승인 대기 공고 목록 조회

**`PENDING_REVIEW`(심사 대기) 상태의 공고만 반환한다.** 상태 필터는 지원하지 않는다(항상 `PENDING_REVIEW`로 고정).

### 3.1 요청

```http
GET /api/admin/job-postings?page=0&size=20
Authorization: Bearer {{adminAccessToken}}
```

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | int | X | `0` | Spring Data 기본 페이지네이션(0부터 시작) |
| `size` | int | X | `20` | Spring Data 기본값 |
| `sort` | String | X | 없음 | 예: `sort=createdAt,asc` |

### 3.2 성공 응답

```http
HTTP/1.1 200 OK
```

컨트롤러가 `Page<JobPostingResponse>`를 그대로 반환하므로, 응답 본문은 `content` 배열과 페이지 메타데이터를 함께 담은 형식이다.

```json
{
  "content": [
    {
      "id": 12,
      "farmProfileId": 7,
      "farmName": "충주 사과농원",
      "cityCounty": "CHUNGJU",
      "farmAddress": "충청북도 충주시 예시로 1",
      "contactNumber": "01012345678",
      "crop": "사과",
      "workType": "수확",
      "workDate": "2026-09-01",
      "startTime": "09:00:00",
      "endTime": "17:00:00",
      "capacity": 5,
      "meetingPlace": "충주시 사과농원 정문",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "supplies": "장갑, 모자",
      "precautions": "미끄럼 주의",
      "farmMessage": "초보자도 환영합니다.",
      "applicantPreference": "체력 좋으신 분",
      "title": "사과 수확 도우미 모집",
      "description": "사과 수확 작업을 도와주실 분을 모집합니다.",
      "beginnerGuide": "장갑 착용 후 조심히 따주세요.",
      "status": "PENDING_REVIEW",
      "displayStatus": "PENDING",
      "reviewRequestedAt": "2026-08-24T00:00:00Z",
      "approvedAt": null,
      "closedAt": null,
      "cancelledAt": null,
      "createdAt": "2026-08-23T00:00:00Z",
      "updatedAt": "2026-08-24T00:00:00Z",
      "latestReviewAction": null,
      "latestReviewReason": null,
      "latestReviewedAt": null
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "offset": 0,
    "paged": true,
    "unpaged": false,
    "sort": { "sorted": false, "unsorted": true, "empty": true }
  },
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "sort": { "sorted": false, "unsorted": true, "empty": true },
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

`content`의 각 항목은 `JobPostingResponse`(`JobPostingResponseAssembler`가 최신 심사 이력을 N+1 없이 일괄 조회해 `latestReviewAction`/`latestReviewReason`/`latestReviewedAt`을 채움)다. **이 응답에는 심사자 신원(누가 심사했는지)이 없다** — 심사자 정보는 5·6장의 승인/반려 응답에서만 확인할 수 있다.

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

## 4. 모집 공고 승인/반려 공통 규칙

- **`PENDING_REVIEW` 상태의 공고만** 승인·반려할 수 있다(`JobPosting.approve()`/`reject()`가 자체 검증). 그 외 상태에서 시도하면 `409 INVALID_JOB_POSTING_STATE`.
- 승인하면 `JobPosting.status`가 `OPEN`으로 바뀌고 `approvedAt`이 기록된다(이 시점부터 도시농부에게 공개됨).
- **반려하면 영구적인 `REJECTED` 상태가 되는 게 아니라 `DRAFT`로 되돌아간다** — `JobPostingStatus` enum에는 `REJECTED` 값 자체가 없다(`DRAFT, PENDING_REVIEW, OPEN, CLOSED, CANCELLED, WORK_COMPLETED`). 농가는 반려된 공고를 수정해서 다시 심사 요청할 수 있다. "반려됐다"는 사실은 `JobPostingReview`(감사 로그)와, 농가용 응답의 `displayStatus=REJECTED`(파생 상태)로만 확인 가능하다.
- 승인/반려 응답은 **`JobPosting`이 아니라 방금 생성된 `JobPostingReview` 감사 로그 레코드**를 반환한다(`JobPostingReviewResponse`) — 심사자 신원(`reviewerUserId`/`reviewerName`)을 보여주기 위함이다.

## 5. 모집 공고 승인

### 5.1 요청

```http
POST /api/admin/job-postings/12/approve
Authorization: Bearer {{adminAccessToken}}
```

이 API는 요청 본문을 받지 않는다.

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 30,
  "reviewerUserId": 3,
  "reviewerName": "충북 담당자",
  "action": "APPROVED",
  "reason": null,
  "titleSnapshot": "사과 수확 도우미 모집",
  "descriptionSnapshot": "사과 수확 작업을 도와주실 분을 모집합니다.",
  "createdAt": "2026-08-25T09:00:00Z"
}
```

`id`는 이 심사 이력(`JobPostingReview`) 자체의 ID이지 공고 ID가 아니다. `titleSnapshot`/`descriptionSnapshot`은 심사 시점의 공고 제목·설명을 그대로 복사해 남긴 것이다.

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨(서비스 재검증 단계) |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |
| `409` | `INVALID_JOB_POSTING_STATE` | 공고가 `PENDING_REVIEW` 상태가 아님 |

상태 오류 예시:

```json
{
  "code": "INVALID_JOB_POSTING_STATE",
  "message": "승인 대기 공고만 승인할 수 있습니다."
}
```

## 6. 모집 공고 반려

### 6.1 요청

```http
POST /api/admin/job-postings/12/reject
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "reason": "근무 시간과 임금 조건이 서로 맞지 않습니다."
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `reason` | String | O | 공백만 입력 불가, 최대 1000자 |

### 6.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 31,
  "reviewerUserId": 3,
  "reviewerName": "충북 담당자",
  "action": "REJECTED",
  "reason": "근무 시간과 임금 조건이 서로 맞지 않습니다.",
  "titleSnapshot": "사과 수확 도우미 모집",
  "descriptionSnapshot": "사과 수확 작업을 도와주실 분을 모집합니다.",
  "createdAt": "2026-08-25T09:05:00Z"
}
```

반려 후 해당 공고는 `DRAFT` 상태로 돌아가며, 3장의 승인 대기 목록에서는 더 이상 조회되지 않는다(공고를 수정해 다시 심사 요청해야 재노출).

### 6.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `reason` 누락(공백 포함) 또는 1000자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |
| `409` | `INVALID_JOB_POSTING_STATE` | 공고가 `PENDING_REVIEW` 상태가 아님 |

## 7. 인증·인가 오류 응답 주의사항

`401`·`403`(URL 패턴 단계)은 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드).

- `401`: `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`
- `403`: `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`

`CENTER_ADMIN_ROLE_REQUIRED`(403)는 컨트롤러 진입 이후 서비스 레이어에서 `DomainException`으로 던져지며 전역 `GlobalExceptionHandler`가 처리한다. 형식(`{ code, message }`)은 동일하다.

## 8. 관련 상태값

### 8.1 `JobPosting.status`

| 값 | 의미 | 이 API에서 전이 방법 |
|---|---|---|
| `DRAFT` | 초안 또는 반려되어 되돌아온 상태 | 전이 불가(농가 본인용 API에서 생성/수정) |
| `PENDING_REVIEW` | 심사 요청됨 | 전이 불가(농가 본인용 `submit-review` API에서 전환) |
| `OPEN` | 승인되어 공개 모집 중 | `POST .../approve` |
| `CLOSED`/`CANCELLED`/`WORK_COMPLETED` | 마감/취소/근무완료 | 전이 불가(이 문서 범위 밖 — 다음 라운드 예정) |

### 8.2 `JobPostingReview.action`

| 값 | 의미 | 이 API에서 기록 방법 |
|---|---|---|
| `APPROVED` | 담당자 승인 | `POST .../approve` |
| `REJECTED` | 담당자 반려(사유 필수) | `POST .../reject` |
| `EDITED`/`CLOSED`/`CANCELLED` | 수정/마감/취소 이력 | 이 문서 범위 밖(다음 라운드 예정) |

## 9. 현재 범위 밖 또는 미구현 기능

- 모집 공고 수정, 마감, 취소 관리자 API(문서 7장의 다른 항목, 다음 라운드 예정)
- 공고별 심사 이력 전체 조회(농가 본인용 `GET /api/farm/job-postings/{postingId}/review-history`는 이미 있으나, 관리자용 이력 조회는 이번 범위 밖)
- 목록 조회 필터(지역·작물·기간 등), 검색어
- 승인·반려 취소(한 번 승인하면 `OPEN`에서 되돌릴 수 없고, 반려는 농가가 재수정·재제출해야 함)
- 여러 공고 일괄 승인·반려
