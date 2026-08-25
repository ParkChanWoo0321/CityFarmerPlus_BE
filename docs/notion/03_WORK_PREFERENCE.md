# CityFarmerPlus 희망 근무 조건 API 명세서

- 기준일: 2026-08-20
- 기준 소스: 현재 `backend-1` 작업 폴더의 `WorkPreferenceController`, DTO, Service, Entity 및 공통 인증·예외 코드
- 로컬 Base URL: `http://localhost:8080`
- API 수: 3개

> 이 3개 API는 희망 근무 조건만 독립적으로 관리한다. 디자인의 사업참여 신청 한 화면에서는 [04A_PARTICIPATION_FORM.md](04A_PARTICIPATION_FORM.md)의 통합 API를 사용할 수 있으며, 통합 API 추가 후에도 이 API들은 유지된다.

> 도시농부가 선호 지역·요일·작업 유형·희망 시작일·희망 종료일·이동 가능 여부를 하나의 신청서 형태로 저장하는 API다. 사업참여 신청 및 개별 공고 지원과는 별도 데이터다.

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
| 다른 역할로 접근 | `403` | `ACCESS_DENIED` |
| 잘못된 JSON 또는 Enum 값 | `400` | `INVALID_REQUEST` |
| DTO 검증 실패 | `400` | `VALIDATION_ERROR` |

---

## 2. API 목록

| 기능 | Method | URL | 권한 | 성공 응답 |
|---|---|---|---|---|
| 내 희망 조건 조회 | `GET` | `/api/urban-farmers/me/work-preference` | `URBAN_FARMER` | `200 OK` |
| 희망 조건 등록 또는 전체 수정 | `PUT` | `/api/urban-farmers/me/work-preference` | `URBAN_FARMER` | `200 OK` |
| 내 희망 조건 삭제 | `DELETE` | `/api/urban-farmers/me/work-preference` | `URBAN_FARMER` | `204 No Content` |

---

## 3. 요청 Enum

### 충북 시·군 `ChungbukCityCounty`

| 값 | 한글 지역명 |
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

### 요일 `DayOfWeek`

`MONDAY`, `TUESDAY`, `WEDNESDAY`, `THURSDAY`, `FRIDAY`, `SATURDAY`, `SUNDAY`

한글 지역명이나 `월요일` 같은 문자열이 아니라 위 영문 Enum 값을 보내야 한다.

---

## 4. 공통 요청 필드

| 필드 | 타입 | 필수 | 검증 및 처리 |
|---|---|---:|---|
| `preferredRegions` | Enum 배열 | O | 1~11개, 각 원소는 `ChungbukCityCounty`, `null` 불가 |
| `availableDays` | Enum 배열 | O | 1~7개, 각 원소는 `DayOfWeek`, `null` 불가 |
| `availableWorkTypes` | String 배열 | O | 1~20개, 각 항목은 공백만 입력 불가·최대 50자이며 값 내부에 쉼표(`,`)·줄바꿈(`\r`, `\n`) 입력 불가 |
| `preferredStartDate` | LocalDate | O | 희망 근무 시작일, `yyyy-MM-dd` |
| `preferredEndDate` | LocalDate | O | 희망 근무 종료일, `yyyy-MM-dd`; 시작일과 같거나 이후이고 현재 날짜보다 과거일 수 없음 |
| `canTravel` | Boolean | O | 이동 가능 여부 |
| `notes` | String | X | 최대 1000자; 빈 값은 `null`, 그 외 앞뒤 공백 제거 |

요청 예시:

```json
{
  "preferredRegions": ["CHEONGJU", "JINCHEON"],
  "availableDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "availableWorkTypes": ["사과 수확", "작물 선별"],
  "preferredStartDate": "2026-09-01",
  "preferredEndDate": "2026-10-31",
  "canTravel": true,
  "notes": "대중교통으로 이동 가능한 지역을 선호합니다."
}
```

### 정규화 규칙

- 지역과 요일의 중복을 제거하고 최초 입력 순서를 유지한다.
- 작업 유형은 앞뒤 공백을 제거한다.
- 작업 유형 값 내부에는 저장 형식의 구분자로 사용하는 쉼표와 줄바꿈을 입력할 수 없다. 위반 시 `400 INVALID_WORK_TYPE`을 반환한다.
- 작업 유형 중복은 대소문자를 구분하지 않고 제거하며, 처음 입력한 표기를 유지한다.
- 배열 최대 개수 검증은 중복 제거 전 원본 요청을 기준으로 한다.
- 희망 근무 시작일과 종료일은 모두 필수이며 같은 날짜를 선택할 수 있다.
- 종료일은 시작일보다 빠를 수 없고 서울 기준으로 이미 지난 날짜일 수 없다. 이미 시작된 기간의 다른 조건을 수정할 수 있도록 시작일이 과거인 것은 허용한다.

예를 들어 `availableWorkTypes`가 `[" 수확 ", "수확", "SORTING", "sorting"]`이면 저장 결과는 `["수확", "SORTING"]`이다.

---

## 5. 내 희망 조건 조회

### 요청

| 항목 | 값 |
|---|---|
| Method | `GET` |
| URL | `/api/urban-farmers/me/work-preference` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | 요청 본문 없음 |

```http
GET /api/urban-farmers/me/work-preference
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `200 OK`

```json
{
  "id": 21,
  "urbanFarmerId": 15,
  "preferredRegions": ["CHEONGJU", "JINCHEON"],
  "availableDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "availableWorkTypes": ["사과 수확", "작물 선별"],
  "preferredStartDate": "2026-09-01",
  "preferredEndDate": "2026-10-31",
  "canTravel": true,
  "notes": "대중교통으로 이동 가능한 지역을 선호합니다.",
  "version": 0,
  "createdAt": "2026-08-11T10:30:00.123Z",
  "updatedAt": "2026-08-11T10:30:00.123Z"
}
```

### 응답 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 희망 근무 조건 ID |
| `urbanFarmerId` | Long | 로그인한 도시농부 회원 ID |
| `preferredRegions` | Enum 배열 | 저장된 선호 지역 목록 |
| `availableDays` | Enum 배열 | 저장된 가능 요일 목록 |
| `availableWorkTypes` | String 배열 | 저장된 작업 유형 목록 |
| `preferredStartDate` | LocalDate 또는 null | 희망 근무 시작일. 신규·수정 데이터는 항상 값이 있고 기존 DB 백필 전 행만 `null` 가능 |
| `preferredEndDate` | LocalDate 또는 null | 희망 근무 종료일. 신규·수정 데이터는 항상 값이 있고 기존 DB 백필 전 행만 `null` 가능 |
| `canTravel` | Boolean | 이동 가능 여부 |
| `notes` | String 또는 null | 특이사항 |
| `version` | Long | JPA 낙관적 잠금 버전 |
| `createdAt` | Instant | 생성 시각, UTC ISO-8601 |
| `updatedAt` | Instant | 최근 수정 시각, UTC ISO-8601 |

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `401` | `INVALID_ACCOUNT` | 비활성 계정 또는 JWT 역할 불일치 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `404` | `WORK_PREFERENCE_NOT_FOUND` | 희망 조건을 아직 등록하지 않았거나 삭제함 |

```json
{
  "code": "WORK_PREFERENCE_NOT_FOUND",
  "message": "희망 근무 조건을 찾을 수 없습니다."
}
```

### Postman 팁

등록 전과 삭제 후에는 `404`가 정상이다. 빈 객체를 반환하는 API가 아니다.

---

## 6. 희망 조건 등록 또는 전체 수정

### 요청

| 항목 | 값 |
|---|---|
| Method | `PUT` |
| URL | `/api/urban-farmers/me/work-preference` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | `application/json` |

```http
PUT /api/urban-farmers/me/work-preference
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

```json
{
  "preferredRegions": ["CHEONGJU", "JINCHEON"],
  "availableDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "availableWorkTypes": ["사과 수확", "작물 선별"],
  "preferredStartDate": "2026-09-01",
  "preferredEndDate": "2026-10-31",
  "canTravel": true,
  "notes": "대중교통으로 이동 가능한 지역을 선호합니다."
}
```

### 업서트 동작

- 기존 데이터가 없으면 새로 생성한다.
- 기존 데이터가 있으면 같은 ID의 내용을 전체 교체한다.
- 생성과 수정 모두 `200 OK`를 반환한다. 생성 시에도 `201 Created`가 아니다.
- 모든 필수 배열, `preferredStartDate`, `preferredEndDate`, `canTravel`을 항상 보내야 한다.
- 도시농부 프로필 생성 여부는 이 API의 선행 조건이 아니다.

### 성공 응답

- HTTP: `200 OK`

```json
{
  "id": 21,
  "urbanFarmerId": 15,
  "preferredRegions": ["CHEONGJU", "JINCHEON"],
  "availableDays": ["MONDAY", "WEDNESDAY", "FRIDAY"],
  "availableWorkTypes": ["사과 수확", "작물 선별"],
  "preferredStartDate": "2026-09-01",
  "preferredEndDate": "2026-10-31",
  "canTravel": true,
  "notes": "대중교통으로 이동 가능한 지역을 선호합니다.",
  "version": 0,
  "createdAt": "2026-08-11T10:30:00.123Z",
  "updatedAt": "2026-08-11T10:30:00.123Z"
}
```

수정 시에는 같은 `id`와 `createdAt`이 유지되고 `version`, `updatedAt`이 갱신된다.

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 빈 배열, 개수 제한, 빈 작업 유형, 날짜 누락·순서 역전, 문자열 길이 또는 `canTravel` 누락 |
| `400` | `INVALID_WORK_TYPE` | 가능한 작업 유형 값 내부에 쉼표 또는 줄바꿈 포함 |
| `400` | `WORK_PREFERENCE_PERIOD_EXPIRED` | 희망 종료일이 서울 기준 현재 날짜보다 과거 |
| `400` | `INVALID_REQUEST` | 존재하지 않는 지역·요일 Enum 또는 잘못된 JSON |
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청이 같은 데이터를 먼저 변경 |
| `409` | `DATA_CONFLICT` | 동시 최초 생성 등으로 유니크 제약 충돌 |

검증 오류 예시:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "희망 근무 지역을 하나 이상 선택해야 합니다."
}
```

### Postman 팁

요청 전체를 저장한 뒤 배열 값만 바꿔 같은 요청을 한 번 더 보내면 생성과 수정 동작을 모두 확인할 수 있다. 테스트 스크립트에서는 첫 응답의 `id`와 두 번째 응답의 `id`가 같은지 확인한다.

---

## 7. 내 희망 조건 삭제

### 요청

| 항목 | 값 |
|---|---|
| Method | `DELETE` |
| URL | `/api/urban-farmers/me/work-preference` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | 요청 본문 없음 |

```http
DELETE /api/urban-farmers/me/work-preference
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `204 No Content`
- Response Body 없음

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `404` | `WORK_PREFERENCE_NOT_FOUND` | 삭제할 희망 조건이 없음 |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청이 같은 데이터를 먼저 변경 또는 삭제 |

삭제는 멱등 동작으로 구현되어 있지 않다. 이미 삭제된 상태에서 다시 요청하면 `204`가 아니라 `404`다.

### Postman 팁

`PUT → GET → DELETE → GET` 순서로 호출하면 마지막 GET이 `404`인지 확인할 수 있다. 삭제 뒤 다시 PUT하면 새로운 레코드가 생성되므로 ID가 달라질 수 있다.

---

## 8. 현재 제한

- 도시농부 한 명당 희망 근무 조건은 최대 1개다.
- 변경 이력 조회 API는 없다.
- 희망 조건을 기준으로 자동 매칭하거나 공고를 자동 지원하는 API가 아니다.
- 희망 조건 등록만으로 사업참여 신청이나 개별 공고 지원이 생성되지 않는다.
