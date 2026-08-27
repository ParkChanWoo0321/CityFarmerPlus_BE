# 농가 모집 공고 관리 API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 10개

> 현재 `FarmJobPostingController`, `FarmJobPostingService`, 공고 DTO/엔티티/예외 구현을 기준으로 작성한 복사용 명세서다.

## 공통 조건

- Base URL: `{{baseUrl}}`
- 모든 API에 JWT `Authorization: Bearer {{farmAccessToken}}` 필수
- `FARM` 역할만 호출 가능
- 목록·상세·심사 이력 조회는 활성 `FARM` 계정과 본인 농가 프로필만 있으면 가능
- 생성·수정·삭제·심사 요청·취소는 농가 프로필이 `APPROVED`여야 함
- 자기 농가가 작성한 공고만 조회·변경 가능
- JSON 요청은 `Content-Type: application/json`
- 시간 비교는 `Asia/Seoul` 기준

## 엔드포인트 요약

| Method | URL | 기능 | 성공 |
|---|---|---|---:|
| `POST` | `/api/farm/job-postings?submitForReview={boolean}` | 공고 초안 생성 또는 생성 즉시 심사 요청 | 201 |
| `GET` | `/api/farm/job-postings` | 내 공고 목록 | 200 |
| `GET` | `/api/farm/job-postings/{postingId}` | 내 공고 상세 | 200 |
| `GET` | `/api/farm/job-postings/{postingId}/review-history` | 심사 이력 | 200 |
| `PATCH` | `/api/farm/job-postings/{postingId}` | 초안 전체 수정 | 200 |
| `DELETE` | `/api/farm/job-postings/{postingId}` | 초안 삭제 | 204 |
| `POST` | `/api/farm/job-postings/{postingId}/submit-review` | 심사 요청 | 200 |
| `POST` | `/api/farm/job-postings/{postingId}/withdraw-review` | 심사 요청 철회 | 200 |
| `PATCH` | `/api/farm/job-postings/{postingId}/applicant-preference` | 희망 지원자 조건 수정 | 200 |
| `POST` | `/api/farm/job-postings/{postingId}/cancel` | 공고 취소 | 200 |

## 상태와 금액 값

### `status`

| 값 | 의미 |
|---|---|
| `DRAFT` | 농가가 수정·삭제할 수 있는 초안 |
| `PENDING_REVIEW` | `CENTER_ADMIN` 중개센터의 심사 대기 |
| `OPEN` | 승인되어 지원 가능한 모집 중 공고 |
| `CLOSED` | 매칭 인원 충족 등으로 마감 |
| `CANCELLED` | 취소 |
| `WORK_COMPLETED` | 연결된 모든 근무가 처리됨 |

### `displayStatus`

농가 화면의 탭과 배지를 위한 파생 상태다.

| 값 | 의미 |
|---|---|
| `DRAFT` | 일반 초안 |
| `PENDING` | 승인 대기 |
| `APPROVED` | 승인되어 모집 중 |
| `CLOSED` | 마감·근무 완료 또는 작업 시작 시각이 지난 `OPEN` 공고 |
| `REJECTED` | 현재 심사 요청이 반려된 뒤 아직 수정·재제출하지 않은 초안 |
| `CANCELLED` | 취소 |

### `wageUnit`

- `HOURLY`: 시급
- `DAILY`: 일급
- 서비스는 금액 정보만 안내하며 결제·송금·정산을 처리하지 않는다.

## 공고 입력 스키마

`POST` 생성과 `PATCH` 전체 수정에 동일하게 적용된다. `PATCH`지만 부분 수정이 아니며 모든 필수 필드를 다시 보내야 한다.

| 필드 | 형식 | 필수 | 검증 |
|---|---|---:|---|
| `crop` | string | O | 공백 불가, 50자 이하 |
| `workType` | string | O | 공백 불가, 100자 이하 |
| `workDate` | date | O | 오늘 또는 미래. 단, 오늘이면 `startTime`이 호출 시각보다 느려야 함 |
| `startTime` | time | O | `HH:mm[:ss]` |
| `endTime` | time | O | `startTime`보다 늦은 시간 |
| `capacity` | integer | O | 1~1000 |
| `meetingPlace` | string | O | 공백 불가, 255자 이하 |
| `wageAmount` | integer | O | 1~100,000,000 |
| `wageUnit` | enum | O | `HOURLY`, `DAILY` |
| `supplies` | string/null | X | 1000자 이하 |
| `precautions` | string/null | X | 2000자 이하 |
| `farmMessage` | string/null | X | 1000자 이하 |
| `applicantPreference` | string/null | X | 1000자 이하 |
| `title` | string | O | 공백 불가, 150자 이하 |
| `description` | string | O | 공백 불가, 5000자 이하 |
| `beginnerGuide` | string/null | X | 2000자 이하 |

선택 문자열이 `null`이거나 공백만 있으면 DB에 `null`로 저장된다.

```json
{
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00",
  "endTime": "16:00",
  "capacity": 3,
  "meetingPlace": "충북 청주시 상당구 농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": "작업 장갑, 모자",
  "precautions": "물을 충분히 섭취해 주세요.",
  "farmMessage": "안전하게 함께 일해요.",
  "applicantPreference": "초보자도 가능합니다.",
  "title": "감자 수확 보조 작업자를 모집합니다",
  "description": "감자 수확을 함께할 분을 모집합니다.",
  "beginnerGuide": "농가의 설명을 듣고 천천히 작업해 주세요."
}
```

## `JobPostingResponse` 응답 형식

삭제를 제외한 생성·상세·수정·상태 변경 API는 다음 전체 객체를 반환한다.

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": "작업 장갑, 모자",
  "precautions": "물을 충분히 섭취해 주세요.",
  "farmMessage": "안전하게 함께 일해요.",
  "applicantPreference": "초보자도 가능합니다.",
  "title": "감자 수확 보조 작업자를 모집합니다",
  "description": "감자 수확을 함께할 분을 모집합니다.",
  "beginnerGuide": "농가의 설명을 듣고 천천히 작업해 주세요.",
  "status": "DRAFT",
  "displayStatus": "DRAFT",
  "reviewRequestedAt": null,
  "approvedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T10:00:00Z",
  "latestReviewAction": null,
  "latestReviewReason": null,
  "latestReviewedAt": null
}
```

`farmAddress`, `contactNumber`는 해당 농가 소유자용 API에서 반환된다. 일반 공고 조회 응답에는 농가 전체 주소와 연락처가 포함되지 않는다.

## 1. 공고 초안 생성

`POST /api/farm/job-postings?submitForReview=false`

- 요청: 상단 “공고 입력 스키마” JSON
- `submitForReview=false`(기본): `DRAFT`로 생성
- `submitForReview=true`: 같은 트랜잭션에서 `PENDING_REVIEW`까지 전환. AI 결과 화면의 “발송 신청”에 사용
- 응답: `201 Created`, 상단 `JobPostingResponse`

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": "작업 장갑, 모자",
  "precautions": "물을 충분히 섭취해 주세요.",
  "farmMessage": "안전하게 함께 일해요.",
  "applicantPreference": "초보자도 가능합니다.",
  "title": "감자 수확 보조 작업자를 모집합니다",
  "description": "감자 수확을 함께할 분을 모집합니다.",
  "beginnerGuide": "농가의 설명을 듣고 천천히 작업해 주세요.",
  "status": "DRAFT",
  "displayStatus": "DRAFT",
  "reviewRequestedAt": null,
  "approvedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T10:00:00Z",
  "latestReviewAction": null,
  "latestReviewReason": null,
  "latestReviewedAt": null
}
```

주요 오류: `VALIDATION_ERROR`(400), `INVALID_REQUEST`(400), `PAST_WORK_DATE`(400), `INVALID_JOB_POSTING_DETAILS`(400), `FARM_APPROVAL_REQUIRED`(403).

## 2. 내 공고 목록

`GET /api/farm/job-postings?displayStatus=REJECTED&page=0&size=20`

| 파라미터 | 기본 | 검증 |
|---|---:|---|
| `displayStatus` | 전체 | `DRAFT`, `PENDING`, `APPROVED`, `CLOSED`, `REJECTED`, `CANCELLED` |
| `page` | 0 | 0 이상 |
| `size` | 20 | 1~100 |

- 필터를 생략하면 모든 상태의 내 공고를 `createdAt` 내림차순으로 반환한다.
- `REJECTED`는 DB 상태가 `DRAFT`이고, 최신 반려 이력이 현재 `reviewRequestedAt` 이후에 생성된 공고만 조회한다. 반려 공고를 수정하거나 재제출 후 철회하면 다시 `DRAFT`로 표시된다.
- 응답 각 항목은 상단 `JobPostingResponse` 전체 형식이다.

```json
{
  "content": [
    {
      "id": 101,
      "farmProfileId": 15,
      "farmName": "푸른농가",
      "cityCounty": "CHEONGJU",
      "farmAddress": "충북 청주시 상당구 ...",
      "contactNumber": "01012345678",
      "crop": "감자",
      "workType": "수확 보조",
      "workDate": "2026-08-20",
      "startTime": "09:00:00",
      "endTime": "16:00:00",
      "capacity": 3,
      "meetingPlace": "농장 입구",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "supplies": "작업 장갑",
      "precautions": "안전수칙 준수",
      "farmMessage": "함께 일해요.",
      "applicantPreference": "초보자 가능",
      "title": "감자 수확 보조",
      "description": "모집 설명",
      "beginnerGuide": "작업 안내",
      "status": "DRAFT",
      "displayStatus": "REJECTED",
      "reviewRequestedAt": null,
      "approvedAt": null,
      "closedAt": null,
      "cancelledAt": null,
      "createdAt": "2026-08-11T10:00:00Z",
      "updatedAt": "2026-08-11T10:00:00Z",
      "latestReviewAction": "REJECTED",
      "latestReviewReason": "집결 장소를 보완해 주세요.",
      "latestReviewedAt": "2026-08-11T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

주요 오류: `VALIDATION_ERROR`(400), `INVALID_REQUEST_PARAMETER`(400), `FARM_PROFILE_NOT_FOUND`(404).

## 3. 내 공고 상세

`GET /api/farm/job-postings/{postingId}`

- 응답: `200 OK`, 상단 `JobPostingResponse` 전체 JSON

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": null,
  "precautions": null,
  "farmMessage": null,
  "applicantPreference": null,
  "title": "감자 수확 보조",
  "description": "모집 설명",
  "beginnerGuide": null,
  "status": "PENDING_REVIEW",
  "displayStatus": "PENDING",
  "reviewRequestedAt": "2026-08-11T10:10:00Z",
  "approvedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T10:10:00Z",
  "latestReviewAction": null,
  "latestReviewReason": null,
  "latestReviewedAt": null
}
```

주요 오류: `JOB_POSTING_NOT_FOUND`(404), `JOB_POSTING_NOT_OWNER`(403).

## 4. 공고 심사 이력

`GET /api/farm/job-postings/{postingId}/review-history`

- 최신 이력부터 내림차순 반환
- `action`: `EDITED`, `APPROVED`, `REJECTED`, `CLOSED`, `CANCELLED`
- 심사자 ID/이름은 해당 공고를 소유한 농가에게만 반환되며, 공개 공고 API에서는 노출되지 않는다.

```json
[
  {
    "id": 55,
    "reviewerUserId": 9001,
    "reviewerName": "중개센터 담당자",
    "action": "REJECTED",
    "reason": "집결 장소를 더 구체적으로 작성해 주세요.",
    "titleSnapshot": "감자 수확 보조",
    "descriptionSnapshot": "모집 설명",
    "createdAt": "2026-08-11T11:00:00Z"
  }
]
```

주요 오류: `JOB_POSTING_NOT_FOUND`(404), `JOB_POSTING_NOT_OWNER`(403).

## 5. 공고 초안 수정

`PATCH /api/farm/job-postings/{postingId}`

- `DRAFT` 상태에서만 수정 가능
- 요청: 상단 전체 입력 JSON. 모든 필수 필드 필요
- 응답: `200 OK`, 상단 `JobPostingResponse`; `status` = `DRAFT`

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 및 선별",
  "workDate": "2026-08-21",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 4,
  "meetingPlace": "농장 정문",
  "wageAmount": 110000,
  "wageUnit": "DAILY",
  "supplies": null,
  "precautions": null,
  "farmMessage": null,
  "applicantPreference": "초보자 가능",
  "title": "감자 수확·선별 작업자 모집",
  "description": "수정된 모집 설명",
  "beginnerGuide": null,
  "status": "DRAFT",
  "displayStatus": "DRAFT",
  "reviewRequestedAt": null,
  "approvedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T12:00:00Z",
  "latestReviewAction": null,
  "latestReviewReason": null,
  "latestReviewedAt": null
}
```

주요 오류: `INVALID_JOB_POSTING_STATE`(409), `PAST_WORK_DATE`(400), `INVALID_JOB_POSTING_DETAILS`(400), `VALIDATION_ERROR`(400), 소유권 오류.

## 6. 공고 초안 삭제

`DELETE /api/farm/job-postings/{postingId}`

- `DRAFT` 상태에서만 삭제 가능
- 반려 후 `DRAFT`로 돌아온 공고처럼 심사 이력이 존재할 수 있으므로, 삭제 시 해당 공고의 `JobPostingReview` 이력을 먼저 모두 삭제한 뒤 공고를 삭제한다.
- 응답: `204 No Content`
- **응답 본문 없음**

```text
(empty body)
```

주요 오류: `INVALID_JOB_POSTING_STATE`(409), `JOB_POSTING_NOT_FOUND`(404), `JOB_POSTING_NOT_OWNER`(403).

## 7. 심사 요청

`POST /api/farm/job-postings/{postingId}/submit-review`

- 요청 본문 없음
- `DRAFT` → `PENDING_REVIEW`

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": null,
  "precautions": null,
  "farmMessage": null,
  "applicantPreference": null,
  "title": "감자 수확 보조",
  "description": "모집 설명",
  "beginnerGuide": null,
  "status": "PENDING_REVIEW",
  "displayStatus": "PENDING",
  "reviewRequestedAt": "2026-08-11T10:10:00Z",
  "approvedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T10:10:00Z",
  "latestReviewAction": null,
  "latestReviewReason": null,
  "latestReviewedAt": null
}
```

주요 오류: `INVALID_JOB_POSTING_STATE`(409), 소유권 오류.

## 8. 심사 요청 철회

`POST /api/farm/job-postings/{postingId}/withdraw-review`

- 요청 본문 없음
- `PENDING_REVIEW` → `DRAFT`
- `reviewRequestedAt`은 이력성 값으로 남을 수 있으므로 현재 상태는 `status`로 판단한다.

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": null,
  "precautions": null,
  "farmMessage": null,
  "applicantPreference": null,
  "title": "감자 수확 보조",
  "description": "모집 설명",
  "beginnerGuide": null,
  "status": "DRAFT",
  "displayStatus": "DRAFT",
  "reviewRequestedAt": "2026-08-11T10:10:00Z",
  "approvedAt": null,
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T10:20:00Z",
  "latestReviewAction": null,
  "latestReviewReason": null,
  "latestReviewedAt": null
}
```

주요 오류: `INVALID_JOB_POSTING_STATE`(409), 소유권 오류.

## 9. 희망 지원자 조건 수정

`PATCH /api/farm/job-postings/{postingId}/applicant-preference`

- `OPEN` 상태에서만 수정 가능
- 작업 시작 전 모집 중인 공고라면 지원자가 있어도 수정 가능
- `null`, `""`, 공백만 있는 문자열은 조건 제거로 처리
- 최대 1000자

```json
{
  "applicantPreference": "수확 경험이 있는 분을 선호하지만 초보자도 가능합니다."
}
```

응답은 상단 `JobPostingResponse` 전체 형식이며 변경된 값이 포함된다.

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": null,
  "precautions": null,
  "farmMessage": null,
  "applicantPreference": "수확 경험이 있는 분을 선호하지만 초보자도 가능합니다.",
  "title": "감자 수확 보조",
  "description": "모집 설명",
  "beginnerGuide": null,
  "status": "OPEN",
  "displayStatus": "APPROVED",
  "reviewRequestedAt": "2026-08-11T10:10:00Z",
  "approvedAt": "2026-08-11T11:00:00Z",
  "closedAt": null,
  "cancelledAt": null,
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T12:00:00Z",
  "latestReviewAction": "APPROVED",
  "latestReviewReason": null,
  "latestReviewedAt": "2026-08-11T11:00:00Z"
}
```

주요 오류: `VALIDATION_ERROR`(400), `INVALID_JOB_POSTING_STATE`(409), 소유권 오류.

## 10. 공고 취소

`POST /api/farm/job-postings/{postingId}/cancel`

- 요청 본문 없음
- `CANCELLED`, `WORK_COMPLETED` 상태는 취소 불가
- `MATCHED` 또는 `WORK_COMPLETED` 지원 건이 하나라도 있으면 농가 취소 불가
- 취소 시 대기 중인 `APPLIED` 지원은 `POSTING_CANCELLED`로 변경

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "farmAddress": "충북 청주시 상당구 ...",
  "contactNumber": "01012345678",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": null,
  "precautions": null,
  "farmMessage": null,
  "applicantPreference": null,
  "title": "감자 수확 보조",
  "description": "모집 설명",
  "beginnerGuide": null,
  "status": "CANCELLED",
  "displayStatus": "CANCELLED",
  "reviewRequestedAt": "2026-08-11T10:10:00Z",
  "approvedAt": "2026-08-11T11:00:00Z",
  "closedAt": null,
  "cancelledAt": "2026-08-11T12:30:00Z",
  "createdAt": "2026-08-11T10:00:00Z",
  "updatedAt": "2026-08-11T12:30:00Z",
  "latestReviewAction": "APPROVED",
  "latestReviewReason": null,
  "latestReviewedAt": "2026-08-11T11:00:00Z"
}
```

주요 오류: `ACTIVE_MATCHES_EXIST`(409), `INVALID_JOB_POSTING_STATE`(409), 소유권 오류.

## 공통 오류 형식

```json
{
  "code": "INVALID_JOB_POSTING_STATE",
  "message": "초안 상태의 공고만 수정할 수 있습니다."
}
```

| HTTP | 대표 `code` | 의미 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | Body/query 검증 실패 |
| 400 | `INVALID_REQUEST` | JSON/열거형/날짜 형식 오류 |
| 400 | `PAST_WORK_DATE` | 이미 시작된 일정 |
| 400 | `INVALID_JOB_POSTING_DETAILS` | 종료 시간 등 세부값 불일치 |
| 401 | `UNAUTHORIZED`, `INVALID_ACCOUNT` | JWT 누락·무효, 현재 계정과 토큰 불일치 |
| 403 | `ACCESS_DENIED` | `FARM` 역할이 아님 |
| 403 | `FARM_APPROVAL_REQUIRED` | 농가 미승인 |
| 403 | `JOB_POSTING_NOT_OWNER` | 다른 농가의 공고 |
| 404 | `FARM_PROFILE_NOT_FOUND` | 계정에 연결된 농가 프로필이 없음 |
| 404 | `JOB_POSTING_NOT_FOUND` | 공고 없음 |
| 409 | `INVALID_JOB_POSTING_STATE` | 현재 상태에서 허용되지 않는 변경 |
| 409 | `ACTIVE_MATCHES_EXIST` | 확정/완료 지원자가 있어 취소 불가 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 동시 변경 충돌 |

## Postman 예시

```bash
# AI 결과를 생성하고 즉시 심사 요청
curl -X POST "{{baseUrl}}/api/farm/job-postings?submitForReview=true" \
  -H "Authorization: Bearer {{farmAccessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"crop":"감자","workType":"수확 보조","workDate":"2026-08-20","startTime":"09:00","endTime":"16:00","capacity":3,"meetingPlace":"농장 입구","wageAmount":100000,"wageUnit":"DAILY","supplies":null,"precautions":null,"farmMessage":null,"applicantPreference":"초보자 가능","title":"감자 수확 보조","description":"모집 설명","beginnerGuide":null}'

# 반려된 내 공고 목록
curl "{{baseUrl}}/api/farm/job-postings?displayStatus=REJECTED&page=0&size=20" \
  -H "Authorization: Bearer {{farmAccessToken}}"

# 심사 요청
curl -X POST "{{baseUrl}}/api/farm/job-postings/101/submit-review" \
  -H "Authorization: Bearer {{farmAccessToken}}"

# 희망 지원자 조건 수정
curl -X PATCH "{{baseUrl}}/api/farm/job-postings/101/applicant-preference" \
  -H "Authorization: Bearer {{farmAccessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"applicantPreference":"초보자도 가능합니다."}'
```

## 중개센터 담당자 API와 현재 제한

- 이 문서에는 관리자/중개센터 전용 API를 기재하지 않았다.
- `PENDING_REVIEW` 공고의 승인·반려와 담당자 수정·강제 마감은 현재 통합된 `CENTER_ADMIN` API가 처리한다.
- DB 상태가 아직 `OPEN`이어도 작업 시작 시각이 지났다면 농가 화면에서는 `CLOSED`로 분류된다. 따라서 `APPROVED` 탭과 홈 승인 건수에는 포함되지 않고 `CLOSED` 탭과 마감 건수에 포함된다.
- 승인된 공고의 전체 내용을 농가가 직접 수정하는 API는 없다. 작업 시작 전 `OPEN` 공고에서 농가가 직접 수정할 수 있는 값은 `applicantPreference`다.
- 지원자 수락/거절에 해당하는 최종 매칭은 `CENTER_ADMIN`이 확정한다. 농가는 의견만 남길 수 있다.
- `POST /api/admin/job-postings/{postingId}/matches`가 최종 매칭을 확정하며 모집 인원이 충족되면 공고를 자동으로 마감한다.
- 지원자가 있는 공고도 확정/완료 매칭만 없다면 농가가 취소할 수 있다. 이때 대기 지원은 자동으로 공고 취소 상태가 된다.
- 공고 심사 이력은 `CENTER_ADMIN` API가 생성한 결과를 농가가 조회하는 용도다.
