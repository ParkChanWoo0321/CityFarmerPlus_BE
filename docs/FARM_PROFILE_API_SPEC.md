# CityFarmerPlus 농가 프로필 API 명세서

- 문서 버전: 2.0
- 갱신일: 2026-08-20
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 농가 프로필 등록, 내 프로필 조회·수정
- 관련 문서: `FARM_OWNERSHIP_SUBMISSION_API_SPEC.md`, `API_SPEC_INDEX.md`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 로컬 Base URL: `http://localhost:8080`

## 1. 핵심 규칙

- `FARM` 역할의 활성 계정만 농가 프로필을 등록하고 조회할 수 있다.
- 한 농가 계정은 하나의 농가 프로필만 등록할 수 있다.
- 프로필 소유자는 요청값이 아니라 JWT의 `sub` 회원 ID로 결정한다.
- 서비스에서도 DB 사용자의 계정 상태와 실제 역할을 다시 확인한다.
- 신규 농가 프로필은 항상 `DRAFT` 상태로 생성된다.
- `users.account_status=ACTIVE`는 로그인이 가능한 계정이라는 의미다.
- `farm_profiles.status=APPROVED`는 중개센터 심사 결과로 농가 소유가 승인되었다는 의미다.
- 소유 증빙 제출은 `FARM_OWNERSHIP_SUBMISSION_API_SPEC.md`에서 별도로 정의한다.
- `CENTER_ADMIN`은 통합된 담당자 API로 농가 소유 증빙을 조회하고 승인·반려할 수 있다.

## 2. API 목록

| 기능 | Method | URL | 권한 | 성공 상태 |
|---|---|---|---|---|
| 농가 프로필 등록 | `POST` | `/api/farm-profiles` | `ROLE_FARM` | `201 Created` |
| 내 농가 프로필 조회 | `GET` | `/api/farm-profiles/me` | `ROLE_FARM` | `200 OK` |
| 내 농가 프로필 수정 | `PATCH` | `/api/farm-profiles/me` | `ROLE_FARM` | `200 OK` |

모든 요청에 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{farmAccessToken}}
```

## 3. 농가 프로필 등록

### 3.1 요청

```http
POST /api/farm-profiles
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
  "farmAreaPyeong": 1500
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `farmName` | String | O | 공백만 입력 불가, 최대 100자 |
| `representativeName` | String | O | 공백만 입력 불가, 최대 50자 |
| `contactNumber` | String | O | `^0\d{1,2}-?\d{3,4}-?\d{4}$` 형식 |
| `farmAddress` | String | O | 공백만 입력 불가, 최대 255자 |
| `cityCounty` | Enum | O | 충북 11개 시·군 코드 |
| `crops` | String Array | O | 1~20개, 항목별 최대 50자 |
| `mainActivities` | String | O | 공백만 입력 불가, 최대 2,000자 |
| `businessRegistrationNumber` | String | X | 입력 시 숫자 10자리, 하이픈 허용 |
| `farmAreaPyeong` | Integer | O | 1~100,000,000평 |

다음 값은 요청받지 않는다.

- 회원 ID 또는 프로필 소유자 ID
- 프로필 승인 상태
- 승인·반려 담당자
- 소유 증빙 파일 정보

### 3.2 정규화 규칙

- 농가명, 대표자명, 주소, 주요 활동 내용의 앞뒤 공백을 제거한다.
- 연락처와 사업자번호는 숫자가 아닌 문자를 모두 제거하고 숫자만 저장한다.
- 작물명의 앞뒤 공백을 제거한다.
- 동일한 작물명이 여러 번 전달되면 대소문자 차이를 무시하고 최초 값 하나만 저장한다.
- 사업자번호는 선택 정보이며 현재 중복 금지 정책을 적용하지 않는다.

연락처 검증은 위 숫자 배열 형식만 확인하며 실제 국내 국번의 존재 여부나 실사용 번호인지는 확인하지 않는다. 선택 사업자번호는 필드를 생략하거나 빈 문자열(`""`)로 보낼 수 있지만 공백만 있는 문자열은 정규화 전에 검증 오류가 된다.

### 3.3 성공 응답

```http
HTTP/1.1 201 Created
Location: /api/farm-profiles/me
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
  "farmAreaPyeong": 1500,
  "status": "DRAFT",
  "reviewerId": null,
  "reviewerName": null,
  "reviewedAt": null,
  "rejectionReason": null,
  "createdAt": "2026-08-03T00:00:00Z",
  "updatedAt": "2026-08-03T00:00:00Z"
}
```

### 3.4 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | 필수값 누락 또는 형식·길이 위반 |
| `400` | `INVALID_REQUEST` | 잘못된 JSON 또는 존재하지 않는 시·군 코드 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `401` | `INVALID_AUTHENTICATION` | JWT `sub`가 숫자 회원 ID가 아님 |
| `403` | `ACCESS_DENIED` | 도시농부 또는 담당자 JWT로 접근 |
| `403` | `FARM_ROLE_REQUIRED` | JWT 역할과 DB 사용자의 실제 역할이 불일치 |
| `403` | `INACTIVE_ACCOUNT` | 정지 또는 탈퇴한 농가 계정 |
| `404` | `USER_NOT_FOUND` | JWT 회원 ID에 해당하는 사용자가 없음 |
| `409` | `FARM_PROFILE_ALREADY_EXISTS` | 해당 계정에 이미 프로필이 있음 |
| `409` | `FARM_PROFILE_DATA_CONFLICT` | 동시 등록 등 DB 제약 충돌 |
| `409` | `FARM_PROFILE_UPDATE_NOT_ALLOWED` | 심사 중·비활성 상태 또는 활성 공고가 있는 승인 농가의 소유 핵심정보 변경 |

중복 프로필 예시:

```json
{
  "code": "FARM_PROFILE_ALREADY_EXISTS",
  "message": "이미 농가 프로필이 등록되어 있습니다."
}
```

## 4. 내 농가 프로필 조회

### 4.1 요청

```http
GET /api/farm-profiles/me
Authorization: Bearer {{farmAccessToken}}
```

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 등록 성공 응답과 동일한 `FarmProfileResponse` 형식이다.

### 4.3 프로필이 없는 경우

```http
HTTP/1.1 404 Not Found
```

```json
{
  "code": "FARM_PROFILE_NOT_FOUND",
  "message": "농가 프로필을 찾을 수 없습니다."
}
```

ID 기반 공개 조회 API는 제공하지 않는다. 연락처와 상세 주소는 프로필 소유자 본인만 조회할 수 있다.

## 5. 내 농가 프로필 수정

```http
PATCH /api/farm-profiles/me
Authorization: Bearer {{farmAccessToken}}
Content-Type: application/json
```

요청은 등록과 동일한 전체 필드를 사용한다. 부분 수정이 아니므로 `farmAreaPyeong`을 포함한 필수 필드를 모두 전달해야 한다.

- `PENDING_REVIEW`, `INACTIVE` 상태는 수정할 수 없다.
- `DRAFT`, `REJECTED`는 수정 후 현재 상태를 유지한다.
- `APPROVED`에서 연락처·작물·주요 활동만 바꾸면 승인을 유지한다.
- `APPROVED`에서 농가명, 대표자명, 주소, 시·군, 사업자번호, 농지 면적을 바꾸면 소유 재심사를 위해 `DRAFT`로 돌아간다.
- 위 소유 핵심정보를 변경하려는 시점에 `CANCELLED`, `WORK_COMPLETED`가 아닌 공고가 있으면 `409 FARM_PROFILE_UPDATE_NOT_ALLOWED`다.

성공 응답은 `FarmProfileResponse` 형식이다.

## 6. 충북 시·군 코드

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

시·군을 주소 문자열과 별도로 저장하여 향후 공고 검색과 지역 기반 매칭 필터에 사용한다.

## 7. 농가 프로필 상태

| 상태 | 의미 | 농가 사용자 API에서 직접 만드는 전이 |
|---|---|---|
| `DRAFT` | 기본 정보만 작성한 초안 | O |
| `PENDING_REVIEW` | 소유 증빙을 제출하고 담당자 검토 중 | O |
| `APPROVED` | 소유 승인 | X, 공통 계약 |
| `REJECTED` | 소유 반려 | X, 공통 계약 |
| `INACTIVE` | 회원 탈퇴에 따른 비활성화 | O |

전체 공통 상태 흐름은 다음과 같다.

```text
DRAFT
→ 소유 증빙 제출
→ PENDING_REVIEW
→ 중개센터 승인: APPROVED
→ 중개센터 반려: REJECTED
→ 증빙 재제출: PENDING_REVIEW
```

상태는 요청 본문으로 직접 변경하지 않고 서비스의 상태 전이 메서드에서만 변경한다.

## 8. 데이터 구조

### 8.1 `farm_profiles`

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `id` | PK, Auto Increment | 프로필 식별자 |
| `owner_user_id` | FK, NOT NULL, UNIQUE | `users.id`, 계정당 프로필 하나 보장 |
| `farm_name` | NOT NULL, 최대 100자 | 농가명 |
| `representative_name` | NOT NULL, 최대 50자 | 대표자명 |
| `contact_number` | NOT NULL, 최대 11자 | 숫자로 정규화된 연락처 |
| `farm_address` | NOT NULL, 최대 255자 | 상세 주소 |
| `city_county` | NOT NULL, 최대 30자 | 충북 시·군 Enum |
| `main_activities` | NOT NULL, 최대 2,000자 | 주요 활동 내용 |
| `business_registration_number` | NULL, 최대 10자 | 선택 사업자번호 |
| `farm_area_pyeong` | 1~100000000 | 농지 면적 |
| `status` | NOT NULL, 최대 20자 | 농가 프로필 상태 |
| `reviewer_user_id` | FK, NULL | 공통 심사자 계약 |
| `reviewed_at` | NULL | 심사 시각 |
| `rejection_reason` | NULL, 최대 1,000자 | 반려 사유 |
| `created_at` | NOT NULL | 생성 시각 |
| `updated_at` | NOT NULL | 수정 시각 |

### 8.2 `farm_profile_crops`

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `farm_profile_id` | FK, NOT NULL | 농가 프로필 ID |
| `display_order` | NOT NULL | 응답 순서 |
| `crop` | NOT NULL, 최대 50자 | 재배 작물명 |

동일 프로필 안에서 같은 작물명이 중복 저장되지 않도록 `(farm_profile_id, crop)` UNIQUE 제약을 사용한다.

## 9. Postman 확인 순서

1. `FARM` 계정으로 기존 로그인 API를 호출한다.
2. 로그인 응답의 `accessToken`을 `farmAccessToken` 환경 변수에 저장한다.
3. `POST {{baseUrl}}/api/farm-profiles`로 농가 프로필을 등록한다.
4. 응답 상태가 `201`, `status`가 `DRAFT`인지 확인한다.
5. 동일 요청을 다시 보내 `409 FARM_PROFILE_ALREADY_EXISTS`인지 확인한다.
6. `GET {{baseUrl}}/api/farm-profiles/me`로 본인 프로필을 조회한다.
7. `PATCH {{baseUrl}}/api/farm-profiles/me`로 전체 필드 수정을 확인한다.
8. 도시농부 토큰으로 호출해 `403 ACCESS_DENIED`인지 확인한다.

## 10. 중개센터 심사 연동

농가 심사 목록·상세·승인·반려와 담당자 증빙 다운로드 API가 `/api/admin/farm-profiles/**`에 통합돼 있다. 사용자 API는 같은 모델에 저장된 심사 결과를 조회하고 `APPROVED` 여부를 공고 작성 자격에 사용한다. 상세 계약은 `ADMIN_FARM_OWNERSHIP_API_SPEC.md`를 따른다.
