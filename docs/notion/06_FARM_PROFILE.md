# CityFarmerPlus 농가 프로필 API

- 기준일: 2026-08-20
- 기준 소스: 현재 `backend-1` 작업 폴더의 FarmProfile Controller, DTO, Service, Entity, Exception
- 로컬 Base URL: `http://localhost:8080`
- 구현 API: 3개

> 이 문서는 다른 문서를 보지 않아도 노션에 단독으로 복사할 수 있는 농가 프로필 명세다. 현재 backend-1은 농가 본인의 프로필 생성·조회·수정만 제공한다. 담당자의 농가 소유 승인·반려 API는 backend-2 범위다.

---

## 1. 기능 개요

- `FARM` 역할의 활성 계정만 농가 프로필을 사용할 수 있다.
- 한 농가 계정은 농가 프로필을 하나만 가진다.
- 프로필 소유자는 요청값이 아니라 JWT `sub`의 회원 ID로 결정한다.
- 프로필 생성 직후 상태는 `DRAFT`다.
- 농가 소유 증빙 제출 시 상태가 `PENDING_REVIEW`로 변경된다.
- 담당자의 승인·반려 결과는 각각 `APPROVED`, `REJECTED`로 저장되는 공통 도메인 계약을 사용한다.
- 농가 소유가 승인된 프로필만 농가 모집 공고 기능을 사용할 수 있다.
- 회원 탈퇴 처리 시 농가 프로필은 `INACTIVE`로 변경된다.

---

## 2. 인증과 권한

모든 API에 `FARM` JWT가 필요하다.

```http
Authorization: Bearer {{farmAccessToken}}
```

| 상황 | HTTP | 코드 |
|---|---:|---|
| JWT 누락·만료·위조 | 401 | `UNAUTHORIZED` |
| JWT의 회원 ID가 숫자가 아님, 탈퇴·정지 계정, 토큰 역할과 DB 역할 불일치 | 401 | `INVALID_ACCOUNT` |
| `URBAN_FARMER`, `CENTER_ADMIN` JWT로 호출 | 403 | `ACCESS_DENIED` |
| 보안 필터 통과 직후 계정 역할이 바뀐 경쟁 상황을 서비스가 다시 방어 | 403 | `FARM_ROLE_REQUIRED` |
| 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 | 404 | `USER_NOT_FOUND` |

오류 응답 형식:

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

---

## 3. API 목록

| 기능 | Method | URL | 권한 | 성공 상태 |
|---|---|---|---|---:|
| 농가 프로필 생성 | `POST` | `/api/farm-profiles` | `FARM` | 201 |
| 내 농가 프로필 조회 | `GET` | `/api/farm-profiles/me` | `FARM` | 200 |
| 내 농가 프로필 수정 | `PATCH` | `/api/farm-profiles/me` | `FARM` | 200 |

---

## 4. 공통 요청 필드

생성과 수정은 같은 필드와 검증 규칙을 사용한다.

| 필드 | 타입 | 필수 | 검증 |
|---|---|---|---|
| `farmName` | String | O | 공백 문자열 불가, 최대 100자 |
| `representativeName` | String | O | 공백 문자열 불가, 최대 50자 |
| `contactNumber` | String | O | `^0\d{1,2}-?\d{3,4}-?\d{4}$` |
| `farmAddress` | String | O | 공백 문자열 불가, 최대 255자 |
| `cityCounty` | Enum | O | 충북 11개 시·군 코드 중 하나 |
| `crops` | String Array | O | 1~20개, 각 항목 공백 불가·최대 50자 |
| `mainActivities` | String | O | 공백 문자열 불가, 최대 2,000자 |
| `businessRegistrationNumber` | String | X | 생략·`null`·빈 문자열 허용, 입력 시 `000-00-00000` 또는 숫자 10자리 |
| `farmAreaPyeong` | Integer | O | 1~100,000,000평 |

### 정규화 규칙

- `farmName`, `representativeName`, `farmAddress`, `mainActivities`는 앞뒤 공백을 제거한다.
- 연락처는 하이픈 등 숫자가 아닌 문자를 제거하고 숫자만 저장한다.
- 사업자등록번호는 값이 있으면 숫자가 아닌 문자를 제거하고 숫자만 저장한다.
- 사업자등록번호를 생략하거나 `null`, 빈 문자열로 보내면 DB에는 `null`로 저장한다.
- 작물명은 각 항목의 앞뒤 공백을 제거한다.
- 작물명 중복은 대소문자를 무시하고 제거하며 첫 번째 입력 순서를 유지한다.
- 서버가 허용 목록과 실제 작물명을 검증하는 기능은 없다.

### 충청북도 시·군 코드

| 코드 | 지역명 |
|---|---|
| `CHEONGJU` | 청주시 |
| `CHUNGJU` | 충주시 |
| `JECHEON` | 제천시 |
| `BOEUN` | 보은군 |
| `OKCHEON` | 옥천군 |
| `YEONGDONG` | 영동군 |
| `JEUNGPYEONG` | 증평군 |
| `JINCHEON` | 진천군 |
| `GOESAN` | 괴산군 |
| `EUMSEONG` | 음성군 |
| `DANYANG` | 단양군 |

`SEOUL`처럼 목록에 없는 값을 보내면 `400 INVALID_REQUEST`다.

---

## 5. 농가 프로필 생성

### `POST /api/farm-profiles`

### 요청

```http
POST {{baseUrl}}/api/farm-profiles
Authorization: Bearer {{farmAccessToken}}
Content-Type: application/json
```

```json
{
  "farmName": "충주 사과농원",
  "representativeName": "홍길동",
  "contactNumber": "010-1234-5678",
  "farmAddress": "충청북도 충주시 예시로 1",
  "cityCounty": "CHUNGJU",
  "crops": ["사과", "복숭아"],
  "mainActivities": "사과 재배와 수확 작업을 합니다.",
  "businessRegistrationNumber": "123-45-67890",
  "farmAreaPyeong": 1200
}
```

### 성공 응답

```http
HTTP/1.1 201 Created
Location: /api/farm-profiles/me
Content-Type: application/json
```

```json
{
  "id": 100,
  "farmName": "충주 사과농원",
  "representativeName": "홍길동",
  "contactNumber": "01012345678",
  "farmAddress": "충청북도 충주시 예시로 1",
  "cityCounty": "CHUNGJU",
  "crops": ["사과", "복숭아"],
  "mainActivities": "사과 재배와 수확 작업을 합니다.",
  "businessRegistrationNumber": "1234567890",
  "farmAreaPyeong": 1200,
  "status": "DRAFT",
  "reviewerId": null,
  "reviewerName": null,
  "reviewedAt": null,
  "rejectionReason": null,
  "createdAt": "2026-08-11T01:00:00Z",
  "updatedAt": "2026-08-11T01:00:00Z"
}
```

### 주요 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 필수값 누락, 길이·형식·개수·면적 범위 위반 |
| 400 | `INVALID_REQUEST` | 잘못된 JSON 또는 존재하지 않는 시·군 Enum |
| 409 | `FARM_PROFILE_ALREADY_EXISTS` | 해당 농가 계정에 이미 프로필이 있음 |
| 409 | `FARM_PROFILE_DATA_CONFLICT` | 동시 생성 등으로 DB 고유 제약 충돌 |

---

## 6. 내 농가 프로필 조회

### `GET /api/farm-profiles/me`

### 요청

```http
GET {{baseUrl}}/api/farm-profiles/me
Authorization: Bearer {{farmAccessToken}}
```

Query Parameter와 Request Body는 없다.

### 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 5절의 `FarmProfileResponse`와 동일하다.

### 프로필이 없는 경우

```http
HTTP/1.1 404 Not Found
```

```json
{
  "code": "FARM_PROFILE_NOT_FOUND",
  "message": "농가 프로필을 찾을 수 없습니다."
}
```

공개 프로필 조회나 다른 농가의 프로필을 ID로 조회하는 API는 현재 없다.

---

## 7. 내 농가 프로필 수정

### `PATCH /api/farm-profiles/me`

### 요청

```http
PATCH {{baseUrl}}/api/farm-profiles/me
Authorization: Bearer {{farmAccessToken}}
Content-Type: application/json
```

요청 JSON은 5장의 생성 요청과 같은 형식이다.

> HTTP Method는 `PATCH`지만 DTO의 모든 필수 필드가 검증된다. 부분 수정 API가 아니므로 현재 값을 유지할 필드도 전부 보내야 한다.

### 성공 응답

```http
HTTP/1.1 200 OK
```

수정된 `FarmProfileResponse`를 반환한다.

### 상태별 수정 가능 여부

| 현재 상태 | 수정 | 결과 |
|---|---|---|
| `DRAFT` | 가능 | `DRAFT` 유지 |
| `PENDING_REVIEW` | 불가 | `409 FARM_PROFILE_UPDATE_NOT_ALLOWED` |
| `APPROVED` | 조건부 가능 | 변경 필드에 따라 승인 유지 또는 `DRAFT` 복귀 |
| `REJECTED` | 가능 | `REJECTED` 유지 |
| `INACTIVE` | 불가 | 일반적으로 인증 필터에서 먼저 `401 INVALID_ACCOUNT` |

### 승인 상태에서 수정할 때

다음 필드는 농가 소유 동일성을 판단하는 핵심 필드다.

```text
farmName
representativeName
farmAddress
cityCounty
businessRegistrationNumber
farmAreaPyeong
```

- 핵심 필드가 하나라도 달라지면 `APPROVED → DRAFT`로 돌아간다.
- 이때 `reviewerId`, `reviewerName`, `reviewedAt`, `rejectionReason`을 초기화한다.
- 다시 농가 소유 증빙을 제출하고 담당자 승인을 받아야 한다.
- 단, 해당 농가에 `CANCELLED`, `WORK_COMPLETED` 이외 상태의 모집 공고가 하나라도 있으면 핵심 필드 변경을 거절한다.
- `contactNumber`, `crops`, `mainActivities`만 변경하면 `APPROVED` 상태를 유지한다.
- 핵심 필드가 같다면 활성 공고가 있어도 비핵심 정보 수정은 가능하다.

### 수정 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 전체 필드 누락 또는 검증 위반 |
| 400 | `INVALID_REQUEST` | 잘못된 JSON 또는 Enum |
| 404 | `FARM_PROFILE_NOT_FOUND` | 본인 농가 프로필이 없음 |
| 409 | `FARM_PROFILE_UPDATE_NOT_ALLOWED` | 심사 중·비활성 상태 또는 활성 공고가 있는 승인 농가의 핵심 정보 변경 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청과 농가 프로필 잠금 충돌 |

---

## 8. 응답 필드

| 필드 | 타입 | Nullable | 설명 |
|---|---|---|---|
| `id` | Long | N | 농가 프로필 ID |
| `farmName` | String | N | 농가명 |
| `representativeName` | String | N | 대표자명 |
| `contactNumber` | String | N | 숫자만 저장된 연락처 |
| `farmAddress` | String | N | 농가 상세 주소 |
| `cityCounty` | Enum | N | 충북 시·군 코드 |
| `crops` | String Array | N | 정규화된 재배 작물 목록 |
| `mainActivities` | String | N | 주요 활동 내용 |
| `businessRegistrationNumber` | String | Y | 숫자 10자리 또는 `null` |
| `farmAreaPyeong` | Integer | 신규 데이터 N | 농지 면적(평). 현재 요청은 필수지만 이전 스키마 데이터 호환을 위해 Entity 컬럼은 nullable |
| `status` | Enum | N | 농가 소유 승인 상태 |
| `reviewerId` | Long | Y | 심사 담당자 회원 ID |
| `reviewerName` | String | Y | 심사 담당자 이름 |
| `reviewedAt` | Instant | Y | 승인·반려 처리 시각 |
| `rejectionReason` | String | Y | 반려 사유 |
| `createdAt` | Instant | N | 생성 시각 |
| `updatedAt` | Instant | N | 최근 수정 시각 |

---

## 9. 농가 프로필 상태 전이

```text
프로필 생성
  └─> DRAFT
        └─ 소유 증빙 제출 ─> PENDING_REVIEW
                               ├─ backend-2 승인 ─> APPROVED
                               └─ backend-2 반려 ─> REJECTED
                                                      └─ 재제출 ─> PENDING_REVIEW

APPROVED ── 소유 핵심 정보 변경 ─> DRAFT
회원 탈퇴 ──────────────────────> INACTIVE
```

| 상태 | 의미 | backend-1 사용자 API에서 직접 생성 가능 |
|---|---|---|
| `DRAFT` | 농가 기본 정보만 있는 초안 | O, 프로필 생성 또는 승인 후 핵심 정보 변경 |
| `PENDING_REVIEW` | 소유 증빙 제출 후 담당자 심사 대기 | O, 소유 증빙 제출 결과 |
| `APPROVED` | 담당자가 농가 소유를 승인 | X, backend-2 심사 결과 필요 |
| `REJECTED` | 담당자가 농가 소유를 반려 | X, backend-2 심사 결과 필요 |
| `INACTIVE` | 회원 탈퇴에 따른 비활성화 | O, 계정 탈퇴 처리 내부 동작 |

상태를 요청 JSON으로 직접 변경하는 API는 없다.

---

## 10. 전체 오류 코드

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | DTO 필드 검증 실패 |
| 400 | `INVALID_REQUEST` | JSON 파싱 실패, 잘못된 시·군 Enum |
| 401 | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| 401 | `INVALID_ACCOUNT` | 탈퇴·정지 계정 또는 토큰 역할 불일치 |
| 403 | `ACCESS_DENIED` | 농가 이외 JWT 역할 |
| 403 | `FARM_ROLE_REQUIRED` | 보안 필터 통과 직후 계정 역할이 바뀐 경쟁 상황을 서비스가 다시 방어 |
| 404 | `USER_NOT_FOUND` | 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 |
| 404 | `FARM_PROFILE_NOT_FOUND` | 본인 농가 프로필이 없음 |
| 409 | `FARM_PROFILE_ALREADY_EXISTS` | 계정당 하나인 프로필을 중복 생성 |
| 409 | `FARM_PROFILE_DATA_CONFLICT` | 프로필 생성 DB 제약 충돌 |
| 409 | `FARM_PROFILE_UPDATE_NOT_ALLOWED` | 현재 상태 또는 활성 공고 때문에 수정 불가 |
| 409 | `CONCURRENT_UPDATE_CONFLICT` | 동시 갱신·잠금 충돌 |

---

## 11. backend-2 담당자 기능과의 경계

현재 backend-1에는 다음 HTTP API가 없다.

- 농가 심사 대기 목록·상세 API
- 농가 소유 증빙 담당자 다운로드 API
- 농가 승인 API
- 농가 반려 API
- 담당자가 승인된 농가 정보를 대신 수정하는 API

다만 병합을 위한 다음 공통 계약은 이미 존재한다.

- 담당자 역할 `CENTER_ADMIN`
- 상태 `PENDING_REVIEW`, `APPROVED`, `REJECTED`
- `reviewerId`, `reviewerName`, `reviewedAt`, `rejectionReason`
- 담당자만 심사할 수 있다는 Entity 규칙

따라서 backend-1만 실행하면 프로필 생성과 증빙 제출로 `PENDING_REVIEW`까지 진행할 수 있지만, 정상 HTTP 요청만으로 `APPROVED` 또는 `REJECTED` 상태를 만들 수 없다.

---

## 12. Postman 권장 확인 순서

1. `FARM` 계정으로 로그인해 `farmAccessToken`을 저장한다.
2. `POST /api/farm-profiles`로 프로필을 생성한다.
3. 응답이 `201`, `Location=/api/farm-profiles/me`, `status=DRAFT`인지 확인한다.
4. 동일 계정으로 다시 생성해 `409 FARM_PROFILE_ALREADY_EXISTS`를 확인한다.
5. `GET /api/farm-profiles/me`로 정규화된 연락처·사업자등록번호를 확인한다.
6. `PATCH /api/farm-profiles/me`에서 모든 필드를 보내 수정한다.
7. 도시농부 토큰으로 호출해 `403 ACCESS_DENIED`를 확인한다.
8. 소유 증빙 제출 후 `PENDING_REVIEW` 상태에서 수정을 시도해 `409 FARM_PROFILE_UPDATE_NOT_ALLOWED`를 확인한다.
9. backend-2 승인 기능 병합 후 비핵심 수정은 `APPROVED` 유지, 핵심 수정은 `DRAFT` 복귀인지 확인한다.
