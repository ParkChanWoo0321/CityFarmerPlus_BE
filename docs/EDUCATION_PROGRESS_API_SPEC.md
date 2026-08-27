# 교육 실시간 수강률 API 명세

- 기준일: 2026-08-28
- 기준 구현: `codex/education-realtime-progress` 현재 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 운영 반영 상태: feature 브랜치 구현·검증 완료, 운영 배포 전

이 문서는 교육기관 LMS가 수강 진도를 서버로 전달하는 API와 도시농부 화면이 현재
수강률을 조회하는 응답 계약을 설명한다.

> 수강률과 이수증 승인은 서로 다른 상태다. `progressPercentage=100` 또는
> `progressStatus=COMPLETED`만으로 `eligibleToApply=true`가 되지 않는다. 모집 공고
> 지원 자격은 기존처럼 필수 과정의 이수증을 중개센터 관리자가 모두 승인해야 한다.

---

## 1. 구현 범위

현재 서버가 제공하는 기능은 다음과 같다.

- 교육기관의 HMAC-SHA256 서명 진도 이벤트 수신
- 회원·과정별 현재 수강 시간과 수강률 저장
- 교육기관 이벤트 원문 SHA-256과 처리 결과 감사 이력 저장
- 이벤트 ID 멱등 처리
- 과거 이벤트와 진도 감소 방어
- 도시농부 교육 인증 조회 응답에 과정별 수강률 포함
- 비밀키 미설정 시 웹훅 API 자동 비활성화

현재 범위에 포함되지 않는 기능은 다음과 같다.

- 특정 LMS 제품의 계정 생성·SSO·수강 신청 API
- 브라우저가 직접 보내는 영상 재생 heartbeat
- WebSocket 또는 SSE 기반 서버 푸시
- 수강 완료에 따른 이수증 자동 승인

따라서 실제 자동 갱신을 시작하려면 사용하는 교육기관 LMS가 2절의 웹훅 계약으로
이벤트를 보내도록 설정해야 한다.

---

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 |
|---|---|---|---|---:|
| 교육기관 진도 이벤트 수신 | `POST` | `/api/integrations/education/progress-events` | HMAC 서명 | 200 |
| 도시농부 교육·수강 현황 조회 | `GET` | `/api/urban-farmers/me/education-certification` | `URBAN_FARMER` JWT | 200 |

---

## 3. 교육기관 진도 이벤트

### `POST /api/integrations/education/progress-events`

교육기관 서버만 호출하는 server-to-server API다. 사용자 JWT는 사용하지 않는다.

### 3.1 요청 헤더

```http
Content-Type: application/json
X-Education-Event-Timestamp: 1787875200
X-Education-Signature: sha256={{64자리_소문자_hex}}
```

| 헤더 | 필수 | 규칙 |
|---|---|---|
| `Content-Type` | O | `application/json` |
| `X-Education-Event-Timestamp` | O | 요청을 보낸 시각의 Unix epoch seconds |
| `X-Education-Signature` | O | `sha256=` 접두사를 포함한 HMAC-SHA256 hex |

기본 서명 시각 허용 오차는 서버 현재 시각 기준 ±5분이다.

### 3.2 서명 생성 규칙

```text
signatureBase = timestamp + "." + rawJsonBody
signatureHex  = HMAC-SHA256(EDUCATION_PROGRESS_WEBHOOK_SECRET, signatureBase)
headerValue   = "sha256=" + signatureHex
```

- `timestamp`는 헤더에 넣은 문자열과 정확히 같아야 한다.
- `rawJsonBody`는 실제 HTTP로 전송할 UTF-8 바이트다.
- 서명 후 JSON 공백, 줄바꿈, 필드 순서를 바꾸면 서명이 실패한다.
- 공유 비밀키는 UTF-8 기준 32바이트 이상이어야 한다.
- 비밀키를 프론트엔드 코드, 모바일 앱, Git, 문서에 포함하면 안 된다.

Node.js 서명 예시:

```javascript
import crypto from "node:crypto";

const secret = process.env.EDUCATION_PROGRESS_WEBHOOK_SECRET;
const payload = {
  provider: "CHUNGBUK_LMS",
  eventId: "evt-20260828-0001",
  externalEnrollmentId: "enrollment-21-1",
  urbanFarmerId: 21,
  courseId: 1,
  totalMinutes: 480,
  completedMinutes: 240,
  occurredAt: "2026-08-28T00:30:00Z"
};

const rawBody = JSON.stringify(payload);
const timestamp = Math.floor(Date.now() / 1000).toString();
const signature = crypto
  .createHmac("sha256", secret)
  .update(`${timestamp}.`, "utf8")
  .update(rawBody, "utf8")
  .digest("hex");

const response = await fetch(
  "https://cityfarmerplus-api-82951616760.us-west1.run.app/api/integrations/education/progress-events",
  {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Education-Event-Timestamp": timestamp,
      "X-Education-Signature": `sha256=${signature}`
    },
    body: rawBody
  }
);
```

### 3.3 요청 본문

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

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `provider` | String | O | 1~50자, 영문·숫자로 시작, 이후 영문·숫자·`.`·`_`·`-` |
| `eventId` | String | O | 제공자 내 고유 ID, 1~100자, 영문·숫자로 시작, 이후 영문·숫자·`.`·`_`·`:`·`-` |
| `externalEnrollmentId` | String | O | LMS 수강 등록 ID, `eventId`와 같은 문자·길이 규칙 |
| `urbanFarmerId` | Long | O | 서버의 활성 `URBAN_FARMER` 회원 ID, 양수 |
| `courseId` | Long | O | 서버의 활성 교육 과정 ID, 양수 |
| `totalMinutes` | Integer | O | 1~525600, 해당 과정의 `requiredHours * 60` 이상 |
| `completedMinutes` | Integer | O | 0~525600, `totalMinutes` 이하 |
| `occurredAt` | Instant | O | UTC ISO-8601 권장, 서버 현재보다 5분 넘게 미래일 수 없음 |

서버는 `provider`를 대문자로 정규화해 저장한다.

### 3.4 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "enrollmentId": 100,
  "urbanFarmerId": 21,
  "courseId": 1,
  "provider": "CHUNGBUK_LMS",
  "externalEnrollmentId": "enrollment-21-1",
  "progressStatus": "IN_PROGRESS",
  "totalMinutes": 480,
  "completedMinutes": 240,
  "remainingMinutes": 240,
  "progressPercentage": 50,
  "startedAt": "2026-08-28T00:00:00Z",
  "completedAt": null,
  "providerUpdatedAt": "2026-08-28T00:30:00Z",
  "lastSyncedAt": "2026-08-28T00:30:01Z",
  "version": 2
}
```

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `enrollmentId` | Long | N | 서버 수강 등록 ID |
| `urbanFarmerId` | Long | N | 도시농부 회원 ID |
| `courseId` | Long | N | 교육 과정 ID |
| `provider` | String | N | 정규화된 교육 제공자 코드 |
| `externalEnrollmentId` | String | N | LMS 수강 등록 ID |
| `progressStatus` | Enum | N | `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` |
| `totalMinutes` | Integer | N | 전체 교육 시간 |
| `completedMinutes` | Integer | N | 현재 수강 시간 |
| `remainingMinutes` | Integer | N | `max(0, totalMinutes - completedMinutes)` |
| `progressPercentage` | Integer | N | `(completedMinutes * 100) / totalMinutes`의 소수점 버림, 0~100 |
| `startedAt` | Instant | Y | 최초 1분 이상 수강한 이벤트 시각 |
| `completedAt` | Instant | Y | 최초 100% 완료 이벤트 시각 |
| `providerUpdatedAt` | Instant | N | 현재 값으로 반영된 제공자 이벤트 시각 |
| `lastSyncedAt` | Instant | N | 마지막으로 유효한 새 이벤트를 수신한 서버 시각 |
| `version` | Long | N | 낙관적 잠금 버전 |

### 3.5 상태 계산

| 조건 | `progressStatus` |
|---|---|
| `completedMinutes == 0` | `NOT_STARTED` |
| `0 < completedMinutes < totalMinutes` | `IN_PROGRESS` |
| `completedMinutes == totalMinutes` | `COMPLETED` |

완료 전에는 계산된 비율이 99%를 넘더라도 `progressPercentage`를 정수 나눗셈으로
계산하므로 100%가 표시되지 않는다.

### 3.6 멱등성·이벤트 순서

- 멱등 키는 `(provider, eventId)`다.
- 동일한 원문을 같은 `eventId`로 재전송하면 저장을 반복하지 않고 현재 상태를 `200`으로 반환한다.
- 같은 `eventId`에 다른 원문을 사용하면 `409 EDUCATION_PROGRESS_EVENT_CONFLICT`다.
- `occurredAt`이 현재 저장된 `providerUpdatedAt`보다 과거이면 이벤트 이력만 저장하고 현재 진도를 덮어쓰지 않는다.
- 더 최신 이벤트가 `completedMinutes`를 감소시키면 `409 EDUCATION_PROGRESS_REGRESSION`이다.
- `COMPLETED`가 된 과정은 더 최신 이벤트로 `IN_PROGRESS`나 `NOT_STARTED`로 되돌릴 수 없다.
- 같은 `occurredAt`에 같은 시간 값은 현재 상태를 유지한다. 다른 시간 값이면 충돌로 거절한다.

---

## 4. 도시농부 수강률 조회

### `GET /api/urban-farmers/me/education-certification`

기존 교육 인증 조회 API의 `courses[]`에 수강 진도 필드가 추가됐다.

### 4.1 요청

```http
GET {{baseUrl}}/api/urban-farmers/me/education-certification
Authorization: Bearer {{urbanFarmerAccessToken}}
```

### 4.2 과정별 응답 예시

```json
{
  "courseId": 1,
  "title": "충북형 도시농부 필수 교육",
  "description": "도시농업 기본 교육",
  "requiredHours": 8,
  "externalApplicationUrl": "https://education.example.com/course/1",
  "mandatory": true,
  "latestSubmissionStatus": null,
  "latestSubmissionId": null,
  "attemptNumber": null,
  "recognizedHours": null,
  "rejectionReason": null,
  "submittedAt": null,
  "progressStatus": "IN_PROGRESS",
  "totalMinutes": 480,
  "completedMinutes": 240,
  "remainingMinutes": 240,
  "progressPercentage": 50,
  "startedAt": "2026-08-28T00:00:00Z",
  "completedAt": null,
  "progressUpdatedAt": "2026-08-28T00:30:00Z",
  "lastSyncedAt": "2026-08-28T00:30:01Z"
}
```

웹훅 이벤트가 한 번도 없는 과정도 진도 필드는 누락되지 않는다.

```json
{
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
```

이때 기본 `totalMinutes`는 `requiredHours * 60`이다.

### 4.3 인증 상태와의 관계

다음 필드는 기존 이수증 심사 결과로만 계산한다.

- 최상위 `status`
- `eligibleToApply`
- `approvedRequiredCourseCount`
- `recognizedHours`
- 과정별 `latestSubmissionStatus`

따라서 다음과 같은 응답도 정상이다.

```json
{
  "eligibleToApply": false,
  "courses": [
    {
      "progressStatus": "COMPLETED",
      "progressPercentage": 100,
      "latestSubmissionStatus": null
    }
  ]
}
```

교육 수강은 완료했지만 이수증을 아직 제출하지 않았거나 관리자가 승인하지 않은 상태다.

---

## 5. 프론트엔드 연동

프론트엔드는 웹훅 API를 호출하지 않는다. 비밀키가 필요한 웹훅은 교육기관 서버만
호출해야 한다.

권장 화면 흐름:

1. 내 정보 또는 교육 화면 진입 시 `GET /api/urban-farmers/me/education-certification`을 호출한다.
2. `courses[]`에서 해당 과정의 `progressPercentage`, `remainingMinutes`, `progressStatus`를 표시한다.
3. 외부 교육 페이지에서 돌아오면 조회 API를 즉시 다시 호출한다.
4. 같은 화면을 열어 둔 동안 즉시 갱신이 필요하면 5~10초 간격으로 재조회한다.
5. `progressStatus=COMPLETED`라도 `eligibleToApply=false`이면 이수증 제출·승인 안내를 계속 표시한다.

현재 서버는 이벤트 수신 즉시 DB를 갱신하지만 WebSocket/SSE 푸시는 제공하지 않으므로
화면 갱신 시점은 프론트 재조회 주기에 따라 결정된다.

---

## 6. 오류 응답

공통 형식:

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `MISSING_REQUEST_HEADER` | 서명 시각 또는 서명 헤더 누락 |
| 400 | `VALIDATION_ERROR` | 필수 필드·문자 형식·숫자 범위 위반 |
| 400 | `INVALID_REQUEST` | 요청 본문 누락 등 Spring 요청 변환 실패 |
| 400 | `INVALID_EDUCATION_PROGRESS_EVENT` | 서명은 통과했지만 JSON을 해석할 수 없음 |
| 400 | `INVALID_EDUCATION_PROGRESS` | 수강 시간이 전체 시간을 초과함 |
| 400 | `INVALID_EDUCATION_PROGRESS_TIME` | `occurredAt`이 현재보다 5분 넘게 미래임 |
| 400 | `INSUFFICIENT_EDUCATION_PROGRESS_DURATION` | 전체 시간이 과정 필수 시간보다 짧음 |
| 401 | `INVALID_EDUCATION_PROGRESS_SIGNATURE` | 서명·서명 형식·서명 시각이 유효하지 않음 |
| 404 | `ACTIVE_URBAN_FARMER_NOT_FOUND` | 대상 회원이 없거나 활성 도시농부가 아님 |
| 404 | `ACTIVE_EDUCATION_COURSE_NOT_FOUND` | 과정이 없거나 비활성 상태 |
| 409 | `EDUCATION_PROGRESS_EVENT_CONFLICT` | 같은 이벤트 ID에 다른 원문 사용 |
| 409 | `EDUCATION_ENROLLMENT_CONFLICT` | 회원·과정·외부 등록 ID 연결이 기존 값과 충돌 |
| 409 | `EDUCATION_PROGRESS_REGRESSION` | 진도 감소, 완료 취소 또는 동일 시각 값 충돌 |
| 409 | `DATA_CONFLICT` | 동시 생성 요청이 DB UNIQUE 제약에서 충돌 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 동시 진도 갱신 잠금 충돌 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | `application/json`이 아닌 요청 |
| 503 | `EDUCATION_PROGRESS_WEBHOOK_DISABLED` | 서버 웹훅 비밀키 미설정 |

서명 검증이 JSON 파싱보다 먼저 수행되므로 서명이 틀린 잘못된 JSON은 `401`을 반환한다.

---

## 7. 서버 설정

| 환경 변수 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `EDUCATION_PROGRESS_WEBHOOK_SECRET` | 운영 O | 빈 값 | 32바이트 이상 HMAC 공유 비밀키. 빈 값이면 API 비활성화 |
| `EDUCATION_PROGRESS_WEBHOOK_TOLERANCE` | X | `5m` | 서명 시각 허용 오차 |

운영에서는 비밀키를 Google Secret Manager에 저장하고 Cloud Run에는 secret reference로
주입한다. 실제 비밀키 값은 로그나 API 응답에 출력하지 않는다.

신규 DB 테이블:

- `education_enrollments`: 회원·과정별 현재 진도
- `education_progress_events`: 제공자 이벤트 감사 이력

운영은 `JPA_DDL_AUTO=validate`이므로 애플리케이션 배포 전에
`gcp/migrations/20260828_education_progress.sql`을 적용해야 한다.

---

## 8. 확인 시나리오

1. 비밀키가 없을 때 웹훅이 `503 EDUCATION_PROGRESS_WEBHOOK_DISABLED`인지 확인한다.
2. 잘못된 서명이 `401 INVALID_EDUCATION_PROGRESS_SIGNATURE`인지 확인한다.
3. 480분 과정에 240분 이벤트를 보내 응답이 `IN_PROGRESS`, 50%, 잔여 240분인지 확인한다.
4. 같은 이벤트 원문을 재전송해 중복 행 없이 `200`인지 확인한다.
5. 같은 이벤트 ID에 다른 본문을 보내 `409`인지 확인한다.
6. 과거 이벤트가 현재 240분을 감소시키지 않는지 확인한다.
7. 더 최신 120분 이벤트가 `409 EDUCATION_PROGRESS_REGRESSION`인지 확인한다.
8. 480분 완료 이벤트 후 상태가 `COMPLETED`, 100%, 잔여 0분인지 확인한다.
9. 도시농부 조회 응답에 같은 진도가 나타나는지 확인한다.
10. 수강 완료만으로 `eligibleToApply`가 `true`가 되지 않는지 확인한다.
