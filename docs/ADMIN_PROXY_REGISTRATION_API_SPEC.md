# CityFarmerPlus 관리자 대리 접수 API 명세서

- 문서 버전: 2.0
- 작성일: 2026-08-27
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 문서 11장(대리 접수) 전체 — 도시농부 계정·프로필·희망 근무 조건·사업참여 신청(등록·제출), 농가 계정·프로필·소유 증빙 제출, 모집 공고 초안

## 1. 공통 사항

### 1.1 기본 URL

```text
운영: https://cityfarmerplus-api-82951616760.us-west1.run.app
로컬: http://localhost:8080
```

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{adminAccessToken}}
```

### 1.3 인증 및 권한 규칙

- `/api/admin/**`는 `SecurityConfig`에서 `hasRole("CENTER_ADMIN")`으로 URL 패턴 단위로 이미 보호된다. 이 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('CENTER_ADMIN')")`을 추가로 걸어 이중으로 확인한다.
- 서비스 레이어에서 JWT와 무관하게 **DB에 저장된 실제 역할을 다시 조회**한다(`AdminProxyUrbanFarmerService`/`AdminProxyFarmService`의 `requireCenterAdmin`, 두 서비스에 각각 존재하는 동일한 로직).
- 담당자 ID는 요청 body로 받지 않는다. 항상 JWT의 `sub`(`AuthenticatedUser.id(authentication)`)에서 추출한다.
- **새 도메인 로직을 만들지 않고 기존 셀프서비스 API가 쓰는 서비스 메서드를 그대로 호출한다.** 계정 생성은 `AuthService.signup(...)`, 도시농부 프로필은 `UrbanFarmerProfileService.create(userId, ...)`, 희망 근무 조건은 `WorkPreferenceService.upsert(userId, ...)`, 사업참여 신청은 `ParticipationApplicationService.create(userId, ...)`/`.submit(userId, applicationId)`, 농가 프로필은 `FarmProfileService.create(userId, ...)`, 교육 이수증 제출은 `EducationSubmissionService.submit(userId, ...)`, 농가 소유 증빙 제출은 `FarmOwnershipSubmissionService.submit(userId, ...)`, 공고 초안은 `FarmJobPostingService.create(userId, ..., submitForReview)`를 그대로 재사용한다. 전부 인증된 사용자 자신의 ID 대신 관리자가 지정한 `{userId}`(또는 새로 생성된 계정의 ID)를 넘긴다는 점만 다르다. 어느 서비스도 내부에서 `SecurityContextHolder`나 `Authentication`을 직접 읽지 않는다 — 대상 식별자는 전부 명시적 파라미터라서, 파일 업로드가 있는 교육 이수증·농가 소유 증빙 제출도 엉뚱한 사람 이름으로 기록될 위험이 없다.
- **감사 로그(`ProxyRegistrationLog`)는 실제 액션과 같은 트랜잭션 안에서 함께 저장된다.** 이 문서의 모든 API가 실제 처리와 로그 저장을 하나의 `@Transactional` 메서드 안에서 수행한다 — 어느 한쪽만 성공하고 다른 쪽이 실패하는 경우는 없다(둘 다 성공하거나 전부 롤백).
- 이 로그 엔티티는 도시농부·농가·교육·사업참여·공고 등 여러 도메인을 가로지르는 범용 감사 이력이라 특정 애그리거트에 속하지 않는다. 그래서 `JobPostingReview`/`WorkAssignmentCorrection`과 달리 기존 도메인 패키지가 아니라 **새 최상위 패키지 [`proxy`](../src/main/java/chungbuk/cityfarmerplus/proxy)**(`proxy.entity.ProxyRegistrationLog`)에 둔다. `admin/**` 전체에는 `@Entity` 클래스가 하나도 없다는 기존 관례를 그대로 따른 것이다 — `admin.proxy` 패키지에는 이 문서의 컨트롤러·서비스·DTO만 있다.
- 컨트롤러는 대상별로 둘로 나뉜다 — 도시농부 관련 6개 API는 `AdminProxyUrbanFarmerController`(`/api/admin/proxy/urban-farmers/**`), 농가 관련 4개 API는 `AdminProxyFarmController`(`/api/admin/proxy/farms/**`).
- **이번 라운드에는 이 로그를 조회하는 API가 없다.** DB에는 기록되지만, 관리자가 화면에서 대리 접수 이력을 확인하는 기능은 다음 라운드에서 진행한다. `ActionType`/`TargetType` 전체 값은 13장을 참고한다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`AuthException`, `DomainException` 둘 다 전역 `GlobalExceptionHandler`가 처리한다. 계정 생성 API는 `AuthService.signup`을 그대로 타므로 일반 회원가입과 동일한 오류 코드(`DUPLICATE_LOGIN_ID` 등)를 그대로 받을 수 있다. 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 도시농부 계정 대리 생성 | `POST` | `/api/admin/proxy/urban-farmers` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 도시농부 프로필 대리 등록 | `POST` | `/api/admin/proxy/urban-farmers/{userId}/profile` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 희망 근무 조건 대리 등록 | `PUT` | `/api/admin/proxy/urban-farmers/{userId}/work-preference` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 사업참여 신청 대리 등록 | `POST` | `/api/admin/proxy/urban-farmers/{userId}/participation-applications` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 사업참여 신청 대리 제출 | `POST` | `/api/admin/proxy/urban-farmers/{userId}/participation-applications/{applicationId}/submit` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 교육 이수증 대리 제출 | `POST` | `/api/admin/proxy/urban-farmers/{userId}/education-submissions` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 농가 계정 대리 생성 | `POST` | `/api/admin/proxy/farms` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 농가 프로필 대리 등록 | `POST` | `/api/admin/proxy/farms/{userId}/profile` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 농가 소유 증빙 대리 제출 | `POST` | `/api/admin/proxy/farms/{userId}/ownership-submissions` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 모집 공고 초안 대리 생성 | `POST` | `/api/admin/proxy/farms/{userId}/job-postings` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |

같은 대상(도시농부·농가)의 계정 생성과 그 외 API는 독립적으로 호출할 수 있다. 완전히 새로운 회원이면 계정 생성 → 나머지 API 순서로 호출하고, 이미 계정이 있는 기존 회원이라면 계정 생성 API 없이 나머지 API만 단독으로 호출하면 된다.

## 3. 도시농부 계정 대리 생성

담당자가 창구에서 접수한 정보로 도시농부 계정을 대신 생성한다. 도시농부 본인 회원가입(`POST /api/auth/signup`)과 완전히 같은 검증·저장 로직(`AuthService.signup`)을 타며, `userType`은 요청에서 받지 않고 서버가 항상 `URBAN_FARMER`로 고정한다(다른 역할 계정을 이 API로 만들 수 없다).

### 3.1 요청

```http
POST /api/admin/proxy/urban-farmers
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "loginId": "citizen_kim",
  "password": "Passw0rd!23",
  "name": "김도시",
  "phoneNumber": "010-2222-3333",
  "birthDate": "1985-04-12",
  "address": "충청북도 청주시 예시로 10",
  "reason": "센터 방문 상담 중 본인이 스마트폰으로 직접 가입하기 어려워 담당자가 대신 접수함"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `loginId` | String | O | 영문 소문자·숫자·밑줄 4~30자(`SignupRequest`와 동일 정규식) |
| `password` | String | O | 8~64자, UTF-8 기준 72바이트 이하 |
| `name` | String | O | 공백 불가, 50자 이하 |
| `phoneNumber` | String | X | 숫자 10~11자리(형식 검증만, 하이픈 유무 무관) |
| `birthDate` | LocalDate | X | 오늘 또는 과거 날짜 |
| `address` | String | X | 255자 이하 |
| `reason` | String | O | 공백만 입력 불가, 최대 1000자(`ProxyRegistrationLog.reason`으로 기록) |

`userType`은 요청 필드에 없다 — 서버가 항상 `URBAN_FARMER`로 채워서 `AuthService.signup`을 호출한다.

### 3.2 성공 응답

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

```json
{
  "id": 42,
  "loginId": "citizen_kim",
  "name": "김도시",
  "phoneNumber": "01022223333",
  "birthDate": "1985-04-12",
  "address": "충청북도 청주시 예시로 10",
  "userType": "URBAN_FARMER",
  "accountStatus": "ACTIVE"
}
```

일반 회원가입과 동일한 `UserResponse` 형식이다. 응답에는 감사 로그 자체가 나타나지 않는다 — 로그는 `admin`(호출한 담당자), `targetUser`(방금 생성된 이 계정), `actionType=URBAN_FARMER_ACCOUNT_CREATED`, `targetType=USER`, `targetObjectId=이 계정의 id`로 DB에만 기록된다.

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필드 형식·길이 위반, 또는 `reason` 누락(공백 포함)·1000자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `409` | `DUPLICATE_LOGIN_ID` | 이미 존재하는 `loginId`(`AuthService.signup`이 그대로 던지는 오류) |

## 4. 도시농부 프로필 대리 등록

이미 계정이 있는(방금 3장에서 막 만들었든, 예전부터 있었든) 도시농부의 프로필을 담당자가 대신 등록한다. 도시농부 본인용 `POST /api/urban-farmers/me/profile`과 완전히 같은 검증·저장 로직(`UrbanFarmerProfileService.create`)을 타며, 대상이 실제로 활성 상태인 `URBAN_FARMER` 계정인지도 그 로직이 그대로 검증한다.

### 4.1 요청

```http
POST /api/admin/proxy/urban-farmers/42/profile
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "agriculturalBusinessRegistered": false,
  "experienceCount": 0,
  "notes": "센터 방문 상담 중 담당자가 대신 등록",
  "reason": "본인이 스마트폰 입력에 어려움을 겪어 담당자가 청취한 내용을 대신 입력함"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `agriculturalBusinessRegistered` | Boolean | O | - |
| `experienceCount` | Integer | O | 0~10000 |
| `notes` | String | X | 1000자 이하 |
| `reason` | String | O | 공백만 입력 불가, 최대 1000자(`ProxyRegistrationLog.reason`으로 기록) |

### 4.2 성공 응답

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

```json
{
  "id": 30,
  "userId": 42,
  "agriculturalBusinessRegistered": false,
  "experienceCount": 0,
  "notes": "센터 방문 상담 중 담당자가 대신 등록",
  "version": 0,
  "createdAt": "2026-08-27T01:00:00Z",
  "updatedAt": "2026-08-27T01:00:00Z"
}
```

도시농부 본인용 프로필 생성과 동일한 `UrbanFarmerProfileResponse` 형식이다. 로그는 `admin`(호출한 담당자), `targetUser`(`{userId}`), `actionType=URBAN_FARMER_PROFILE_REGISTERED`, `targetType=URBAN_FARMER_PROFILE`, `targetObjectId=이 프로필의 id`로 DB에만 기록된다.

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필드 형식·범위 위반, 또는 `reason` 누락(공백 포함)·1000자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음, 또는 `{userId}`에 해당하는 회원이 없음 |
| `409` | `URBAN_FARMER_PROFILE_ALREADY_EXISTS` | 이 `{userId}`에 이미 프로필이 등록되어 있음(`UrbanFarmerProfileService.create`가 그대로 던지는 오류) |

`{userId}`가 `URBAN_FARMER`가 아니거나 탈퇴·정지 상태인 경우는 `UrbanFarmerProfileService`의 내부 접근 검증(`UserRoleAccessService.requireUrbanFarmerForUpdate`)에서 걸러진다 — 정확한 오류 코드는 해당 검증 로직을 따른다.

## 5. 희망 근무 조건 대리 등록

도시농부 본인용 `PUT /api/urban-farmers/me/work-preference`와 완전히 같은 로직(`WorkPreferenceService.upsert`)이다. 등록이 없으면 새로 만들고, 이미 있으면 그대로 덮어쓴다(멱등) — 그래서 `POST`가 아니라 `PUT`이다.

### 5.1 요청

```http
PUT /api/admin/proxy/urban-farmers/42/work-preference
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "preferredRegions": ["CHEONGJU", "CHUNGJU"],
  "availableDays": ["MON", "WED", "FRI"],
  "availableWorkTypes": ["수확", "파종"],
  "preferredStartDate": "2026-09-01",
  "preferredEndDate": "2026-11-30",
  "canTravel": true,
  "notes": "센터 방문 상담 중 담당자가 대신 등록",
  "reason": "본인이 스마트폰 입력에 어려움을 겪어 담당자가 청취한 내용을 대신 입력함"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `preferredRegions` | Enum 배열 | O | 1~11개 |
| `availableDays` | Enum 배열 | O | 1~7개 |
| `availableWorkTypes` | String 배열 | O | 1~20개, 각 50자 이하·쉼표/줄바꿈 불가 |
| `preferredStartDate`/`preferredEndDate` | LocalDate | O | 종료일이 시작일보다 빠를 수 없음 |
| `canTravel` | Boolean | O | - |
| `notes` | String | X | 1000자 이하 |
| `reason` | String | O | 공백만 입력 불가, 최대 1000자 |

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

도시농부 본인용과 동일한 `WorkPreferenceResponse` 형식이다. 로그는 `actionType=WORK_PREFERENCE_REGISTERED`, `targetType=WORK_PREFERENCE`, `targetObjectId=이 희망 근무 조건의 id`로 기록된다.

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필드 형식 위반, 종료일이 시작일보다 빠름, 또는 `reason` 누락(공백 포함)·1000자 초과 |
| `400` | `INVALID_WORK_PREFERENCE_PERIOD` | 시작일·종료일 누락, 또는 종료일이 시작일보다 빠름(서비스 레이어 재검증) |
| `400` | `WORK_PREFERENCE_PERIOD_EXPIRED` | 종료일이 이미 지난 과거 날짜 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | 관리자 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정 또는 `{userId}` 대상 계정이 정지·탈퇴 상태(둘 중 어느 쪽 문제인지는 코드만으로 구분되지 않는다) |
| `403` | `URBAN_FARMER_ROLE_REQUIRED` | `{userId}`가 `URBAN_FARMER` 계정이 아님 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID 또는 `{userId}`에 해당하는 회원이 없음 |

## 6. 사업참여 신청 대리 등록

도시농부 본인용 사업참여 신청 생성(`ParticipationApplicationService.create`)과 동일하다. 신청서는 `DRAFT` 상태로 생성되며, 실제로 심사 대상이 되려면 7장의 제출 API를 별도로 호출해야 한다(자동 제출되지 않는다).

### 6.1 요청

```http
POST /api/admin/proxy/urban-farmers/42/participation-applications
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "센터 방문 상담 중 담당자가 대신 등록",
  "reason": "본인이 온라인 신청 방법을 몰라 담당자가 대신 접수함"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `programYear` | Integer | O | 2000~2100 |
| `agriculturalBusinessRegistered` | Boolean | O | - |
| `applicationNote` | String | X | 1000자 이하 |
| `reason` | String | O | 공백만 입력 불가, 최대 1000자 |

### 6.2 성공 응답

```http
HTTP/1.1 201 Created
```

도시농부 본인용과 동일한 `ParticipationApplicationResponse`(`status=DRAFT`) 형식이다. 로그는 `actionType=PARTICIPATION_APPLICATION_CREATED`, `targetType=PARTICIPATION_APPLICATION`, `targetObjectId=이 신청서의 id`로 기록된다.

### 6.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필드 형식 위반, 또는 `reason` 누락(공백 포함)·1000자 초과 |
| `400` | `PROGRAM_YEAR_REQUIRED` | `programYear` 누락(서비스 레이어 재검증) |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | 관리자 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정 또는 `{userId}` 대상 계정이 정지·탈퇴 상태 |
| `403` | `URBAN_FARMER_ROLE_REQUIRED` | `{userId}`가 `URBAN_FARMER` 계정이 아님 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID 또는 `{userId}`에 해당하는 회원이 없음 |
| `409` | `PARTICIPATION_APPLICATION_ALREADY_EXISTS` | 이 `{userId}`·`programYear` 조합의 신청이 이미 존재함 |

## 7. 사업참여 신청 대리 제출

6장에서 만든(또는 도시농부 본인이 만들어 둔) `DRAFT` 상태 신청서를 담당자가 대신 제출 처리한다(`ParticipationApplicationService.submit`). 제출하면 `SUBMITTED` 상태가 되어 심사 목록에 노출된다.

### 7.1 요청

```http
POST /api/admin/proxy/urban-farmers/42/participation-applications/7/submit
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "reason": "본인이 온라인 제출 방법을 몰라 담당자가 대신 제출함"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `reason` | String | O | 공백만 입력 불가, 최대 1000자 |

### 7.2 성공 응답

```http
HTTP/1.1 200 OK
```

`ParticipationApplicationResponse`(`status=SUBMITTED`) 형식이다. 로그는 `actionType=PARTICIPATION_APPLICATION_SUBMITTED`, `targetType=PARTICIPATION_APPLICATION`, `targetObjectId=이 신청서의 id`로 기록된다.

### 7.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `reason` 누락(공백 포함) 또는 1000자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | 관리자 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정 또는 `{userId}` 대상 계정이 정지·탈퇴 상태 |
| `403` | `URBAN_FARMER_ROLE_REQUIRED` | `{userId}`가 `URBAN_FARMER` 계정이 아님 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID 또는 `{userId}`에 해당하는 회원이 없음 |
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | `{applicationId}`가 없거나 `{userId}` 소유가 아님 |
| `409` | `INVALID_PARTICIPATION_STATUS` | 신청서가 `DRAFT` 상태가 아님(이미 제출됐거나 심사됨) |

## 8. 교육 이수증 대리 제출

파일 업로드가 있는 API다. 도시농부 본인용 `POST /api/urban-farmers/me/education-certification/submissions`와 완전히 같은 검증·저장 로직(`EducationSubmissionService.submit`)을 그대로 탄다. 파일 저장 경로도 `education-certification/{userId}/...`로 `{userId}`(대상 도시농부) 기준이라, 관리자의 신원이 저장 데이터 어디에도 섞이지 않는다.

### 8.1 요청

```http
POST /api/admin/proxy/urban-farmers/42/education-submissions
Authorization: Bearer {{adminAccessToken}}
Content-Type: multipart/form-data
```

| Part 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `request` | JSON Part | O | 과정·이수일·이수 시간·대리 등록 사유 |
| `documents` | File Array | X | 같은 Key로 최대 5개 첨부(`FarmOwnershipSubmissionController`와 동일하게 `required=false` — 실제 개수 제약은 서비스 레이어 검증을 따른다) |

`request` JSON:

```json
{
  "courseId": 1,
  "completionDate": "2026-08-20",
  "completionHours": 8,
  "reason": "본인이 스마트폰으로 파일 첨부를 못 해 담당자가 대신 제출함"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `courseId` | Long | O | 존재하고 `active=true`인 과정 ID |
| `completionDate` | LocalDate | O | 오늘 또는 과거 날짜 |
| `completionHours` | Integer | O | 8~1000, `max(8, course.requiredHours)` 이상이어야 함(서비스 레이어 재검증) |
| `reason` | String | O | 공백만 입력 불가, 최대 1000자 |

### 8.2 성공 응답

```http
HTTP/1.1 201 Created
```

도시농부 본인용과 동일한 `EducationSubmissionResponse`(`status=PENDING_REVIEW`) 형식이다. 로그는 `actionType=EDUCATION_SUBMISSION_REGISTERED`, `targetType=EDUCATION_SUBMISSION`, `targetObjectId=이 제출의 id`로 기록된다.

### 8.3 오류 응답

`reason` 관련 검증(`400 VALIDATION_ERROR`)과 관리자 권한 오류(`401`/`403`/`404`, 5장과 동일한 코드 체계에 `USER_NOT_FOUND`가 관리자·`{userId}` 대상 모두에 적용)를 제외한 나머지 오류(파일 개수·크기·형식, 과정 유효성, 이수 시간, 중복 제출 등)는 도시농부 본인용 API와 완전히 동일한 코드를 그대로 반환한다 — 전체 목록은 [`notion/05_EDUCATION.md`](notion/05_EDUCATION.md) 12장을 참고한다.

## 9. 농가 계정 대리 생성

3장(도시농부 계정 대리 생성)과 완전히 같은 구조다. `userType`만 서버가 `FARM`으로 고정한다는 점이 다르다. 요청·응답 DTO도 3장과 같은 `ProxyAccountRequest`를 그대로 재사용한다(필드가 100% 동일해서 도시농부/농가용으로 따로 만들지 않았다).

### 9.1 요청

```http
POST /api/admin/proxy/farms
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "loginId": "farm_park",
  "password": "Passw0rd!23",
  "name": "박농부",
  "phoneNumber": "010-3333-4444",
  "birthDate": "1970-02-20",
  "address": "충청북도 충주시 예시로 20",
  "reason": "센터 방문 상담 중 본인이 스마트폰으로 직접 가입하기 어려워 담당자가 대신 접수함"
}
```

필드는 3.1절과 동일하다.

### 9.2 성공 응답

```http
HTTP/1.1 201 Created
```

`UserResponse`(`userType=FARM`) 형식이다. 로그는 `actionType=FARM_ACCOUNT_CREATED`, `targetType=USER`, `targetObjectId=이 계정의 id`로 기록된다.

### 9.3 오류 응답

3.3절과 동일한 코드 체계다(`VALIDATION_ERROR`, `UNAUTHORIZED`, `ACCESS_DENIED`, `CENTER_ADMIN_ROLE_REQUIRED`, `INACTIVE_ACCOUNT`, `USER_NOT_FOUND`, `DUPLICATE_LOGIN_ID`).

## 10. 농가 프로필 대리 등록

도시농부 프로필 대리 등록(4장)과 같은 구조로, `FarmProfileService.create`를 그대로 재사용한다.

### 10.1 요청

```http
POST /api/admin/proxy/farms/55/profile
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "farmName": "충주 사과농원",
  "representativeName": "박농부",
  "contactNumber": "010-3333-4444",
  "farmAddress": "충청북도 충주시 예시로 20",
  "cityCounty": "CHUNGJU",
  "crops": ["사과"],
  "mainActivities": "사과 재배와 수확 작업을 합니다.",
  "businessRegistrationNumber": "123-45-67890",
  "farmAreaPyeong": 1500,
  "reason": "센터 방문 상담 중 담당자가 대신 등록"
}
```

필드 제약은 [`FARM_PROFILE_API_SPEC.md`](FARM_PROFILE_API_SPEC.md)의 농가 프로필 생성 API와 동일하다(`FarmProfileCreateRequest`를 그대로 재사용).

### 10.2 성공 응답

```http
HTTP/1.1 201 Created
```

`FarmProfileResponse`(`status=DRAFT`) 형식이다. 로그는 `actionType=FARM_PROFILE_REGISTERED`, `targetType=FARM_PROFILE`, `targetObjectId=이 프로필의 id`로 기록된다.

### 10.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필드 형식·길이 위반, 또는 `reason` 누락(공백 포함)·1000자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | 관리자 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정 또는 `{userId}` 대상 계정이 정지·탈퇴 상태 |
| `403` | `FARM_ROLE_REQUIRED` | `{userId}`가 `FARM` 계정이 아님 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID 또는 `{userId}`에 해당하는 회원이 없음 |
| `409` | `FARM_PROFILE_ALREADY_EXISTS` | 이 `{userId}`에 이미 프로필이 등록되어 있음 |
| `409` | `FARM_PROFILE_DATA_CONFLICT` | 이미 등록된 농가 정보와 DB 제약 충돌 |

## 11. 농가 소유 증빙 대리 제출

파일 업로드가 있는 API다. 농가 본인용 `POST /api/farm-profiles/me/ownership-submissions`와 완전히 같은 검증·저장 로직(`FarmOwnershipSubmissionService.submit`)을 그대로 탄다. 파일 저장 경로도 `farm-ownership/{farmProfileId}/...`로 대상 농가 프로필 기준이다.

### 11.1 요청

```http
POST /api/admin/proxy/farms/55/ownership-submissions
Authorization: Bearer {{adminAccessToken}}
Content-Type: multipart/form-data
```

| Part 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `request` | JSON Part | O | 대리 등록 사유만 담는다(`{"reason": "..."}`) — 농가 본인용 API에는 없는 Part로, 이 API가 사유 기록을 위해 추가한 것이다 |
| `documents` | File Array | X | 같은 Key로 첨부(실제 1~5개 제약은 서비스 레이어 검증) |

### 11.2 성공 응답

```http
HTTP/1.1 201 Created
```

농가 본인용과 동일한 `FarmOwnershipSubmissionResponse`(`status=PENDING_REVIEW`) 형식이다. 로그는 `actionType=FARM_OWNERSHIP_SUBMISSION_REGISTERED`, `targetType=FARM_OWNERSHIP_SUBMISSION`, `targetObjectId=이 제출의 id`로 기록된다.

### 11.3 오류 응답

`reason` 관련 검증(`400 VALIDATION_ERROR`)과 관리자 권한 오류를 제외한 나머지(파일 개수·크기·형식, 제출 가능 상태 등)는 농가 본인용 API와 완전히 동일한 코드를 그대로 반환한다 — 전체 목록은 [`FARM_OWNERSHIP_SUBMISSION_API_SPEC.md`](FARM_OWNERSHIP_SUBMISSION_API_SPEC.md)를 참고한다. `FARM_ROLE_REQUIRED`(403)·`FARM_PROFILE_NOT_FOUND`(404)는 `{userId}`가 `FARM`이 아니거나 아직 프로필이 없을 때 발생한다.

## 12. 모집 공고 초안 대리 생성

농가 본인용 `POST /api/farm-profiles/me/job-postings`(정확히는 `/api/farm/job-postings`)와 완전히 같은 로직(`FarmJobPostingService.create`)이다. 문서에서 "초안"이라 표현한 대로, 기본값은 `DRAFT` 생성만 하고 심사 요청까지 자동으로 하지 않는다 — 농가 본인용 API가 이미 갖고 있던 `submitForReview` 쿼리 파라미터를 그대로 노출한다.

### 12.1 요청

```http
POST /api/admin/proxy/farms/55/job-postings?submitForReview=false
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `submitForReview` | boolean | X | `false` | `true`면 생성과 동시에 `PENDING_REVIEW`로 심사 요청까지 한다 |

```json
{
  "crop": "사과",
  "workType": "수확",
  "workDate": "2026-09-10",
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
  "reason": "센터 방문 상담 중 담당자가 대신 초안을 작성함"
}
```

필드 제약은 농가 본인용 `JobPostingUpsertRequest`와 동일하다(`ADMIN_JOB_POSTING_API_SPEC.md` 12.1절의 필드 표 참고, `reason`만 추가).

### 12.2 성공 응답

```http
HTTP/1.1 201 Created
```

농가 본인용과 동일한 `JobPostingResponse`(`status=DRAFT`, `submitForReview=true`면 `PENDING_REVIEW`) 형식이다. 로그는 `actionType=JOB_POSTING_DRAFT_CREATED`, `targetType=JOB_POSTING`, `targetObjectId=이 공고의 id`로 기록된다.

### 12.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필드 형식·길이 위반, 또는 `reason` 누락(공백 포함)·1000자 초과 |
| `400` | `PAST_WORK_DATE` | 작업 시작 일시가 현재 이전 |
| `400` | `INVALID_JOB_POSTING_DETAILS` | `endTime`이 `startTime`보다 늦지 않음 등 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | 관리자 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정 또는 `{userId}` 대상 계정이 정지·탈퇴 상태 |
| `403` | `FARM_APPROVAL_REQUIRED` | `{userId}`의 농가 프로필이 아직 `APPROVED` 상태가 아님 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID 또는 `{userId}`에 해당하는 회원이 없음 |

## 13. 대리 접수 작업 종류(`ActionType`)·대상 객체 종류(`TargetType`)

`ProxyRegistrationLog`에 기록되는 전체 값이다. 조회 API가 없어 DB를 직접 봐야 확인할 수 있다는 점을 감안해 전체 값을 여기 정리한다.

| `ActionType` | 기록하는 API |
|---|---|
| `URBAN_FARMER_ACCOUNT_CREATED` | 3장 |
| `URBAN_FARMER_PROFILE_REGISTERED` | 4장 |
| `WORK_PREFERENCE_REGISTERED` | 5장 |
| `PARTICIPATION_APPLICATION_CREATED` | 6장 |
| `PARTICIPATION_APPLICATION_SUBMITTED` | 7장 |
| `EDUCATION_SUBMISSION_REGISTERED` | 8장 |
| `FARM_ACCOUNT_CREATED` | 9장 |
| `FARM_PROFILE_REGISTERED` | 10장 |
| `FARM_OWNERSHIP_SUBMISSION_REGISTERED` | 11장 |
| `JOB_POSTING_DRAFT_CREATED` | 12장 |

| `TargetType` | 의미 |
|---|---|
| `USER` | 계정(`User`) 자체가 대상 객체 |
| `URBAN_FARMER_PROFILE` | 도시농부 프로필이 대상 객체 |
| `WORK_PREFERENCE` | 희망 근무 조건이 대상 객체 |
| `PARTICIPATION_APPLICATION` | 사업참여 신청서가 대상 객체 |
| `EDUCATION_SUBMISSION` | 교육 이수증 제출이 대상 객체 |
| `FARM_PROFILE` | 농가 프로필이 대상 객체 |
| `FARM_OWNERSHIP_SUBMISSION` | 농가 소유 증빙 제출이 대상 객체 |
| `JOB_POSTING` | 모집 공고가 대상 객체 |

`targetUser`(대상 사용자)는 모든 액션에서 항상 실제 회원(도시농부 또는 농가)이지만, `targetObjectId`(대상 객체 ID)는 액션에 따라 `targetUser`의 ID와 같을 수도(계정 생성) 다를 수도 있다(그 외 전부 — 새로 만들어진 하위 레코드의 ID).

## 14. 현재 범위 밖 또는 미구현 기능

- 프로필·희망 근무 조건 대리 **수정**(이번 라운드는 전부 신규 등록만. 희망 근무 조건은 대상에 기존 값이 있으면 그대로 덮어쓰는 멱등 등록이라 사실상 수정도 되지만, 그 외 프로필류는 이미 있으면 `409`로 거부되고 별도 대리 수정 API는 없음)
- 사업참여 신청 대리 **취소**, 교육 이수증·농가 소유 증빙 재제출
- 대리 접수 이력(`ProxyRegistrationLog`) 조회 API — DB에는 기록되지만 화면에서 확인하는 기능은 다음 라운드 예정
- 계정 생성 시 임시 비밀번호 자동 발급·안내(현재는 담당자가 비밀번호를 직접 입력해서 전달해야 함)
- 모집 공고 초안 이후 단계(수정·심사 요청·마감·취소)의 대리 처리 — 초안 생성까지만 이 문서 범위(필요하면 농가 본인용 API 또는 관리자용 `ADMIN_JOB_POSTING_API_SPEC.md`의 수정/마감/취소 API를 별도로 사용해야 한다)
