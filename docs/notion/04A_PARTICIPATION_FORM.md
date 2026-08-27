# 도시농부 통합 사업참여 신청 폼 API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 3개

## 기능 목적과 기존 API 관계

이 API는 디자인의 “희망 근무 조건 신청” 한 화면에서 다음 세 데이터를 한 번에 조회·저장·제출하기 위한 트랜잭션 API다.

- 도시농부 프로필: 농업경영체 등록 여부, 경험 횟수, 경험 특이사항
- 희망 근무 조건: 지역, 요일, 작업 유형, 희망 기간, 이동 가능 여부, 특이사항
- 사업참여 신청: 사업연도, 신청 특이사항, 제출·심사 상태

기존 API는 삭제하거나 대체하지 않는다. 다음 API는 각각 독립 API로 계속 사용할 수 있다.

- `/api/urban-farmers/me/profile` 3개
- `/api/urban-farmers/me/work-preference` 3개
- `/api/urban-farmers/me/participation-applications` 7개

같은 엔티티를 여러 API가 수정할 수 있으므로 통합 폼 응답의 최신 version을 다음 저장 요청에 전달하는 방식을 권장한다.

## 인증

세 API 모두 다음 조건이 필요하다.

- Bearer JWT
- 활성 `URBAN_FARMER` 계정

```http
Authorization: Bearer {{urbanFarmerAccessToken}}
```

## 엔드포인트

| Method | URL | 기능 | 성공 |
|---|---|---|---:|
| `GET` | `/api/urban-farmers/me/participation-forms/{programYear}` | 세 데이터와 현재 신청 상태 통합 조회 | 200 |
| `PUT` | `/api/urban-farmers/me/participation-forms/{programYear}` | 수정 가능한 값을 한 트랜잭션으로 저장 | 200 |
| `POST` | `/api/urban-farmers/me/participation-forms/{programYear}/submit` | 세 데이터를 저장하고 신청 제출·재제출 | 200 |

`programYear`는 2000~2100이다.

## 공통 요청 JSON

`PUT`과 `POST .../submit`은 같은 요청 형식을 사용한다.

```json
{
  "agriculturalBusinessRegistered": false,
  "experienceCount": 3,
  "experienceNotes": "감자 수확 경험이 있습니다.",
  "preferredRegions": ["CHEONGJU", "CHUNGJU"],
  "availableDays": ["MONDAY", "WEDNESDAY"],
  "availableWorkTypes": ["수확", "선별"],
  "preferredStartDate": "2026-09-01",
  "preferredEndDate": "2026-10-31",
  "canTravel": true,
  "workPreferenceNotes": "대중교통으로 이동 가능한 지역을 선호합니다.",
  "applicationNote": "평일 근무를 희망합니다.",
  "expectedApplicationVersion": 2,
  "expectedProfileVersion": 1,
  "expectedWorkPreferenceVersion": 3
}
```

| 필드 | 필수 | 규칙 |
|---|---:|---|
| `agriculturalBusinessRegistered` | O | boolean |
| `experienceCount` | O | 0~10000 |
| `experienceNotes` | X | 1000자 이하, 공백 값은 `null`로 정규화 |
| `preferredRegions` | O | 충북 11개 시·군 enum 중 1~11개, 중복 제거 |
| `availableDays` | O | `DayOfWeek` 1~7개, 중복 제거 |
| `availableWorkTypes` | O | 1~20개, 항목당 50자 이하, 쉼표·줄바꿈 불가, 대소문자 무시 중복 제거 |
| `preferredStartDate` | O | `YYYY-MM-DD` |
| `preferredEndDate` | O | 시작일과 같거나 이후, 종료일이 현재 날짜보다 과거일 수 없음 |
| `canTravel` | O | boolean |
| `workPreferenceNotes` | X | 1000자 이하, 공백 값은 `null`로 정규화 |
| `applicationNote` | X | 1000자 이하, 공백 값은 `null`로 정규화 |
| `expectedApplicationVersion` | X | 직전 조회의 `applicationVersion` |
| `expectedProfileVersion` | X | 직전 조회의 `profileVersion` |
| `expectedWorkPreferenceVersion` | X | 직전 조회의 `workPreferenceVersion` |

아직 생성되지 않은 리소스의 expected version은 `null`로 보낸다. version을 생략할 수는 있지만, 다른 API나 탭의 변경을 덮어쓰지 않도록 전달하는 방식을 권장한다.

## 공통 응답 JSON

```json
{
  "programYear": 2026,
  "status": "SUBMITTED",
  "nextAction": "SAVE_PENDING_CHANGES",
  "editableFields": [
    "agriculturalBusinessRegistered",
    "experienceCount",
    "experienceNotes",
    "preferredRegions",
    "availableDays",
    "availableWorkTypes",
    "preferredStartDate",
    "preferredEndDate",
    "canTravel",
    "workPreferenceNotes",
    "applicationNote"
  ],
  "applicationId": 101,
  "applicationVersion": 2,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "평일 근무를 희망합니다.",
  "rejectionReason": null,
  "reviewedByUserId": null,
  "submittedAt": "2026-08-13T03:00:00Z",
  "reviewedAt": null,
  "cancelledAt": null,
  "profileId": 31,
  "profileVersion": 1,
  "experienceCount": 3,
  "experienceNotes": "감자 수확 경험이 있습니다.",
  "workPreferenceId": 41,
  "workPreferenceVersion": 3,
  "preferredRegions": ["CHEONGJU", "CHUNGJU"],
  "availableDays": ["MONDAY", "WEDNESDAY"],
  "availableWorkTypes": ["수확", "선별"],
  "preferredStartDate": "2026-09-01",
  "preferredEndDate": "2026-10-31",
  "canTravel": true,
  "workPreferenceNotes": "대중교통으로 이동 가능한 지역을 선호합니다."
}
```

신청이 없는 조회도 404가 아니라 `200`과 `status: NOT_STARTED`를 반환한다. 기존 프로필이나 희망 조건이 있으면 해당 값은 함께 반환하고, 없는 리소스의 ID와 version은 `null`이다.

## 상태별 동작

| 현재 `status` | `PUT` 저장 | `POST .../submit` | `nextAction` |
|---|---|---|---|
| `NOT_STARTED` | 세 데이터 생성, 신청은 `DRAFT` | 세 데이터 생성 후 `SUBMITTED` | `SUBMIT` |
| `DRAFT` | 수정 후 `DRAFT` 유지 | `SUBMITTED` | `SUBMIT` |
| `SUBMITTED` | 심사 대기 상태를 유지하며 수정 | 이미 제출됨으로 409 | `SAVE_PENDING_CHANGES` |
| `REJECTED` | 반려 정보와 상태를 유지하며 수정 | `SUBMITTED`로 재제출하고 이전 심사 정보 초기화 | `RESUBMIT` |
| `APPROVED` | 경험·희망 조건만 수정 | 409 | `SAVE_APPROVED_PREFERENCES` |
| `CANCELLED` | 409 | 409 | `NONE` |

승인 후에는 `agriculturalBusinessRegistered`와 `applicationNote`가 잠긴다. `PUT` 요청 자체는 공통 DTO를 사용하므로 두 필드도 보내야 하며, 현재 승인 값과 동일해야 한다. 응답의 `editableFields`를 화면 활성화 기준으로 사용한다.

## 원자성과 동시 수정

`PUT`과 제출 API는 사용자, 사업참여 신청, 프로필, 희망 근무 조건을 고정된 순서로 잠근 뒤 하나의 DB 트랜잭션에서 처리한다. 하나라도 실패하면 세 데이터 변경이 모두 롤백된다.

expected version이 현재 값과 다르면 다음 오류로 저장을 중단한다.

```json
{
  "code": "PARTICIPATION_FORM_VERSION_CONFLICT",
  "message": "workPreference 정보가 다른 요청에 의해 변경되었습니다. 최신 정보를 다시 조회해 주세요."
}
```

## 대표 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | DTO 필수값·길이·범위 오류 |
| 400 | `INVALID_PROGRAM_YEAR` | 사업연도가 2000~2100 밖임 |
| 400 | `INVALID_PARTICIPATION_FORM` | 필수 통합 폼 데이터 누락 |
| 400 | `INVALID_PARTICIPATION_FORM_PERIOD` | 희망 기간 누락 또는 종료일이 시작일보다 빠름 |
| 400 | `PARTICIPATION_FORM_PERIOD_EXPIRED` | 희망 종료일이 현재 날짜보다 과거 |
| 400 | `INVALID_WORK_TYPE` | 작업 유형에 쉼표·줄바꿈 포함 |
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | JWT 누락·무효 또는 비활성 계정 |
| 403 | `ACCESS_DENIED` / `URBAN_FARMER_REQUIRED` | 역할 불일치 |
| 409 | `INVALID_PARTICIPATION_FORM_STATUS` | 현재 상태에서 저장·제출 불가 |
| 409 | `APPROVED_PARTICIPATION_FIELDS_LOCKED` | 승인 후 잠긴 신청 필드 변경 |
| 409 | `PARTICIPATION_FORM_VERSION_CONFLICT` | expected version 불일치 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 비관적 잠금·낙관적 버전 등 동시 변경 충돌 |

## Postman 예시

```bash
# 통합 조회
curl "{{baseUrl}}/api/urban-farmers/me/participation-forms/2026" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}"

# 임시 저장 또는 제출 대기 중 수정
curl -X PUT "{{baseUrl}}/api/urban-farmers/me/participation-forms/2026" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"agriculturalBusinessRegistered":false,"experienceCount":3,"experienceNotes":"감자 수확 경험","preferredRegions":["CHEONGJU"],"availableDays":["MONDAY"],"availableWorkTypes":["수확"],"preferredStartDate":"2026-09-01","preferredEndDate":"2026-10-31","canTravel":true,"workPreferenceNotes":null,"applicationNote":null,"expectedApplicationVersion":null,"expectedProfileVersion":null,"expectedWorkPreferenceVersion":null}'

# 최초 제출 또는 반려 후 재제출
curl -X POST "{{baseUrl}}/api/urban-farmers/me/participation-forms/2026/submit" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"agriculturalBusinessRegistered":false,"experienceCount":3,"experienceNotes":"감자 수확 경험","preferredRegions":["CHEONGJU"],"availableDays":["MONDAY"],"availableWorkTypes":["수확"],"preferredStartDate":"2026-09-01","preferredEndDate":"2026-10-31","canTravel":true,"workPreferenceNotes":null,"applicationNote":null,"expectedApplicationVersion":0,"expectedProfileVersion":0,"expectedWorkPreferenceVersion":0}'
```

마지막 제출 예시의 version 값은 앞선 조회·저장 응답의 실제 값으로 교체해야 한다. 세 리소스가 모두 없으면 version 필드를 `null`로 보낸다.

## 사용자·중개센터 역할 경계

- 도시농부 사용자 API는 통합 저장과 `SUBMITTED` 전환까지 처리한다.
- `SUBMITTED → APPROVED/REJECTED` 심사는 현재 통합된 `CENTER_ADMIN` 중개센터 API가 처리한다.
- 승인·반려는 동일한 `ParticipationApplication` ID와 version을 기준으로 처리해야 한다.
