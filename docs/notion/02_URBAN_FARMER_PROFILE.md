# CityFarmerPlus 도시농부 프로필 API 명세서

- 기준일: 2026-08-20
- 기준 소스: 현재 `backend-1` 작업 폴더의 `UrbanFarmerProfileController`, DTO, Service, Entity 및 공통 인증·예외 코드
- 로컬 Base URL: `http://localhost:8080`
- API 수: 3개

> 도시농부 계정의 활동 경험과 농업경영체 등록 여부를 관리하는 API다. `/api/auth/me`의 기본 회원 정보와 별도 데이터이며, 중개센터 전용 API는 포함하지 않는다.

---

## 1. 인증과 공통 형식

모든 API는 `URBAN_FARMER` 역할의 활성 계정만 사용할 수 있다.

```http
Authorization: Bearer {{accessToken}}
```

JSON 본문이 있는 요청:

```http
Content-Type: application/json
```

공통 오류 형식:

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

| 상황 | HTTP | 코드 |
|---|---:|---|
| JWT 누락·만료·위조 | `401` | `UNAUTHORIZED` |
| 탈퇴·정지 계정 또는 JWT 역할과 DB 역할 불일치 | `401` | `INVALID_ACCOUNT` |
| `FARM` 등 다른 역할로 접근 | `403` | `ACCESS_DENIED` |
| JSON을 읽을 수 없음 | `400` | `INVALID_REQUEST` |
| DTO 검증 실패 | `400` | `VALIDATION_ERROR` |

---

## 2. API 목록

| 기능 | Method | URL | 권한 | 성공 응답 |
|---|---|---|---|---|
| 프로필 최초 생성 | `POST` | `/api/urban-farmers/me/profile` | `URBAN_FARMER` | `201 Created` |
| 내 프로필 조회 | `GET` | `/api/urban-farmers/me/profile` | `URBAN_FARMER` | `200 OK` |
| 내 프로필 수정 | `PATCH` | `/api/urban-farmers/me/profile` | `URBAN_FARMER` | `200 OK` |

---

## 3. 공통 요청 필드

생성과 수정은 같은 `UrbanFarmerProfileRequest`를 사용한다.

| 필드 | 타입 | 필수 | 검증 및 처리 |
|---|---|---:|---|
| `agriculturalBusinessRegistered` | Boolean | O | 농업경영체 등록 여부, `true` 또는 `false` |
| `experienceCount` | Integer | O | 활동 경험 횟수, `0~10000` |
| `notes` | String | X | 최대 1000자; `null`, 빈 문자열, 공백 문자열은 `null`로 저장; 그 외 앞뒤 공백 제거 |

요청 예시:

```json
{
  "agriculturalBusinessRegistered": false,
  "experienceCount": 3,
  "notes": "사과 수확 및 선별 작업 경험이 있습니다."
}
```

---

## 4. 프로필 최초 생성

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/urban-farmers/me/profile` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | `application/json` |

```http
POST /api/urban-farmers/me/profile
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

```json
{
  "agriculturalBusinessRegistered": false,
  "experienceCount": 3,
  "notes": "사과 수확 및 선별 작업 경험이 있습니다."
}
```

### 성공 응답

- HTTP: `201 Created`
- Body: `UrbanFarmerProfileResponse`

```json
{
  "id": 10,
  "userId": 15,
  "agriculturalBusinessRegistered": false,
  "experienceCount": 3,
  "notes": "사과 수확 및 선별 작업 경험이 있습니다.",
  "version": 0,
  "createdAt": "2026-08-11T10:20:30.123Z",
  "updatedAt": "2026-08-11T10:20:30.123Z"
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 프로필 ID |
| `userId` | Long | 로그인한 도시농부 회원 ID |
| `agriculturalBusinessRegistered` | Boolean | 농업경영체 등록 여부 |
| `experienceCount` | Integer | 활동 경험 횟수 |
| `notes` | String 또는 null | 특이사항 |
| `version` | Long | JPA 낙관적 잠금 버전 |
| `createdAt` | Instant | 생성 시각, UTC ISO-8601 |
| `updatedAt` | Instant | 최근 수정 시각, UTC ISO-8601 |

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락, 경험 횟수 범위 또는 특이사항 길이 위반 |
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `401` | `INVALID_ACCOUNT` | 비활성 계정 또는 JWT 역할 불일치 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `409` | `URBAN_FARMER_PROFILE_ALREADY_EXISTS` | 이미 프로필이 존재함 |
| `409` | `DATA_CONFLICT` | 동시 생성 등으로 DB 유니크 제약 충돌 |

중복 생성 오류 예시:

```json
{
  "code": "URBAN_FARMER_PROFILE_ALREADY_EXISTS",
  "message": "이미 도시농부 프로필이 등록되어 있습니다."
}
```

### Postman 팁

도시농부로 회원가입·로그인한 토큰을 사용한다. 같은 계정으로 POST를 두 번 보내면 두 번째 요청은 `409`가 정상이다.

---

## 5. 내 프로필 조회

### 요청

| 항목 | 값 |
|---|---|
| Method | `GET` |
| URL | `/api/urban-farmers/me/profile` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | 요청 본문 없음 |

```http
GET /api/urban-farmers/me/profile
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `200 OK`

```json
{
  "id": 10,
  "userId": 15,
  "agriculturalBusinessRegistered": false,
  "experienceCount": 3,
  "notes": "사과 수확 및 선별 작업 경험이 있습니다.",
  "version": 0,
  "createdAt": "2026-08-11T10:20:30.123Z",
  "updatedAt": "2026-08-11T10:20:30.123Z"
}
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `401` | `INVALID_ACCOUNT` | 비활성 계정 또는 JWT 역할 불일치 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `404` | `URBAN_FARMER_PROFILE_NOT_FOUND` | 아직 프로필을 생성하지 않음 |

```json
{
  "code": "URBAN_FARMER_PROFILE_NOT_FOUND",
  "message": "도시농부 프로필을 찾을 수 없습니다."
}
```

### Postman 팁

프로필 생성 전 `404`, 생성 후 `200`으로 바뀌는지 순서대로 확인한다.

---

## 6. 내 프로필 수정

### 요청

| 항목 | 값 |
|---|---|
| Method | `PATCH` |
| URL | `/api/urban-farmers/me/profile` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | `application/json` |

```json
{
  "agriculturalBusinessRegistered": true,
  "experienceCount": 5,
  "notes": "농업경영체 등록 완료, 수확 작업 5회 경험"
}
```

### 중요한 수정 규칙

- HTTP 메서드는 `PATCH`지만 요청 DTO의 `agriculturalBusinessRegistered`, `experienceCount`가 모두 필수다.
- 두 필드를 생략한 부분 수정 요청은 `400 VALIDATION_ERROR`다.
- 즉, 현재 저장된 값을 포함해 프로필의 전체 입력값을 다시 보내야 한다.
- `notes`를 `null`, `""`, 공백 문자열로 보내면 특이사항을 삭제한다.
- 프로필이 없으면 수정 API가 새로 만들지 않는다. 먼저 POST로 생성해야 한다.

### 성공 응답

- HTTP: `200 OK`
- 변경 후 전체 프로필 반환

```json
{
  "id": 10,
  "userId": 15,
  "agriculturalBusinessRegistered": true,
  "experienceCount": 5,
  "notes": "농업경영체 등록 완료, 수확 작업 5회 경험",
  "version": 1,
  "createdAt": "2026-08-11T10:20:30.123Z",
  "updatedAt": "2026-08-11T10:30:00.456Z"
}
```

수정 서비스는 변경 내용을 JPA flush한 뒤 응답 DTO를 만든다. 따라서 즉시 응답의 `version`은 실제 증가한 값이고 `updatedAt`도 이번 수정 시각이다.

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락, 경험 횟수 범위 또는 특이사항 길이 위반 |
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `404` | `URBAN_FARMER_PROFILE_NOT_FOUND` | 수정할 프로필이 없음 |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청이 같은 프로필을 먼저 갱신 |

### Postman 팁

수정 전에 GET 응답을 복사해 `id`, `userId`, `version`, 시각 필드를 제거하고 세 입력 필드만 수정해서 전송하면 필수값 누락을 피할 수 있다.

---

## 7. 상태와 현재 제한

- 한 도시농부 회원당 프로필은 최대 1개다.
- 프로필 삭제 API는 없다.
- 프로필 수정 이력 조회 API는 없다.
- `agriculturalBusinessRegistered`는 현재 요청자가 입력하는 프로필 값이며, 별도 서류 심사 결과를 의미하지 않는다.
- 기본 회원 이름·전화번호·생년월일·주소는 이 API가 아니라 `PATCH /api/auth/me`에서 수정한다.
