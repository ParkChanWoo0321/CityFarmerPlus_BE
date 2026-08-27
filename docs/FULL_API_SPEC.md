# CityFarmerPlus 전체 API 명세

- 문서 기준일: 2026-08-27
- 기준: 현재 저장소의 Controller, DTO, Security, Service 상태 검증 코드
- 현재 HTTP 작업 수: 111개 (사용자 API 67개 + 관리자 API 42개 + health 2개)
- 로컬 기본 URL: http://localhost:8080
- 운영 URL: https://cityfarmerplus-api-82951616760.us-west1.run.app
- 시간 기준: 별도 표기가 없는 업무 시간 검증은 Asia/Seoul

이 문서는 노션과 프론트엔드 연동에 그대로 사용할 수 있는 현재 백엔드 통합 계약이다. 사용자 API 67개, 관리자·내부 발급 API 42개, health 2개를 모두 포함한다. 기능별 상세 예시와 운영 설명은 `API_SPEC_INDEX.md`가 연결하는 개별 문서를 함께 참고하되, 실제 HTTP method/path의 정본은 이 문서다. 구현 예정 기능은 현재 기능처럼 적지 않는다.

---

## 1. 공통 계약

### 1.1 인증과 역할

JWT가 필요한 요청:

~~~http
Authorization: Bearer {{accessToken}}
~~~

| 역할 | 의미 |
|---|---|
| URBAN_FARMER | 공고에 지원하고 근무하는 도시농부 |
| FARM | 인력을 모집하는 농가 |
| CENTER_ADMIN | 중개센터 담당자 |

- 한 계정은 하나의 역할만 가진다.
- 일반 회원가입으로 CENTER_ADMIN을 만들 수 없다.
- CENTER_ADMIN 계정은 일반 회원가입으로 만들 수 없고, 운영 기본 비활성인 내부 발급 API로만 만든다.
- `/api/admin/**`는 CENTER_ADMIN 역할을 URL 패턴과 서비스에서 다시 확인한다.
- JWT의 sub는 회원 ID, role claim은 위 역할 값이다.
- JWT가 있어도 계정이 ACTIVE가 아니거나 토큰 역할과 DB 역할이 다르면 401 INVALID_ACCOUNT가 반환된다.
- 로그아웃은 서버 토큰 폐기가 아니라 클라이언트가 JWT를 삭제하는 stateless 방식이다.

인증 없이 호출 가능한 API:

| Method | URL |
|---|---|
| POST | /api/auth/signup |
| GET | /api/auth/check-id |
| POST | /api/auth/login |
| GET | /api/education/courses |
| GET | /api/job-postings |
| GET | /api/job-postings/{postingId} |
| GET | /api/support/faqs |
| GET | /api/market-prices/latest |
| GET | /health |
| GET | /health/live |
| POST | /api/internal/center-admins (provisioning key가 설정된 동안만) |
| OPTIONS | /** |

위 표 이외의 API는 Bearer JWT가 필요하다. 공개 공고 조회는 JWT 없이 호출할 수 있고, 유효한 JWT를 함께 보내면 현재 사용자의 지원 정보가 응답에 추가된다. 공개 API라도 잘못된 Bearer JWT를 보내면 401을 반환한다.

### 1.2 Content-Type

| 요청 유형 | Content-Type |
|---|---|
| 일반 요청 본문 | application/json |
| 교육·소유 증빙 업로드 | multipart/form-data |
| 파일 다운로드 응답 | 저장된 application/pdf, image/jpeg, image/png 등 |

multipart 요청에서 request 파트는 JSON, documents 파트는 파일 목록이다. Postman에서는 Content-Type boundary를 직접 입력하지 않고 form-data가 자동 생성하도록 한다.

### 1.3 공통 페이지 응답

page와 size를 직접 받는 API는 기본 page=0, size=20이며 size는 1~100이다. Spring Pageable API는 page, size, sort를 사용할 수 있고 기본 size는 20이다.

~~~json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
~~~

### 1.4 공통 오류

~~~json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
~~~

| HTTP | 대표 코드 | 의미 |
|---|---|---|
| 400 | VALIDATION_ERROR | DTO 또는 Query Parameter 검증 실패 |
| 400 | INVALID_REQUEST | 읽을 수 없는 JSON |
| 400 | INVALID_REQUEST_PARAMETER | Enum, 날짜 등 파라미터 형식 오류 |
| 400 | MISSING_REQUEST_PARAMETER | 필수 Query Parameter 누락 |
| 400 | MISSING_REQUEST_HEADER | 필수 요청 Header 누락 |
| 400 | MISSING_MULTIPART_PART | 필수 multipart 파트 누락 |
| 400 | INVALID_ARGUMENT | 처리 중 발견한 잘못된 요청 값 |
| 401 | UNAUTHORIZED | JWT 누락, 만료 또는 위조 |
| 401 | INVALID_ACCOUNT | 탈퇴·정지 계정 또는 토큰 역할 불일치 |
| 403 | ACCESS_DENIED | 역할 권한 부족 |
| 404 | 도메인별 NOT_FOUND | 대상 데이터가 없음 |
| 404 | RESOURCE_NOT_FOUND | 매핑되지 않은 URL |
| 409 | DATA_CONFLICT | 중복 데이터 또는 DB 무결성 충돌 |
| 409 | CONCURRENT_UPDATE_CONFLICT | 다른 요청이 먼저 상태를 변경함 |
| 409 | INVALID_STATE | 현재 상태에서 수행할 수 없는 요청 |
| 413 | UPLOAD_REQUEST_TOO_LARGE | 서버 multipart 수신 한도 초과 |
| 415 | UNSUPPORTED_MEDIA_TYPE | 요청 Content-Type 오류 |
| 500 | INTERNAL_SERVER_ERROR | 예상하지 못한 서버 오류. 내부 상세는 응답하지 않음 |

### 1.5 공통 Enum

충북 시·군 ChungbukCityCounty:

CHEONGJU, CHUNGJU, JECHEON, BOEUN, OKCHEON, YEONGDONG, JEUNGPYEONG, JINCHEON, GOESAN, EUMSEONG, DANYANG

요일은 Java DayOfWeek 값인 MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY를 사용한다.

---

## 2. 인증·회원

### 2.1 API 목록

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 회원가입 | POST | /api/auth/signup | 공개 | JSON SignupRequest | 201 TokenResponse |
| 아이디 중복 확인 | GET | /api/auth/check-id?loginId= | 공개 | Query | 200 LoginIdAvailabilityResponse |
| 로그인 | POST | /api/auth/login | 공개 | JSON LoginRequest | 200 TokenResponse |
| 내 회원 정보 | GET | /api/auth/me | 활성 계정 | 없음 | 200 UserResponse |
| 내 정보 수정 | PATCH | /api/auth/me | 활성 계정 | JSON UserProfileUpdateRequest | 200 UserResponse |
| 회원 탈퇴 | POST | /api/auth/withdrawal | 활성 계정 | JSON AccountWithdrawalRequest | 204 |
| 로그아웃 | POST | /api/auth/logout | 활성 계정 | 없음 | 204 |

### 2.2 요청·응답 필드

SignupRequest:

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| loginId | string | O | 영문 소문자·숫자·밑줄 4~30자 |
| password | string | O | 8~64자, UTF-8 72바이트 이하 |
| name | string | O | 50자 이하 |
| userType | enum | O | URBAN_FARMER 또는 FARM. CENTER_ADMIN 가입은 거절 |
| phoneNumber | string | X | 숫자 10~11자리, 하이픈 허용 |
| birthDate | date | X | 미래일 불가 |
| address | string | X | 255자 이하 |

LoginRequest는 loginId, password를 받는다.

UserProfileUpdateRequest는 부분 수정 요청이다.

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| name | string | X | 공백만 입력 불가, 50자 이하 |
| phoneNumber | string | X | 숫자 10~11자리, 하이픈 허용 |
| birthDate | date | X | 미래일 불가 |
| address | string | X | 255자 이하 |

UserResponse:

id, loginId, name, phoneNumber, birthDate, address, userType, accountStatus

TokenResponse:

accessToken, tokenType=Bearer, expiresInSeconds, user

회원가입과 로그인은 같은 `TokenResponse` 본문을 반환한다. 회원가입은 `201 Created`, 로그인은 `200 OK`이며, 회원가입 성공 직후 발급된 JWT를 별도 로그인 없이 보호 API에 사용할 수 있다.

회원가입 성공 응답 예시:

~~~json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": 1,
    "loginId": "farm_user",
    "name": "농가 사용자",
    "phoneNumber": "01012345678",
    "birthDate": "1985-03-12",
    "address": "충청북도 충주시",
    "userType": "FARM",
    "accountStatus": "ACTIVE"
  }
}
~~~

회원 상태는 ACTIVE, SUSPENDED, WITHDRAWN이다. 공개 회원가입 직후에는 ACTIVE다. 탈퇴는 ACTIVE 계정에서 현재 비밀번호를 다시 확인한 뒤 처리한다. 같은 DB 트랜잭션에 교육·농가 소유 증빙 파일 삭제 작업을 기록하고 계정을 WITHDRAWN으로 변경하며, 커밋 직후 삭제가 실패해도 백그라운드 작업이 재시도한다.

탈퇴 업무 규칙:

- 도시농부에게 MATCHED 지원이 있으면 탈퇴할 수 없다.
- 탈퇴 가능한 도시농부의 APPLIED 지원은 WITHDRAWN으로 정리된다.
- 도시농부의 사업참여 신청 중 DRAFT, SUBMITTED, REJECTED는 CANCELLED로 정리한다. APPROVED 신청과 기존 심사자·심사 시각·반려 사유는 이력으로 유지한다.
- 교육 제출 상태 enum은 변경하지 않는다. 담당자용 상태별 목록·건수·심사 잠금 쿼리는 ACTIVE 계정의 제출만 반환하므로 탈퇴·정지 계정의 PENDING_REVIEW 제출은 심사 대상에서 제외된다.
- 농가에 MATCHED 지원이 연결된 활성 공고가 있으면 탈퇴할 수 없다.
- 탈퇴 가능한 농가의 미종결 공고는 CANCELLED, 남은 APPLIED 지원은 POSTING_CANCELLED, 농가 프로필은 INACTIVE가 된다.

CENTER_ADMIN은 공개 회원가입에서 거절된다. 내부 발급 API는 `ADMIN_PROVISIONING_KEY`가 비어 있으면 비활성화되고, 활성화 시 `X-Admin-Provisioning-Key`가 필요하다.

---

## 3. 도시농부 프로필과 희망 근무 조건

### 3.1 도시농부 프로필 API

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 프로필 생성 | POST | /api/urban-farmers/me/profile | URBAN_FARMER | 201 |
| 내 프로필 조회 | GET | /api/urban-farmers/me/profile | URBAN_FARMER | 200 |
| 내 프로필 수정 | PATCH | /api/urban-farmers/me/profile | URBAN_FARMER | 200 |

요청 JSON:

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| agriculturalBusinessRegistered | boolean | O | 농업경영체 등록 여부 |
| experienceCount | integer | O | 0~10000 |
| notes | string | X | 1000자 이하 |

응답 필드:

id, userId, agriculturalBusinessRegistered, experienceCount, notes, version, createdAt, updatedAt

### 3.2 희망 근무 조건 API

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 조회 | GET | /api/urban-farmers/me/work-preference | URBAN_FARMER | 200 |
| 등록 또는 전체 갱신 | PUT | /api/urban-farmers/me/work-preference | URBAN_FARMER | 200 |
| 삭제 | DELETE | /api/urban-farmers/me/work-preference | URBAN_FARMER | 204 |

요청 JSON:

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| preferredRegions | ChungbukCityCounty[] | O | 1~11개 |
| availableDays | DayOfWeek[] | O | 1~7개 |
| availableWorkTypes | string[] | O | 1~20개, 항목당 50자 이하, 값 내부 쉼표·줄바꿈 금지 |
| preferredStartDate | LocalDate | O | 희망 근무 시작일, yyyy-MM-dd |
| preferredEndDate | LocalDate | O | 희망 근무 종료일, 시작일과 같거나 이후이며 현재 날짜보다 과거 불가 |
| canTravel | boolean | O | 이동 가능 여부 |
| notes | string | X | 1000자 이하 |

응답에는 위 필드와 id, urbanFarmerId, version, createdAt, updatedAt이 포함된다. 기존 DB 행은 백필 전까지 두 날짜가 null일 수 있으나 신규 등록·수정 요청에서는 모두 필수다.

사업참여 신청과 희망 근무 조건은 서로 다른 데이터다. 공고 지원 자격은 사업참여 승인 여부가 아니라 필수 교육 인증으로 판단한다.

---

## 4. 도시농부 사업참여 신청

### 4.1 사용자 API

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 초안 생성 | POST | /api/urban-farmers/me/participation-applications | URBAN_FARMER | JSON | 201 |
| 내 신청 목록 | GET | /api/urban-farmers/me/participation-applications | URBAN_FARMER | 없음 | 200 List |
| 내 신청 상세 | GET | /api/urban-farmers/me/participation-applications/{applicationId} | URBAN_FARMER | 없음 | 200 |
| 수정 | PATCH | /api/urban-farmers/me/participation-applications/{applicationId} | URBAN_FARMER | JSON | 200 |
| 초안 삭제 | DELETE | /api/urban-farmers/me/participation-applications/{applicationId} | URBAN_FARMER | 없음 | 204 |
| 심사 제출 | POST | /api/urban-farmers/me/participation-applications/{applicationId}/submit | URBAN_FARMER | 없음 | 200 |
| 신청 취소 | POST | /api/urban-farmers/me/participation-applications/{applicationId}/cancel | URBAN_FARMER | 없음 | 200 |

생성 요청:

programYear(2000~2100), agriculturalBusinessRegistered(boolean), applicationNote(선택, 1000자 이하)

수정 요청:

agriculturalBusinessRegistered, applicationNote

같은 사용자와 사업연도 조합은 하나만 생성할 수 있다.

응답 핵심 필드:

id, urbanFarmerId, urbanFarmerName, programYear, agriculturalBusinessRegistered, applicationNote, status, reviewedByUserId, rejectionReason, submittedAt, reviewedAt, cancelledAt, version, createdAt, updatedAt

상태 전이:

~~~text
생성 → DRAFT
DRAFT → SUBMITTED
SUBMITTED → APPROVED
SUBMITTED → REJECTED
REJECTED 수정 → DRAFT
DRAFT만 물리 삭제 가능
이미 CANCELLED가 아닌 신청 → CANCELLED
~~~

`SUBMITTED → APPROVED/REJECTED` 전이는 관리자 사업참여 심사 API가 처리한다. 상세 계약은 `ADMIN_PARTICIPATION_APPLICATION_API_SPEC.md`를 따른다.

### 4.2 디자인용 통합 신청 폼 API

통합 신청 폼은 프로필·희망 근무 조건·사업참여 신청을 디자인의 한 화면에서 조회하고 한 트랜잭션으로 저장하기 위한 API다.

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 통합 조회 | GET | /api/urban-farmers/me/participation-forms/{programYear} | URBAN_FARMER | 없음 | 200 |
| 통합 저장 | PUT | /api/urban-farmers/me/participation-forms/{programYear} | URBAN_FARMER | ParticipationFormRequest | 200 |
| 통합 제출·재제출 | POST | /api/urban-farmers/me/participation-forms/{programYear}/submit | URBAN_FARMER | ParticipationFormRequest | 200 |

요청 필드:

agriculturalBusinessRegistered, experienceCount, experienceNotes, preferredRegions, availableDays, availableWorkTypes, preferredStartDate, preferredEndDate, canTravel, workPreferenceNotes, applicationNote, expectedApplicationVersion, expectedProfileVersion, expectedWorkPreferenceVersion

세 expected version은 선택 필드다. 직전 통합 조회의 version과 현재 DB version이 다르면 `PARTICIPATION_FORM_VERSION_CONFLICT` 409를 반환한다. 저장·제출은 사용자, 신청, 프로필, 희망 조건을 고정된 순서로 잠그고 하나의 트랜잭션에서 처리한다.

응답 핵심 필드:

programYear, status, nextAction, editableFields, applicationId, applicationVersion, agriculturalBusinessRegistered, applicationNote, rejectionReason, reviewedByUserId, submittedAt, reviewedAt, cancelledAt, profileId, profileVersion, experienceCount, experienceNotes, workPreferenceId, workPreferenceVersion, preferredRegions, availableDays, availableWorkTypes, preferredStartDate, preferredEndDate, canTravel, workPreferenceNotes

상태 규칙:

- 신청이 없으면 조회는 404가 아니라 `NOT_STARTED`를 반환한다.
- `PUT`: 새 신청은 `DRAFT`, `SUBMITTED`는 심사 대기를 유지한 채 수정, `REJECTED`는 반려 상태를 유지한 채 수정한다.
- 제출 API: 새 신청·`DRAFT`는 `SUBMITTED`, `REJECTED`는 심사 정보를 초기화하고 `SUBMITTED`로 재제출한다.
- `APPROVED`에서 `PUT`은 경험·희망 조건만 수정할 수 있다. 농업경영체 등록 여부와 신청 특이사항은 잠긴다.
- `CANCELLED`는 통합 폼으로 수정하거나 재제출할 수 없다.

이 API가 추가되어도 3장의 개별 계약은 독립 API로 유지된다. 프로필 3개, 희망 근무 조건 3개, 사업참여 신청 7개를 삭제하거나 통합 폼으로 대체하지 않는다.

---

## 5. 교육 과정과 교육 인증

### 5.1 교육 과정 API

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 활성 과정 목록 | GET | /api/education/courses | 공개 | 없음 | 200 List |

EducationCourse 필드:

| 필드 | 규칙 |
|---|---|
| title | 필수, 150자 이하 |
| description | 필수, 2000자 이하 |
| requiredHours | 1~1000 |
| externalApplicationUrl | 선택, http 또는 https, 500자 이하 |
| mandatory | 필수 과정 여부 |
| displayOrder | 0~10000 |

응답에는 id, active, version, createdAt, updatedAt도 포함된다. 과정 생성·수정·비활성화는 `ADMIN_EDUCATION_COURSE_API_SPEC.md`의 관리자 API가 처리한다.

### 5.2 도시농부 교육 인증 API

| 기능 | Method | URL | 권한 | Content-Type | 성공 |
|---|---|---|---|---|---|
| 전체 인증 진행률 | GET | /api/urban-farmers/me/education-certification | URBAN_FARMER | 없음 | 200 |
| 제출 이력 | GET | /api/urban-farmers/me/education-certification/submissions | URBAN_FARMER | 없음 | 200 List |
| 이수증 제출 | POST | /api/urban-farmers/me/education-certification/submissions | URBAN_FARMER | multipart/form-data | 201 |
| 제출 상세 | GET | /api/urban-farmers/me/education-certification/submissions/{submissionId} | URBAN_FARMER | 없음 | 200 |
| 내 파일 다운로드 | GET | /api/urban-farmers/me/education-certification/submissions/{submissionId}/documents/{documentId} | URBAN_FARMER | 없음 | 200 binary |

multipart 파트:

- request: EducationSubmissionRequest JSON
- documents: 파일 1~5개

EducationSubmissionRequest:

courseId, completionDate(미래일 불가), completionHours(8~1000)

실제 최소 이수 시간은 max(8, 선택 과정 requiredHours)다. 같은 과정은 최신 제출이 없거나 REJECTED일 때만 재제출할 수 있다.

EducationSubmissionResponse:

id, certificationId, urbanFarmerId, urbanFarmerName, courseId, courseTitle, attemptNumber, completionDate, completionHours, status, reviewedByUserId, reviewedAt, recognizedHours, rejectionReason, documents, version, submittedAt

교육 document 항목:

id, displayOrder, originalFilename, contentType, sizeBytes, sha256, createdAt

EducationCertificationResponse 핵심:

| 필드 | 의미 |
|---|---|
| status | NOT_SUBMITTED, PENDING_REVIEW, PARTIALLY_APPROVED, APPROVED, REJECTED |
| eligibleToApply | 공고 지원 가능 여부 |
| requiredCourseCount | 현재 활성 필수 과정 수 |
| approvedRequiredCourseCount | 승인 완료한 필수 과정 수 |
| courses | 활성 과정별 최신 제출 상태와 반려 사유 |
| recognizedHours | 승인된 필수 과정 인정시간 합계 |

활성 필수 과정이 한 개 이상 존재하고 모든 활성 필수 과정의 최신 제출이 승인되어야 eligibleToApply=true다. 인정시간은 과정 필수 시간 이상이고 제출한 completionHours를 초과할 수 없다.

제출 상태:

~~~text
제출 → PENDING_REVIEW
PENDING_REVIEW → APPROVED
PENDING_REVIEW → REJECTED
REJECTED → 새 attempt로 재제출 가능
~~~

과거 제출과 파일 메타데이터는 새 회차와 분리되어 보존된다.

APPROVED, REJECTED와 reviewedByUserId, reviewedAt, recognizedHours, rejectionReason은 관리자 심사 API가 관리한다. 관리자 목록·상세·승인·반려·제출 범위 증빙 다운로드 계약은 `ADMIN_EDUCATION_SUBMISSION_API_SPEC.md`를 따른다.

---

## 6. 농가 프로필과 소유 증빙

### 6.1 농가 프로필 API

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 프로필 생성 | POST | /api/farm-profiles | FARM | JSON | 201 |
| 내 프로필 조회 | GET | /api/farm-profiles/me | FARM | 없음 | 200 |
| 내 프로필 수정 | PATCH | /api/farm-profiles/me | FARM | JSON 전체 필드 | 200 |

FarmProfile 요청 필드:

| 필드 | 규칙 |
|---|---|
| farmName | 필수, 100자 이하 |
| representativeName | 필수, 50자 이하 |
| contactNumber | 국내 전화 형식, 하이픈 허용 |
| farmAddress | 필수, 255자 이하 |
| cityCounty | ChungbukCityCounty |
| crops | 1~20개, 항목 50자 이하 |
| mainActivities | 필수, 2000자 이하 |
| businessRegistrationNumber | 선택, 10자리 사업자 번호 형식 |
| farmAreaPyeong | 필수, 1~100000000평 |

응답에는 status, reviewerId, reviewerName, reviewedAt, rejectionReason, createdAt, updatedAt이 추가된다.

수정 규칙:

- PENDING_REVIEW 또는 INACTIVE 프로필은 수정할 수 없다.
- APPROVED 상태에서 농가명, 대표자명, 농가 주소, 시·군, 사업자 번호, 농지 면적 중 하나가 변경되면 DRAFT로 돌아가고 소유 증빙 재심사가 필요하다.
- 위 소유 핵심정보를 바꾸려는 시점에 CANCELLED 또는 WORK_COMPLETED가 아닌 공고가 하나라도 있으면 수정 자체가 거절된다.
- 연락처, 작물, 주요 활동만 변경하면 APPROVED 상태를 유지한다.

### 6.2 농가 소유 증빙 API

| 기능 | Method | URL | 권한 | Content-Type | 성공 |
|---|---|---|---|---|---|
| 내 제출 이력 | GET | /api/farm-profiles/me/ownership-submissions | FARM | 없음 | 200 List |
| 내 제출 상세 | GET | /api/farm-profiles/me/ownership-submissions/{submissionId} | FARM | 없음 | 200 |
| 소유 증빙 제출 | POST | /api/farm-profiles/me/ownership-submissions | FARM | multipart/form-data | 201 |
| 문서 다운로드 | GET | /api/farm-ownership-documents/{documentId}/file | FARM 소유자 | 없음 | 200 binary |

제출 요청은 documents 파일 파트만 사용한다. DRAFT 또는 REJECTED 농가 프로필만 제출할 수 있으며 제출 즉시 프로필과 제출 건이 PENDING_REVIEW가 된다.

FarmOwnershipSubmissionResponse:

id, attemptNumber, status, farmProfileStatus, submittedAt, reviewerId, reviewerName, reviewedAt, rejectionReason, documents, farmNameSnapshot, representativeNameSnapshot, farmAddressSnapshot, cityCountySnapshot, businessRegistrationNumberSnapshot, farmAreaPyeongSnapshot

각 회차에는 제출 당시 소유 식별 정보가 스냅샷으로 저장된다. FARM은 본인 농가 문서만 다운로드할 수 있다.

상태 전이:

~~~text
프로필 생성 → DRAFT
DRAFT 또는 REJECTED에서 증빙 제출 → PENDING_REVIEW
PENDING_REVIEW → APPROVED
PENDING_REVIEW → REJECTED
REJECTED → 새 attempt로 재제출 가능
APPROVED에서 소유 핵심정보 변경 → DRAFT
~~~

APPROVED, REJECTED와 reviewerId, reviewerName, reviewedAt, rejectionReason은 관리자 심사 API가 관리한다. 관리자 목록·상세·승인·반려·프로필 범위 증빙 다운로드 계약은 `ADMIN_FARM_OWNERSHIP_API_SPEC.md`를 따른다. 회원 탈퇴 시 농가 프로필은 INACTIVE가 되어 PENDING_REVIEW 목록에서 제외된다.

---

## 7. 파일 업로드 공통 제한

교육 이수증과 농가 소유 증빙에 동일하게 적용한다.

| 항목 | 제한 |
|---|---|
| 허용 확장자 | pdf, jpg, jpeg, png |
| 정규화 MIME | application/pdf, image/jpeg, image/png |
| 파일 수 | 요청당 1~5개 |
| 파일당 크기 | 최대 10 MiB |
| 파일 합계 | 최대 30 MiB |
| Spring 파일 수신 한도 | 파일당 12MB |
| Spring 요청 수신 한도 | 요청당 31MB |

확장자와 선언 MIME만 보지 않고 실제 파일 시그니처와 읽은 바이트 수를 검증한다. 다운로드 응답은 Content-Disposition: attachment와 UTF-8 파일명을 사용한다. 회원 탈퇴 시 해당 회원의 교육 또는 농가 소유 증빙 저장 파일은 삭제 작업으로 등록되며, 실패 작업은 기본 60초 주기로 재시도한다. 탈퇴한 소유자의 문서는 물리 삭제 재시도 중에도 API로 다운로드할 수 없다.

---

## 8. AI 공고 초안과 모집 공고

### 8.1 AI 공고 미리보기

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 공고 문구 생성 | POST | /api/ai/job-posting-previews | 승인된 FARM | JSON | 200 |

요청:

crop(50자), workType(100자), workDate(오늘 이후), startTime, endTime, capacity(1~1000), meetingPlace(255자), supplies(선택 1000자), precautions(선택 2000자)

응답:

title, description, supplies, precautions, beginnerGuide, generator

현재 generator는 RULE_BASED_V1이다. 외부 생성형 AI API를 호출하지 않으며 생성 결과는 자동 저장·자동 게시되지 않는다.

### 8.2 공고 공통 입력

JobPostingUpsertRequest:

| 필드 | 규칙 |
|---|---|
| crop | 필수, 50자 이하 |
| workType | 필수, 100자 이하 |
| workDate | 오늘 또는 미래 |
| startTime, endTime | 종료가 시작보다 늦어야 함 |
| capacity | 1~1000 |
| meetingPlace | 필수, 255자 이하 |
| wageAmount | 1~100000000 |
| wageUnit | HOURLY 또는 DAILY |
| supplies | 선택, 1000자 이하 |
| precautions | 선택, 2000자 이하 |
| farmMessage | 선택, 1000자 이하 |
| applicantPreference | 선택, 1000자 이하 |
| title | 필수, 150자 이하 |
| description | 필수, 5000자 이하 |
| beginnerGuide | 선택, 2000자 이하 |

### 8.3 농가 공고 API

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 초안 생성/즉시 심사 요청 | POST | /api/farm/job-postings | 승인된 FARM | submitForReview, JobPostingUpsertRequest | 201 |
| 내 공고 목록 | GET | /api/farm/job-postings | FARM | displayStatus, page, size | 200 Page |
| 내 공고 상세 | GET | /api/farm/job-postings/{postingId} | FARM | 없음 | 200 |
| 심사 이력 | GET | /api/farm/job-postings/{postingId}/review-history | FARM 소유자 | 없음 | 200 List |
| 초안 수정 | PATCH | /api/farm/job-postings/{postingId} | FARM 소유자 | JobPostingUpsertRequest | 200 |
| 초안 삭제 | DELETE | /api/farm/job-postings/{postingId} | FARM 소유자 | 없음 | 204 |
| 심사 요청 | POST | /api/farm/job-postings/{postingId}/submit-review | FARM 소유자 | 없음 | 200 |
| 심사 철회 | POST | /api/farm/job-postings/{postingId}/withdraw-review | FARM 소유자 | 없음 | 200 |
| 희망 지원자 조건 수정 | PATCH | /api/farm/job-postings/{postingId}/applicant-preference | FARM 소유자 | applicantPreference | 200 |
| 공고 취소 | POST | /api/farm/job-postings/{postingId}/cancel | FARM 소유자 | 없음 | 200 |

- 공고 생성에는 승인된 농가 프로필이 필요하다.
- 농가는 DRAFT 공고 본문만 수정·삭제할 수 있다. 반려 결과가 연결된 DRAFT를 삭제하면 FK 정합성을 위해 해당 공고의 심사 이력을 먼저 함께 삭제한 뒤 공고를 삭제한다.
- applicantPreference는 작업 시작 전 OPEN 상태에서만 수정할 수 있다.
- MATCHED 또는 WORK_COMPLETED 지원자가 있으면 농가는 공고를 취소할 수 없다.
- 지원자만 있는 OPEN 공고를 농가가 취소하면 남은 APPLIED 지원은 POSTING_CANCELLED가 된다.

농가용 JobPostingResponse는 공통 입력 필드와 함께 farmProfileId, farmName, cityCounty, farmAddress, contactNumber, status, displayStatus, reviewRequestedAt, approvedAt, closedAt, cancelledAt, createdAt, updatedAt, latestReviewAction, latestReviewReason, latestReviewedAt을 반환한다. DB 상태가 아직 OPEN이어도 작업 시작 시각이 지났다면 화면용 displayStatus는 CLOSED다.

PENDING_REVIEW 이후 승인·반려·마감·수정·취소와 `JobPostingReview` 이력은 `ADMIN_JOB_POSTING_API_SPEC.md`의 관리자 API가 처리한다.

### 8.4 공고 조회

공개 공고 API는 Bearer JWT 없이 호출할 수 있다. 유효한 JWT를 선택적으로 보내면 현재 사용자의 지원 이력이 응답에 추가된다.

| 기능 | Method | URL | 권한 | Query | 성공 |
|---|---|---|---|---|---|
| 모집 중·마감 목록 | GET | /api/job-postings | 공개, JWT 선택 | keyword, region, crop, dateFrom, dateTo, workType, recruitmentStatus, page, size | 200 Page |
| 공개 공고 상세 | GET | /api/job-postings/{postingId} | 공개, JWT 선택 | includeClosed | 200 |

PublicJobPostingResponse:

id, farmProfileId, farmName, cityCounty, crop, workType, workDate, startTime, endTime, capacity, meetingPlace, wageAmount, wageUnit, supplies, precautions, farmMessage, applicantPreference, title, description, beginnerGuide, approvedAt, recruitmentStatus, acceptingApplications, myApplication

익명 요청의 `myApplication`은 `null`이다. 유효한 JWT를 보낸 요청은 해당 사용자의 지원 이력이 있을 때 `applicationId`와 현재 지원 `status`를 반환한다.

`recruitmentStatus` 쿼리는 `OPEN`, `CLOSED`, `ALL`이며 기본값은 `OPEN`이다. `CLOSED`에는 DB 상태가 `CLOSED`·`WORK_COMPLETED`인 공고와 작업 시작 시각이 지난 `OPEN` 공고가 포함된다. `ALL`은 모집 중 공고를 먼저 정렬한다. `crop`은 정확 일치 필터이며, keyword는 제목·작물·작업 종류·농가명·집결 장소·지역 enum/한국어명을 부분검색한다.

`includeClosed` 기본값은 false다. true이면 이전에 승인·공개된 `CLOSED`, `WORK_COMPLETED`, 작업 시작 시각이 지난 `OPEN` 상세도 조회할 수 있다. 초안·심사 대기·취소 공고와 미승인·비활성 농가 공고는 계속 비공개다.

`acceptingApplications`는 현재 실제 접수 가능 여부이며 교육 자격까지 판정하지 않는다. `myApplication`은 현재 인증 사용자의 해당 공고 지원 이력 요약인 applicationId와 status이며 이력이 없으면 null이다. 목록은 페이지의 공고 ID를 사용한 한 번의 배치 조회로 구성한다.

공개 응답에는 농가의 정확한 주소와 연락처가 포함되지 않는다.

공고 상태:

~~~text
생성 → DRAFT
DRAFT → PENDING_REVIEW
PENDING_REVIEW 심사 철회 → DRAFT
PENDING_REVIEW 반려 → DRAFT
PENDING_REVIEW 승인 → OPEN
OPEN 인원 충족 또는 담당자 마감 → CLOSED
마감된 공고의 모든 배정 종결 → WORK_COMPLETED
취소 가능한 상태 → CANCELLED
~~~

ReviewAction은 EDITED, APPROVED, REJECTED, CLOSED, CANCELLED이며 사유와 당시 제목·본문 스냅샷을 보관한다.

---

## 9. 공고 지원과 농가 의견

### 9.1 도시농부 지원 API

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 공고 지원 | POST | /api/job-postings/{postingId}/applications | URBAN_FARMER | 201 |
| 내 지원 목록 | GET | /api/urban-farmers/me/job-applications | URBAN_FARMER | 200 Page |
| 내 지원 상세 | GET | /api/urban-farmers/me/job-applications/{applicationId} | URBAN_FARMER | 200 |
| 지원 취소 | POST | /api/urban-farmers/me/job-applications/{applicationId}/withdraw | URBAN_FARMER | 200 |

지원 조건:

- 현재 활성 필수 교육 과정이 한 개 이상 존재해야 한다.
- 모든 활성 필수 교육 과정이 승인되어야 한다.
- 공고가 OPEN이고 작업 시작 전이어야 한다.
- 같은 공고에 중복 APPLIED 지원은 불가하다.
- WITHDRAWN 지원은 같은 행을 재사용하여 다시 지원할 수 있으며 최신 지역·요일·희망 기간·가능 작업 유형·이동 가능 여부·경험 스냅샷으로 갱신된다.
- 같은 날짜·시간대의 여러 공고에 지원하는 것은 허용한다.
- APPLIED 상태만 매칭 확정 전에 취소할 수 있다.

JobApplicationResponse:

id, jobPostingId, postingTitle, farmName, cityCounty, workDate, startTime, endTime, status, farmOpinion, farmOpinionNote, wageAmount, wageUnit, createdAt, withdrawnAt, matchedAt

### 9.2 농가 지원자 의견 API

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 지원자 목록 | GET | /api/farm/job-postings/{postingId}/applications | 승인된 FARM 소유자 | 없음 | 200 List |
| 농가 의견 등록·수정 | PATCH | /api/farm/job-postings/{postingId}/applications/{applicationId}/opinion | 승인된 FARM 소유자 | opinion, note | 200 |

FarmOpinion은 NONE, PREFERRED, NOT_PREFERRED다. note는 선택이며 1000자 이하이다. 의견은 작업 시작 전 OPEN 공고의 APPLIED 지원자에게만 남길 수 있으며 최종 매칭 확정이 아니다.

지원자 응답:

applicationId, urbanFarmerUserId, name, phoneNumber, status, farmOpinion, farmOpinionNote, preferredRegionsSnapshot, availableDaysSnapshot, preferredStartDateSnapshot, preferredEndDateSnapshot, availableWorkTypesSnapshot, canTravelSnapshot, experienceCountSnapshot, educationVerifiedAt, appliedAt

`phoneNumber`는 지원자의 현재 회원 연락처이며 가입·회원정보 입력 여부에 따라 `null`일 수 있다. 자기 공고를 소유한 승인 농가의 지원자 조회에만 포함된다.

지원 상태:

APPLIED, WITHDRAWN, MATCHED, NOT_MATCHED, POSTING_CANCELLED, NO_SHOW, WORK_COMPLETED

MATCHED, NOT_MATCHED와 confirmedBy·matchedAt 필드는 관리자 최종 매칭 API가 관리한다. 농가의 PREFERRED/NOT_PREFERRED 의견만으로 지원 상태가 확정되지는 않는다.

---

## 10. 근무 일정, 출결, 근무 완료

### 10.1 도시농부 근무 API

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 내 근무 목록 | GET | /api/urban-farmers/me/work-assignments | URBAN_FARMER | 200 Page |
| 내 근무 상세 | GET | /api/urban-farmers/me/work-assignments/{assignmentId} | URBAN_FARMER 소유자 | 200 |
| 작업 안내 | GET | /api/urban-farmers/me/work-assignments/{assignmentId}/guide | URBAN_FARMER 소유자 | 200 |

WorkAssignmentResponse:

id, jobPostingId, jobApplicationId, urbanFarmerUserId, urbanFarmerName, confirmedByUserId, confirmedByName, confirmedByContactNumber, farmName, farmAddress, farmContactNumber, crop, workType, workDate, startTime, endTime, recruitmentCapacity, meetingPlace, wageAmount, wageUnit, supplies, precautions, status, attendanceStatus, completedAt

`recruitmentCapacity`는 매칭 당시 공고 모집 인원 스냅샷이다. 새 근무 배정에는 공고 capacity가 저장되며, 필드 추가 전 생성된 기존 DB 행은 `null`일 수 있다.

작업 안내 응답:

workAssignmentId, workSummary, officialPrecautions, preparationChecklist, recommendedClothing, safetyRules, workSteps, beginnerTip, generator

현재 작업 안내 generator는 RULE_BASED_V1이다.

내 근무 목록 쿼리:

- `view=ALL|UPCOMING|PAST`, 기본 `ALL`; `page` 기본 0, `size` 기본 20
- `ALL`: 모든 상태, 작업일·시작 시간 최신 순(기존 동작 유지)
- `UPCOMING`: 서울 시간 기준 종료 전인 `SCHEDULED`, 진행 중 포함, 작업일·시작 시간 가까운 순
- `PAST`: `COMPLETED`/`NO_SHOW`/`CANCELLED` 또는 종료 지난 `SCHEDULED`, 작업일·시작 시간 최신 순
- 종료 시각과 현재 시각이 같으면 `PAST`로 분류한다.

### 10.2 농가 근무 API

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 내 농가 배정 목록 | GET | /api/farm/work-assignments | 승인된 FARM | page, size | 200 Page |
| 출결 등록·동일 값 재시도 | PUT | /api/farm/work-assignments/{assignmentId}/attendance | 승인된 FARM 소유자 | status | 200 |
| 근무 완료 확정 | POST | /api/farm/work-assignments/{assignmentId}/complete | 승인된 FARM 소유자 | 없음 | 200 |

- 출결은 작업 시작 시각 이후에만 등록할 수 있다.
- status는 PRESENT 또는 ABSENT다. NOT_RECORDED 요청은 거절된다.
- 최초 등록은 `SCHEDULED` + `NOT_RECORDED`에서만 상태를 변경한다.
- 같은 농가 소유자가 이미 등록된 것과 같은 `PRESENT`/`ABSENT`를 재시도하면 `200`과 현재 배정을 반환한다. 출결·근무·지원 상태와 기존 기록 시각·기록자는 변경하지 않는다.
- 이미 등록된 값과 다른 상태로의 변경 요청은 `INVALID_WORK_ASSIGNMENT_STATE`(409)다. 정정은 CENTER_ADMIN 전용 근무 정정 API와 이력으로 처리한다.
- ABSENT 등록 시 배정은 NO_SHOW, 지원은 NO_SHOW가 된다.
- 근무 완료는 작업 종료 시각 이후이고 PRESENT인 SCHEDULED 배정만 가능하다.

근무 상태 전이:

~~~text
매칭 확정 → SCHEDULED / NOT_RECORDED
출근 등록 → SCHEDULED / PRESENT
결근 등록 → NO_SHOW / ABSENT
출근 근무 완료 → COMPLETED / PRESENT
CENTER_ADMIN 출결 정정 계약: ABSENT → NO_SHOW
CENTER_ADMIN 출결 정정 계약: NO_SHOW를 PRESENT로 정정 → SCHEDULED
CENTER_ADMIN 공고 취소 계약 → CANCELLED
~~~

해당 공고의 SCHEDULED 배정이 모두 사라지면 CLOSED 공고는 WORK_COMPLETED가 된다. 작업이 끝날 때까지 OPEN으로 남은 공고도 농가의 마지막 근무 완료 처리에서 먼저 CLOSED로 전환한 뒤 WORK_COMPLETED가 된다. 관리자 출결 정정과 정정 이력은 `ADMIN_WORK_ASSIGNMENT_API_SPEC.md`를 따른다.

---

## 11. 홈

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 도시농부 홈 | GET | /api/urban-farmers/me/home | URBAN_FARMER | 200 |
| 농가 홈 | GET | /api/farm/me/home | FARM 프로필 보유 | 200 |

도시농부 홈:

educationStatus, latestParticipationApplicationId, latestParticipationStatus, participationProgramYear, participationSubmittedAt, workPreferenceRegistered, preferredRegions, availableDays, upcomingWork(최대 5), recentOpenPostings(최대 5)

사업참여 신청 요약은 서울 기준 현재 연도의 신청만 조회하며 다른 연도의 더 최근 신청을 대신 반환하지 않는다. `upcomingWork`는 `SCHEDULED`이면서 작업일이 미래이거나 오늘 종료 시각이 아직 지나지 않은 근무만 날짜·시작 시각 오름차순으로 반환한다.

농가 홈:

farmProfile, postingCounts(DB 공고 상태별 개수), displayPostingCounts(화면 상태별 개수이며 CLOSED에는 작업 시작 시각이 지난 OPEN 포함), recentPostings(최대 5), upcomingWork(최대 5)

농가 홈의 `upcomingWork`도 `SCHEDULED`이면서 작업일이 미래이거나 오늘 종료 시각이 아직 지나지 않은 근무만 날짜·시작 시각 오름차순으로 최대 5개 반환한다.

---

## 12. FAQ와 AI 행정 상담

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| FAQ 목록 | GET | /api/support/faqs | 공개 | 없음 | 200 List |
| 상담 메시지 전송 | POST | /api/ai/support/messages | 활성 계정 | message 1~1000자 | 200 |
| 내 상담 이력 | GET | /api/ai/support/messages | 활성 계정 | page, size | 200 Page |

FAQ 응답:

category, question, answer

상담 응답:

id, question, category, answer, officialConfirmationRequired, createdAt

현재 상담은 외부 AI가 아닌 규칙 기반 답변이다. 확정되지 않은 행정 내용은 officialConfirmationRequired=true로 공식 확인이 필요함을 표시하며 개인정보·계좌·지급 처리를 수행하지 않는다.

---

## 13. 관리자·내부 발급 API — 42개

### 13.1 인증과 집계 기준

- `/api/admin/**` 41개는 `SecurityConfig`와 각 Controller의 `@PreAuthorize("hasRole('CENTER_ADMIN')")`가 모두 `CENTER_ADMIN` JWT를 요구한다.
- `POST /api/internal/center-admins` 1개는 JWT 없이 호출되지만 `ADMIN_PROVISIONING_KEY`가 설정된 동안만 동작하고 `X-Admin-Provisioning-Key` 헤더가 일치해야 한다. 운영 기본값은 비활성화다.
- 관리자 JWT도 DB의 계정이 `ACTIVE`이고 토큰 역할과 DB 역할이 일치해야 한다. 서비스 계층은 주요 변경 작업에서 `CENTER_ADMIN` 역할을 다시 확인한다.
- 이 절의 개수는 내부 발급 1 + 대시보드 1 + 사업참여 4 + 교육 8 + 농가 소유 5 + 공고 9 + 근무 4 + 대리 접수 10 = 42다.

### 13.2 전체 method/path 목록

#### 13.2.1 담당자 발급·대시보드 — 2개

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 최초 담당자 발급 | POST | /api/internal/center-admins | 공개 + provisioning key | `X-Admin-Provisioning-Key`, `CenterAdminProvisioningRequest` | 201 `UserResponse` |
| 관리자 대시보드 | GET | /api/admin/dashboard | CENTER_ADMIN | 없음 | 200 `AdminDashboardResponse` |

#### 13.2.2 사업참여 신청 심사 — 4개

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 전체 신청 목록 | GET | /api/admin/participation-applications | CENTER_ADMIN | 없음 | 200 `List<ParticipationApplicationResponse>` |
| 신청 상세 | GET | /api/admin/participation-applications/{applicationId} | CENTER_ADMIN | 없음 | 200 `ParticipationApplicationResponse` |
| 신청 승인 | POST | /api/admin/participation-applications/{applicationId}/approve | CENTER_ADMIN | 없음 | 200 `ParticipationApplicationResponse` |
| 신청 반려 | POST | /api/admin/participation-applications/{applicationId}/reject | CENTER_ADMIN | `ParticipationRejectRequest` | 200 `ParticipationApplicationResponse` |

목록은 생성 시각 내림차순이다. `SUBMITTED` 신청만 승인·반려할 수 있고 반려 `reason`은 공백 불가, 최대 500자다.

#### 13.2.3 교육 과정·이수증 심사 — 8개

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 교육 과정 생성 | POST | /api/admin/education/courses | CENTER_ADMIN | `EducationCourseRequest` | 201 `EducationCourseResponse` |
| 교육 과정 수정 | PATCH | /api/admin/education/courses/{courseId} | CENTER_ADMIN | `EducationCourseRequest` | 200 `EducationCourseResponse` |
| 교육 과정 비활성화 | POST | /api/admin/education/courses/{courseId}/deactivate | CENTER_ADMIN | 없음 | 200 `EducationCourseResponse` |
| 심사 대기 제출 목록 | GET | /api/admin/education/submissions | CENTER_ADMIN | `page`, `size`, `sort` | 200 `PageResponse<EducationSubmissionResponse>` |
| 제출 상세 | GET | /api/admin/education/submissions/{submissionId} | CENTER_ADMIN | 없음 | 200 `EducationSubmissionResponse` |
| 제출 증빙 다운로드 | GET | /api/admin/education/submissions/{submissionId}/documents/{documentId} | CENTER_ADMIN | 없음 | 200 binary |
| 제출 승인 | POST | /api/admin/education/submissions/{submissionId}/approve | CENTER_ADMIN | `EducationApproveRequest` | 200 `EducationSubmissionResponse` |
| 제출 반려 | POST | /api/admin/education/submissions/{submissionId}/reject | CENTER_ADMIN | `EducationRejectRequest` | 200 `EducationSubmissionResponse` |

`EducationCourseRequest`는 `title` 필수·150자 이하, `description` 필수·2000자 이하, `requiredHours` 1 이상, `externalApplicationUrl` 선택·빈 문자열 또는 HTTPS·500자 이하, `mandatory`, `displayOrder` 0 이상을 받는다. 승인 요청의 `recognizedHours`는 1 이상이며 실제로는 해당 과정 필수 시간 이상이고 사용자가 제출한 `completionHours` 이하여야 한다. 반려 `reason`은 공백 불가, 최대 500자다. 승인·반려는 개별 제출과 전체 교육 진행률을 같은 트랜잭션에서 갱신한다.

#### 13.2.4 농가 소유 증빙 심사 — 5개

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 상태별 농가 목록 | GET | /api/admin/farm-profiles | CENTER_ADMIN | 필수 `status` | 200 `List<FarmProfileResponse>` |
| 최신 소유 제출 상세 | GET | /api/admin/farm-profiles/{profileId} | CENTER_ADMIN | 없음 | 200 `FarmOwnershipSubmissionResponse` |
| 소유 증빙 다운로드 | GET | /api/admin/farm-profiles/{profileId}/ownership/documents/{documentId} | CENTER_ADMIN | 없음 | 200 binary |
| 소유 증빙 승인 | POST | /api/admin/farm-profiles/{profileId}/ownership/approve | CENTER_ADMIN | 없음 | 200 `FarmOwnershipSubmissionResponse` |
| 소유 증빙 반려 | POST | /api/admin/farm-profiles/{profileId}/ownership/reject | CENTER_ADMIN | `FarmOwnershipRejectRequest` | 200 `FarmOwnershipSubmissionResponse` |

`status`는 `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `INACTIVE` 중 하나다. 최신 제출과 프로필이 모두 심사 대기인 경우에만 승인·반려하며 두 상태와 심사자·심사 시각을 같은 트랜잭션으로 갱신한다. 반려 `reason`은 공백 불가, 최대 1000자다.

#### 13.2.5 공고 심사·수정·매칭 — 9개

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 심사 대기 공고 목록 | GET | /api/admin/job-postings | CENTER_ADMIN | `page`, `size`, `sort` | 200 `PageResponse<JobPostingResponse>` |
| 공고 승인 | POST | /api/admin/job-postings/{postingId}/approve | CENTER_ADMIN | 없음 | 200 `JobPostingReviewResponse` |
| 공고 반려 | POST | /api/admin/job-postings/{postingId}/reject | CENTER_ADMIN | `JobPostingRejectRequest` | 200 `JobPostingReviewResponse` |
| 공고 수정 | PATCH | /api/admin/job-postings/{postingId} | CENTER_ADMIN | `AdminJobPostingUpdateRequest` | 200 `JobPostingResponse` |
| 공고 강제 마감 | POST | /api/admin/job-postings/{postingId}/close | CENTER_ADMIN | 없음 | 200 `JobPostingReviewResponse` |
| 공고 취소 | POST | /api/admin/job-postings/{postingId}/cancel | CENTER_ADMIN | 없음 | 200 `JobPostingReviewResponse` |
| 공고 심사 이력 | GET | /api/admin/job-postings/{postingId}/review-history | CENTER_ADMIN | 없음 | 200 `List<JobPostingReviewResponse>` |
| 지원 후보 목록 | GET | /api/admin/job-postings/{postingId}/candidates | CENTER_ADMIN | 없음 | 200 `List<JobCandidateResponse>` |
| 지원자 배치 매칭 | POST | /api/admin/job-postings/{postingId}/matches | CENTER_ADMIN | `JobPostingMatchRequest` | 200 `List<WorkAssignmentResponse>` |

승인은 `PENDING_REVIEW → OPEN`, 반려는 `PENDING_REVIEW → DRAFT`이며 반려 사실은 `JobPostingReview(action=REJECTED)`로 남는다. 반려 `reason`은 최대 1000자다. 관리자 수정은 `reason`과 8.2절의 공고 전체 필드를 받고, 이미 확정된 근무자가 있는 공고의 근무 조건은 수정할 수 없다. 마감은 남은 `APPLIED` 지원을 `NOT_MATCHED`로 바꾸고, 취소는 지원과 아직 예정인 근무도 취소한다.

`JobPostingMatchRequest.applicationIds`는 양수 ID 1~100개이며 중복을 허용하지 않는다. `OPEN` 공고의 `APPLIED` 지원만 매칭할 수 있고 정원 초과와 동일 시간대 중복 근무를 거절한다. 매칭은 지원을 `MATCHED`로 바꾸고 `WorkAssignment`를 생성한다. 정원을 채우면 공고를 `CLOSED`로 바꾸고 남은 `APPLIED` 지원은 `NOT_MATCHED`가 된다.

#### 13.2.6 근무 조회·출결 정정 — 4개

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 전체 근무 목록 | GET | /api/admin/work-assignments | CENTER_ADMIN | 선택 `status`, `page`, `size`, `sort` | 200 `PageResponse<WorkAssignmentResponse>` |
| 근무 상세 | GET | /api/admin/work-assignments/{assignmentId} | CENTER_ADMIN | 없음 | 200 `WorkAssignmentResponse` |
| 출결 정정 | POST | /api/admin/work-assignments/{assignmentId}/attendance-correction | CENTER_ADMIN | `AttendanceCorrectionRequest` | 200 `WorkAssignmentCorrectionResponse` |
| 정정 이력 | GET | /api/admin/work-assignments/{assignmentId}/correction-history | CENTER_ADMIN | 없음 | 200 `List<WorkAssignmentCorrectionResponse>` |

목록 `status`는 `SCHEDULED`, `COMPLETED`, `NO_SHOW`, `CANCELLED` 중 하나다. 정정 요청은 `status=PRESENT|ABSENT`와 공백이 아닌 `reason` 최대 1000자를 받는다. 최초 출결이 `NOT_RECORDED`인 배정은 관리자 정정 대상이 아니다. 정정으로 종결 배정이 다시 `SCHEDULED`가 되면 필요할 때 `WORK_COMPLETED` 공고도 다시 열린다.

#### 13.2.7 대리 접수 — 10개

| 기능 | Method | URL | 권한 | 요청 | 성공 |
|---|---|---|---|---|---|
| 도시농부 계정 생성 | POST | /api/admin/proxy/urban-farmers | CENTER_ADMIN | `ProxyAccountRequest` | 201 `UserResponse` |
| 도시농부 프로필 등록 | POST | /api/admin/proxy/urban-farmers/{userId}/profile | CENTER_ADMIN | `ProxyUrbanFarmerProfileRequest` | 201 `UrbanFarmerProfileResponse` |
| 희망 근무 조건 등록·수정 | PUT | /api/admin/proxy/urban-farmers/{userId}/work-preference | CENTER_ADMIN | `ProxyWorkPreferenceRequest` | 200 `WorkPreferenceResponse` |
| 사업참여 초안 생성 | POST | /api/admin/proxy/urban-farmers/{userId}/participation-applications | CENTER_ADMIN | `ProxyParticipationApplicationRequest` | 201 `ParticipationApplicationResponse` |
| 사업참여 제출 | POST | /api/admin/proxy/urban-farmers/{userId}/participation-applications/{applicationId}/submit | CENTER_ADMIN | `ProxyParticipationSubmitRequest` | 200 `ParticipationApplicationResponse` |
| 교육 이수증 대리 제출 | POST | /api/admin/proxy/urban-farmers/{userId}/education-submissions | CENTER_ADMIN | multipart `request`, `documents` | 201 `EducationSubmissionResponse` |
| 농가 계정 생성 | POST | /api/admin/proxy/farms | CENTER_ADMIN | `ProxyAccountRequest` | 201 `UserResponse` |
| 농가 프로필 등록 | POST | /api/admin/proxy/farms/{userId}/profile | CENTER_ADMIN | `ProxyFarmProfileRequest` | 201 `FarmProfileResponse` |
| 농가 소유 증빙 대리 제출 | POST | /api/admin/proxy/farms/{userId}/ownership-submissions | CENTER_ADMIN | multipart `request`, `documents` | 201 `FarmOwnershipSubmissionResponse` |
| 농가 공고 초안 생성 | POST | /api/admin/proxy/farms/{userId}/job-postings | CENTER_ADMIN | `submitForReview=false`, `ProxyJobPostingDraftRequest` | 201 `JobPostingResponse` |

모든 대리 요청은 공백이 아닌 `reason` 최대 1000자를 받고 append-only 감사 로그를 남긴다. `ProxyAccountRequest`는 일반 계정 필드와 동일하게 아이디 4~30자, 비밀번호 8~64자·UTF-8 72바이트 이하, 이름 50자 이하를 검증하며 URL에 따라 역할을 `URBAN_FARMER` 또는 `FARM`으로 고정한다. 대리 프로필·희망 조건·사업참여·공고 입력은 각 사용자 요청과 같은 필드 제한을 적용한다.

교육 대리 제출의 `request`는 `courseId`, 미래가 아닌 `completionDate`, `completionHours` 8~1000, `reason`이며 `documents`는 7절의 파일 제한을 따른다. 농가 소유 대리 제출의 `request`는 `reason`, `documents`는 같은 소유 증빙 제한을 따른다. 농가 공고는 `submitForReview=true`일 때 생성 트랜잭션 안에서 바로 심사 대기 상태로 전환한다.

### 13.3 대표 요청 DTO

| DTO | 핵심 필드·검증 |
|---|---|
| `CenterAdminProvisioningRequest` | `loginId`, `password`, `name`; 공개 회원가입과 동일한 아이디·비밀번호·이름 제한 |
| `ParticipationRejectRequest` | `reason` 필수, 500자 이하 |
| `EducationCourseRequest` | `title`, `description`, `requiredHours`, `externalApplicationUrl`, `mandatory`, `displayOrder` |
| `EducationApproveRequest` | `recognizedHours` 필수, 1 이상; 서비스에서 과정 필수시간·제출시간 범위 재검증 |
| `EducationRejectRequest` | `reason` 필수, 500자 이하 |
| `FarmOwnershipRejectRequest` | `reason` 필수, 1000자 이하 |
| `JobPostingRejectRequest` | `reason` 필수, 1000자 이하 |
| `AdminJobPostingUpdateRequest` | `reason` + 8.2절 공고 전체 입력; 작업일은 오늘 또는 미래이고 시작 시각은 현재 이후, 시간·정원·금액·본문 제한 적용 |
| `JobPostingMatchRequest` | `applicationIds` 1~100개, 각 ID 양수·null 불가·중복 불가 |
| `AttendanceCorrectionRequest` | `status` 필수, `reason` 필수·1000자 이하 |
| `Proxy*Request` | 해당 사용자 DTO 필드 + 감사용 `reason` 필수·1000자 이하 |

### 13.4 대표 응답 DTO

기존 사용자 DTO를 반환하는 관리자 API는 같은 JSON 계약을 재사용한다.

| DTO | 핵심 필드 또는 참조 |
|---|---|
| `UserResponse` | 2.2절의 회원 필드. 비밀번호는 반환하지 않음 |
| `ParticipationApplicationResponse` | 4.1절의 신청·상태·심사자·version 필드 |
| `EducationCourseResponse` | 5.1절의 과정 필드 + `id`, `active`, `version`, 생성·수정 시각 |
| `EducationSubmissionResponse` | 5.2절의 제출·과정·심사·증빙·version 필드 |
| `FarmProfileResponse` | 6.1절의 농가 정보·상태·심사 필드 |
| `FarmOwnershipSubmissionResponse` | 6.2절의 제출 회차·스냅샷·증빙·심사 필드 |
| `JobPostingResponse` | 8.2~8.3절의 공고 전체 필드·상태·최근 심사 필드 |
| `WorkAssignmentResponse` | 10.1절의 근무·공고·농가·담당자·출결 스냅샷 필드 |

관리자 고유 응답:

| DTO | 필드 |
|---|---|
| `AdminDashboardResponse` | `submittedParticipationApplications`, `pendingEducationSubmissions`, `pendingFarmOwnershipSubmissions`, `pendingJobPostings`, `openJobPostings`, `pendingJobApplications`, `scheduledWorkAssignments`, `completedWorkAssignments`, `activeUrbanFarmerCount`, `activeFarmCount`, `activeCenterAdminCount` |
| `JobPostingReviewResponse` | `id`, `reviewerUserId`, `reviewerName`, `action`, `reason`, `titleSnapshot`, `descriptionSnapshot`, `createdAt` |
| `JobCandidateResponse` | `applicationId`, `urbanFarmerUserId`, `name`, `phoneNumber`, `status`, `farmOpinion`, `farmOpinionNote`, 희망 지역·요일·기간·작업·이동 가능 여부 스냅샷, `experienceCountSnapshot`, `educationVerifiedAt`, `appliedAt` |
| `WorkAssignmentCorrectionResponse` | `id`, `workAssignmentId`, 정정 전·후 근무 상태와 출결 상태, `correctedByUserId`, `correctedByName`, `reason`, `correctedAt` |

파일 다운로드 성공 응답은 JSON이 아니라 저장된 파일 바이트이며 `Content-Type`, `Content-Length`, UTF-8 파일명의 `Content-Disposition: attachment`를 반환한다.

### 13.5 주요 관리자 오류

1.4절 공통 오류가 모든 관리자 API에 적용된다. 아래는 관리자 도메인에서 추가로 중요한 코드다.

| HTTP | 코드 | 주요 발생 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 요청 DTO 필드 제한 위반 |
| 400 | `INVALID_REQUEST_PARAMETER` | status 등 Enum·ID 형식 오류 |
| 400 | `MISSING_REQUEST_HEADER` | provisioning key 헤더 누락 |
| 400 | `INVALID_RECOGNIZED_HOURS` | 교육 인정 시간이 과정·제출 범위를 위반 |
| 401 | `UNAUTHORIZED` | 관리자 JWT 누락·만료·위조 |
| 401 | `INVALID_PROVISIONING_KEY` | 내부 담당자 발급 키 불일치 |
| 403 | `ACCESS_DENIED` | CENTER_ADMIN이 아닌 JWT로 `/api/admin/**` 호출 |
| 403 | `INACTIVE_ACCOUNT` | 서비스 재검증 시 담당자 계정이 비활성 |
| 403 | `CENTER_ADMIN_ROLE_REQUIRED` | 서비스 재검증 시 담당자 역할 불일치 |
| 403 | `PROVISIONING_DISABLED` | 내부 담당자 발급 기능 비활성 |
| 404 | `USER_NOT_FOUND` | 담당자 또는 대리 처리 대상 사용자 없음 |
| 404 | `PARTICIPATION_APPLICATION_NOT_FOUND` | 사업참여 신청 없음 |
| 404 | `EDUCATION_COURSE_NOT_FOUND` | 교육 과정 없음 |
| 404 | `EDUCATION_SUBMISSION_NOT_FOUND` | 교육 제출 없음 |
| 404 | `EDUCATION_DOCUMENT_NOT_FOUND` | 제출과 증빙 문서 관계가 일치하지 않음 |
| 404 | `FARM_PROFILE_NOT_FOUND` | 농가 프로필 없음 |
| 404 | `OWNERSHIP_SUBMISSION_NOT_FOUND` | 농가 소유 제출 없음 |
| 404 | `OWNERSHIP_DOCUMENT_NOT_FOUND` | 농가 소유 증빙 없음 |
| 404 | `JOB_POSTING_NOT_FOUND` | 공고 없음 |
| 404 | `JOB_APPLICATION_NOT_FOUND` | 매칭 요청 지원이 없거나 다른 공고 소속 |
| 404 | `WORK_ASSIGNMENT_NOT_FOUND` | 근무 배정 없음 |
| 409 | `DUPLICATE_LOGIN_ID` | 담당자·대리 계정 아이디 중복 |
| 409 | `INVALID_PARTICIPATION_STATUS` | `SUBMITTED`가 아닌 신청 승인·반려 |
| 409 | `INVALID_EDUCATION_SUBMISSION_STATUS` | `PENDING_REVIEW`가 아닌 교육 제출 심사 |
| 409 | `OWNERSHIP_REVIEW_NOT_ALLOWED` | 최신 심사 대기 소유 제출이 아님 |
| 409 | `INVALID_JOB_POSTING_STATE` | 현재 공고 상태에서 승인·반려·마감·취소·매칭 불가 |
| 409 | `MATCHED_POSTING_UPDATE_NOT_ALLOWED` | 확정 근무자가 있는 공고 수정 |
| 409 | `CAPACITY_BELOW_MATCHED_COUNT` | 확정 인원보다 작은 정원으로 수정 |
| 409 | `INVALID_JOB_APPLICATION_STATE` | 중복 ID 또는 `APPLIED`가 아닌 지원 매칭 |
| 409 | `JOB_POSTING_CAPACITY_EXCEEDED` | 매칭 후 정원 초과 |
| 409 | `OVERLAPPING_WORK_ASSIGNMENT` | 같은 사용자의 확정 근무 시간 중복 |
| 409 | `INVALID_WORK_ASSIGNMENT_STATE` | 최초 출결 전 정정 또는 허용하지 않는 정정 |
| 410 | `EDUCATION_DOCUMENT_FILE_UNAVAILABLE` | 교육 증빙 메타데이터는 있으나 저장 파일을 읽을 수 없음 |
| 500 | `OWNERSHIP_DOCUMENT_READ_ERROR` | 농가 증빙 메타데이터는 있으나 파일 읽기 실패 |

관리자 상세 예시와 도메인별 전체 오류 표는 `API_SPEC_INDEX.md`가 연결하는 `ADMIN_*_API_SPEC.md`를 참고한다. 이 통합본과 개별 문서가 충돌하면 현재 Controller·DTO·Security·Exception 코드와 이 통합본의 method/path를 우선한다.

---

## 14. 주요 전체 흐름과 중개센터 역할 경계

### 14.1 도시농부

~~~text
URBAN_FARMER 회원가입·로그인
→ 기본 회원 정보 수정
→ 개별 API로 프로필·희망 근무 조건·사업참여 신청을 작성하거나 통합 신청 폼으로 한 번에 저장·제출
→ 활성 필수 교육 과정별 이수증 제출
→ CENTER_ADMIN 심사로 모든 필수 교육이 APPROVED가 된 뒤
→ CENTER_ADMIN이 OPEN으로 승인한 모집 공고 조회·지원
→ 농가 선호 의견 확인 대상
→ CENTER_ADMIN 최종 매칭
→ 확정 근무·담당자 연락처·작업 안내 조회
→ 농가 출결 및 근무 완료 처리
~~~

### 14.2 농가

~~~text
FARM 회원가입·로그인
→ 농가 프로필 작성
→ 소유 증빙 제출
→ CENTER_ADMIN 심사로 APPROVED
→ AI 규칙 기반 공고 문구 미리보기
→ 공고 초안 생성·수정
→ 공고 심사 요청
→ CENTER_ADMIN이 수정·승인하여 OPEN
→ 지원자 선호 의견 등록
→ CENTER_ADMIN 최종 매칭
→ 출결 등록
→ 작업 종료 후 근무 완료 확정
~~~

### 14.3 관리자 업무

통합 백엔드는 `/api/admin/**`에서 사업참여·교육·농가 소유 증빙 심사, 교육 과정 관리, 공고 심사·최종 매칭, 근무 정정, 대리 접수와 대시보드를 제공한다. 관리자·내부 발급 API 42개의 method/path와 공통 계약은 13장이 정본이며, 기능별 상세 예시는 `API_SPEC_INDEX.md`의 관리자 전용 명세를 함께 참고한다. 내부 담당자 발급 경로는 운영 기본 비활성이고 일반 회원가입과 분리된다.

### 14.4 KAMIS 최근 조사 가격

`GET /api/market-prices/latest`는 인증 없이 KAMIS의 최근 조사 가격을 조회한다. `marketType=RETAIL|WHOLESALE`, 선택 `categoryCode=100|200|300|400|500|600`, 선택 `keyword`, `page`, `size`를 받는다. 응답에는 `provider`, `description`, `observedDate`, `fetchedAt`, `stale`, 페이지 정보와 가격 목록이 포함된다.

KAMIS 데이터는 실시간 체결가나 농가 실수취가가 아니라 최근 조사일 기준 도·소매 조사 가격이다. 서버는 성공 응답을 1시간 캐시하며 KAMIS 일시 장애 때 24시간 이내의 마지막 성공값을 `stale=true`로 반환한다. 상세 계약은 `MARKET_PRICE_API_SPEC.md`를 따른다.

---

## 15. 확정 제외 및 현재 미구현

다음은 사용자 최종 결정에 따라 현재 백엔드 범위에서 제외한다.

- 네이버·카카오 등 소셜 로그인
- 서비스 내부 알림함, 푸시, 문자, 알림톡
- 정산 기록, 지급대장, 정산 상태, 정산 CSV
- 결제, 송금, 계좌 관리
- 전체 시스템을 관리하는 SUPER_ADMIN
- 농가와 도시농부 채팅

따라서 Figma의 소셜 로그인 버튼, 공개 중개센터 역할 가입, 알림 벨·알림함, 정산 화면은 현재 API 계약에 대응하지 않는다. 공고의 wageAmount와 wageUnit은 작업 조건 표시용이며 결제나 지급 처리를 뜻하지 않는다.

현재 코드에 없는 기능:

- 지도 좌표·지도 URL·지도 공급자 연동
- 실제 LLM 공급자 호출
- JWT refresh token, 서버 토큰 블랙리스트
- 비밀번호 찾기·재설정
- 기간·지역별 고급 통계

이 항목들은 구현된 것처럼 프론트 계약에 사용하면 안 된다.

---

## 16. Postman 권장 변수

| 변수 | 의미 |
|---|---|
| baseUrl | 운영 `https://cityfarmerplus-api-82951616760.us-west1.run.app` 또는 로컬 `http://localhost:8080` |
| accessToken | 현재 로그인 JWT |
| urbanFarmerAccessToken | 도시농부 JWT |
| farmAccessToken | 농가 JWT |
| postingId | 공고 ID |
| applicationId | 지원 ID |
| assignmentId | 근무 배정 ID |
| submissionId | 교육 또는 농가 제출 ID |
| documentId | 파일 ID |

날짜는 yyyy-MM-dd, 시간은 HH:mm:ss 또는 Spring이 파싱 가능한 ISO LocalTime 형식을 사용한다.
