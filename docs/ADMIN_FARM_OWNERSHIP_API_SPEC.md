# CityFarmerPlus 관리자 농가 소유 증빙 심사 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-24
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 농가 프로필 목록 조회(상태 필터), 상세(최신 소유 증빙 제출) 조회, 소유 증빙 승인, 반려

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

- `/api/admin/**`는 `SecurityConfig`에서 `hasRole("CENTER_ADMIN")`으로 URL 패턴 단위로 이미 보호된다. 이 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('CENTER_ADMIN')")`을 추가로 걸어 이중으로 확인한다.
- 서비스 레이어에서 JWT와 무관하게 **DB에 저장된 실제 역할을 다시 조회**한다(`AdminFarmOwnershipService.requireCenterAdmin`).
- 심사자 ID는 요청 body로 받지 않는다. 항상 JWT의 `sub`(`AuthenticatedUser.id(authentication)`)에서 추출한다.
- URL은 소유 증빙 제출 ID가 아니라 **`profileId`(농가 프로필 ID)** 를 받는다. `FarmOwnershipSubmissionRepository.findLatestByFarmProfileIdForUpdate(profileId)`가 항상 해당 농가 프로필의 **최신 회차(`attemptNumber` 최댓값)** 제출만 반환하도록 쿼리 자체가 구성돼 있어, "최신 제출 회차만 심사 가능" 규칙이 별도 검증 코드 없이 조회 단계에서부터 보장된다.
- 이 API들은 [`farm`](../src/main/java/chungbuk/cityfarmerplus/farm), [`farm.ownership`](../src/main/java/chungbuk/cityfarmerplus/farm/ownership) 패키지의 `FarmProfile`/`FarmOwnershipSubmission` 엔티티와 응답 DTO(`FarmProfileResponse`, `FarmOwnershipSubmissionResponse`)를 그대로 재사용한다.
- **승인·반려 시 `FarmOwnershipSubmission`(제출 건)과 `FarmProfile`(농가 프로필) 두 엔티티를 같은 트랜잭션 안에서, 같은 심사자·같은 심사 시각으로 함께 갱신한다.** 하나만 반영되고 하나는 실패하는 상태가 생기지 않도록 `AdminFarmOwnershipService.approve()`/`reject()` 전체가 `@Transactional`이다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`FarmProfileException`/`FarmOwnershipException`은 전역 `GlobalExceptionHandler`가 아니라, `farm` 도메인 전용의 **컨트롤러 범위 지정** 어드바이스인 `FarmProfileExceptionHandler`(`@RestControllerAdvice(assignableTypes = {...})`)가 처리한다. 이 어드바이스의 대상 목록에 `AdminFarmOwnershipController`를 추가해서 이 API들의 오류도 같은 형식으로 응답하도록 반영했다. 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다. 자세한 내용은 7장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 농가 프로필 목록 조회(상태 필터) | `GET` | `/api/admin/farm-profiles?status=` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 농가 소유 증빙 상세 조회 | `GET` | `/api/admin/farm-profiles/{profileId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 농가 소유 증빙 파일 다운로드 | `GET` | `/api/admin/farm-profiles/{profileId}/ownership/documents/{documentId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 농가 소유 증빙 승인 | `POST` | `/api/admin/farm-profiles/{profileId}/ownership/approve` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 농가 소유 증빙 반려 | `POST` | `/api/admin/farm-profiles/{profileId}/ownership/reject` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

## 3. 농가 프로필 목록 조회

**`status` 쿼리 파라미터가 필수다.** 예를 들어 심사 대기 목록은 `status=PENDING_REVIEW`로 조회한다.

### 3.1 요청

```http
GET /api/admin/farm-profiles?status=PENDING_REVIEW
Authorization: Bearer {{adminAccessToken}}
```

| 쿼리 파라미터 | 타입 | 필수 | 값 |
|---|---|---|---|
| `status` | Enum | **O** | `DRAFT` / `PENDING_REVIEW` / `APPROVED` / `REJECTED` / `INACTIVE` |

페이지네이션은 지원하지 않는다(`updatedAt` 내림차순 전체 목록).

### 3.2 성공 응답

```http
HTTP/1.1 200 OK
```

`FarmProfileResponse` 배열을 반환한다.

```json
[
  {
    "id": 7,
    "farmName": "충주 사과농원",
    "representativeName": "홍길동",
    "contactNumber": "01012345678",
    "farmAddress": "충청북도 충주시 예시로 1",
    "cityCounty": "CHUNGJU",
    "crops": ["사과", "복숭아"],
    "mainActivities": "사과 재배와 수확 작업을 합니다.",
    "businessRegistrationNumber": "1234567890",
    "farmAreaPyeong": 500,
    "status": "PENDING_REVIEW",
    "reviewerId": null,
    "reviewerName": null,
    "reviewedAt": null,
    "rejectionReason": null,
    "createdAt": "2026-08-20T00:00:00Z",
    "updatedAt": "2026-08-23T01:00:00Z"
  }
]
```

이 응답에는 **현재 프로필 값**이 담긴다(제출 당시 스냅샷이 아님). 스냅샷은 4장 상세 조회에서 확인한다.

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `INVALID_REQUEST_PARAMETER` | `status`에 정의되지 않은 값(오타 등)을 전달 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

`status` 쿼리 파라미터 자체가 누락된 경우에는 Spring 기본 `400` 응답이 반환되며, 이때는 공통 오류 JSON 형식(`{ code, message }`)이 보장되지 않는다.

## 4. 농가 소유 증빙 상세 조회

농가 프로필의 **최신 회차** 소유 증빙 제출을 조회한다. **제출 당시 스냅샷 값을 우선 표시**하도록 `FarmOwnershipSubmissionResponse`를 그대로 사용한다 — 심사 시점에 농가가 프로필을 이미 수정했더라도, 이 응답의 `*Snapshot` 필드들은 제출 당시 값 그대로 남아 있다.

### 4.1 요청

```http
GET /api/admin/farm-profiles/7
Authorization: Bearer {{adminAccessToken}}
```

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 15,
  "attemptNumber": 1,
  "status": "PENDING_REVIEW",
  "farmProfileStatus": "PENDING_REVIEW",
  "submittedAt": "2026-08-23T01:00:00Z",
  "reviewerId": null,
  "reviewerName": null,
  "reviewedAt": null,
  "rejectionReason": null,
  "documents": [
    {
      "id": 30,
      "originalFilename": "농지원부.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 204800
    }
  ],
  "farmNameSnapshot": "충주 사과농원",
  "representativeNameSnapshot": "홍길동",
  "farmAddressSnapshot": "충청북도 충주시 예시로 1",
  "cityCountySnapshot": "CHUNGJU",
  "businessRegistrationNumberSnapshot": "1234567890",
  "farmAreaPyeongSnapshot": 500
}
```

`status`는 이 제출 건의 상태, `farmProfileStatus`는 농가 프로필 전체의 현재 상태다(정상 흐름에서는 둘이 같은 값이지만, 개념적으로 서로 다른 필드다).

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |
| `404` | `FARM_PROFILE_NOT_FOUND` | 해당 `profileId`의 농가 프로필이 없음 |
| `404` | `OWNERSHIP_SUBMISSION_NOT_FOUND` | 농가 프로필은 있지만 소유 증빙을 한 번도 제출한 적 없음(`DRAFT` 상태 등) |

## 5. 농가 소유 증빙 승인

해당 농가 프로필의 **최신 회차 제출이 `PENDING_REVIEW`일 때만** 승인할 수 있다. 승인 시 `FarmOwnershipSubmission.approve()`와 `FarmProfile.approveOwnership()`이 **같은 트랜잭션 안에서, 같은 심사자·같은 심사 시각**으로 함께 호출된다 — 제출 건 상태는 `APPROVED`로, 농가 프로필 상태도 `APPROVED`로 함께 바뀐다. 요청 본문은 받지 않는다.

### 5.1 요청

```http
POST /api/admin/farm-profiles/7/ownership/approve
Authorization: Bearer {{adminAccessToken}}
```

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 15,
  "attemptNumber": 1,
  "status": "APPROVED",
  "farmProfileStatus": "APPROVED",
  "submittedAt": "2026-08-23T01:00:00Z",
  "reviewerId": 3,
  "reviewerName": "충북 담당자",
  "reviewedAt": "2026-08-24T13:00:00Z",
  "rejectionReason": null,
  "documents": [
    {
      "id": 30,
      "originalFilename": "농지원부.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 204800
    }
  ],
  "farmNameSnapshot": "충주 사과농원",
  "representativeNameSnapshot": "홍길동",
  "farmAddressSnapshot": "충청북도 충주시 예시로 1",
  "cityCountySnapshot": "CHUNGJU",
  "businessRegistrationNumberSnapshot": "1234567890",
  "farmAreaPyeongSnapshot": 500
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
| `404` | `FARM_PROFILE_NOT_FOUND` | 해당 `profileId`의 농가 프로필이 없음 |
| `409` | `OWNERSHIP_REVIEW_NOT_ALLOWED` | 제출 이력이 없거나, 최신 제출이 `PENDING_REVIEW`가 아님(이미 승인·반려됨) |

상태 오류 예시:

```json
{
  "code": "OWNERSHIP_REVIEW_NOT_ALLOWED",
  "message": "심사 대기 중인 최신 소유 증빙 제출만 처리할 수 있습니다."
}
```

## 6. 농가 소유 증빙 반려

승인과 조건은 동일하다(최신 제출이 `PENDING_REVIEW`일 때만). 반려 시 `reason`이 필수이며, 제출 건과 농가 프로필 모두 `REJECTED`로 바뀌고 심사자·심사 시각·반려 사유가 함께 기록된다.

### 6.1 요청

```http
POST /api/admin/farm-profiles/7/ownership/reject
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "reason": "농지원부의 소유자 정보가 대표자명과 일치하지 않습니다."
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `reason` | String | O | 공백만 입력 불가, 최대 1000자 |

### 6.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 15,
  "attemptNumber": 1,
  "status": "REJECTED",
  "farmProfileStatus": "REJECTED",
  "submittedAt": "2026-08-23T01:00:00Z",
  "reviewerId": 3,
  "reviewerName": "충북 담당자",
  "reviewedAt": "2026-08-24T13:05:00Z",
  "rejectionReason": "농지원부의 소유자 정보가 대표자명과 일치하지 않습니다.",
  "documents": [
    {
      "id": 30,
      "originalFilename": "농지원부.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 204800
    }
  ],
  "farmNameSnapshot": "충주 사과농원",
  "representativeNameSnapshot": "홍길동",
  "farmAddressSnapshot": "충청북도 충주시 예시로 1",
  "cityCountySnapshot": "CHUNGJU",
  "businessRegistrationNumberSnapshot": "1234567890",
  "farmAreaPyeongSnapshot": 500
}
```

### 6.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `reason` 누락(공백 포함) 또는 1000자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `FARM_PROFILE_NOT_FOUND` | 해당 `profileId`의 농가 프로필이 없음 |
| `409` | `OWNERSHIP_REVIEW_NOT_ALLOWED` | 제출 이력이 없거나, 최신 제출이 `PENDING_REVIEW`가 아님 |

## 7. 인증·인가 오류 응답 주의사항

`401`·`403`(URL 패턴 단계)은 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드).

- `401`: `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`
- `403`: `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`

`CENTER_ADMIN_ROLE_REQUIRED`(403)는 컨트롤러 진입 이후 서비스 레이어에서 `DomainException`으로 던져지며 전역 `GlobalExceptionHandler`가 처리한다.

`FARM_PROFILE_NOT_FOUND`, `OWNERSHIP_REVIEW_NOT_ALLOWED` 등 이 도메인 고유 오류는 `GlobalExceptionHandler`가 아니라 **`farm` 패키지 전용의 컨트롤러 범위 지정 어드바이스** `FarmProfileExceptionHandler`(`@RestControllerAdvice(assignableTypes = {...})`)가 처리한다. 이 어드바이스는 지정된 컨트롤러 클래스에서 던져진 예외만 잡으므로, `AdminFarmOwnershipController`를 이 목록에 새로 추가해서 반영했다. 응답 형식(`{ code, message }`)은 전역 핸들러와 동일하다.

## 8. 상태값

### 8.1 `FarmProfile.status`

| 값 | 의미 | 이 API에서 전이 방법 |
|---|---|---|
| `DRAFT` | 기본 정보만 등록, 소유 증빙 미제출 | 전이 불가 |
| `PENDING_REVIEW` | 소유 증빙 제출, 심사 대기 | 전이 불가(농가 본인용 제출 API에서 전환) |
| `APPROVED` | 담당자가 소유 증빙 승인 | `POST .../ownership/approve` |
| `REJECTED` | 담당자가 소유 증빙 반려 | `POST .../ownership/reject` |
| `INACTIVE` | 비활성화된 농가 프로필 | 전이 불가(이 API 범위 밖) |

### 8.2 `FarmOwnershipSubmission.status`

| 값 | 의미 | 이 API에서 전이 방법 |
|---|---|---|
| `PENDING_REVIEW` | 제출 완료, 심사 대기 | 전이 불가(농가 본인용 제출 API에서 생성) |
| `APPROVED` | 담당자 승인 | `POST .../ownership/approve` |
| `REJECTED` | 담당자 반려 | `POST .../ownership/reject` |

## 9. 데이터 구조

이 기능은 새 테이블·컬럼을 추가하지 않는다. 순수 추가된 조회 메서드는 다음과 같다(전체 컬럼 정의는 각 엔티티 참고).

- `FarmProfileRepository.findByIdForUpdate(Long id)`: `profileId` 기준 비관적 락 조회(`PESSIMISTIC_WRITE`). 기존 `findByOwnerIdForUpdate`(농가 본인용, `ownerId` 기준)와 별개로, 관리자가 `profileId`로 직접 접근하는 용도로 추가했다. `owner_user_id`는 `updatable = false` + 유니크 제약으로 프로필 생성 후 절대 바뀌지 않는 값이라, 두 락 메서드가 서로 다른 키로 같은 행을 잠가도 안전하다.
- `FarmProfileRepository.findAllByStatusOrderByUpdatedAtDesc(FarmProfileStatus status)`: 3장 목록 조회용 파생 쿼리.

## 10. 농가 소유 증빙 파일 다운로드

최신 제출 상세 응답의 `documents[].id`를 `documentId`로 사용한다. 문서가 반드시 URL의 `profileId`에 속해야 하며, 다른 농가의 문서 ID를 조합하면 `404 OWNERSHIP_DOCUMENT_NOT_FOUND`를 반환한다. 성공 시 저장소의 원본을 스트리밍하고 `Content-Type`, `Content-Length`, UTF-8 파일명이 포함된 `Content-Disposition: attachment`를 반환한다.

```http
GET /api/admin/farm-profiles/{profileId}/ownership/documents/{documentId}
Authorization: Bearer {{adminAccessToken}}
```

## 11. 현재 범위 밖 또는 미구현 기능

- 목록 조회 페이지네이션, 지역(`cityCounty`)·검색어 필터
- 승인·반려 취소 또는 재심사(한 번 전이하면 되돌릴 수 없음)
- 여러 농가 프로필 일괄 승인·반려
- 도시농부 사업참여 심사([`ADMIN_PARTICIPATION_APPLICATION_API_SPEC.md`](./ADMIN_PARTICIPATION_APPLICATION_API_SPEC.md)) · 교육 이수증 심사([`ADMIN_EDUCATION_SUBMISSION_API_SPEC.md`](./ADMIN_EDUCATION_SUBMISSION_API_SPEC.md))와 달리, 이 기능은 프로필과 제출 두 엔티티를 함께 갱신하므로 향후 유사 도메인(예: 농산물 인증 등)을 추가할 때도 "두 엔티티 동시 갱신 + 같은 트랜잭션" 패턴을 참고할 것
