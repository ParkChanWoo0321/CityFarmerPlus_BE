# CityFarmerPlus 도시농부 사업참여 신청 API 명세서

- 기준일: 2026-08-20
- 기준 소스: 현재 `main` 통합 코드의 `ParticipationApplicationController`, DTO, Service, Entity 및 공통 인증·예외 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 7개

> 이 API의 신청서는 도시농부 사업 자체에 참여하기 위한 신청이다. 희망 근무 조건과 개별 농가 공고 지원은 각각 별도 도메인이다. 이 문서에는 도시농부 본인 API만 포함하며 중개센터 승인·반려 API는 포함하지 않는다.

디자인의 단일 신청 화면은 [04A_PARTICIPATION_FORM.md](04A_PARTICIPATION_FORM.md)의 통합 API 3개를 사용할 수 있다. 통합 API가 추가되어도 이 문서의 사업참여 신청 7개 API와 프로필·희망 근무 조건 API는 삭제되지 않고 독립 API로 유지된다.

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
| 잘못된 JSON | `400` | `INVALID_REQUEST` |
| DTO 검증 실패 | `400` | `VALIDATION_ERROR` |
| Path Variable 숫자 형식 오류 | `400` | `INVALID_REQUEST_PARAMETER` |
| 다른 요청이 상태를 먼저 변경 | `409` | `CONCURRENT_UPDATE_CONFLICT` |

본인 소유 신청서만 조회·수정할 수 있다. 다른 도시농부의 `applicationId`를 사용해도 존재 여부를 노출하지 않고 `404 PARTICIPATION_APPLICATION_NOT_FOUND`를 반환한다.

---

## 2. API 목록

| 기능 | Method | URL | 권한 | 성공 응답 |
|---|---|---|---|---|
| 사업참여 신청 초안 생성 | `POST` | `/api/urban-farmers/me/participation-applications` | `URBAN_FARMER` | `201 Created` |
| 내 신청 목록 조회 | `GET` | `/api/urban-farmers/me/participation-applications` | `URBAN_FARMER` | `200 OK` |
| 내 신청 상세 조회 | `GET` | `/api/urban-farmers/me/participation-applications/{applicationId}` | `URBAN_FARMER` | `200 OK` |
| 초안·반려 신청 수정 | `PATCH` | `/api/urban-farmers/me/participation-applications/{applicationId}` | `URBAN_FARMER` | `200 OK` |
| 초안 삭제 | `DELETE` | `/api/urban-farmers/me/participation-applications/{applicationId}` | `URBAN_FARMER` | `204 No Content` |
| 초안 제출 | `POST` | `/api/urban-farmers/me/participation-applications/{applicationId}/submit` | `URBAN_FARMER` | `200 OK` |
| 신청 취소 | `POST` | `/api/urban-farmers/me/participation-applications/{applicationId}/cancel` | `URBAN_FARMER` | `200 OK` |

---

## 3. 상태 값과 전이

| 상태 | 의미 |
|---|---|
| `DRAFT` | 초안. 수정·삭제·제출 가능 |
| `SUBMITTED` | 제출 완료. 도시농부 수정·삭제 불가 |
| `APPROVED` | 승인. 심사 결과 저장용 상태 |
| `REJECTED` | 반려. 수정하면 `DRAFT`로 복귀 |
| `CANCELLED` | 취소. 추가 상태 전이 불가 |

현재 코드의 전이:

```text
생성 -> DRAFT
DRAFT --수정--> DRAFT
DRAFT --제출--> SUBMITTED
REJECTED --수정--> DRAFT
DRAFT --삭제--> 실제 레코드 삭제
DRAFT | SUBMITTED | APPROVED | REJECTED --취소--> CANCELLED
```

승인과 반려는 현재 통합 코드의 `POST /api/admin/participation-applications/{applicationId}/approve`, `POST /api/admin/participation-applications/{applicationId}/reject`가 처리한다. `CENTER_ADMIN` JWT가 필요하며 상세 계약은 `../ADMIN_PARTICIPATION_APPLICATION_API_SPEC.md`를 따른다.

회원 탈퇴 처리에서는 별도 규칙을 사용한다. 해당 사용자의 신청 행을 잠근 뒤 `DRAFT`, `SUBMITTED`, `REJECTED`만 `CANCELLED`로 바꾸고, `APPROVED`와 이미 `CANCELLED`인 신청은 유지한다. 반려 신청을 취소할 때도 기존 `reviewedByUserId`, `reviewedAt`, `rejectionReason`은 이력으로 보존한다.

---

## 4. 공통 응답 형식

단건 응답 예시:

```json
{
  "id": 31,
  "urbanFarmerId": 15,
  "urbanFarmerName": "김도시",
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "도시농부 사업에 참여하고 싶습니다.",
  "status": "DRAFT",
  "reviewedByUserId": null,
  "rejectionReason": null,
  "submittedAt": null,
  "reviewedAt": null,
  "cancelledAt": null,
  "version": 0,
  "createdAt": "2026-08-11T10:40:00.123Z",
  "updatedAt": "2026-08-11T10:40:00.123Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | Long | 사업참여 신청 ID |
| `urbanFarmerId` | Long | 신청한 도시농부 회원 ID |
| `urbanFarmerName` | String | 응답 시점의 도시농부 회원 이름 |
| `programYear` | Integer | 사업연도, 생성 후 변경 불가 |
| `agriculturalBusinessRegistered` | Boolean | 신청서에 입력한 농업경영체 등록 여부 |
| `applicationNote` | String 또는 null | 신청 특이사항 |
| `status` | Enum | 현재 신청 상태 |
| `reviewedByUserId` | Long 또는 null | 심사 담당자 회원 ID |
| `rejectionReason` | String 또는 null | 반려 사유 |
| `submittedAt` | Instant 또는 null | 최근 제출 시각, UTC ISO-8601 |
| `reviewedAt` | Instant 또는 null | 심사 시각, UTC ISO-8601 |
| `cancelledAt` | Instant 또는 null | 취소 시각, UTC ISO-8601 |
| `version` | Long | JPA 낙관적 잠금 버전 |
| `createdAt` | Instant | 생성 시각, UTC ISO-8601 |
| `updatedAt` | Instant | 최근 수정 시각, UTC ISO-8601 |

---

## 5. 사업참여 신청 초안 생성

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/urban-farmers/me/participation-applications` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | `application/json` |

```json
{
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "도시농부 사업에 참여하고 싶습니다."
}
```

### 요청 필드와 검증

| 필드 | 타입 | 필수 | 검증 및 처리 |
|---|---|---:|---|
| `programYear` | Integer | O | `2000~2100`; 누락 시 서비스에서 별도 오류 반환 |
| `agriculturalBusinessRegistered` | Boolean | O | `true` 또는 `false` |
| `applicationNote` | String | X | 최대 1000자; 빈 값은 `null`, 그 외 앞뒤 공백 제거 |

### 성공 응답

- HTTP: `201 Created`
- 최초 상태: `DRAFT`

```json
{
  "id": 31,
  "urbanFarmerId": 15,
  "urbanFarmerName": "김도시",
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "도시농부 사업에 참여하고 싶습니다.",
  "status": "DRAFT",
  "reviewedByUserId": null,
  "rejectionReason": null,
  "submittedAt": null,
  "reviewedAt": null,
  "cancelledAt": null,
  "version": 0,
  "createdAt": "2026-08-11T10:40:00.123Z",
  "updatedAt": "2026-08-11T10:40:00.123Z"
}
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `PROGRAM_YEAR_REQUIRED` | `programYear` 누락 또는 `null` |
| `400` | `VALIDATION_ERROR` | 사업연도 범위, 필수 Boolean 또는 특이사항 길이 위반 |
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `409` | `PARTICIPATION_APPLICATION_ALREADY_EXISTS` | 같은 회원·사업연도 신청이 이미 존재 |
| `409` | `DATA_CONFLICT` | 동시 생성 등으로 DB 유니크 제약 충돌 |

중복 오류 예시:

```json
{
  "code": "PARTICIPATION_APPLICATION_ALREADY_EXISTS",
  "message": "해당 사업연도의 참여 신청이 이미 존재합니다."
}
```

같은 연도의 신청이 `CANCELLED` 상태여도 레코드가 존재하므로 새로 생성할 수 없다.

### Postman 팁

생성 응답의 `id`를 환경변수에 저장하면 후속 상세·수정·제출 요청에 사용할 수 있다.

```javascript
pm.environment.set("participationApplicationId", pm.response.json().id);
```

---

## 6. 내 신청 목록 조회

### 요청

| 항목 | 값 |
|---|---|
| Method | `GET` |
| URL | `/api/urban-farmers/me/participation-applications` |
| 권한 | 활성 `URBAN_FARMER` 계정 |
| Content-Type | 요청 본문 없음 |

```http
GET /api/urban-farmers/me/participation-applications
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `200 OK`
- 생성 시각 내림차순
- 페이지네이션 없음
- 신청이 없으면 빈 배열 `[]`

```json
[
  {
    "id": 31,
    "urbanFarmerId": 15,
    "urbanFarmerName": "김도시",
    "programYear": 2026,
    "agriculturalBusinessRegistered": false,
    "applicationNote": "도시농부 사업에 참여하고 싶습니다.",
    "status": "DRAFT",
    "reviewedByUserId": null,
    "rejectionReason": null,
    "submittedAt": null,
    "reviewedAt": null,
    "cancelledAt": null,
    "version": 0,
    "createdAt": "2026-08-11T10:40:00.123Z",
    "updatedAt": "2026-08-11T10:40:00.123Z"
  }
]
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `401` | `INVALID_ACCOUNT` | 비활성 계정 또는 JWT 역할 불일치 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |

### Postman 팁

Query Parameter는 없다. 연도나 상태 필터를 보내도 현재 컨트롤러는 지원하지 않는다.

---

## 7. 내 신청 상세 조회

### 요청

| 항목 | 값 |
|---|---|
| Method | `GET` |
| URL | `/api/urban-farmers/me/participation-applications/{applicationId}` |
| 권한 | 활성 `URBAN_FARMER` 계정, 본인 소유 신청 |
| Content-Type | 요청 본문 없음 |

```http
GET /api/urban-farmers/me/participation-applications/{{participationApplicationId}}
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `200 OK`
- Body: 공통 `ParticipationApplicationResponse`

```json
{
  "id": 31,
  "urbanFarmerId": 15,
  "urbanFarmerName": "김도시",
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "도시농부 사업에 참여하고 싶습니다.",
  "status": "DRAFT",
  "reviewedByUserId": null,
  "rejectionReason": null,
  "submittedAt": null,
  "reviewedAt": null,
  "cancelledAt": null,
  "version": 0,
  "createdAt": "2026-08-11T10:40:00.123Z",
  "updatedAt": "2026-08-11T10:40:00.123Z"
}
```

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `INVALID_REQUEST_PARAMETER` | `applicationId`가 Long 형식이 아님 |
| `401` | `UNAUTHORIZED` | JWT 누락·만료·위조 |
| `403` | `ACCESS_DENIED` | 도시농부가 아닌 계정 |
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 신청이 없거나 다른 사용자의 신청 |

```json
{
  "code": "PARTICIPATION_APPLICATION_NOT_FOUND",
  "message": "사업참여 신청을 찾을 수 없습니다."
}
```

### Postman 팁

다른 도시농부 토큰으로 같은 ID를 조회했을 때도 `404`인지 확인하면 소유권 검증을 테스트할 수 있다.

---

## 8. 초안·반려 신청 수정

### 요청

| 항목 | 값 |
|---|---|
| Method | `PATCH` |
| URL | `/api/urban-farmers/me/participation-applications/{applicationId}` |
| 권한 | 활성 `URBAN_FARMER` 계정, 본인 소유 신청 |
| Content-Type | `application/json` |

```json
{
  "agriculturalBusinessRegistered": true,
  "applicationNote": "농업경영체 등록 정보를 보완했습니다."
}
```

### 요청 필드와 검증

| 필드 | 타입 | 필수 | 검증 및 처리 |
|---|---|---:|---|
| `agriculturalBusinessRegistered` | Boolean | O | `true` 또는 `false` |
| `applicationNote` | String | X | 최대 1000자; 빈 값은 `null`, 그 외 앞뒤 공백 제거 |

`programYear`는 생성 후 수정할 수 없으며 수정 DTO에 존재하지 않는다.

### 상태 처리

- `DRAFT`는 수정 후 계속 `DRAFT`다.
- `REJECTED`는 수정하면 `DRAFT`로 복귀한다.
- 반려 신청 수정 시 `reviewedByUserId`, `reviewedAt`, `rejectionReason`을 `null`로 초기화한다.
- 기존 `submittedAt`은 수정 시 바로 초기화하지 않는다. 이후 재제출하면 새 제출 시각으로 덮어쓴다.
- `SUBMITTED`, `APPROVED`, `CANCELLED` 상태는 수정할 수 없다.

### 성공 응답

- HTTP: `200 OK`

```json
{
  "id": 31,
  "urbanFarmerId": 15,
  "urbanFarmerName": "김도시",
  "programYear": 2026,
  "agriculturalBusinessRegistered": true,
  "applicationNote": "농업경영체 등록 정보를 보완했습니다.",
  "status": "DRAFT",
  "reviewedByUserId": null,
  "rejectionReason": null,
  "submittedAt": "2026-08-11T10:50:00.123Z",
  "reviewedAt": null,
  "cancelledAt": null,
  "version": 3,
  "createdAt": "2026-08-11T10:40:00.123Z",
  "updatedAt": "2026-08-11T11:30:00.456Z"
}
```

위 예시는 반려된 신청을 수정한 경우라 과거 `submittedAt`이 남아 있는 현재 구현을 보여준다. 변경 내용을 JPA flush한 뒤 응답하므로 `version`과 `updatedAt`에는 이번 수정으로 갱신된 값이 즉시 포함된다.

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | 필수 Boolean 누락 또는 특이사항 길이 위반 |
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 신청이 없거나 다른 사용자의 신청 |
| `409` | `INVALID_PARTICIPATION_STATUS` | 수정할 수 없는 상태 |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청이 상태를 먼저 변경 |

```json
{
  "code": "INVALID_PARTICIPATION_STATUS",
  "message": "초안 또는 반려 상태에서만 신청서를 수정할 수 있습니다."
}
```

### Postman 팁

PATCH는 부분 수정처럼 보이지만 `agriculturalBusinessRegistered`는 항상 보내야 한다. 반려 재제출 흐름은 `REJECTED → PATCH → DRAFT → submit → SUBMITTED` 순서다.

---

## 9. 초안 삭제

### 요청

| 항목 | 값 |
|---|---|
| Method | `DELETE` |
| URL | `/api/urban-farmers/me/participation-applications/{applicationId}` |
| 권한 | 활성 `URBAN_FARMER` 계정, 본인 소유 신청 |
| Content-Type | 요청 본문 없음 |

### 성공 응답

- HTTP: `204 No Content`
- Response Body 없음
- `DRAFT` 레코드를 실제 삭제

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 신청이 없거나 다른 사용자의 신청 |
| `409` | `INVALID_PARTICIPATION_STATUS` | `DRAFT`가 아닌 신청 삭제 시도 |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청이 상태를 먼저 변경 또는 삭제 |

```json
{
  "code": "INVALID_PARTICIPATION_STATUS",
  "message": "초안 상태의 신청서만 삭제할 수 있습니다."
}
```

삭제 후에는 같은 사업연도의 새 신청을 다시 생성할 수 있다. 취소는 삭제가 아니므로 이 규칙이 적용되지 않는다.

### Postman 팁

삭제 전 상세 GET을 저장하고, 삭제 후 같은 ID 상세 조회가 `404`인지 확인한다.

---

## 10. 초안 제출

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/urban-farmers/me/participation-applications/{applicationId}/submit` |
| 권한 | 활성 `URBAN_FARMER` 계정, 본인 소유 신청 |
| Content-Type | 요청 본문 없음 |

```http
POST /api/urban-farmers/me/participation-applications/{{participationApplicationId}}/submit
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `200 OK`
- 상태: `DRAFT → SUBMITTED`
- `submittedAt`에 현재 UTC 시각 저장

```json
{
  "id": 31,
  "urbanFarmerId": 15,
  "urbanFarmerName": "김도시",
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "도시농부 사업에 참여하고 싶습니다.",
  "status": "SUBMITTED",
  "reviewedByUserId": null,
  "rejectionReason": null,
  "submittedAt": "2026-08-11T10:50:00.123Z",
  "reviewedAt": null,
  "cancelledAt": null,
  "version": 1,
  "createdAt": "2026-08-11T10:40:00.123Z",
  "updatedAt": "2026-08-11T10:50:00.123Z"
}
```

변경 내용을 JPA flush한 뒤 응답하므로 `status`, `submittedAt`, 증가한 `version`, 갱신된 `updatedAt`이 모두 즉시 응답에 반영된다.

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 신청이 없거나 다른 사용자의 신청 |
| `409` | `INVALID_PARTICIPATION_STATUS` | `DRAFT`가 아닌 신청 제출 시도 |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청이 상태를 먼저 변경 |

```json
{
  "code": "INVALID_PARTICIPATION_STATUS",
  "message": "초안 상태에서만 신청서를 제출할 수 있습니다."
}
```

### Postman 팁

요청 Body는 `none`으로 둔다. 동일 요청을 두 번 보내면 첫 번째는 `200`, 두 번째는 `409`가 정상이다.

---

## 11. 신청 취소

### 요청

| 항목 | 값 |
|---|---|
| Method | `POST` |
| URL | `/api/urban-farmers/me/participation-applications/{applicationId}/cancel` |
| 권한 | 활성 `URBAN_FARMER` 계정, 본인 소유 신청 |
| Content-Type | 요청 본문 없음 |

```http
POST /api/urban-farmers/me/participation-applications/{{participationApplicationId}}/cancel
Authorization: Bearer {{accessToken}}
```

### 성공 응답

- HTTP: `200 OK`
- 상태: `CANCELLED`
- `cancelledAt`에 현재 UTC 시각 저장

```json
{
  "id": 31,
  "urbanFarmerId": 15,
  "urbanFarmerName": "김도시",
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "도시농부 사업에 참여하고 싶습니다.",
  "status": "CANCELLED",
  "reviewedByUserId": null,
  "rejectionReason": null,
  "submittedAt": "2026-08-11T10:50:00.123Z",
  "reviewedAt": null,
  "cancelledAt": "2026-08-11T11:10:00.456Z",
  "version": 2,
  "createdAt": "2026-08-11T10:40:00.123Z",
  "updatedAt": "2026-08-11T11:10:00.456Z"
}
```

변경 내용을 JPA flush한 뒤 응답하므로 `status`, `cancelledAt`, 증가한 `version`, 갱신된 `updatedAt`이 모두 즉시 응답에 반영된다.

### 현재 취소 규칙

- `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED` 모두 취소할 수 있다.
- 이미 `CANCELLED`인 신청만 다시 취소할 수 없다.
- 취소 시 과거 제출·심사·반려 정보는 별도로 초기화하지 않는다.
- 취소 취소 또는 초안 복구 API는 없다.

### 대표 오류

| HTTP | 코드 | 발생 조건 |
|---:|---|---|
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 신청이 없거나 다른 사용자의 신청 |
| `409` | `INVALID_PARTICIPATION_STATUS` | 이미 취소된 신청을 다시 취소 |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 다른 요청이 상태를 먼저 변경 |

```json
{
  "code": "INVALID_PARTICIPATION_STATUS",
  "message": "이미 취소된 신청입니다."
}
```

### Postman 팁

요청 Body는 `none`으로 둔다. 취소 후 같은 사업연도의 새 초안 생성은 중복 제약 때문에 `409`이므로, 단순 재신청 용도로 취소를 사용하면 안 된다.

---

## 12. 현재 제한과 기능 간 관계

- 도시농부 한 명은 사업연도별로 신청을 최대 1개만 가질 수 있다.
- 목록 API는 페이지네이션·연도 필터·상태 필터를 지원하지 않는다.
- 도시농부 프로필이나 교육 인증 여부를 생성·수정·제출 시 검사하지 않는다.
- 교육 인증은 개별 농가 공고 지원 자격에서 별도로 검사한다.
- `agriculturalBusinessRegistered`는 신청서 입력값이며 이 API에서 서류로 검증하지 않는다.
- 승인·반려는 통합된 `CENTER_ADMIN` HTTP API가 처리하며 이 문서에는 도시농부 본인 API만 기재한다.
- 이 문서의 개별 사업참여 API만 호출하면 희망 근무 조건이 자동 생성되지 않는다.
- 통합 신청 폼 API를 호출한 경우에만 프로필·희망 근무 조건·사업참여 신청이 한 트랜잭션에서 함께 저장된다.
