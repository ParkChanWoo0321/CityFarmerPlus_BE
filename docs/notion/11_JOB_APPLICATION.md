# 공고 지원·농가 지원자 의견 API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 6개

## 기능 경계

- 도시농부: 모집 중 공고 지원, 내 지원 조회, 매칭 전 지원 취소
- 농가: 자기 공고의 지원자 조회, 선호/비선호 의견 기록
- **농가 의견은 수락/거절이 아니다. 최종 매칭은 `CENTER_ADMIN` 중개센터가 확정한다.**
- 중개센터/관리자 전용 API는 이 문서에 포함하지 않는다.

## 공통 인증

- JWT 필수: `Authorization: Bearer {accessToken}`
- 활성 계정만 사용 가능
- 도시농부 API: `URBAN_FARMER`
- 농가 API: `FARM` + `APPROVED` 농가 프로필

## 엔드포인트

| Method | URL | 권한 | 기능 | 성공 |
|---|---|---|---|---:|
| `POST` | `/api/job-postings/{postingId}/applications` | URBAN_FARMER | 공고 지원/재지원 | 201 |
| `GET` | `/api/urban-farmers/me/job-applications` | URBAN_FARMER | 내 지원 목록 | 200 |
| `GET` | `/api/urban-farmers/me/job-applications/{applicationId}` | URBAN_FARMER | 내 지원 상세 | 200 |
| `POST` | `/api/urban-farmers/me/job-applications/{applicationId}/withdraw` | URBAN_FARMER | 지원 취소 | 200 |
| `GET` | `/api/farm/job-postings/{postingId}/applications` | FARM | 농가 지원자 목록 | 200 |
| `PATCH` | `/api/farm/job-postings/{postingId}/applications/{applicationId}/opinion` | FARM | 농가 의견 수정 | 200 |

## 지원 상태

| `status` | 의미 |
|---|---|
| `APPLIED` | 지원 완료, 매칭 대기 |
| `WITHDRAWN` | 도시농부가 지원 취소 |
| `MATCHED` | `CENTER_ADMIN` 중개센터가 매칭 확정 |
| `NOT_MATCHED` | 매칭 미선정 |
| `POSTING_CANCELLED` | 공고 취소로 지원도 취소 |
| `NO_SHOW` | 결근 처리 |
| `WORK_COMPLETED` | 근무 완료 |

농가 의견 `farmOpinion`: `NONE`, `PREFERRED`, `NOT_PREFERRED`

## `JobApplicationResponse` 형식

```json
{
  "id": 501,
  "jobPostingId": 101,
  "postingTitle": "감자 수확 보조",
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "status": "APPLIED",
  "farmOpinion": "NONE",
  "farmOpinionNote": null,
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "createdAt": "2026-08-11T12:00:00Z",
  "withdrawnAt": null,
  "matchedAt": null
}
```

`wageUnit`: `HOURLY`, `DAILY`. 결제·송금·정산 기능은 없다.

## 1. 공고 지원/재지원

`POST /api/job-postings/{postingId}/applications`

- 권한: `URBAN_FARMER`
- Body 없음
- 다음 조건을 모두 충족해야 한다.
  - 공고가 `OPEN`
  - 농가가 승인 및 활성 상태
  - 작업 시작 전
  - 모든 활성 필수 교육 과정의 이수증 승인 완료
- 도시농부 사업 참여 신청 승인은 공고 지원 조건이 아니다.
- 근무 희망 조건은 없어도 지원 가능하다. 조건이 있으면 지역·요일·희망 시작일·희망 종료일·가능 작업 유형·이동 가능 여부를 지원 시점 값으로 스냅샷한다.
- 도시농부 프로필의 경험 횟수도 스냅샷한다. 프로필이 없으면 0이다.
- 같은 공고의 `WITHDRAWN` 건은 재지원되고 모든 지원 조건 스냅샷을 최신 값으로 갱신하며 농가 의견을 초기화한다.
- 다른 상태의 기존 지원은 중복 지원으로 거부한다.

### 응답

`201 Created`

```json
{
  "id": 501,
  "jobPostingId": 101,
  "postingTitle": "감자 수확 보조",
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "status": "APPLIED",
  "farmOpinion": "NONE",
  "farmOpinionNote": null,
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "createdAt": "2026-08-11T12:00:00Z",
  "withdrawnAt": null,
  "matchedAt": null
}
```

### 대표 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 403 | `EDUCATION_CERTIFICATION_REQUIRED` | 필수 교육 이수증 승인 미완료, 또는 활성 필수 과정이 0개 |
| 403 | `URBAN_FARMER_REQUIRED` / `ACCESS_DENIED` | 역할 불일치 |
| 404 | `JOB_POSTING_NOT_OPEN` | 없거나 지원 불가능한 공고 |
| 409 | `DUPLICATE_JOB_APPLICATION` | 취소 상태가 아닌 기존 지원 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 동시 지원/상태 변경 충돌 |

## 2. 내 지원 목록

`GET /api/urban-farmers/me/job-applications?page=0&size=20`

- 권한: `URBAN_FARMER`
- `page`: 기본 0, 0 이상
- `size`: 기본 20, 1~100
- `createdAt` 내림차순
- 모든 지원 상태를 포함

```json
{
  "content": [
    {
      "id": 501,
      "jobPostingId": 101,
      "postingTitle": "감자 수확 보조",
      "farmName": "푸른농가",
      "cityCounty": "CHEONGJU",
      "workDate": "2026-08-20",
      "startTime": "09:00:00",
      "endTime": "16:00:00",
      "status": "APPLIED",
      "farmOpinion": "PREFERRED",
      "farmOpinionNote": "수확 경험을 선호합니다.",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "createdAt": "2026-08-11T12:00:00Z",
      "withdrawnAt": null,
      "matchedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

오류: `VALIDATION_ERROR`(400), `UNAUTHORIZED`/`INVALID_ACCOUNT`(401), `ACCESS_DENIED`/`URBAN_FARMER_REQUIRED`(403).

## 3. 내 지원 상세

`GET /api/urban-farmers/me/job-applications/{applicationId}`

```json
{
  "id": 501,
  "jobPostingId": 101,
  "postingTitle": "감자 수확 보조",
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "status": "APPLIED",
  "farmOpinion": "NONE",
  "farmOpinionNote": null,
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "createdAt": "2026-08-11T12:00:00Z",
  "withdrawnAt": null,
  "matchedAt": null
}
```

오류: `JOB_APPLICATION_NOT_FOUND`(404), `JOB_APPLICATION_NOT_OWNER`(403), `INVALID_REQUEST_PARAMETER`(400).

## 4. 지원 취소

`POST /api/urban-farmers/me/job-applications/{applicationId}/withdraw`

- Body 없음
- `APPLIED` → `WITHDRAWN`만 허용
- `MATCHED` 이후는 도시농부가 이 API로 취소할 수 없다.

```json
{
  "id": 501,
  "jobPostingId": 101,
  "postingTitle": "감자 수확 보조",
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "workDate": "2026-08-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "status": "WITHDRAWN",
  "farmOpinion": "NONE",
  "farmOpinionNote": null,
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "createdAt": "2026-08-11T12:00:00Z",
  "withdrawnAt": "2026-08-11T13:00:00Z",
  "matchedAt": null
}
```

오류: `INVALID_JOB_APPLICATION_STATE`(409), `JOB_APPLICATION_NOT_FOUND`(404), `JOB_APPLICATION_NOT_OWNER`(403), `CONCURRENT_UPDATE_CONFLICT`(409).

## 5. 농가 지원자 목록

`GET /api/farm/job-postings/{postingId}/applications`

- 권한: 활성 `FARM` + 저장된 농가 프로필
- 자기 공고만 가능
- 모든 상태의 지원을 `createdAt` 오름차순으로 반환

```json
[
  {
    "applicationId": 501,
    "urbanFarmerUserId": 301,
    "name": "홍길동",
    "phoneNumber": "01012345678",
    "status": "APPLIED",
    "farmOpinion": "NONE",
    "farmOpinionNote": null,
    "preferredRegionsSnapshot": "CHEONGJU,CHUNGJU",
    "availableDaysSnapshot": "MONDAY,TUESDAY",
    "preferredStartDateSnapshot": "2026-09-01",
    "preferredEndDateSnapshot": "2026-10-31",
    "availableWorkTypesSnapshot": "수확,선별",
    "canTravelSnapshot": true,
    "experienceCountSnapshot": 2,
    "educationVerifiedAt": "2026-08-11T12:00:00Z",
    "appliedAt": "2026-08-11T12:00:00Z"
  }
]
```

### 개인정보 범위

- 자기 공고의 승인 농가에게만 지원자 ID·이름·현재 회원 연락처·지원 스냅샷을 제공한다.
- `phoneNumber`는 회원 연락처를 입력하지 않은 지원자라면 `null`이다.
- 로그인 ID, 비밀번호, 주소, 이수증 파일은 반환하지 않는다.
- `educationVerifiedAt`은 현재 구현에서 **이수증 승인 시각이 아니라, 지원 시점에 교육 자격을 재확인한 시각**으로 저장된다.
- `preferredRegionsSnapshot`, `availableDaysSnapshot`, `availableWorkTypesSnapshot`은 쉼표로 연결한 지원 시점 문자열이다.
- 희망 근무 조건이 없었던 지원은 희망 조건 스냅샷이 `null`일 수 있다. 경험 횟수는 프로필이 없으면 0이다.

오류: `FARM_PROFILE_NOT_FOUND`(404), `JOB_POSTING_NOT_FOUND`(404), `JOB_POSTING_NOT_OWNER`(403).

## 6. 농가 지원자 의견 수정

지원자 의견은 해당 공고가 작업 시작 전 모집 중이고 지원 상태가 `APPLIED`일 때만 수정할 수 있다. 화면에서 `CLOSED`로 분류된 공고에는 더 이상 의견을 변경할 수 없다.

`PATCH /api/farm/job-postings/{postingId}/applications/{applicationId}/opinion`

- 자기 공고에 속한 지원만 가능
- 공고가 `OPEN`이고 지원이 `APPLIED`인 동안 언제든 덮어쓰기 가능
- `opinion` 필수: `NONE`, `PREFERRED`, `NOT_PREFERRED`
- `note` 선택, 1000자 이하. `null`/공백은 DB `null`

```json
{
  "opinion": "PREFERRED",
  "note": "수확 경험이 있는 지원자로 선호합니다."
}
```

```json
{
  "applicationId": 501,
  "urbanFarmerUserId": 301,
  "name": "홍길동",
  "phoneNumber": "01012345678",
  "status": "APPLIED",
  "farmOpinion": "PREFERRED",
  "farmOpinionNote": "수확 경험이 있는 지원자로 선호합니다.",
  "preferredRegionsSnapshot": "CHEONGJU,CHUNGJU",
  "availableDaysSnapshot": "MONDAY,TUESDAY",
  "preferredStartDateSnapshot": "2026-09-01",
  "preferredEndDateSnapshot": "2026-10-31",
  "availableWorkTypesSnapshot": "수확,선별",
  "canTravelSnapshot": true,
  "experienceCountSnapshot": 2,
  "educationVerifiedAt": "2026-08-11T12:00:00Z",
  "appliedAt": "2026-08-11T12:00:00Z"
}
```

오류: `VALIDATION_ERROR`(400), `INVALID_REQUEST`(400), `INVALID_JOB_APPLICATION_STATE`(409), `JOB_APPLICATION_NOT_FOUND`(404), 공고 소유권 오류.

## 공통 오류 형식

```json
{
  "code": "DUPLICATE_JOB_APPLICATION",
  "message": "이미 지원한 공고입니다."
}
```

## Postman 예시

```bash
# 도시농부 지원
curl -X POST "{{baseUrl}}/api/job-postings/101/applications" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}"

# 내 지원 목록
curl "{{baseUrl}}/api/urban-farmers/me/job-applications?page=0&size=20" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}"

# 농가 지원자 목록
curl "{{baseUrl}}/api/farm/job-postings/101/applications" \
  -H "Authorization: Bearer {{farmAccessToken}}"

# 농가 의견 수정
curl -X PATCH "{{baseUrl}}/api/farm/job-postings/101/applications/501/opinion" \
  -H "Authorization: Bearer {{farmAccessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"opinion":"PREFERRED","note":"수확 경험을 선호합니다."}'
```

## 현재 제한과 중개센터 역할

- 같은 날짜·시간대의 여러 공고에 지원하는 것은 현재 허용된다. 지원 단계에서 일정 중복을 검사하지 않는다.
- 최종 매칭 시 인원 제한, 일정 중복 검사, 미선정 처리와 근무 일정 생성은 현재 통합된 `CENTER_ADMIN` API가 처리한다.
- 농가 의견 `PREFERRED`/`NOT_PREFERRED`는 중개센터의 매칭 판단을 돕는 정보일 뿐, 자동 매칭이 아니다.
- 매칭 확정은 `POST /api/admin/job-postings/{postingId}/matches`로 구현돼 있다. 확정된 매칭을 취소하거나 되돌리는 API는 현재 없다.
