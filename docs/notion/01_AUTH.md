# CityFarmerPlus 회원·인증 API 명세서

- 기준일: 2026-08-26
- 기준 소스: 현재 `backend-1` 작업 폴더의 `AuthController`, 인증 DTO, `AuthService`, `AccountWithdrawalService`, Security 및 예외 처리 코드
- 로컬 Base URL: `http://localhost:8080`
- API 수: 7개

> 이 문서는 다른 문서 없이 노션 페이지 하나에 그대로 복사할 수 있는 회원·인증 API 계약이다. 소셜 로그인과 본인인증은 범위에서 제외한다.

---

## 1. 인증과 공통 형식

보호 API는 회원가입 또는 로그인 응답으로 받은 JWT를 보낸다.

```http
Authorization: Bearer {{accessToken}}
```

JSON 본문이 있는 요청:

```http
Content-Type: application/json
```

공통 오류 응답:

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
| JSON을 읽을 수 없음 | `400` | `INVALID_REQUEST` |
| DTO 또는 Query 검증 실패 | `400` | `VALIDATION_ERROR` |

### 사용자 역할

| `userType` | 의미 | 공개 회원가입 |
|---|---|---|
| `URBAN_FARMER` | 도시농부 | 가능 |
| `FARM` | 농가 | 가능 |
| `CENTER_ADMIN` | backend-2 중개센터 담당자와 공유할 역할 값 | 불가능 |

- 한 계정은 하나의 역할만 가진다.
- 신규 회원의 `accountStatus`는 `ACTIVE`다.
- 계정 상태 값은 `ACTIVE`, `SUSPENDED`, `WITHDRAWN`이다.
- backend-1에는 중개센터 계정 발급 API가 없다.

---

## 2. API 목록

| 기능 | Method | URL | 권한 | 성공 응답 |
|---|---|---|---|---|
| 회원가입 | `POST` | `/api/auth/signup` | 공개 | `201 Created` |
| 아이디 중복 확인 | `GET` | `/api/auth/check-id` | 공개 | `200 OK` |
| 로그인 | `POST` | `/api/auth/login` | 공개 | `200 OK` |
| 내 회원 정보 조회 | `GET` | `/api/auth/me` | 활성 계정 | `200 OK` |
| 내 회원 정보 수정 | `PATCH` | `/api/auth/me` | 활성 계정 | `200 OK` |
| 회원 탈퇴 | `POST` | `/api/auth/withdrawal` | 활성 계정 | `204 No Content` |
| 로그아웃 | `POST` | `/api/auth/logout` | 활성 계정 | `204 No Content` |

---

## 3. 회원가입

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/auth/signup` |
| 권한 | 공개, JWT 불필요 |
| Content-Type | `application/json` |

```json
{
  "loginId": "urban_user",
  "password": "password123!",
  "name": "김도시",
  "userType": "URBAN_FARMER",
  "phoneNumber": "010-1234-5678",
  "birthDate": "1990-05-20",
  "address": "충청북도 청주시 상당구"
}
```

### 요청 필드와 검증

| 필드 | 타입 | 필수 | 검증 및 처리 |
|---|---|---:|---|
| `loginId` | String | O | 영문 소문자·숫자·밑줄만 사용, 4~30자 |
| `password` | String | O | 공백만 입력 불가, 8~64자, UTF-8 기준 72바이트 이하 |
| `name` | String | O | 공백만 입력 불가, 최대 50자, 저장 전 앞뒤 공백 제거 |
| `userType` | Enum | O | `URBAN_FARMER` 또는 `FARM`; `CENTER_ADMIN`은 공개 가입 거절 |
| `phoneNumber` | String | X | 빈 문자열 또는 숫자 10~11자리, 하이픈 허용; 저장 시 숫자만 남김 |
| `birthDate` | `YYYY-MM-DD` | X | 오늘 또는 과거 날짜 |
| `address` | String | X | 최대 255자; 공백 값은 `null`로 저장 |

`loginId` 검증은 정규화보다 먼저 실행되므로 회원가입 요청에는 대문자나 앞뒤 공백을 보내면 안 된다. 저장 시에는 소문자로 정규화하며 중복 여부는 대소문자를 구분하지 않는다.

### 성공 응답

- HTTP: `201 Created`
- Body: 로그인과 동일한 `TokenResponse`
- JWT 서명: `HS256`
- `sub`: 회원 ID 문자열
- `role`: 회원 역할
- 기본 만료시간: 1시간. 실제 `expiresInSeconds`는 서버 환경 설정을 따른다.

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": 15,
    "loginId": "urban_user",
    "name": "김도시",
    "phoneNumber": "01012345678",
    "birthDate": "1990-05-20",
    "address": "충청북도 청주시 상당구",
    "userType": "URBAN_FARMER",
    "accountStatus": "ACTIVE"
  }
}
```

회원가입 성공 직후 별도 로그인 없이 `accessToken`을 보호 API에 사용할 수 있다.

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 필수값, 형식, 글자 수 또는 UTF-8 바이트 제한 위반 |
| `400` | `INVALID_REQUEST` | 잘못된 JSON 또는 존재하지 않는 Enum 값 |
| `400` | `MANAGER_SIGNUP_NOT_ALLOWED` | `CENTER_ADMIN` 공개 가입 시도 |
| `409` | `DUPLICATE_LOGIN_ID` | 이미 사용 중인 아이디 |

오류 예시:

```json
{
  "code": "DUPLICATE_LOGIN_ID",
  "message": "이미 사용 중인 아이디입니다."
}
```

### Postman 팁

`Body > raw > JSON`을 선택한다. 도시농부와 농가 회원을 각각 테스트하려면 `userType`만 `URBAN_FARMER`, `FARM`으로 바꾼다.

---

## 4. 아이디 중복 확인

### 요청

| 항목 | 값 |
|---|---|
| Method | `GET` |
| URL | `/api/auth/check-id?loginId=urban_user` |
| 권한 | 공개, JWT 불필요 |
| Content-Type | 요청 본문 없음 |

Query Parameter:

| 이름 | 타입 | 필수 | 검증 |
|---|---|---:|---|
| `loginId` | String | O | 영문 소문자·숫자·밑줄 4~30자 |

### 성공 응답

- HTTP: `200 OK`

사용 가능한 경우:

```json
{
  "loginId": "urban_user",
  "available": true
}
```

이미 사용 중인 경우에도 `200 OK`이며 `available`만 `false`다.

```json
{
  "loginId": "urban_user",
  "available": false
}
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 아이디 패턴 또는 길이 위반 |
| `400` | Spring 기본 400 응답 | 필수 Query Parameter `loginId` 자체를 누락 |

현재 전역 예외 처리기는 `loginId` 값의 검증 실패는 공통 JSON으로 처리하지만, 필수 Query Parameter 자체가 없는 경우를 별도 코드로 변환하지 않는다.

### Postman 팁

`Params` 탭에 Key `loginId`, Value `urban_user`를 입력한다. 회원가입 직전 확인용 API이며 최종 중복 방지는 회원가입 트랜잭션과 DB 유니크 제약이 담당한다.

---

## 5. 로그인

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/auth/login` |
| 권한 | 공개, JWT 불필요 |
| Content-Type | `application/json` |

```json
{
  "loginId": "urban_user",
  "password": "password123!"
}
```

| 필드 | 타입 | 필수 | 검증 및 처리 |
|---|---|---:|---|
| `loginId` | String | O | 공백만 입력 불가; 앞뒤 공백 제거 후 소문자로 조회 |
| `password` | String | O | 공백만 입력 불가 |

### 성공 응답

- HTTP: `200 OK`
- Body: 회원가입과 동일한 `TokenResponse`
- JWT 서명: `HS256`
- `sub`: 회원 ID 문자열
- `role`: 회원 역할
- 기본 만료시간: 1시간. 실제 `expiresInSeconds`는 서버 환경 설정을 따른다.

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresInSeconds": 3600,
  "user": {
    "id": 15,
    "loginId": "urban_user",
    "name": "김도시",
    "phoneNumber": "01012345678",
    "birthDate": "1990-05-20",
    "address": "충청북도 청주시 상당구",
    "userType": "URBAN_FARMER",
    "accountStatus": "ACTIVE"
  }
}
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 아이디 또는 비밀번호가 비어 있음 |
| `401` | `INVALID_CREDENTIALS` | 아이디가 없거나 비밀번호가 일치하지 않음 |
| `403` | `INACTIVE_ACCOUNT` | 계정 상태가 `ACTIVE`가 아님 |

보안을 위해 존재하지 않는 아이디와 틀린 비밀번호는 같은 오류를 반환한다.

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "아이디 또는 비밀번호가 일치하지 않습니다."
}
```

### Postman 팁

Tests 또는 Scripts에서 회원가입 또는 로그인 응답 토큰을 환경변수로 저장하면 이후 보호 API에서 재사용할 수 있다.

```javascript
const body = pm.response.json();
pm.environment.set("accessToken", body.accessToken);
```

---

## 6. 내 회원 정보 조회

### 요청

| 항목 | 값 |
|---|---|
| Method | `GET` |
| URL | `/api/auth/me` |
| 권한 | 활성 계정, 역할 제한 없음 |
| Content-Type | 요청 본문 없음 |

```http
GET /api/auth/me
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `200 OK`

```json
{
  "id": 15,
  "loginId": "urban_user",
  "name": "김도시",
  "phoneNumber": "01012345678",
  "birthDate": "1990-05-20",
  "address": "충청북도 청주시 상당구",
  "userType": "URBAN_FARMER",
  "accountStatus": "ACTIVE"
}
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `401` | `INVALID_ACCOUNT` | DB 계정이 없거나 비활성, JWT 역할 불일치 |
| `404` | `USER_NOT_FOUND` | 인증 필터 통과 후 처리 중 사용자가 사라진 예외적 경우 |

### Postman 팁

`Authorization > Bearer Token`에 `{{accessToken}}`을 입력한다. Header에 `Bearer` 문자열을 두 번 붙이지 않는다.

---

## 7. 내 회원 정보 수정

### 요청

| 항목 | 값 |
|---|---|
| Method | `PATCH` |
| URL | `/api/auth/me` |
| 권한 | 활성 계정, 역할 제한 없음 |
| Content-Type | `application/json` |

```json
{
  "name": "김도시 수정",
  "phoneNumber": "010-9999-8888",
  "birthDate": "1990-05-20",
  "address": "충청북도 충주시"
}
```

### 요청 필드와 검증

모든 필드는 선택이며, JSON에 전달된 값만 변경한다.

| 필드 | 타입 | 필수 | 검증 및 처리 |
|---|---|---:|---|
| `name` | String | X | 공백만 입력 불가, 최대 50자, 저장 전 앞뒤 공백 제거 |
| `phoneNumber` | String | X | 숫자 10~11자리, 하이픈 허용; `""`를 보내면 `null`로 초기화 |
| `birthDate` | `YYYY-MM-DD` | X | 오늘 또는 과거 날짜 |
| `address` | String | X | 최대 255자; `""` 또는 공백 문자열을 보내면 `null`로 초기화 |

중요한 `null` 처리:

- 필드를 생략하거나 JSON `null`을 보내면 해당 필드는 변경하지 않는다.
- 전화번호와 주소를 지우려면 JSON `null`이 아니라 빈 문자열 `""`을 보낸다.
- 생년월일은 현재 API로 `null` 초기화할 수 없다.
- `loginId`, `password`, `userType`, `accountStatus`는 이 API로 변경할 수 없다.

### 성공 응답

- HTTP: `200 OK`
- 변경 후 전체 `UserResponse` 반환

```json
{
  "id": 15,
  "loginId": "urban_user",
  "name": "김도시 수정",
  "phoneNumber": "01099998888",
  "birthDate": "1990-05-20",
  "address": "충청북도 충주시",
  "userType": "URBAN_FARMER",
  "accountStatus": "ACTIVE"
}
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 이름·전화번호·생년월일·주소 검증 실패 |
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `401` | `INVALID_ACCOUNT` | 비활성 계정 또는 JWT 역할 불일치 |

### Postman 팁

부분 수정이므로 바꾸려는 필드만 Body에 넣는다. 빈 JSON `{}`도 유효하며 변경 없이 현재 정보를 반환한다.

---

## 8. 회원 탈퇴

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/auth/withdrawal` |
| 권한 | 활성 계정, 역할 제한 없음 |
| Content-Type | `application/json` |

```json
{
  "password": "password123!"
}
```

| 필드 | 타입 | 필수 | 검증 |
|---|---|---:|---|
| `password` | String | O | 공백만 입력 불가, UTF-8 기준 72바이트 이하 |

### 성공 응답

- HTTP: `204 No Content`
- Response Body 없음

처리 결과:

- 계정 상태를 `WITHDRAWN`으로 변경한다.
- 도시농부의 `APPLIED` 공고 지원은 `WITHDRAWN`으로 정리한다.
- 도시농부의 사업참여 신청 중 `DRAFT`, `SUBMITTED`, `REJECTED`는 `CANCELLED`로 정리한다. `APPROVED` 신청과 기존 심사자·심사 시각·반려 사유는 보존한다.
- 교육 제출 메타데이터의 상태 enum은 바꾸지 않되, 탈퇴·정지 계정의 `PENDING_REVIEW` 제출은 활성 계정 전용 담당자 심사 쿼리에서 제외한다.
- 농가의 미종결 공고는 `CANCELLED`, 해당 공고의 남은 `APPLIED` 지원은 `POSTING_CANCELLED`, 농가 프로필은 `INACTIVE`로 정리한다.
- 교육 이수증과 농가 소유 증빙 파일은 트랜잭션 커밋 후 삭제하며, 실패하면 저장된 삭제 작업을 백그라운드에서 재시도한다.

### 제한과 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 비밀번호 누락·공백 또는 72바이트 초과 |
| `401` | `INVALID_PASSWORD` | 현재 비밀번호 불일치 |
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `409` | `ACCOUNT_WITHDRAWAL_NOT_ALLOWED` | 보안 필터 통과 후 동시 상태 변경 등으로 탈퇴 가능한 상태가 아니게 된 경우 |
| `409` | `UPCOMING_WORK_EXISTS` | 도시농부 또는 농가에 `MATCHED` 상태의 확정 근무가 존재 |

확정 근무가 있으면 관련 데이터를 임의 취소하지 않고 탈퇴 전체를 롤백한다.
이미 탈퇴한 계정이 기존 JWT로 다시 호출하면 일반적으로 활성 계정 필터에서 먼저 `401 INVALID_ACCOUNT`가 반환된다.

```json
{
  "code": "UPCOMING_WORK_EXISTS",
  "message": "확정된 근무가 있으면 계정을 탈퇴할 수 없습니다. 담당자에게 문의해 주세요."
}
```

### Postman 팁

성공 후 저장된 `accessToken` 환경변수를 지운다. 탈퇴 전후 상태를 비교하려면 탈퇴 직전에 `/api/auth/me`를 호출해 응답을 보관한다.

---

## 9. 로그아웃

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/auth/logout` |
| 권한 | 활성 계정, 역할 제한 없음 |
| Content-Type | 요청 본문 없음 |

```http
POST /api/auth/logout
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `204 No Content`
- Response Body 없음

### 현재 제한

- 서버는 세션, Refresh Token, 토큰 블랙리스트를 사용하지 않는다.
- 로그아웃 API는 서버에서 JWT를 폐기하지 않는다.
- 클라이언트가 저장한 JWT를 삭제해야 한다.
- 계정 상태가 계속 `ACTIVE`라면 기존 JWT는 만료 시각까지 암호학적으로 유효하다.

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `401` | `INVALID_ACCOUNT` | 비활성 계정 또는 JWT 역할 불일치 |

### Postman 팁

로그아웃 호출 뒤 환경변수 삭제:

```javascript
pm.environment.unset("accessToken");
```

---

## 10. 현재 미지원 범위

- 네이버·카카오 등 소셜 로그인
- 휴대전화·실명 본인인증
- Refresh Token
- 비밀번호 변경·재설정
- 서버 측 토큰 강제 폐기
- backend-1의 중개센터 계정 발급 API
