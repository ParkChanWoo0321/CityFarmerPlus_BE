# CityFarmerPlus 회원·인증 API 명세서

- 문서 버전: 2.0
- 갱신일: 2026-08-20
- 구현 기준 브랜치: `backend-1`
- 적용 범위: 회원가입, 아이디 확인, 로그인, 내 정보 조회·수정, 회원 탈퇴, 로그아웃
- 관련 문서: [FULL_API_SPEC.md](FULL_API_SPEC.md), [API_SPEC_INDEX.md](API_SPEC_INDEX.md)

## 1. 공통 사항

로컬 기본 URL:

```text
http://localhost:8080
```

보호 API의 인증 헤더:

```http
Authorization: Bearer {{accessToken}}
```

공통 오류 형식:

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

## 2. 역할과 계정 상태

| `userType` | 의미 | 공개 회원가입 |
|---|---|---|
| `URBAN_FARMER` | 도시농부 | 가능 |
| `FARM` | 농가 | 가능 |
| `CENTER_ADMIN` | backend-2 담당자 공통 역할 | 불가능 |

한 계정은 하나의 역할만 가진다. 본인인증은 하지 않으므로 같은 사람이 서로 다른 아이디로 두 일반 역할 계정을 각각 만드는 것은 차단하지 못한다.

| `accountStatus` | 의미 |
|---|---|
| `ACTIVE` | 정상 사용 가능 |
| `SUSPENDED` | 정지 |
| `WITHDRAWN` | 탈퇴 |

신규 회원은 `ACTIVE`로 생성된다. `CENTER_ADMIN` enum과 JWT 역할 변환은 backend-2 병합용 공통 계약이지만, backend-1에는 담당자 계정 발급 또는 담당자 업무 처리 API가 없다.

## 3. API 목록

| 기능 | Method | URL | 인증 | 성공 |
|---|---|---|---|---|
| 회원가입 | `POST` | `/api/auth/signup` | 불필요 | `201 Created` |
| 아이디 중복 확인 | `GET` | `/api/auth/check-id` | 불필요 | `200 OK` |
| 로그인 | `POST` | `/api/auth/login` | 불필요 | `200 OK` |
| 내 정보 조회 | `GET` | `/api/auth/me` | Bearer JWT | `200 OK` |
| 내 정보 수정 | `PATCH` | `/api/auth/me` | Bearer JWT | `200 OK` |
| 회원 탈퇴 | `POST` | `/api/auth/withdrawal` | Bearer JWT | `204 No Content` |
| 로그아웃 | `POST` | `/api/auth/logout` | Bearer JWT | `204 No Content` |

## 4. 회원가입

```http
POST /api/auth/signup
Content-Type: application/json
```

```json
{
  "loginId": "farm_user",
  "password": "password123!",
  "name": "농가 사용자",
  "userType": "FARM",
  "phoneNumber": "010-1234-5678",
  "birthDate": "1985-03-12",
  "address": "충청북도 충주시"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `loginId` | String | O | 영문 소문자·숫자·밑줄 4~30자 |
| `password` | String | O | 8~64자, UTF-8 72바이트 이하 |
| `name` | String | O | 공백만 입력 불가, 최대 50자 |
| `userType` | Enum | O | `URBAN_FARMER` 또는 `FARM` |
| `phoneNumber` | String | X | 숫자 10~11자리, 하이픈 허용 |
| `birthDate` | Date | X | 미래일 불가 |
| `address` | String | X | 최대 255자 |

`loginId`는 소문자로 정규화하고, 전화번호는 숫자만 저장한다. 비밀번호는 BCrypt 해시로 저장한다.

성공 응답:

```json
{
  "id": 1,
  "loginId": "farm_user",
  "name": "농가 사용자",
  "phoneNumber": "01012345678",
  "birthDate": "1985-03-12",
  "address": "충청북도 충주시",
  "userType": "FARM",
  "accountStatus": "ACTIVE"
}
```

대표 오류:

| HTTP | 코드 | 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 형식·길이 위반 |
| `400` | `MANAGER_SIGNUP_NOT_ALLOWED` | `CENTER_ADMIN` 공개 가입 시도 |
| `409` | `DUPLICATE_LOGIN_ID` | 이미 사용 중인 아이디 |

## 5. 아이디 중복 확인

```http
GET /api/auth/check-id?loginId=farm_user
```

`loginId`는 회원가입과 같은 4~30자 패턴을 사용한다.

```json
{
  "loginId": "farm_user",
  "available": true
}
```

이미 사용 중이면 HTTP 상태는 그대로 `200`이고 `available=false`다.

## 6. 로그인

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

성공 응답:

```json
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
```

아이디가 없거나 비밀번호가 틀리면 모두 `401 INVALID_CREDENTIALS`로 응답한다. 비활성 계정은 `403 INACTIVE_ACCOUNT`다.

## 7. 내 정보 조회

```http
GET /api/auth/me
Authorization: Bearer {{accessToken}}
```

응답은 회원가입의 `UserResponse` 형식과 같다.

## 8. 내 정보 수정

```http
PATCH /api/auth/me
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

```json
{
  "name": "수정한 이름",
  "phoneNumber": "010-9999-8888",
  "birthDate": "1985-03-12",
  "address": "충청북도 청주시"
}
```

모든 필드는 선택이며 전달한 필드만 수정한다.

| 필드 | 규칙 |
|---|---|
| `name` | 공백만 입력 불가, 최대 50자 |
| `phoneNumber` | 숫자 10~11자리, 하이픈 허용. 빈 문자열은 `null`로 저장 |
| `birthDate` | 미래일 불가. `null`은 변경하지 않음 |
| `address` | 최대 255자. 빈 문자열은 `null`로 저장 |

`loginId`, `userType`, `accountStatus`, 비밀번호는 이 API로 바꿀 수 없다.

## 9. 회원 탈퇴

```http
POST /api/auth/withdrawal
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

```json
{
  "password": "password123!"
}
```

현재 비밀번호를 다시 확인한 뒤 계정을 `WITHDRAWN`으로 변경한다.

- 도시농부에게 `MATCHED` 지원이 있으면 `409 UPCOMING_WORK_EXISTS`다.
- 탈퇴 가능한 도시농부의 `APPLIED` 지원은 `WITHDRAWN`으로 정리한다.
- 도시농부의 사업참여 신청 중 `DRAFT`, `SUBMITTED`, `REJECTED`는 `CANCELLED`로 정리하고, `APPROVED` 신청과 기존 심사 이력은 유지한다.
- 교육 `PENDING_REVIEW` 제출은 메타데이터를 유지하되 활성 계정 전용 담당자 쿼리에서 제외한다.
- 농가의 미종결 공고에 `MATCHED` 지원이 있으면 탈퇴할 수 없다.
- 탈퇴 가능한 농가의 미종결 공고는 `CANCELLED`, 남은 지원은 `POSTING_CANCELLED`, 농가 프로필은 `INACTIVE`가 된다.
- 교육 및 농가 소유 증빙 파일은 커밋 후 삭제하고, 실패하면 기록된 삭제 작업을 백그라운드에서 재시도한다.

비밀번호가 틀리면 `401 INVALID_PASSWORD`, 이미 탈퇴·정지 상태면 `409 ACCOUNT_WITHDRAWAL_NOT_ALLOWED`다.

## 10. 로그아웃

```http
POST /api/auth/logout
Authorization: Bearer {{accessToken}}
```

서버 세션과 토큰 블랙리스트를 사용하지 않는다. 응답은 `204`이며 클라이언트가 저장한 JWT를 삭제해야 한다. 기존 토큰은 만료 전까지 암호학적으로는 유효하지만, 계정 상태가 달라졌다면 활성 계정 필터가 요청을 거절한다.

## 11. JWT 계약

| 항목 | 값 |
|---|---|
| 알고리즘 | `HS256` |
| 기본 만료 | 1시간 |
| `sub` | 회원 ID 문자열 |
| `role` | `URBAN_FARMER`, `FARM`, `CENTER_ADMIN` |
| Refresh Token | 미지원 |
| 서버 블랙리스트 | 미지원 |

`role`은 각각 `ROLE_URBAN_FARMER`, `ROLE_FARM`, `ROLE_CENTER_ADMIN`으로 변환된다. JWT가 유효해도 DB 계정이 `ACTIVE`가 아니거나 토큰 역할과 DB 역할이 다르면 활성 계정 필터가 `401 INVALID_ACCOUNT`를 반환한다.

## 12. 환경 변수

| 환경 변수 | 필수 | 설명 |
|---|---:|---|
| `DB_URL` | X | MySQL URL |
| `DB_USERNAME` | X | 기본 `root` |
| `DB_PASSWORD` | O | MySQL 비밀번호 |
| `JPA_DDL_AUTO` | X | 기본 `update` |
| `JWT_SECRET` | O | UTF-8 32바이트 이상 |
| `JWT_ISSUER` | X | JWT 발급자 |
| `JWT_ACCESS_TOKEN_EXPIRATION` | X | 기본 `1h` |

개발용 자리표시자 비밀키는 실제 키로 교체해야 한다.

## 13. 현재 미지원

- 소셜 로그인과 본인인증
- Refresh Token과 비밀번호 재설정
- 비밀번호 변경
- 서버 측 강제 로그아웃
- backend-1의 담당자 계정 발급 및 담당자 업무 처리 API
