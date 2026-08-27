# CityFarmerPlus 관리자 모집 공고 승인/반려 및 지원자 매칭 API 명세서

- 문서 버전: 1.2
- 작성일: 2026-08-26
- 구현 기준 브랜치: `backend-2`
- 적용 범위: 승인 대기 모집 공고 목록 조회, 승인, 반려, 지원자 후보 목록 조회(필터 없음), 지원자 배치 매칭 확정, 모집 공고 수정·강제 마감·취소, 공고별 심사 이력 조회(관리자용) (후보 필터 검색은 범위 밖)

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
- 서비스 레이어에서 JWT와 무관하게 **DB에 저장된 실제 역할을 다시 조회**한다(승인/반려는 `AdminJobPostingService.requireCenterAdmin`, 후보 조회/매칭은 `AdminJobPostingMatchingService.requireCenterAdmin` — 두 서비스에 각각 존재하는 동일한 로직이다).
- 심사자·매칭 확정자 ID는 요청 body로 받지 않는다. 항상 JWT의 `sub`(`AuthenticatedUser.id(authentication)`)에서 추출한다.
- 승인/반려 API는 [`jobposting`](../src/main/java/chungbuk/cityfarmerplus/jobposting) 패키지의 `JobPosting`/`JobPostingReview` 엔티티와 응답 DTO(`JobPostingResponse`, `JobPostingReviewResponse`), 조립기(`JobPostingResponseAssembler`)를 재사용한다. 매칭 API는 여기에 더해 [`application`](../src/main/java/chungbuk/cityfarmerplus/application) 패키지의 `JobApplication`(지원)과 [`work`](../src/main/java/chungbuk/cityfarmerplus/work) 패키지의 `WorkAssignment`(근무 일정)까지 함께 다룬다.
- **승인·반려는 두 엔티티를 함께 다룬다.** `JobPosting`은 상태만 전이하고(`PENDING_REVIEW → OPEN` 또는 `PENDING_REVIEW → DRAFT`), **누가·왜 심사했는지는 `JobPosting`이 아니라 별도의 append-only 감사 로그 엔티티 `JobPostingReview`에 기록된다.** 이 API는 `posting.approve()`/`reject()`와 `JobPostingReview.record(...)` 저장을 같은 트랜잭션 안에서 함께 처리한다.
- **매칭 확정은 세 엔티티를 함께 다룬다.** `JobApplication.match()`로 지원 상태를 바꾸고, `WorkAssignment.fromMatchedApplication()`으로 근무 일정을 새로 만들고, 정원이 다 찼으면 `JobPosting.closeWhenCapacityReached()`로 공고를 자동 마감하고 나머지 지원들을 `JobApplication.markNotMatched()`로 전환한다 — 전부 하나의 트랜잭션 안에서 처리된다(8.4절 참고).

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`JobPostingException`, `JobApplicationException` 둘 다 `common.exception.DomainException`을 상속하므로 전역 `GlobalExceptionHandler`가 컨트롤러 종류와 무관하게 처리한다(농가 소유 증빙 심사 때와 달리, 이 두 도메인은 컨트롤러 범위를 지정하는 별도 어드바이스가 없어 새 컨트롤러를 추가 등록할 필요가 없었다). 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다. 자세한 내용은 9장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 승인 대기 공고 목록 조회 | `GET` | `/api/admin/job-postings` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 모집 공고 승인 | `POST` | `/api/admin/job-postings/{postingId}/approve` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 모집 공고 반려 | `POST` | `/api/admin/job-postings/{postingId}/reject` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 지원자 후보 목록 조회 | `GET` | `/api/admin/job-postings/{postingId}/candidates` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 지원자 배치 매칭 확정 | `POST` | `/api/admin/job-postings/{postingId}/matches` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 모집 공고 수정 | `PATCH` | `/api/admin/job-postings/{postingId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 모집 공고 강제 마감 | `POST` | `/api/admin/job-postings/{postingId}/close` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 모집 공고 취소 | `POST` | `/api/admin/job-postings/{postingId}/cancel` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 공고별 심사 이력 조회 | `GET` | `/api/admin/job-postings/{postingId}/review-history` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

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

## 7. 지원자 후보 목록 조회

**필터 없이 해당 공고에 지원한 전체 내역을 제출일 오름차순으로 반환한다.** 상태별·지역별·요일별 등 필터링은 다음 라운드에서 별도로 추가될 예정이다(11장 참고).

### 7.1 요청

```http
GET /api/admin/job-postings/12/candidates
Authorization: Bearer {{adminAccessToken}}
```

### 7.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "applicationId": 101,
    "urbanFarmerUserId": 10,
    "name": "홍길동",
    "phoneNumber": "01098765432",
    "status": "APPLIED",
    "farmOpinion": "PREFERRED",
    "farmOpinionNote": "경험이 많아 보입니다.",
    "preferredRegionsSnapshot": "CHEONGJU,CHUNGJU",
    "availableDaysSnapshot": "MON,WED,FRI",
    "preferredStartDateSnapshot": "2026-09-01",
    "preferredEndDateSnapshot": "2026-09-30",
    "availableWorkTypesSnapshot": "수확,파종",
    "canTravelSnapshot": true,
    "experienceCountSnapshot": 3,
    "educationVerifiedAt": "2026-08-01T00:00:00Z",
    "appliedAt": "2026-08-24T05:00:00Z"
  }
]
```

농가용 후보 조회(`GET /api/farm/job-postings/{postingId}/applications`)와 동일한 `JobCandidateResponse`를 재사용한다. `farmOpinion`(농가가 남긴 선호/비선호 의견)은 참고용일 뿐 매칭 가능 여부를 강제하지 않는다 — `APPLIED` 상태이기만 하면 `farmOpinion`과 무관하게 8장의 매칭 API로 확정할 수 있다.

### 7.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |

## 8. 지원자 배치 매칭 확정

여러 지원(`applicationId`)을 한 번에 매칭 확정한다. 매칭되면 `JobApplication.status`가 `MATCHED`로 바뀌고, 각 지원마다 `WorkAssignment`(근무 일정)가 새로 생성된다.

### 8.1 요청

```http
POST /api/admin/job-postings/12/matches
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "applicationIds": [101, 102]
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `applicationIds` | Long 배열 | O | 비어 있으면 안 됨(`@NotEmpty`) |

### 8.2 성공 응답

```http
HTTP/1.1 200 OK
```

새로 생성된 `WorkAssignment` 목록을 반환한다(요청에 담긴 `applicationIds` 순서가 아니라, 락 조회 결과 순서 — `id` 오름차순).

```json
[
  {
    "id": 55,
    "jobPostingId": 12,
    "jobApplicationId": 101,
    "urbanFarmerUserId": 10,
    "urbanFarmerName": "홍길동",
    "confirmedByUserId": 3,
    "confirmedByName": "충북 담당자",
    "confirmedByContactNumber": "01011112222",
    "farmName": "충주 사과농원",
    "farmAddress": "충청북도 충주시 예시로 1",
    "farmContactNumber": "01012345678",
    "crop": "사과",
    "workType": "수확",
    "workDate": "2026-09-01",
    "startTime": "09:00:00",
    "endTime": "17:00:00",
    "recruitmentCapacity": 5,
    "meetingPlace": "충주시 사과농원 정문",
    "wageAmount": 100000,
    "wageUnit": "DAILY",
    "supplies": "장갑, 모자",
    "precautions": "미끄럼 주의",
    "status": "SCHEDULED",
    "attendanceStatus": "NOT_RECORDED",
    "completedAt": null
  }
]
```

`confirmedByUserId`/`confirmedByName`/`confirmedByContactNumber`가 이 배치를 확정한 관리자 정보다. 근무 조건(장소·시간·임금 등)은 매칭 시점의 `JobPosting`/`FarmProfile` 값을 그대로 스냅샷한 것이라, 이후 공고가 수정돼도 이 값은 바뀌지 않는다.

**이 응답에는 이번에 매칭된 건만 담긴다.** 정원이 차서 자동으로 `NOT_MATCHED` 처리된 나머지 지원자는 이 응답에 나오지 않는다 — 확인하려면 7장의 후보 목록을 다시 조회해야 한다.

### 8.3 검증 순서와 오류 응답

`AdminJobPostingMatchingService.match()`가 하나의 `@Transactional` 메서드 안에서 아래 순서로 처리한다. **어느 단계에서 실패하든 그 이전에 획득한 락(공고, 지원 전체)까지 포함해 트랜잭션 전체가 롤백된다** — 부분 매칭이나 락만 남는 상태는 발생하지 않는다(8.4절 참고).

| 순서 | 검증/처리 | 실패 시 상태 | 코드 |
|---|---|---|---|
| 1 | 관리자 역할 재검증 | `404`/`403` | `USER_NOT_FOUND`/`INACTIVE_ACCOUNT`/`CENTER_ADMIN_ROLE_REQUIRED` |
| 2 | 공고 잠금 조회(`findByIdForUpdate`) | `404` | `JOB_POSTING_NOT_FOUND` |
| 3 | 공고가 `OPEN` 상태인지 | `409` | `INVALID_JOB_POSTING_STATE` |
| 4 | `applicationIds`에 중복이 없는지 | `409` | `INVALID_JOB_APPLICATION_STATE`(메시지: "요청에 중복된 지원 ID가 포함되어 있습니다.") |
| 5 | 지원 전체 잠금 조회(`findAllByIdForUpdate`), 개수 일치 확인 | `404` | `JOB_APPLICATION_NOT_FOUND`(존재하지 않는 ID 포함) |
| 6 | 매칭 후 인원이 `capacity`를 넘지 않는지(현재 `MATCHED` 수 + 이번 배치 수) | `409` | `JOB_POSTING_CAPACITY_EXCEEDED` |
| 7 | 각 지원이 이 `postingId` 소속인지 | `404` | `JOB_APPLICATION_NOT_FOUND` |
| 8 | 각 지원이 `APPLIED` 상태인지 | `409` | `INVALID_JOB_APPLICATION_STATE`(메시지: "지원 완료 상태만 매칭할 수 있습니다.") |
| 9 | 각 지원자가 같은 시간대에 이미 확정된 근무가 없는지(`countOverlappingAssignments`) | `409` | `OVERLAPPING_WORK_ASSIGNMENT` |
| 10 | (전부 통과) 각 지원 `match()` + `WorkAssignment` 생성 | — | — |
| 11 | 매칭 후 정원 도달 시 공고 자동 마감 + 나머지 `APPLIED` 전부 `NOT_MATCHED` 전환 | — | — |

6~9번은 **배치 안의 지원 하나라도 조건을 어기면 그 배치 전체가 실패**한다(일부만 매칭하고 나머지는 실패 처리하는 부분 성공은 없음).

전체 오류 목록:

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `applicationIds`가 비어 있음 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |
| `404` | `JOB_APPLICATION_NOT_FOUND` | 존재하지 않는 지원 ID가 포함됨, 또는 다른 공고 소속 지원 ID가 섞여 있음 |
| `409` | `INVALID_JOB_POSTING_STATE` | 공고가 `OPEN` 상태가 아님 |
| `409` | `INVALID_JOB_APPLICATION_STATE` | `applicationIds`에 중복 ID가 있음, 또는 배치 안에 `APPLIED`가 아닌 지원이 있음 |
| `409` | `JOB_POSTING_CAPACITY_EXCEEDED` | 이번 배치까지 매칭하면 모집 인원(`capacity`)을 초과함 |
| `409` | `OVERLAPPING_WORK_ASSIGNMENT` | 지원자 중 누군가 같은 시간대에 이미 확정된 근무가 있음 |

정원 초과 예시:

```json
{
  "code": "JOB_POSTING_CAPACITY_EXCEEDED",
  "message": "모집 인원을 초과하여 매칭할 수 없습니다."
}
```

### 8.4 트랜잭션·동시성 설계

- `posting = jobPostingRepository.findByIdForUpdate(postingId)`(8.3의 2번)와 `jobApplicationRepository.findAllByIdForUpdate(distinctIds)`(5번)는 둘 다 `PESSIMISTIC_WRITE` 락이며, 서비스 메서드 전체가 하나의 `@Transactional`(읽기 전용 아님) 경계 안에 있다. 6~9번 검증 중 어느 하나라도 예외를 던지면(전부 unchecked `DomainException` 계열) 스프링이 트랜잭션을 롤백하고, 그 시점까지 잡고 있던 두 락도 롤백과 함께 자동 해제된다 — 애플리케이션 코드가 락을 별도로 풀어줄 필요가 없다.
- `findAllByIdForUpdate`는 `id` 오름차순으로 정렬해서 잠그므로, 서로 다른 두 배치 매칭 요청이 겹치는 지원 ID 일부를 포함하더라도 항상 같은 순서로 락을 시도해 데드락을 피한다.
- 같은 공고에 대한 동시 배치 매칭 요청 두 건이 있으면, 두 번째 요청은 2번(공고 잠금) 단계에서 첫 번째 트랜잭션이 끝날 때까지 대기한다 — 즉 한 공고에 대한 매칭 처리는 사실상 직렬화된다. 11번(정원 도달 시 나머지 자동 `NOT_MATCHED` 전환)이 별도 락 없이 `findByJobPostingIdAndStatus`로 조회해도 안전한 이유가 이것이다.

## 9. 인증·인가 오류 응답 주의사항

`401`·`403`(URL 패턴 단계)은 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드).

- `401`: `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`
- `403`: `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`

`CENTER_ADMIN_ROLE_REQUIRED`(403)는 컨트롤러 진입 이후 서비스 레이어에서 `DomainException`으로 던져지며 전역 `GlobalExceptionHandler`가 처리한다. 형식(`{ code, message }`)은 동일하다.

## 10. 관련 상태값

### 10.1 `JobPosting.status`

| 값 | 의미 | 이 API에서 전이 방법 |
|---|---|---|
| `DRAFT` | 초안 또는 반려되어 되돌아온 상태 | 전이 불가(농가 본인용 API에서 생성/수정) |
| `PENDING_REVIEW` | 심사 요청됨 | 전이 불가(농가 본인용 `submit-review` API에서 전환) |
| `OPEN` | 승인되어 공개 모집 중, 매칭 대상 | `POST .../approve`로 진입, 정원 도달 시 `POST .../matches`가 자동으로 `CLOSED`로 전환. `PATCH .../{postingId}`(수정)도 이 상태에서 가능(13장) |
| `CLOSED` | 마감 | 정원 도달 자동 전환, 또는 `POST .../close`(관리자 강제 마감, 13장. `OPEN`에서만 가능) |
| `CANCELLED` | 취소 | `POST .../cancel`(관리자 취소, 14장. `CANCELLED`·`WORK_COMPLETED`가 아니면 어떤 상태에서도 가능) |
| `WORK_COMPLETED` | 근무완료 | 이 문서 범위 밖(근태 관리 기능) |

### 10.2 `JobPostingReview.action`

| 값 | 의미 | 이 API에서 기록 방법 |
|---|---|---|
| `APPROVED` | 담당자 승인 | `POST .../approve` |
| `REJECTED` | 담당자 반려(사유 필수) | `POST .../reject` |
| `EDITED` | 담당자 수정(사유 필수) | `PATCH .../{postingId}`(12장) |
| `CLOSED` | 담당자 강제 마감 | `POST .../close`(13장) |
| `CANCELLED` | 담당자 취소 | `POST .../cancel`(14장) |

### 10.3 `JobApplication.status`

| 값 | 의미 | 이 API에서 전이 방법 |
|---|---|---|
| `APPLIED` | 지원 완료, 매칭 대기 | 전이 불가(도시농부 본인용 지원 API에서 생성) |
| `MATCHED` | 매칭 확정 | `POST .../matches`(선택된 지원 건) |
| `NOT_MATCHED` | 정원 마감으로 매칭 안 됨 | `POST .../matches`(정원 도달 시 나머지 `APPLIED` 전체, 부수 효과) |
| `WITHDRAWN`/`POSTING_CANCELLED`/`NO_SHOW`/`WORK_COMPLETED` | 취소/공고취소/결근/근무완료 | 이 문서 범위 밖(도시농부 본인용 취소, 또는 근태 관리 기능) |

### 10.4 `WorkAssignment.status` / `attendanceStatus`

| 필드 | 매칭 시점 값 |
|---|---|
| `status` | `SCHEDULED`(고정 — 매칭 확정 시 항상 이 값으로 생성) |
| `attendanceStatus` | `NOT_RECORDED`(고정 — 출결은 근무 당일 별도 API에서 기록, 이 문서 범위 밖) |

## 11. 현재 범위 밖 또는 미구현 기능

- 모집 공고 수정, 강제 마감, 취소, 공고별 심사 이력 조회(관리자용) — 12~15장에서 구현됨
- 승인 대기 공고 목록 조회 필터(지역·작물·기간 등), 검색어
- 승인·반려 취소(한 번 승인하면 `OPEN`에서 되돌릴 수 없고, 반려는 농가가 재수정·재제출해야 함)
- 여러 공고 일괄 승인·반려
- **지원자 후보 필터 조회**(지원 상태·희망 지역·근무 가능 요일·경험 횟수·농가 의견·시간 충돌 여부 등) — 다음 라운드에서 별도 진행 예정. 이번 7장의 목록 조회는 필터 없이 전체만 반환한다
- 매칭 확정 취소(되돌리기), 근무 일정 취소 관리자 API
- 배치 매칭 결과에 자동 `NOT_MATCHED` 처리된 지원자 목록을 함께 반환하는 기능(현재는 응답에 없고 후보 목록 재조회로만 확인 가능)

## 12. 모집 공고 수정

농가 본인용 수정(`PATCH /api/farm/job-postings/{postingId}`, `DRAFT` 상태만 가능)과 달리, 관리자는 **`DRAFT`·`PENDING_REVIEW`·`OPEN` 세 상태 모두**에서 공고 내용을 수정할 수 있다(`JobPosting.updateByAdmin()`). 전체 필드를 새 값으로 교체하는 방식이며 부분 수정은 지원하지 않는다.

### 12.1 요청

```http
PATCH /api/admin/job-postings/12
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "reason": "농가 요청으로 근무 시간과 임금을 조정했습니다.",
  "crop": "사과",
  "workType": "수확",
  "workDate": "2026-09-02",
  "startTime": "08:00:00",
  "endTime": "16:00:00",
  "capacity": 5,
  "meetingPlace": "충주시 사과농원 정문",
  "wageAmount": 110000,
  "wageUnit": "DAILY",
  "supplies": "장갑, 모자",
  "precautions": "미끄럼 주의",
  "farmMessage": "초보자도 환영합니다.",
  "applicantPreference": "체력 좋으신 분",
  "title": "사과 수확 도우미 모집",
  "description": "사과 수확 작업을 도와주실 분을 모집합니다.",
  "beginnerGuide": "장갑 착용 후 조심히 따주세요."
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `reason` | String | O | 공백만 입력 불가, 최대 1000자(`JobPostingReview.reason`으로 기록) |
| `crop` | String | O | 공백 불가, 50자 이하 |
| `workType` | String | O | 공백 불가, 100자 이하 |
| `workDate` | LocalDate | O | 오늘 또는 미래 날짜(`@FutureOrPresent`) |
| `startTime`/`endTime` | LocalTime | O | `endTime`이 `startTime`보다 늦어야 함(`JobPostingScheduleValidator`) |
| `capacity` | int | O | 1~1000, **이미 확정(`MATCHED`)된 인원 수 이상**이어야 함 |
| `meetingPlace` | String | O | 공백 불가, 255자 이하 |
| `wageAmount` | int | O | 1~100,000,000 |
| `wageUnit` | Enum | O | `HOURLY`, `DAILY` |
| `supplies` | String | X | 1000자 이하 |
| `precautions` | String | X | 2000자 이하 |
| `farmMessage` | String | X | 1000자 이하 |
| `applicantPreference` | String | X | 1000자 이하 |
| `title` | String | O | 공백 불가, 150자 이하 |
| `description` | String | O | 공백 불가, 5000자 이하 |
| `beginnerGuide` | String | X | 2000자 이하 |

농가용 `JobPostingUpsertRequest`와 필드 구성이 같지만(`reason`만 추가), 두 DTO는 이 코드베이스의 다른 요청 DTO들처럼 평면(flat) 레코드 관례를 따르기 위해 서로 독립된 타입이다.

작업 날짜·시간은 실제로 `endTime.isAfter(startTime)`와 `workDate.atTime(startTime)`이 현재 시각 이후인지까지 `JobPostingScheduleValidator`가 재검증한다(단순 날짜 레벨의 `@FutureOrPresent`보다 엄격하다) — 농가 본인용 생성·수정 API와 동일한 검증기를 재사용한다.

### 12.2 성공 응답

```http
HTTP/1.1 200 OK
```

승인/반려와 달리 이 API는 **`JobPostingReview`가 아니라 갱신된 `JobPosting` 전체(`JobPostingResponse`)**를 반환한다 — 바뀐 필드 값(정원·임금·일정 등)을 한 번에 확인할 수 있도록 하기 위함이다. `latestReviewAction`에 방금 기록된 `EDITED`가 채워진다.

```json
{
  "id": 12,
  "farmProfileId": 7,
  "farmName": "충주 사과농원",
  "cityCounty": "CHUNGJU",
  "farmAddress": "충청북도 충주시 예시로 1",
  "contactNumber": "01012345678",
  "crop": "사과",
  "workType": "수확",
  "workDate": "2026-09-02",
  "startTime": "08:00:00",
  "endTime": "16:00:00",
  "capacity": 5,
  "meetingPlace": "충주시 사과농원 정문",
  "wageAmount": 110000,
  "wageUnit": "DAILY",
  "supplies": "장갑, 모자",
  "precautions": "미끄럼 주의",
  "farmMessage": "초보자도 환영합니다.",
  "applicantPreference": "체력 좋으신 분",
  "title": "사과 수확 도우미 모집",
  "description": "사과 수확 작업을 도와주실 분을 모집합니다.",
  "beginnerGuide": "장갑 착용 후 조심히 따주세요.",
  "status": "OPEN",
  "displayStatus": "APPROVED",
  "reviewRequestedAt": "2026-08-24T00:00:00Z",
  "approvedAt": "2026-08-25T09:00:00Z",
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-23T00:00:00Z",
  "updatedAt": "2026-08-26T09:10:00Z",
  "latestReviewAction": "EDITED",
  "latestReviewReason": "농가 요청으로 근무 시간과 임금을 조정했습니다.",
  "latestReviewedAt": "2026-08-26T09:10:00Z"
}
```

### 12.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `reason` 누락(공백 포함) 또는 1000자 초과, 그 외 필드 형식·길이 위반 |
| `400` | `PAST_WORK_DATE` | 작업 시작 일시(`workDate`+`startTime`)가 현재 이전 |
| `400` | `INVALID_JOB_POSTING_DETAILS` | `endTime`이 `startTime`보다 늦지 않음, 또는 그 외 상세 정보 형식 오류 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |
| `409` | `INVALID_JOB_POSTING_STATE` | 공고가 `DRAFT`·`PENDING_REVIEW`·`OPEN` 중 어느 것도 아님(예: `CLOSED`, `CANCELLED`, `WORK_COMPLETED`) |
| `409` | `CAPACITY_BELOW_MATCHED_COUNT` | 새 `capacity`가 이미 확정(`MATCHED`)된 지원자 수보다 작음 |

정원 축소 오류 예시:

```json
{
  "code": "CAPACITY_BELOW_MATCHED_COUNT",
  "message": "이미 확정된 인원보다 모집 인원을 적게 수정할 수 없습니다."
}
```

## 13. 모집 공고 강제 마감

`OPEN` 상태의 공고만 관리자가 강제로 마감할 수 있다(`JobPosting.close()`). 정원 도달 시 8장의 매칭 API가 자동으로 마감하는 것과 별개로, 정원이 남아 있어도 관리자 판단으로 조기 마감할 때 쓴다.

### 13.1 요청

```http
POST /api/admin/job-postings/12/close
Authorization: Bearer {{adminAccessToken}}
```

이 API는 요청 본문을 받지 않는다(사유 입력 필드 없음).

### 13.2 성공 응답

```http
HTTP/1.1 200 OK
```

승인/반려와 동일하게 방금 기록된 `JobPostingReview` 감사 로그를 반환한다(`JobPostingReviewResponse`).

```json
{
  "id": 32,
  "reviewerUserId": 3,
  "reviewerName": "충북 담당자",
  "action": "CLOSED",
  "reason": null,
  "titleSnapshot": "사과 수확 도우미 모집",
  "descriptionSnapshot": "사과 수확 작업을 도와주실 분을 모집합니다.",
  "createdAt": "2026-08-26T09:15:00Z"
}
```

### 13.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |
| `409` | `INVALID_JOB_POSTING_STATE` | 공고가 `OPEN` 상태가 아님 |

## 14. 모집 공고 취소

`CANCELLED`·`WORK_COMPLETED` 상태가 아니라면 어떤 상태(`DRAFT`, `PENDING_REVIEW`, `OPEN`, `CLOSED`)의 공고든 관리자가 취소할 수 있다(`JobPosting.cancel()`). 참조 무결성 때문에 공고를 물리 삭제하는 API는 없고, 지원자가 이미 있는 공고도 이 취소 API로 `CANCELLED` 상태로 남긴다.

**이 API는 공고 하나만 취소하는 게 아니라, 관련 지원·근무 일정까지 같은 트랜잭션 안에서 함께 정리한다.** 농가 본인용 취소(`POST /api/farm/job-postings/{postingId}/cancel`)는 `MATCHED`·`WORK_COMPLETED` 지원이 하나라도 있으면 취소 자체를 막아버리지만, 관리자 취소는 매칭 이후 단계에서도 강제로 취소할 수 있어야 하므로 다음을 함께 처리한다.

1. `JobPosting.cancel()` — 공고 상태를 `CANCELLED`로 전환.
2. 이 공고에 속한 **모든** `JobApplication`에 `cancelWithPostingByAdmin()`을 호출 — `APPLIED`·`MATCHED`·`NO_SHOW` 상태만 `POSTING_CANCELLED`로 바뀌고(엔티티 내부 가드), 이미 `WITHDRAWN`/`NOT_MATCHED`/`WORK_COMPLETED`/`POSTING_CANCELLED`인 지원은 조용히 그대로 둔다. 상태별로 걸러서 조회하지 않고 전체에 호출해도 안전하다.
3. 이 공고에 속한 `WorkAssignment` 중 **`SCHEDULED`인 것만** `cancel()`을 호출해 `WorkStatus.CANCELLED`로 전환한다. `COMPLETED`·`NO_SHOW`인 근무 일정은 건드리지 않는다 — `WorkAssignment.cancel()` 자체의 가드는 `COMPLETED`/`CANCELLED`만 막고 `NO_SHOW`는 막지 않으므로, 서비스 레이어(`AdminJobPostingService.cancel()`)에서 `status == SCHEDULED`인 것만 걸러서 호출한다.

동시성은 별도 비관적 락 없이 `JobPosting.getForUpdate()`(1번 전에 이미 획득)와 각 엔티티의 `@Version` 낙관적 잠금으로 처리한다 — `JobApplication`/`WorkAssignment`를 바꾸는 다른 모든 경로(출결 등록, 근무 완료, 지원 철회, 매칭 확정)가 전부 `JobPosting`을 먼저 잠그고 들어오므로 이 취소 트랜잭션과 자연히 직렬화된다.

### 14.1 요청

```http
POST /api/admin/job-postings/12/cancel
Authorization: Bearer {{adminAccessToken}}
```

이 API는 요청 본문을 받지 않는다(사유 입력 필드 없음).

### 14.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 33,
  "reviewerUserId": 3,
  "reviewerName": "충북 담당자",
  "action": "CANCELLED",
  "reason": null,
  "titleSnapshot": "사과 수확 도우미 모집",
  "descriptionSnapshot": "사과 수확 작업을 도와주실 분을 모집합니다.",
  "createdAt": "2026-08-26T09:20:00Z"
}
```

### 14.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |
| `409` | `INVALID_JOB_POSTING_STATE` | 공고가 이미 `CANCELLED` 또는 `WORK_COMPLETED` 상태임 |

## 15. 공고별 심사 이력 조회(관리자용)

농가 본인용 이력 조회(`GET /api/farm/job-postings/{postingId}/review-history`, 소유권 확인 필요)와 같은 저장소 조회(`JobPostingReviewRepository.findByJobPostingIdOrderByCreatedAtDescIdDesc`)와 응답 DTO(`JobPostingReviewResponse`)를 그대로 재사용한다. 관리자는 소유 농가와 무관하게 모든 공고의 이력을 조회할 수 있다.

### 15.1 요청

```http
GET /api/admin/job-postings/12/review-history
Authorization: Bearer {{adminAccessToken}}
```

### 15.2 성공 응답

```http
HTTP/1.1 200 OK
```

최신순(`createdAt` 내림차순, 동시각이면 `id` 내림차순)으로 전체 이력을 배열로 반환한다. 페이지네이션은 없다.

```json
[
  {
    "id": 33,
    "reviewerUserId": 3,
    "reviewerName": "충북 담당자",
    "action": "CANCELLED",
    "reason": null,
    "titleSnapshot": "사과 수확 도우미 모집",
    "descriptionSnapshot": "사과 수확 작업을 도와주실 분을 모집합니다.",
    "createdAt": "2026-08-26T09:20:00Z"
  },
  {
    "id": 32,
    "reviewerUserId": 3,
    "reviewerName": "충북 담당자",
    "action": "EDITED",
    "reason": "농가 요청으로 근무 시간과 임금을 조정했습니다.",
    "titleSnapshot": "사과 수확 도우미 모집",
    "descriptionSnapshot": "사과 수확 작업을 도와주실 분을 모집합니다.",
    "createdAt": "2026-08-26T09:10:00Z"
  },
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
]
```

이력이 없으면(아직 승인·반려·수정·마감·취소가 한 번도 없었던 공고) `200 OK`와 `[]`을 반환한다.

### 15.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `404` | `JOB_POSTING_NOT_FOUND` | 해당 `postingId`의 공고가 없음 |

이 API는 목록 조회(3장)와 마찬가지로 관리자 역할을 서비스 레이어에서 다시 조회하지 않는다(`requireCenterAdmin` 미호출) — 단순 읽기 전용 조회라 승인/반려/수정/마감/취소처럼 `JobPostingReview`에 심사자를 기록할 필요가 없기 때문이다. 따라서 `CENTER_ADMIN_ROLE_REQUIRED`/`INACTIVE_ACCOUNT`/`USER_NOT_FOUND`는 이 API에는 없다.

## 16. 다음 라운드로 미룬 항목

- 마감·취소 API에 사유(`reason`) 입력 필드 추가 여부(현재는 수정만 사유 필수, 마감·취소는 감사 로그에 `reason=null`로 기록됨)
