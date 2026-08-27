# CityFarmerPlus API 공통 계약

- 기준일: 2026-08-27
- 기준 소스: 현재 `main` 통합 코드의 Controller, DTO, Security, Service, Exception
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 사용자 HTTP API: 67개 (`OPTIONS`와 관리자·health 제외)
- 업무 시간대: 공고·지원·근무·대시보드에서 현재 시각을 비교할 때 `Asia/Seoul`을 사용한다. 전역 서버/Jackson 시간대 설정을 의미하지 않는다.

> 이 문서는 노션에 단독으로 복사할 수 있는 공통 계약 문서다. 기능별 요청·응답은 같은 폴더의 개별 문서를 참고한다.

---

## 1. 인증 방식

보호 API는 로그인 응답으로 받은 JWT를 Bearer Token으로 전송한다.

```http
Authorization: Bearer {{accessToken}}
```

JWT 기본 계약:

| 항목 | 값 |
|---|---|
| 서명 알고리즘 | `HS256` |
| 기본 유효시간 | 1시간 |
| 사용자 식별자 | `sub` Claim의 회원 ID 문자열 |
| 역할 | `role` Claim |
| Refresh Token | 미지원 |
| 서버 세션 | 사용하지 않음 |
| 서버 토큰 블랙리스트 | 미지원 |

JWT 자체가 유효하더라도 DB의 현재 계정이 `ACTIVE`가 아니거나 토큰 역할과 DB 역할이 다르면 요청을 거절한다.

```json
{
  "code": "INVALID_ACCOUNT",
  "message": "현재 계정으로 인증할 수 없습니다."
}
```

### 인증 없이 호출 가능한 API

| Method | URL |
|---|---|
| `POST` | `/api/auth/signup` |
| `GET` | `/api/auth/check-id` |
| `POST` | `/api/auth/login` |
| `GET` | `/api/education/courses` |
| `GET` | `/api/job-postings` |
| `GET` | `/api/job-postings/{postingId}` |
| `GET` | `/api/support/faqs` |
| `GET` | `/api/market-prices/latest` |
| `GET` | `/health` |
| `GET` | `/health/live` |
| `POST` | `/api/internal/center-admins` (provisioning key가 설정된 동안만) |
| `OPTIONS` | `/api/**` |
| `OPTIONS` | `/health` |
| `OPTIONS` | `/health/**` |

그 외 API는 현재 Security 설정상 Bearer JWT가 필요하다. 공개 공고 조회에 JWT는 선택 사항이며, 유효한 JWT를 보내면 현재 사용자의 지원 정보로 응답이 개인화된다. 공개 API에 잘못된 Bearer JWT를 보내면 401을 반환한다.

---

## 2. 사용자 역할

| `userType` | 설명 | 공개 회원가입 |
|---|---|---|
| `URBAN_FARMER` | 도시농부 | 가능 |
| `FARM` | 농가 | 가능 |
| `CENTER_ADMIN` | 중개센터 담당자 | 불가능 |

- 한 계정은 하나의 역할만 가진다.
- 공개 회원가입에서 `CENTER_ADMIN`을 전달하면 거절한다.
- 중개센터 계정은 운영 기본 비활성인 내부 발급 API로만 만들 수 있다.
- `/api/admin/**` 42개가 사업참여·교육·농가·공고 심사, 최종 매칭, 근무 정정, 대리 접수와 대시보드를 처리한다.

---

## 3. 요청 형식

### JSON

```http
Content-Type: application/json
```

### 파일 업로드

교육 이수증과 농가 소유 증빙은 다음 형식을 사용한다.

```http
Content-Type: multipart/form-data
```

Postman에서는 `Body > form-data`를 선택하고 `Content-Type`의 boundary를 직접 입력하지 않는다.

기능별 파트 구성:

| Key | Type | 설명 |
|---|---|---|
| `request` | JSON Part | 교육 이수증 제출에서 사용하는 제출 정보 |
| `documents` | File | 교육 이수증 또는 농가 소유 증빙 파일 |

농가 소유 증빙 제출에는 `request` Part가 없고 `documents`만 사용한다. 교육의 `request` Part는 Part 자체의 Content-Type을 `application/json`으로 설정한다.

서버 전체 multipart 제한:

| 항목 | 제한 |
|---|---:|
| 파일 한 개에 대한 Spring 수신 제한 | 12MB |
| 요청 전체에 대한 Spring 수신 제한 | 31MB |

도메인별 실제 허용 크기·개수·확장자는 교육 및 농가 소유 증빙 문서에 별도로 적혀 있다.

---

## 4. 응답 형식

### 일반 JSON

```http
Content-Type: application/json
```

### 본문 없는 성공 응답

삭제·취소·로그아웃 등 일부 API는 다음과 같이 본문 없이 응답한다.

```http
HTTP/1.1 204 No Content
```

### 파일 다운로드

- 저장 당시의 Content-Type을 응답한다.
- `Content-Disposition` 헤더로 원본 파일명을 전달한다.
- CORS 설정에서 `Content-Disposition`을 프론트엔드에 노출한다.

---

## 5. 공통 페이지 응답

목록 API의 기본값:

| Query | 기본값 | 제약 |
|---|---:|---:|
| `page` | 0 | 0 이상 |
| `size` | 20 | 1~100 |

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

`content` 내부 객체는 기능별 응답 DTO에 따라 달라진다.

---

## 6. 공통 오류 응답

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

| HTTP | 공통 코드 | 발생 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | DTO 또는 Query Parameter 검증 실패 |
| 400 | `INVALID_REQUEST` | JSON 본문 파싱 실패, 잘못된 Enum·날짜 형식 |
| 400 | `INVALID_REQUEST_PARAMETER` | Path 또는 Query Parameter 타입 변환 실패 |
| 400 | `MISSING_REQUEST_PARAMETER` | 필수 Query Parameter 누락 |
| 400 | `MISSING_MULTIPART_PART` | 필수 multipart 파트 누락 |
| 400 | `INVALID_ARGUMENT` | 처리 중 발견한 잘못된 요청 값 |
| 401 | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| 401 | `INVALID_ACCOUNT` | 탈퇴·정지 계정 또는 토큰 역할 불일치 |
| 403 | `ACCESS_DENIED` | 역할 권한 부족 |
| 404 | 도메인별 `*_NOT_FOUND` | 대상 데이터가 없음 |
| 404 | `RESOURCE_NOT_FOUND` | 매핑되지 않은 URL |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP Method |
| 409 | `DATA_CONFLICT` | DB 고유 제약 또는 무결성 충돌 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 낙관적·비관적 잠금 충돌 |
| 409 | `INVALID_STATE` | 현재 상태에서 수행할 수 없는 요청 |
| 413 | `UPLOAD_REQUEST_TOO_LARGE` | 서버 multipart 수신 제한 초과 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 요청 Content-Type |
| 500 | `INTERNAL_SERVER_ERROR` | 예상하지 못한 서버 오류. 내부 상세는 응답하지 않음 |

업무 규칙 오류 코드는 각 기능 문서에 별도로 정리한다.

---

## 7. 공통 날짜·시간 형식

| Java 타입 | JSON 예시 |
|---|---|
| `LocalDate` | `2026-08-20` |
| `LocalTime` | `09:00:00` |
| `Instant` | `2026-08-11T01:30:00Z` |

날짜·시간 문자열은 ISO-8601 형식을 사용한다.

---

## 8. 공통 지역·요일 값

### 충청북도 시·군 `ChungbukCityCounty`

```text
CHEONGJU
CHUNGJU
JECHEON
BOEUN
OKCHEON
YEONGDONG
JEUNGPYEONG
JINCHEON
GOESAN
EUMSEONG
DANYANG
```

### 요일 `DayOfWeek`

```text
MONDAY
TUESDAY
WEDNESDAY
THURSDAY
FRIDAY
SATURDAY
SUNDAY
```

---

## 9. CORS 계약

허용 Method:

```text
GET, POST, PUT, PATCH, DELETE, OPTIONS
```

허용 요청 헤더:

```text
Authorization, Content-Type, Accept, Origin
```

프론트엔드에 노출하는 응답 헤더:

```text
Content-Disposition
```

- 쿠키 기반 인증을 사용하지 않으므로 `allowCredentials=false`다.
- 허용 Origin은 환경변수 `CORS_ALLOWED_ORIGINS`의 쉼표 구분 목록으로 설정한다.
- 로컬 기본값은 `localhost`와 `127.0.0.1`의 5173·3000 포트다.
- 동일한 CORS 정책은 `/api/**`와 프론트 상태 확인용 `/health`에 적용된다.

---

## 10. Postman 공통 환경 변수

| 변수 | 예시 |
|---|---|
| `baseUrl` | 운영 `https://cityfarmerplus-api-82951616760.us-west1.run.app` 또는 로컬 `http://localhost:8080` |
| `accessToken` | 로그인 응답의 `accessToken` |
| `urbanFarmerAccessToken` | 도시농부 JWT |
| `farmAccessToken` | 농가 JWT |

보호 API의 Authorization 탭에서 `Bearer Token`을 선택하고 역할에 맞는 토큰을 사용한다.

---

## 11. 확정 제외 범위

다음 기능은 현재 API 범위에 포함하지 않는다.

- 네이버·카카오 등 소셜 로그인
- 본인인증
- 알림함·푸시·문자 알림
- 정산 상태·정산 CSV
- 결제·송금·계좌 관리
- 전체 관리용 `SUPER_ADMIN`
- 농가·도시농부 간 채팅

공고의 임금 정보는 작업 조건 안내용 데이터이며 서버가 결제를 처리하지 않는다.
