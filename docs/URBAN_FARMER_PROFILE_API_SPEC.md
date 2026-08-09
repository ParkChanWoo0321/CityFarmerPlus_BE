# CityFarmerPlus 도시농부 프로필 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-06
- 구현 기준 브랜치: `backend-2`
- 적용 범위: 도시농부 프로필 등록, 내 프로필 조회, 내 프로필 수정

## 1. 공통 사항

### 1.1 기본 URL

로컬 개발 환경의 기본 URL은 다음과 같다.

```text
http://localhost:8080
```

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{accessToken}}
```

### 1.3 인증 및 권한 규칙

- `userType`이 `URBAN_FARMER`인 회원의 JWT만 호출할 수 있다. `SecurityConfig`에서 `/api/urban-farmer/**` 요청 전체에 `hasRole("URBAN_FARMER")`를 적용한다.
- 프로필 소유자는 요청값이 아니라 JWT의 `sub`(회원 ID, `Authentication.getName()`)로 결정한다.
- 한 회원 계정은 도시농부 프로필을 하나만 등록할 수 있다(`UrbanFarmerProfile.user`가 `User`와 1:1, `user_id` UNIQUE).
- 서비스 레이어에서 계정 상태나 역할을 별도로 재검증하지 않는다. JWT 권한(`ROLE_URBAN_FARMER`) 확인이 유일한 접근 제어다.

### 1.4 공통 오류 응답 형식

비즈니스 로직 오류와 입력값 검증 오류는 `GlobalExceptionHandler`가 처리하며 다음 형식으로 응답한다.

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

인증(`401`)·인가(`403`) 오류는 `GlobalExceptionHandler`가 아니라 `SecurityConfig`가 직접 같은 `{ code, message }` 형식으로 응답한다. 자세한 내용은 6장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 프로필 등록 | `POST` | `/api/urban-farmer/profile` | Bearer JWT (`ROLE_URBAN_FARMER`) | `200 OK` |
| 내 프로필 조회 | `GET` | `/api/urban-farmer/profile` | Bearer JWT (`ROLE_URBAN_FARMER`) | `200 OK` |
| 내 프로필 수정 | `PUT` | `/api/urban-farmer/profile` | Bearer JWT (`ROLE_URBAN_FARMER`) | `200 OK` |

## 3. 프로필 등록

### 3.1 요청

```http
POST /api/urban-farmer/profile
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

```json
{
  "preferredRegion": "청주시 흥덕구",
  "preferredDays": "월,수,금",
  "preferredWorkType": "수확",
  "canTravel": true,
  "experienceCount": 3,
  "introduction": "주말 농장 경험이 있습니다."
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `preferredRegion` | String | O | 공백만 입력 불가 |
| `preferredDays` | String | O | 공백만 입력 불가 (예: `"월,수,금"` 형태의 자유 문자열) |
| `preferredWorkType` | String | O | 공백만 입력 불가 |
| `canTravel` | boolean | X | 미입력 시 `false` |
| `experienceCount` | int | X | 0 이상, 미입력 시 `0` |
| `introduction` | String | X | 최대 500자 |

다음 값은 요청받지 않는다.

- 회원 ID 또는 프로필 소유자 ID (JWT에서 결정)
- 교육 이수 상태 (등록 시 항상 `NOT_COMPLETED`로 생성, [`URBAN_FARMER_EDUCATION_API_SPEC.md`](./URBAN_FARMER_EDUCATION_API_SPEC.md) 참고)

### 3.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 1,
  "userId": 10,
  "preferredRegion": "청주시 흥덕구",
  "preferredDays": "월,수,금",
  "preferredWorkType": "수확",
  "canTravel": true,
  "experienceCount": 3,
  "introduction": "주말 농장 경험이 있습니다."
}
```

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락(공백 포함) 또는 `experienceCount` 음수, `introduction` 500자 초과 |
| `400` | `BAD_REQUEST` | 이미 프로필이 등록된 계정으로 재등록 시도 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `URBAN_FARMER`가 아닌 계정(`FARM`, `CENTER_ADMIN`)의 JWT로 접근 |

중복 등록 예시:

```json
{
  "code": "BAD_REQUEST",
  "message": "이미 등록된 도시농부 프로필이 있습니다."
}
```

## 4. 내 프로필 조회

### 4.1 요청

```http
GET /api/urban-farmer/profile
Authorization: Bearer {{accessToken}}
```

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 등록 성공 응답과 동일한 `UrbanFarmerProfileResponse` 형식이다.

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `BAD_REQUEST` | 아직 프로필을 등록하지 않은 계정으로 조회 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `URBAN_FARMER`가 아닌 계정의 JWT로 접근 |

프로필 없음 예시(다른 명세서의 `404`와 달리, 이 프로젝트는 `IllegalArgumentException`을 그대로 사용하므로 `400`으로 응답한다):

```json
{
  "code": "BAD_REQUEST",
  "message": "등록된 도시농부 프로필이 없습니다."
}
```

## 5. 내 프로필 수정

### 5.1 요청

```http
PUT /api/urban-farmer/profile
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

요청 본문 형식은 3.1 등록 요청과 동일하다. 전체 필드를 다시 전송해야 하며 부분 수정(PATCH)은 지원하지 않는다.

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 등록 성공 응답과 동일한 `UrbanFarmerProfileResponse` 형식이다.

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락 또는 형식·길이 위반 |
| `400` | `BAD_REQUEST` | 아직 프로필을 등록하지 않은 계정으로 수정 시도 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `URBAN_FARMER`가 아닌 계정의 JWT로 접근 |

## 6. 인증·인가 오류 응답 주의사항

`401`·`403`은 `GlobalExceptionHandler`가 아니라 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드). 코드값은 고정이며 요청 상황에 따라 메시지가 달라지지 않는다.

- `401`: `authenticationEntryPoint`가 호출되어 `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`를 응답한다.
- `403`: `accessDeniedHandler`가 호출되어 `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`를 응답한다.

형식 자체(`{ code, message }`)는 `GlobalExceptionHandler`가 만드는 응답과 같지만, 만들어지는 위치(Security 필터 체인 vs. 컨트롤러 예외 처리)가 다르다.

## 7. 데이터 구조

테이블명: `urban_farmer_profiles`

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `id` | PK, Auto Increment | 프로필 식별자 |
| `user_id` | FK, NOT NULL, UNIQUE | `users.id`, 계정당 프로필 하나 보장 |
| `preferred_region` | NOT NULL, 최대 100자 | 희망 근무 지역 |
| `preferred_days` | NOT NULL, 최대 100자 | 희망 근무 가능 요일 |
| `preferred_work_type` | NOT NULL, 최대 100자 | 선호 작업 종류 |
| `can_travel` | NOT NULL | 이동 가능 여부 |
| `experience_count` | NOT NULL | 농작업 경험 횟수 |
| `introduction` | NULL, 최대 500자 | 자기소개·특이사항 |
| `education_status` | NOT NULL | 교육 이수 상태, 기본값 `NOT_COMPLETED` ([`URBAN_FARMER_EDUCATION_API_SPEC.md`](./URBAN_FARMER_EDUCATION_API_SPEC.md) 참고) |

스키마는 Hibernate `ddl-auto=update`로 관리하며 별도 마이그레이션 도구(Flyway 등)는 사용하지 않는다.

## 8. 현재 범위 밖 또는 미구현 기능

- 프로필 삭제
- 프로필 부분 수정(PATCH)
- 프로필 공개 조회(다른 사용자·중개센터 담당자의 조회 API)
- 계정 상태(정지·탈퇴)에 따른 접근 차단
