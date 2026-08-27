# CityFarmerPlus 관리자 사업참여 신청 심사 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-20
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 사업참여 신청 목록 조회, 상세 조회, 승인, 반려

## 1. 공통 사항

### 1.1 기본 URL

```text
운영: https://cityfarmerplus-api-82951616760.us-west1.run.app
로컬: http://localhost:8080
```

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{adminAccessToken}}
```

### 1.3 인증 및 권한 규칙

- `/api/admin/**`는 `SecurityConfig`에서 `hasRole("CENTER_ADMIN")`으로 URL 패턴 단위로 이미 보호된다. 이 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('CENTER_ADMIN')")`을 추가로 걸어 이중으로 확인한다(둘 다 JWT의 `role` claim만 검사).
- 서비스 레이어에서 JWT와 무관하게 **DB에 저장된 실제 역할을 다시 조회**한다(`AdminParticipationApplicationService.requireCenterAdmin`). JWT 발급 이후 계정이 강등되거나 삭제된 경우를 방어하기 위한 것으로, 정상적인 요청에서는 거의 발생하지 않는다.
- 심사자 ID는 요청 body로 받지 않는다. 항상 JWT의 `sub`(`AuthenticatedUser.id(authentication)`)에서 추출한다.
- 이 API들은 도시농부 본인용 [`urbanfarmer.participation`](../src/main/java/chungbuk/cityfarmerplus/urbanfarmer/participation) 패키지의 `ParticipationApplication` 엔티티와 `ParticipationApplicationRepository`, 응답 DTO(`ParticipationApplicationResponse`)를 그대로 재사용한다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`GlobalExceptionHandler`의 `handleDomainException`이 처리한다. 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다. 자세한 내용은 7장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 사업참여 신청 목록 조회 | `GET` | `/api/admin/participation-applications` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 사업참여 신청 상세 조회 | `GET` | `/api/admin/participation-applications/{applicationId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 사업참여 신청 승인 | `POST` | `/api/admin/participation-applications/{applicationId}/approve` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 사업참여 신청 반려 | `POST` | `/api/admin/participation-applications/{applicationId}/reject` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

## 3. 사업참여 신청 목록 조회

### 3.1 요청

```http
GET /api/admin/participation-applications
Authorization: Bearer {{adminAccessToken}}
```

쿼리 파라미터는 받지 않는다(상태·연도별 필터링은 미지원).

### 3.2 성공 응답

```http
HTTP/1.1 200 OK
```

`DRAFT`를 포함한 모든 상태의 신청서를 `createdAt` 내림차순으로 정렬한 배열을 반환한다(페이지네이션 없음).

```json
[
  {
    "id": 5,
    "urbanFarmerId": 10,
    "urbanFarmerName": "홍길동",
    "programYear": 2026,
    "agriculturalBusinessRegistered": false,
    "applicationNote": "텃밭 참여 경험이 있습니다.",
    "status": "SUBMITTED",
    "reviewedByUserId": null,
    "rejectionReason": null,
    "submittedAt": "2026-08-10T02:00:00Z",
    "reviewedAt": null,
    "cancelledAt": null,
    "version": 1,
    "createdAt": "2026-08-09T12:00:00Z",
    "updatedAt": "2026-08-10T02:00:00Z"
  }
]
```

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

## 4. 사업참여 신청 상세 조회

### 4.1 요청

```http
GET /api/admin/participation-applications/5
Authorization: Bearer {{adminAccessToken}}
```

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 3.2와 동일한 `ParticipationApplicationResponse` 형식이다(단일 객체).

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 해당 `applicationId`의 신청서가 없음 |

신청서 없음 예시:

```json
{
  "code": "PARTICIPATION_APPLICATION_NOT_FOUND",
  "message": "사업참여 신청을 찾을 수 없습니다."
}
```

## 5. 사업참여 신청 승인

`SUBMITTED` 상태의 신청서만 승인할 수 있다. 승인 시 상태가 `APPROVED`로 바뀌고, 심사자(JWT에서 추출한 관리자 ID)와 심사 시각이 기록된다. 요청 본문은 받지 않는다 — 사업연도, 신청자 ID는 이 API로 변경할 수 없다.

### 5.1 요청

```http
POST /api/admin/participation-applications/5/approve
Authorization: Bearer {{adminAccessToken}}
```

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 5,
  "urbanFarmerId": 10,
  "urbanFarmerName": "홍길동",
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "텃밭 참여 경험이 있습니다.",
  "status": "APPROVED",
  "reviewedByUserId": 3,
  "rejectionReason": null,
  "submittedAt": "2026-08-10T02:00:00Z",
  "reviewedAt": "2026-08-20T06:00:00Z",
  "cancelledAt": null,
  "version": 2,
  "createdAt": "2026-08-09T12:00:00Z",
  "updatedAt": "2026-08-20T06:00:00Z"
}
```

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨(서비스 재검증 단계) |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 해당 `applicationId`의 신청서가 없음 |
| `409` | `INVALID_PARTICIPATION_STATUS` | 신청서가 `SUBMITTED` 상태가 아님 |

상태 오류 예시(이미 승인되었거나 아직 초안인 신청서를 다시 승인 시도):

```json
{
  "code": "INVALID_PARTICIPATION_STATUS",
  "message": "제출 상태의 신청서만 심사할 수 있습니다."
}
```

## 6. 사업참여 신청 반려

`SUBMITTED` 상태의 신청서만 반려할 수 있다. 반려 시 `reason`이 필수이며, 상태가 `REJECTED`로 바뀌고 심사자·심사 시각·반려 사유가 기록된다.

### 6.1 요청

```http
POST /api/admin/participation-applications/5/reject
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "reason": "제출한 서류의 농업경영체 등록 여부가 실제와 다릅니다."
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `reason` | String | O | 공백만 입력 불가, 최대 500자 |

### 6.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 5,
  "urbanFarmerId": 10,
  "urbanFarmerName": "홍길동",
  "programYear": 2026,
  "agriculturalBusinessRegistered": false,
  "applicationNote": "텃밭 참여 경험이 있습니다.",
  "status": "REJECTED",
  "reviewedByUserId": 3,
  "rejectionReason": "제출한 서류의 농업경영체 등록 여부가 실제와 다릅니다.",
  "submittedAt": "2026-08-10T02:00:00Z",
  "reviewedAt": "2026-08-20T06:05:00Z",
  "cancelledAt": null,
  "version": 2,
  "createdAt": "2026-08-09T12:00:00Z",
  "updatedAt": "2026-08-20T06:05:00Z"
}
```

### 6.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `reason` 누락(공백 포함) 또는 500자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `PARTICIPATION_APPLICATION_NOT_FOUND` | 해당 `applicationId`의 신청서가 없음 |
| `409` | `INVALID_PARTICIPATION_STATUS` | 신청서가 `SUBMITTED` 상태가 아님 |

## 7. 인증·인가 오류 응답 주의사항

`401`·`403`(URL 패턴 단계)은 `GlobalExceptionHandler`가 아니라 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드).

- `401`: `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`
- `403`: `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`

이와 달리 5·6장의 `CENTER_ADMIN_ROLE_REQUIRED`(403)는 **컨트롤러 진입 이후 서비스 레이어**에서 `DomainException`으로 던져지며 `GlobalExceptionHandler`가 처리한다. 형식(`{ code, message }`)은 같지만 코드값과 발생 위치가 다르므로 클라이언트에서 구분할 때 주의한다.

## 8. 사업참여 신청 상태값

| 값 | 의미 | 이 API에서 전이 방법 |
|---|---|---|
| `DRAFT` | 도시농부가 작성 중인 초안 | 전이 불가(도시농부 본인용 API에서만 생성) |
| `SUBMITTED` | 제출 완료, 심사 대기 | 전이 불가(도시농부 본인용 제출 API에서 전환) |
| `APPROVED` | 관리자 승인 | `POST .../approve` |
| `REJECTED` | 관리자 반려 | `POST .../reject` |
| `CANCELLED` | 신청 취소 | 전이 불가(도시농부 본인용 취소 API 또는 회원 탈퇴 시 전환) |

`SUBMITTED` 상태에서만 승인·반려가 가능하며, 그 외 상태에서 시도하면 `409 INVALID_PARTICIPATION_STATUS`로 거부된다.

## 9. 현재 범위 밖 또는 미구현 기능

- 목록 조회 상태별·연도별 필터링, 검색, 페이지네이션
- 승인·반려 취소 또는 재심사(둘 다 한 번 전이하면 되돌릴 수 없음)
- 반려된 신청서의 재제출 승인 알림 등 사후 처리
- 여러 신청서 일괄 승인·반려
