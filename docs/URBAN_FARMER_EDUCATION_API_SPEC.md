# CityFarmerPlus 도시농부 교육 이수 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-06
- 구현 기준 브랜치: `backend-2`
- 적용 범위: 내 교육 이수 상태 조회, 수료증 등록, 교육 이수 완료 처리

## 1. 공통 사항

### 1.1 기본 URL

```text
http://localhost:8080
```

### 1.2 요청 및 응답 형식

- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{accessToken}}
```

- 이 API들은 요청 본문을 받지 않는다.

### 1.3 인증 및 권한 규칙

- `userType`이 `URBAN_FARMER`인 회원의 JWT만 호출할 수 있다(`/api/urban-farmer/**`에 `hasRole("URBAN_FARMER")` 적용).
- 대상 계정은 요청값이 아니라 JWT의 `sub`(회원 ID)로 결정한다.
- 이 API들은 [`URBAN_FARMER_PROFILE_API_SPEC.md`](./URBAN_FARMER_PROFILE_API_SPEC.md)로 등록한 도시농부 프로필(`UrbanFarmerProfile`)의 `educationStatus` 필드를 조회·변경한다. **프로필을 먼저 등록해야 하며, 자동으로 빈 프로필을 생성하지 않는다.**

### 1.4 공통 오류 응답 형식

```json
{
  "message": "오류 설명"
}
```

`401`·`403` 오류는 이 형식을 따르지 않는다. 자세한 내용은 6장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 교육 이수 상태 조회 | `GET` | `/api/urban-farmer/education` | Bearer JWT (`ROLE_URBAN_FARMER`) | `200 OK` |
| 수료증 등록 | `POST` | `/api/urban-farmer/education/certificate` | Bearer JWT (`ROLE_URBAN_FARMER`) | `200 OK` |
| 교육 이수 완료 처리 | `POST` | `/api/urban-farmer/education/complete` | Bearer JWT (`ROLE_URBAN_FARMER`) | `200 OK` |

## 3. 교육 이수 상태 조회

### 3.1 요청

```http
GET /api/urban-farmer/education
Authorization: Bearer {{accessToken}}
```

### 3.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "educationStatus": "NOT_COMPLETED"
}
```

### 3.3 오류 응답

| 상태 | 발생 조건 |
|---|---|
| `400` | 도시농부 프로필을 아직 등록하지 않은 계정으로 조회 |
| `401` | JWT 누락, 만료 또는 위조 |
| `403` | `URBAN_FARMER`가 아닌 계정의 JWT로 접근 |

프로필 미등록 예시:

```json
{
  "message": "먼저 프로필을 등록해주세요."
}
```

## 4. 수료증 등록

지금은 실제 파일 업로드를 지원하지 않는다. 수료증을 등록했다는 사실만 기록하며, 호출 시 상태를 `CERTIFICATE_REGISTERED`로 변경한다.

### 4.1 요청

```http
POST /api/urban-farmer/education/certificate
Authorization: Bearer {{accessToken}}
```

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "educationStatus": "CERTIFICATE_REGISTERED"
}
```

### 4.3 오류 응답

| 상태 | 발생 조건 |
|---|---|
| `400` | 도시농부 프로필을 아직 등록하지 않은 계정으로 호출 |
| `401` | JWT 누락, 만료 또는 위조 |
| `403` | `URBAN_FARMER`가 아닌 계정의 JWT로 접근 |

이전 `educationStatus` 값과 무관하게 항상 `CERTIFICATE_REGISTERED`로 덮어쓴다. 상태 전이 순서(예: `NOT_COMPLETED`에서만 호출 가능)는 검증하지 않는다.

## 5. 교육 이수 완료 처리

지금은 관리자 승인 절차 없이 상태를 `COMPLETED`로 변경하는 것까지만 구현되어 있다. **추후 중개센터 관리자 기능 추가 시 승인 절차를 거치도록 변경할 예정이다.**

### 5.1 요청

```http
POST /api/urban-farmer/education/complete
Authorization: Bearer {{accessToken}}
```

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "educationStatus": "COMPLETED"
}
```

### 5.3 오류 응답

| 상태 | 발생 조건 |
|---|---|
| `400` | 도시농부 프로필을 아직 등록하지 않은 계정으로 호출 |
| `401` | JWT 누락, 만료 또는 위조 |
| `403` | `URBAN_FARMER`가 아닌 계정의 JWT로 접근 |

이전 `educationStatus` 값과 무관하게 항상 `COMPLETED`로 덮어쓴다. 수료증 등록(`CERTIFICATE_REGISTERED`) 여부는 검증하지 않는다.

## 6. 인증·인가 오류 응답 주의사항

- `401`: `SecurityConfig`의 `authenticationEntryPoint`가 `response.sendError(401, "인증이 필요합니다.")`를 호출하며, 실제 응답 본문은 Spring Boot 기본 에러 핸들러가 생성한다. `{ "message" }` 형식이 아니다.
- `403`: 별도의 `AccessDeniedHandler`가 없어 Spring Security 기본 처리를 따른다. 마찬가지로 `{ "message" }` 형식이 아니다.

## 7. 교육 이수 상태 값

| 값 | 의미 | 이 기능에서 전이 방법 |
|---|---|---|
| `NOT_COMPLETED` | 교육 미이수 | 프로필 등록 시 기본값 |
| `APPLICABLE` | 교육 신청 가능 | 이 기능에서 전이시키는 API 없음 (향후 교육 신청 기능에서 처리 예정) |
| `CERTIFICATE_REGISTERED` | 수료증 등록됨 | `POST /api/urban-farmer/education/certificate` |
| `COMPLETED` | 교육 이수 완료 | `POST /api/urban-farmer/education/complete` |

## 8. 데이터 구조

`educationStatus`는 별도 테이블이 아니라 `urban_farmer_profiles.education_status` 컬럼에 저장된다. 컬럼 정의는 [`URBAN_FARMER_PROFILE_API_SPEC.md`](./URBAN_FARMER_PROFILE_API_SPEC.md) 7장을 참고한다.

## 9. 현재 범위 밖 또는 미구현 기능

- 실제 수료증 파일 업로드·보관·열람
- `APPLICABLE` 상태로 전환하는 교육 신청 API
- 상태 전이 순서 검증(예: `CERTIFICATE_REGISTERED` 상태에서만 완료 처리 허용)
- 중개센터 관리자의 수료증 검토·승인·반려
- 계정 상태(정지·탈퇴)에 따른 접근 차단
