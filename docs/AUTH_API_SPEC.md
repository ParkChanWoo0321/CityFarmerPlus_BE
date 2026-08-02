# CityFarmerPlus 회원·인증 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-07-26
- 구현 기준 브랜치: `backend-1`
- 적용 범위: 회원가입, 아이디 중복 확인, 로그인, JWT 인증, 내 정보 조회, 로그아웃

## 1. 공통 사항

### 1.1 기본 URL

로컬 개발 환경의 기본 URL은 다음과 같다.

```text
http://localhost:8080
```

Postman에서는 다음 환경 변수를 사용하는 것을 권장한다.

| 변수 | 예시 |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `accessToken` | 로그인 응답의 `accessToken` |

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- JWT 인증이 필요한 API는 다음 헤더를 전송한다.

```http
Authorization: Bearer {{accessToken}}
```

### 1.3 사용자 유형

| 값 | 의미 | 공개 회원가입 |
|---|---|---|
| `URBAN_FARMER` | 도시농부 | 가능 |
| `FARM` | 농가 | 가능 |
| `CENTER_ADMIN` | 충북 전체 시·군 담당자 | 불가능 |

한 회원 레코드는 하나의 `userType`만 가지므로 도시농부와 농가 역할을 동시에 가질 수 없다.

### 1.4 계정 상태

| 값 | 의미 |
|---|---|
| `ACTIVE` | 정상 사용 가능 |
| `SUSPENDED` | 정지된 계정 |
| `WITHDRAWN` | 탈퇴한 계정 |

신규 회원은 `ACTIVE` 상태로 생성된다. `SUSPENDED`, `WITHDRAWN` 상태의 계정은 로그인하거나 인증 사용자 정보를 조회할 수 없다.

### 1.5 공통 오류 응답

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 회원가입 | `POST` | `/api/auth/signup` | 불필요 | `201 Created` |
| 아이디 중복 확인 | `GET` | `/api/auth/check-id` | 불필요 | `200 OK` |
| 로그인 | `POST` | `/api/auth/login` | 불필요 | `200 OK` |
| 내 정보 조회 | `GET` | `/api/auth/me` | Bearer JWT | `200 OK` |
| 로그아웃 | `POST` | `/api/auth/logout` | Bearer JWT | `204 No Content` |

## 3. 회원가입

### 3.1 요청

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "loginId": "farm_user",
  "password": "password123!",
  "name": "농가 사용자",
  "userType": "FARM"
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `loginId` | String | O | 4~30자, 영문 소문자·숫자·밑줄만 허용 |
| `password` | String | O | 8~64자 |
| `name` | String | O | 공백만 입력할 수 없음, 최대 50자 |
| `userType` | Enum | O | `URBAN_FARMER` 또는 `FARM` |

비밀번호는 평문으로 저장하지 않고 BCrypt 해시로 저장한다.

### 3.2 성공 응답

```http
HTTP/1.1 201 Created
```

```json
{
  "id": 1,
  "loginId": "farm_user",
  "name": "농가 사용자",
  "userType": "FARM",
  "accountStatus": "ACTIVE"
}
```

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락 또는 형식·길이 위반 |
| `400` | `INVALID_REQUEST` | 잘못된 JSON 또는 존재하지 않는 Enum 값 |
| `400` | `MANAGER_SIGNUP_NOT_ALLOWED` | `CENTER_ADMIN`으로 공개 회원가입 시도 |
| `409` | `DUPLICATE_LOGIN_ID` | 이미 사용 중인 아이디 |
| `409` | `DATA_CONFLICT` | 기타 데이터 무결성 충돌 |

중복 아이디 예시:

```json
{
  "code": "DUPLICATE_LOGIN_ID",
  "message": "이미 사용 중인 아이디입니다."
}
```

## 4. 아이디 중복 확인

### 4.1 요청

```http
GET /api/auth/check-id?loginId=farm_user
```

| Query Parameter | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `loginId` | String | O | 4~30자, 영문 소문자·숫자·밑줄만 허용 |

### 4.2 사용 가능한 아이디

```http
HTTP/1.1 200 OK
```

```json
{
  "loginId": "farm_user",
  "available": true
}
```

### 4.3 이미 사용 중인 아이디

```json
{
  "loginId": "farm_user",
  "available": false
}
```

잘못된 아이디 형식은 `400 VALIDATION_ERROR`로 처리한다.

현재 `loginId` 쿼리 파라미터 자체가 누락된 경우에는 Spring 기본 `400` 응답이 반환될 수 있어 공통 오류 JSON 형식이 보장되지 않는다.

## 5. 로그인

### 5.1 요청

```http
POST /api/auth/login
Content-Type: application/json
```

```json
{
  "loginId": "farm_user",
  "password": "password123!"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `loginId` | String | O | 가입 시 설정한 아이디 |
| `password` | String | O | 가입 시 설정한 비밀번호 |

로그인 시 아이디의 앞뒤 공백을 제거하고 영문 대문자를 소문자로 변환한 뒤 조회한다.

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": 1,
    "loginId": "farm_user",
    "name": "농가 사용자",
    "userType": "FARM",
    "accountStatus": "ACTIVE"
  }
}
```

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 아이디 또는 비밀번호가 비어 있음 |
| `400` | `INVALID_REQUEST` | 요청 JSON이 올바르지 않음 |
| `401` | `INVALID_CREDENTIALS` | 아이디가 없거나 비밀번호가 일치하지 않음 |
| `403` | `INACTIVE_ACCOUNT` | 정지 또는 탈퇴 상태의 계정 |

아이디 존재 여부 노출을 방지하기 위해 존재하지 않는 아이디와 잘못된 비밀번호는 같은 오류로 응답한다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "아이디 또는 비밀번호가 일치하지 않습니다."
}
```

## 6. 내 정보 조회

### 6.1 요청

```http
GET /api/auth/me
Authorization: Bearer {{accessToken}}
```

### 6.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 1,
  "loginId": "farm_user",
  "name": "농가 사용자",
  "userType": "FARM",
  "accountStatus": "ACTIVE"
}
```

### 6.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | 토큰 누락, 만료, 위조 또는 형식 오류 |
| `401` | `INVALID_AUTHENTICATION` | 토큰의 사용자 식별자가 올바르지 않음 |
| `403` | `INACTIVE_ACCOUNT` | 정지 또는 탈퇴 상태의 계정 |
| `404` | `USER_NOT_FOUND` | 토큰의 사용자 ID에 해당하는 회원이 없음 |

인증 실패 예시:

```json
{
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다."
}
```

## 7. 로그아웃

### 7.1 요청

```http
POST /api/auth/logout
Authorization: Bearer {{accessToken}}
```

### 7.2 성공 응답

```http
HTTP/1.1 204 No Content
```

응답 본문은 없다.

현재 인증 방식은 서버 세션을 사용하지 않는 Stateless JWT 방식이다. 로그아웃 시 서버에서 토큰을 폐기하거나 블랙리스트에 등록하지 않으므로 클라이언트가 저장한 토큰을 삭제해야 한다. 삭제하지 않은 기존 토큰은 만료 시각까지 유효하다.

## 8. JWT 명세

### 8.1 서명 및 만료

| 항목 | 값 |
|---|---|
| 서명 알고리즘 | `HS256` |
| 기본 만료 시간 | 1시간 |
| 인증 방식 | `Authorization: Bearer <token>` |
| Refresh Token | 현재 미지원 |
| Audience(`aud`) 검증 | 현재 미지원 |

### 8.2 Payload Claim

| Claim | 설명 | 예시 |
|---|---|---|
| `iss` | 토큰 발급자 | `https://api.cityfarmerplus.local` |
| `sub` | 회원 ID | `"1"` |
| `iat` | 발급 시각 | Unix timestamp |
| `exp` | 만료 시각 | Unix timestamp |
| `role` | 사용자 유형 | `FARM` |

`role` Claim은 인증 시 다음 Spring Security 권한으로 변환된다.

| JWT `role` | Spring Security Authority |
|---|---|
| `URBAN_FARMER` | `ROLE_URBAN_FARMER` |
| `FARM` | `ROLE_FARM` |
| `CENTER_ADMIN` | `ROLE_CENTER_ADMIN` |

## 9. 환경 설정

로컬 비밀값은 프로젝트 루트의 `.env`에 저장하며 `.env`는 Git에 커밋하지 않는다.

| 환경 변수 | 필수 | 설명 |
|---|---|---|
| `DB_URL` | 선택 | MySQL 접속 URL |
| `DB_USERNAME` | 선택 | MySQL 사용자명, 기본값 `root` |
| `DB_PASSWORD` | O | MySQL 비밀번호 |
| `JPA_DDL_AUTO` | 선택 | 기본값 `update` |
| `JPA_SHOW_SQL` | 선택 | SQL 출력 여부, 기본값 `false` |
| `JWT_SECRET` | O | UTF-8 기준 32바이트 이상의 JWT 서명 키 |
| `JWT_ISSUER` | 선택 | JWT 발급자 |
| `JWT_ACCESS_TOKEN_EXPIRATION` | 선택 | Spring Duration 형식, 기본값 `1h` |

운영 환경에서는 로컬 개발용 JWT 키를 사용하지 않고 충분히 긴 무작위 비밀키를 별도로 주입해야 한다.

## 10. 회원 테이블 논리 구조

테이블명: `users`

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `id` | PK, Auto Increment | 회원 식별자 |
| `login_id` | NOT NULL, UNIQUE, 최대 30자 | 로그인 아이디 |
| `password` | NOT NULL, 최대 100자 | BCrypt 비밀번호 해시 |
| `name` | NOT NULL, 최대 50자 | 사용자 이름 |
| `user_type` | NOT NULL, 최대 30자 | 사용자 유형 |
| `account_status` | NOT NULL, 최대 20자 | 계정 상태 |
| `created_at` | NOT NULL | 생성 시각 |
| `updated_at` | NOT NULL | 수정 시각 |

## 11. Postman 확인 순서

1. `GET {{baseUrl}}/api/auth/check-id?loginId=farm_user`로 아이디 사용 가능 여부를 확인한다.
2. `POST {{baseUrl}}/api/auth/signup`으로 회원을 생성한다.
3. `POST {{baseUrl}}/api/auth/login`으로 로그인한다.
4. 로그인 응답의 `accessToken`을 Postman 환경 변수 `accessToken`에 저장한다.
5. `GET {{baseUrl}}/api/auth/me`에 Bearer Token을 설정해 인증을 확인한다.
6. `POST {{baseUrl}}/api/auth/logout` 호출 후 클라이언트에 저장한 토큰을 삭제한다.

## 12. 현재 범위 밖 또는 미구현 기능

다음 기능은 프로젝트 요구사항에는 포함되어 있지만 현재 인증 API 구현에는 포함되지 않았다.

- 담당자 계정 생성 전용 API
- Refresh Token과 서버 측 강제 로그아웃
- 회원 승인 및 교육 이수 인증
- 이수증 제출·반려·재제출·과거 파일 보관
- 농가 소유 증빙 제출 및 담당자 승인
- 회원 탈퇴 처리
- 사용자 정보 수정 및 비밀번호 변경
- 역할별 세부 API 접근 제어

담당자는 공개 회원가입으로 생성할 수 없다. 담당자 계정을 Postman으로 생성하려면 별도의 보호된 담당자 생성 API를 추가로 구현해야 한다.

추가로 현재 구현에는 다음 기술적 제한이 있다.

- 역할 Claim을 Spring Security 권한으로 변환하지만 아직 역할별 `@PreAuthorize` 또는 URL 접근 정책은 적용하지 않았다.
- 본인인증을 하지 않으므로 같은 사람이 서로 다른 아이디로 도시농부 계정과 농가 계정을 각각 생성하는 것은 방지할 수 없다.
- 로그인과 `/me`에서는 계정 상태를 확인하지만, JWT 처리 단계에서 매 요청마다 최신 계정 상태를 DB에서 확인하지는 않는다. 향후 보호 API는 정지·탈퇴 계정 차단 정책을 별도로 적용해야 한다.
- 별도의 CORS 설정이 없으므로 다른 도메인의 웹 프론트엔드와 연동할 때 허용 Origin 설정이 필요하다.
- 사용자 요청에 따라 Flyway를 사용하지 않으며 현재 스키마는 Hibernate `ddl-auto=update`로 관리한다.
- 누락된 Query Parameter, 지원하지 않는 HTTP Method 등 일부 Spring 기본 오류는 `{ "code", "message" }` 형식이 아닐 수 있다.
