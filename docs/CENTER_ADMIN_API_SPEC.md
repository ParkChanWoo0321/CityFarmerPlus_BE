# CityFarmerPlus 중개센터 관리자 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-09
- 구현 기준 브랜치: `backend-2`
- 적용 범위: 관리자 계정 발급, 도시농부 목록/상세 조회, 자격 검증 승인, 교육 이수 승인, 프로필 대리 등록

## 1. 공통 사항

### 1.1 기본 URL

```text
http://localhost:8080
```

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- `POST /api/internal/center-admins`를 제외한 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{accessToken}}
```

### 1.3 인증 및 권한 규칙

- `POST /api/internal/center-admins`는 예외적으로 JWT가 필요 없다. 대신 `X-Admin-Provisioning-Key` 헤더값이 `application.properties`의 `app.admin.provisioning-key`와 일치해야 한다. `SecurityConfig`에서 이 경로만 `permitAll()`로 열려 있다.
- `/api/admin/urban-farmers/**`는 `userType`이 `CENTER_ADMIN`인 회원의 JWT만 호출할 수 있다(`SecurityConfig`의 `hasRole("CENTER_ADMIN")`).
- `/api/admin/urban-farmers/{userId}`의 `userId`는 관리자 본인이 아니라 **조회·처리 대상 도시농부의 회원 ID**다. 관리자 자신의 JWT `sub`는 사용하지 않는다.
- 서비스 레이어는 기존 `urbanfarmer` 패키지의 `UrbanFarmerProfileService`/`UrbanFarmerEducationService`/`UrbanFarmerProfileRepository`를 그대로 재사용한다. 도시농부 본인용 API와 로직을 공유하므로 오류 메시지도 동일하게 나온다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

인증(`401`)·인가(`403`) 오류는 `GlobalExceptionHandler`가 아니라 `SecurityConfig`가 직접 같은 `{ code, message }` 형식으로 응답한다. 자세한 내용은 9장을 참고한다. 다만 요청 헤더 누락, 경로 변수 타입 불일치(예: `userId`에 숫자가 아닌 값)처럼 Spring이 컨트롤러 진입 전에 걸러내는 오류는 이 형식을 따르지 않을 수 있다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 관리자 계정 발급 | `POST` | `/api/internal/center-admins` | 발급 키 헤더 | `201 Created` |
| 도시농부 목록 조회 | `GET` | `/api/admin/urban-farmers` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 도시농부 상세 조회 | `GET` | `/api/admin/urban-farmers/{userId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 자격 검증 승인 | `POST` | `/api/admin/urban-farmers/{userId}/verify-eligibility` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 교육 이수 승인 | `POST` | `/api/admin/urban-farmers/{userId}/education/approve` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 프로필 대리 등록 | `POST` | `/api/admin/urban-farmers/{userId}/profile` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

## 3. 관리자 계정 발급

담당자는 공개 회원가입으로 생성할 수 없다. 백엔드 개발자가 발급 키를 아는 상태에서만 호출할 수 있다.

### 3.1 요청

```http
POST /api/internal/center-admins
X-Admin-Provisioning-Key: {{adminProvisioningKey}}
Content-Type: application/json
```

```json
{
  "loginId": "center_admin01",
  "password": "admin-password-123",
  "name": "충북 담당자"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `loginId` | String | O | `^[a-z0-9_]{4,30}$` (영문 소문자·숫자·밑줄, 4~30자) |
| `password` | String | O | 8~64자 |
| `name` | String | O | 공백만 입력 불가, 최대 50자 |

`SignupRequest`(회원가입)와 동일한 검증 규칙이다. `userType`은 요청받지 않으며 서버가 항상 `CENTER_ADMIN`으로 생성한다(`User.registerCenterAdmin()`).

### 3.2 성공 응답

```http
HTTP/1.1 201 Created
```

```json
{
  "id": 3,
  "loginId": "center_admin01",
  "name": "충북 담당자",
  "userType": "CENTER_ADMIN",
  "accountStatus": "ACTIVE"
}
```

응답은 `auth.dto.UserResponse`를 그대로 재사용한다(비밀번호 등 민감정보 제외). 생성 응답에서는 JWT를 발급하지 않으며, 생성된 계정은 `POST /api/auth/login`으로 별도 로그인해야 한다.

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락 또는 형식·길이 위반 |
| `400` | `INVALID_REQUEST` | 잘못된 JSON |
| `401` | `INVALID_PROVISIONING_KEY` | 헤더의 키 값이 설정값과 다름 |
| `403` | `PROVISIONING_DISABLED` | `app.admin.provisioning-key`가 비어있음(발급 기능 비활성화) |
| `409` | `DUPLICATE_LOGIN_ID` | 이미 사용 중인 아이디 |

`X-Admin-Provisioning-Key` 헤더 자체가 누락되면(값이 틀린 게 아니라 헤더가 없는 경우) Spring이 컨트롤러 진입 전에 `400`을 반환하며, 이때는 `{ code, message }` 형식이 보장되지 않는다.

발급 비활성화 예시:

```json
{
  "code": "PROVISIONING_DISABLED",
  "message": "담당자 계정 발급 기능이 비활성화되어 있습니다."
}
```

## 4. 도시농부 목록 조회

### 4.1 요청

```http
GET /api/admin/urban-farmers?page=0&size=20
Authorization: Bearer {{adminAccessToken}}
```

| 쿼리 파라미터 | 타입 | 필수 | 기본값 |
|---|---|---|---|
| `page` | int | X | `0` |
| `size` | int | X | `20` |

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

컨트롤러가 Spring Data의 `Page<AdminUrbanFarmerListItemResponse>`를 그대로 반환하므로, 응답 본문은 `content` 배열과 페이지 메타데이터를 함께 담은 다음 형식이다(실제 직렬화 결과를 확인해서 작성함).

```json
{
  "content": [
    {
      "userId": 10,
      "name": "홍길동",
      "preferredRegion": "청주시 흥덕구",
      "educationStatus": "NOT_COMPLETED",
      "eligibilityVerified": false
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

`content`의 각 항목은 이름, 희망 지역, 교육 이수 상태, 자격 검증 여부만 담는다(상세 필드는 5장 참고). `UrbanFarmerProfileRepository.findAll(Pageable)`에 `@EntityGraph(attributePaths = "user")`가 적용돼 있어 목록 조회 시 N+1 쿼리가 발생하지 않는다.

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

정렬·필터링 파라미터는 지원하지 않는다.

## 5. 도시농부 상세 조회

### 5.1 요청

```http
GET /api/admin/urban-farmers/10
Authorization: Bearer {{adminAccessToken}}
```

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "userId": 10,
  "loginId": "city_farmer01",
  "name": "홍길동",
  "preferredRegion": "청주시 흥덕구",
  "preferredDays": "월,수,금",
  "preferredWorkType": "수확",
  "canTravel": true,
  "experienceCount": 3,
  "introduction": "주말 농장 경험이 있습니다.",
  "farmBusinessRegistered": false,
  "eligibilityVerified": false,
  "educationStatus": "NOT_COMPLETED"
}
```

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `BAD_REQUEST` | 해당 `userId`의 도시농부 프로필이 없음 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

프로필 없음 예시:

```json
{
  "code": "BAD_REQUEST",
  "message": "등록된 도시농부 프로필이 없습니다."
}
```

## 6. 자격 검증 승인

관리자가 해당 도시농부의 농업경영체 **미등록** 여부를 확인했다는 것을 승인 처리한다. `eligibilityVerified`를 `true`로 바꾼다.

### 6.1 요청

```http
POST /api/admin/urban-farmers/10/verify-eligibility
Authorization: Bearer {{adminAccessToken}}
```

이 API는 요청 본문을 받지 않는다.

### 6.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 5.2와 동일한 `AdminUrbanFarmerDetailResponse` 형식이며 `eligibilityVerified`가 `true`로 반영된 최신 상태다.

### 6.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `BAD_REQUEST` | 해당 `userId`의 도시농부 프로필이 없음 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

이전 `eligibilityVerified` 값과 무관하게 항상 `true`로 덮어쓴다. 반려·취소 기능은 없다.

## 7. 교육 이수 승인

기존에 도시농부 본인이 호출하던 `POST /api/urban-farmer/education/complete`는 삭제됐고, 이 API로 대체됐다. 상태를 `COMPLETED`로 바꾼다. 도시농부 본인용 조회(`GET /api/urban-farmer/education`)와 수료증 등록(`POST /api/urban-farmer/education/certificate`)은 그대로 유지된다([`URBAN_FARMER_EDUCATION_API_SPEC.md`](./URBAN_FARMER_EDUCATION_API_SPEC.md) 참고).

### 7.1 요청

```http
POST /api/admin/urban-farmers/10/education/approve
Authorization: Bearer {{adminAccessToken}}
```

이 API는 요청 본문을 받지 않는다.

### 7.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 5.2와 동일한 `AdminUrbanFarmerDetailResponse` 형식이며 `educationStatus`가 `COMPLETED`로 반영된 최신 상태다.

### 7.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `BAD_REQUEST` | 해당 `userId`의 도시농부 프로필이 없음(`먼저 프로필을 등록해주세요.`) |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

이전 `educationStatus` 값과 무관하게 항상 `COMPLETED`로 덮어쓴다. 수료증 등록(`CERTIFICATE_REGISTERED`) 여부는 검증하지 않는다.

## 8. 프로필 대리 등록

이미 회원가입은 됐지만 프로필이 없는 도시농부를 대신해서 관리자가 프로필을 등록한다. 요청 본문은 도시농부 본인용 등록 API와 완전히 동일하다([`URBAN_FARMER_PROFILE_API_SPEC.md`](./URBAN_FARMER_PROFILE_API_SPEC.md) 3장 참고). 신규 회원가입 자체를 관리자가 대신하는 기능은 포함하지 않는다 — 대상 계정은 미리 회원가입돼 있어야 한다.

### 8.1 요청

```http
POST /api/admin/urban-farmers/11/profile
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "preferredRegion": "충주시",
  "preferredDays": "화,목",
  "preferredWorkType": "파종",
  "canTravel": false,
  "experienceCount": 0,
  "introduction": "관리자가 대신 등록한 프로필입니다.",
  "farmBusinessRegistered": false
}
```

필드 제약은 도시농부 본인용 등록 API와 동일하다.

### 8.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 도시농부 본인용 등록 API와 동일한 `UrbanFarmerProfileResponse` 형식이다.

### 8.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락(공백 포함) 또는 `experienceCount` 음수, `introduction` 500자 초과 |
| `400` | `BAD_REQUEST` | 해당 `userId`가 존재하지 않음(`사용자를 찾을 수 없습니다.`) |
| `400` | `BAD_REQUEST` | 해당 `userId`가 `URBAN_FARMER` 계정이 아님(`도시농부 계정만 프로필을 등록할 수 있습니다.`) |
| `400` | `BAD_REQUEST` | 해당 `userId`에 이미 프로필이 등록되어 있음(`이미 등록된 도시농부 프로필이 있습니다.`) |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

세 가지 `BAD_REQUEST` 조건은 모두 `code`가 같고 `message`로만 구분되므로, 클라이언트에서 분기하려면 메시지 문자열을 확인해야 한다.

## 9. 인증·인가 오류 응답 주의사항

`401`·`403`은 `GlobalExceptionHandler`가 아니라 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드). 코드값은 고정이며 요청 상황에 따라 메시지가 달라지지 않는다.

- `401`: `authenticationEntryPoint`가 호출되어 `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`를 응답한다.
- `403`: `accessDeniedHandler`가 호출되어 `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`를 응답한다.

형식 자체(`{ code, message }`)는 `GlobalExceptionHandler`가 만드는 응답과 같지만, 만들어지는 위치(Security 필터 체인 vs. 컨트롤러 예외 처리)가 다르다. `POST /api/internal/center-admins`만 예외로, JWT가 아예 필요 없어 이 절이 적용되지 않고 3.3의 발급 키 관련 오류만 발생한다.

## 10. 데이터 구조

이 기능은 새 테이블을 만들지 않는다. `urban_farmer_profiles` 테이블에 이번에 추가된 컬럼은 다음과 같다(전체 컬럼 정의는 [`URBAN_FARMER_PROFILE_API_SPEC.md`](./URBAN_FARMER_PROFILE_API_SPEC.md) 7장 참고).

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `farm_business_registered` | NOT NULL | 농업경영체 등록 여부(도시농부 자격은 등록 안 되어 있어야 함) |
| `eligibility_verified` | NOT NULL, 기본값 `false` | 관리자가 자격을 확인했는지 여부 |

`app.admin.provisioning-key`(문자열)는 DB가 아니라 `application.properties`에 저장된다.

## 11. 현재 범위 밖 또는 미구현 기능

- 신규 회원가입 자체를 관리자가 대신 처리하는 기능(프로필 대리 등록은 지원하지만 회원가입 대리는 미포함)
- 목록 조회 필터링·정렬(지역별, 교육 상태별 등) 및 검색
- 자격 검증 반려·취소, 교육 이수 승인 취소(둘 다 `true`/`COMPLETED`로만 갈 수 있고 되돌릴 수 없음)
- 관리자 계정 발급 기능의 활성화/비활성화 자동화(현재는 `application.properties`의 `app.admin.provisioning-key`를 직접 비워서 비활성화해야 함)
- 농가·매칭(`JobPosting`) 관련 관리자 기능
