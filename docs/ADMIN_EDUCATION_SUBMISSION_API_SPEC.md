# CityFarmerPlus 관리자 교육 이수증 심사 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-20
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 교육 이수증 제출 목록 조회, 상세 조회, 승인, 반려

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
- 서비스 레이어에서 JWT와 무관하게 **DB에 저장된 실제 역할을 다시 조회**한다(`AdminEducationSubmissionService.requireCenterAdmin`).
- 심사자 ID는 요청 body로 받지 않는다. 항상 JWT의 `sub`(`AuthenticatedUser.id(authentication)`)에서 추출한다.
- 이 API들은 [`education`](../src/main/java/chungbuk/cityfarmerplus/education) 패키지의 `EducationCertificateSubmission` 엔티티, `EducationCertificateSubmissionRepository`, 응답 DTO(`EducationSubmissionResponse`)를 그대로 재사용한다.
- **승인·반려에 성공하면 개별 제출 건뿐 아니라, 도시농부 1명의 전체 교육 이수 집계(`EducationCertification`)도 같은 트랜잭션 안에서 즉시 재계산된다**(`EducationCertificationProgressSynchronizer.synchronizeLocked`). 여러 필수 과정이 있을 때 이번 심사 건 하나만으로 전체 이수 상태가 `APPROVED`로 바뀌지 않으며, 활성 필수 과정 전체의 최신 제출 상태를 다시 계산한 결과에 따라 `NOT_SUBMITTED` / `PENDING_REVIEW` / `PARTIALLY_APPROVED` / `APPROVED` / `REJECTED` 중 하나로 갱신된다. 이 집계 결과는 이 API의 응답에는 포함되지 않으며, 도시농부 본인용 `GET /api/urban-farmers/me/education-certification` 등으로 확인해야 한다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`GlobalExceptionHandler`의 `handleDomainException`이 처리한다. 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다. 자세한 내용은 8장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 이수증 제출 목록 조회 | `GET` | `/api/admin/education/submissions` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 이수증 제출 상세 조회 | `GET` | `/api/admin/education/submissions/{submissionId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 이수증 첨부 파일 다운로드 | `GET` | `/api/admin/education/submissions/{submissionId}/documents/{documentId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 이수증 제출 승인 | `POST` | `/api/admin/education/submissions/{submissionId}/approve` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 이수증 제출 반려 | `POST` | `/api/admin/education/submissions/{submissionId}/reject` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

## 3. 이수증 제출 목록 조회

**`PENDING_REVIEW`(검토 중) 상태의 제출만 반환한다.** 이미 승인·반려된 이력을 조회하는 기능은 없다. 탈퇴·정지 계정의 제출은 목록에서 제외된다.

### 3.1 요청

```http
GET /api/admin/education/submissions?page=0&size=20
Authorization: Bearer {{adminAccessToken}}
```

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `page` | int | X | `0` | Spring Data 기본 페이지네이션(0부터 시작) |
| `size` | int | X | `20` | Spring Data 기본값 |
| `sort` | String | X | 없음 | 예: `sort=submittedAt,asc` |

상태 필터는 지원하지 않는다(항상 `PENDING_REVIEW`로 고정).

### 3.2 성공 응답

```http
HTTP/1.1 200 OK
```

컨트롤러가 `Page<EducationSubmissionResponse>`를 그대로 반환하므로, 응답 본문은 `content` 배열과 페이지 메타데이터를 함께 담은 다음 형식이다.

```json
{
  "content": [
    {
      "id": 21,
      "certificationId": 10,
      "urbanFarmerId": 10,
      "urbanFarmerName": "홍길동",
      "courseId": 2,
      "courseTitle": "도시농업 기초과정",
      "attemptNumber": 1,
      "completionDate": "2026-07-15",
      "completionHours": 10,
      "status": "PENDING_REVIEW",
      "reviewedByUserId": null,
      "reviewedAt": null,
      "recognizedHours": null,
      "rejectionReason": null,
      "documents": [
        {
          "id": 5,
          "displayOrder": 0,
          "originalFilename": "수료증.pdf",
          "contentType": "application/pdf",
          "sizeBytes": 204800,
          "sha256": "3b1c...",
          "createdAt": "2026-07-16T01:00:00Z"
        }
      ],
      "version": 0,
      "submittedAt": "2026-07-16T01:00:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "offset": 0,
    "paged": true,
    "unpaged": false,
    "sort": { "sorted": false, "unsorted": true, "empty": true }
  },
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "sort": { "sorted": false, "unsorted": true, "empty": true },
  "first": true,
  "last": true,
  "numberOfElements": 1,
  "empty": false
}
```

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

## 4. 이수증 제출 상세 조회

### 4.1 요청

```http
GET /api/admin/education/submissions/21
Authorization: Bearer {{adminAccessToken}}
```

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

응답 본문은 3.2의 `content` 배열 안 항목과 동일한 `EducationSubmissionResponse` 형식이다(단일 객체). 목록 조회와 달리 **상태와 무관하게** 조회할 수 있다(승인·반려된 건도 상세 조회 가능).

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |
| `404` | `EDUCATION_SUBMISSION_NOT_FOUND` | 해당 `submissionId`의 제출이 없음 |

제출 없음 예시:

```json
{
  "code": "EDUCATION_SUBMISSION_NOT_FOUND",
  "message": "교육 이수증 제출 내역을 찾을 수 없습니다."
}
```

## 5. 이수증 제출 승인

`PENDING_REVIEW` 상태의 제출만 승인할 수 있다. `recognizedHours`(인정 교육 시간)는 필수이며, 해당 과정의 필수 이수 시간(`EducationCourse.requiredHours`, 최소 8시간 보정) 이상이고 도시농부가 신고한 `completionHours` 이하여야 한다. 승인 시 상태가 `APPROVED`로 바뀌고 심사자·심사 시각·인정 시간이 기록된다.

### 5.1 요청

```http
POST /api/admin/education/submissions/21/approve
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "recognizedHours": 10
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `recognizedHours` | Integer | O | 1 이상(DTO 레벨). 실제 유효 범위는 과정별 필수 시간과 제출된 이수 시간에 따라 달라지며 엔티티에서 재검증한다 |

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 21,
  "certificationId": 10,
  "urbanFarmerId": 10,
  "urbanFarmerName": "홍길동",
  "courseId": 2,
  "courseTitle": "도시농업 기초과정",
  "attemptNumber": 1,
  "completionDate": "2026-07-15",
  "completionHours": 10,
  "status": "APPROVED",
  "reviewedByUserId": 3,
  "reviewedAt": "2026-08-20T07:00:00Z",
  "recognizedHours": 10,
  "rejectionReason": null,
  "documents": [
    {
      "id": 5,
      "displayOrder": 0,
      "originalFilename": "수료증.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 204800,
      "sha256": "3b1c...",
      "createdAt": "2026-07-16T01:00:00Z"
    }
  ],
  "version": 1,
  "submittedAt": "2026-07-16T01:00:00Z"
}
```

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `recognizedHours` 누락 또는 1 미만 |
| `400` | `INVALID_RECOGNIZED_HOURS` | `recognizedHours`가 과정 필수 시간 미만이거나 제출된 `completionHours`를 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `EDUCATION_SUBMISSION_NOT_FOUND` | 해당 `submissionId`의 제출이 없음(탈퇴·정지 계정의 제출 포함) |
| `409` | `INVALID_EDUCATION_SUBMISSION_STATUS` | 제출이 `PENDING_REVIEW` 상태가 아님 |

인정 시간 오류 예시:

```json
{
  "code": "INVALID_RECOGNIZED_HOURS",
  "message": "인정 교육 시간은 과정 필수 시간 이상이며 제출한 이수 시간을 초과할 수 없습니다."
}
```

## 6. 이수증 제출 반려

`PENDING_REVIEW` 상태의 제출만 반려할 수 있다. 반려 시 `reason`이 필수이며, 상태가 `REJECTED`로 바뀌고 심사자·심사 시각·반려 사유가 기록된다(`recognizedHours`는 `null`로 초기화).

### 6.1 요청

```http
POST /api/admin/education/submissions/21/reject
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "reason": "제출한 수료증 파일이 본인 명의가 아닙니다."
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
  "id": 21,
  "certificationId": 10,
  "urbanFarmerId": 10,
  "urbanFarmerName": "홍길동",
  "courseId": 2,
  "courseTitle": "도시농업 기초과정",
  "attemptNumber": 1,
  "completionDate": "2026-07-15",
  "completionHours": 10,
  "status": "REJECTED",
  "reviewedByUserId": 3,
  "reviewedAt": "2026-08-20T07:05:00Z",
  "recognizedHours": null,
  "rejectionReason": "제출한 수료증 파일이 본인 명의가 아닙니다.",
  "documents": [
    {
      "id": 5,
      "displayOrder": 0,
      "originalFilename": "수료증.pdf",
      "contentType": "application/pdf",
      "sizeBytes": 204800,
      "sha256": "3b1c...",
      "createdAt": "2026-07-16T01:00:00Z"
    }
  ],
  "version": 1,
  "submittedAt": "2026-07-16T01:00:00Z"
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
| `404` | `EDUCATION_SUBMISSION_NOT_FOUND` | 해당 `submissionId`의 제출이 없음(탈퇴·정지 계정의 제출 포함) |
| `409` | `INVALID_EDUCATION_SUBMISSION_STATUS` | 제출이 `PENDING_REVIEW` 상태가 아님 |

## 7. 승인·반려 시 전체 이수 집계 재계산 규칙

승인·반려 API가 성공하면 같은 트랜잭션 안에서 `EducationCertificationProgressSynchronizer.synchronizeLocked(certificationId)`가 호출되어, 해당 도시농부의 `EducationCertification`(전체 이수 인증) 집계가 다음 규칙으로 다시 계산된다.

1. 현재 활성(`active=true`)인 모든 과정 중 **필수(`mandatory=true`)** 과정만 대상으로 한다.
2. 각 필수 과정마다 **가장 최근 회차(`attemptNumber` 최댓값)의 제출**만 본다.
3. 필수 과정 전부가 "최신 제출이 `APPROVED`이고 `recognizedHours`가 해당 과정 필수 시간 이상"을 만족해야만 전체 상태가 `APPROVED`가 된다.
4. 하나라도 미달이면: 필수 과정 중 `PENDING_REVIEW`가 있으면 `PENDING_REVIEW`, 없고 `REJECTED`가 있으면 `REJECTED`, 없고 `APPROVED`만 일부 있으면 `PARTIALLY_APPROVED`, 아무 제출도 없으면 `NOT_SUBMITTED`.

**즉 필수 과정이 여러 개일 때 이번 승인 1건만으로는 전체 상태가 `APPROVED`로 바뀌지 않는다.** 이 API의 응답 본문(`EducationSubmissionResponse`)에는 개별 제출 건의 상태만 담기고, 재계산된 전체 집계 상태는 포함되지 않는다.

## 8. 인증·인가 오류 응답 주의사항

`401`·`403`(URL 패턴 단계)은 `GlobalExceptionHandler`가 아니라 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드).

- `401`: `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`
- `403`: `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`

이와 달리 5·6장의 `CENTER_ADMIN_ROLE_REQUIRED`(403)는 **컨트롤러 진입 이후 서비스 레이어**에서 `DomainException`으로 던져지며 `GlobalExceptionHandler`가 처리한다. 형식(`{ code, message }`)은 같지만 코드값과 발생 위치가 다르다.

## 9. 이수증 제출 상태값

| 값 | 의미 | 이 API에서 전이 방법 |
|---|---|---|
| `PENDING_REVIEW` | 제출 완료, 심사 대기 | 전이 불가(도시농부 본인용 제출 API에서 생성) |
| `APPROVED` | 관리자 승인 | `POST .../approve` |
| `REJECTED` | 관리자 반려 | `POST .../reject` |

`PENDING_REVIEW` 상태에서만 승인·반려가 가능하며, 그 외 상태에서 시도하면 `409 INVALID_EDUCATION_SUBMISSION_STATUS`로 거부된다.

## 10. 이수증 첨부 파일 다운로드

상세 응답의 `documents[].id`를 `documentId`로 사용한다. 문서가 반드시 URL의 `submissionId`에 속해야 하며, 다른 제출의 문서 ID를 조합하면 `404 EDUCATION_DOCUMENT_NOT_FOUND`를 반환한다. 성공 시 저장소의 원본을 스트리밍하고 `Content-Type`, `Content-Length`, UTF-8 파일명이 포함된 `Content-Disposition: attachment`를 반환한다.

```http
GET /api/admin/education/submissions/{submissionId}/documents/{documentId}
Authorization: Bearer {{adminAccessToken}}
```

## 11. 현재 범위 밖 또는 미구현 기능

- 목록 조회 상태 필터(현재 `PENDING_REVIEW` 고정), 과정별·도시농부별 검색
- 승인·반려 취소 또는 재심사(한 번 전이하면 되돌릴 수 없음)
- 교육 과정 관리(등록·수정·비활성화) CRUD API — [`ADMIN_EDUCATION_COURSE_API_SPEC.md`](ADMIN_EDUCATION_COURSE_API_SPEC.md)에서 별도로 구현됨
- 여러 제출 건 일괄 승인·반려
