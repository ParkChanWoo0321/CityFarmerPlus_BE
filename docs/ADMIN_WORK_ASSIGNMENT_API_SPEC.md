# CityFarmerPlus 관리자 근무 배정 조회 및 출결 정정 API 명세서

- 문서 버전: 1.1
- 작성일: 2026-08-26
- 구현 기준 브랜치: `backend-2`
- 적용 범위: 근무 배정 목록·상세 조회, 출결 정정, 정정 이력 조회 (최초 출결 등록·근무 완료 확정은 농가 전용이며 이 문서 범위 밖)

## 1. 공통 사항

### 1.1 기본 URL

```text
http://localhost:8080
```

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{adminAccessToken}}
```

### 1.3 인증 및 권한 규칙

- `/api/admin/**`는 `SecurityConfig`에서 `hasRole("CENTER_ADMIN")`으로 URL 패턴 단위로 이미 보호된다. 이 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('CENTER_ADMIN')")`을 추가로 걸어 이중으로 확인한다.
- 서비스 레이어에서 JWT와 무관하게 **DB에 저장된 실제 역할을 다시 조회**한다(`AdminWorkAssignmentService.requireCenterAdmin`).
- 정정자 ID는 요청 body로 받지 않는다. 항상 JWT의 `sub`(`AuthenticatedUser.id(authentication)`)에서 추출한다.
- **담당자의 역할은 "이미 등록된 출결을 바로잡는 것"으로 한정된다.** 최초 출결 등록(`PUT /api/farm/work-assignments/{assignmentId}/attendance`)과 정상적인 근무 완료 확정(`POST /api/farm/work-assignments/{assignmentId}/complete`)은 전부 농가 전용 API이며, 이 API로는 절대 할 수 없다. 엔티티에도 두 액션이 이름부터 분리되어 있다 — 농가용은 `WorkAssignment.recordAttendance()`(최초 1회만, `AttendanceStatus.NOT_RECORDED`일 때만 가능), 관리자용은 `WorkAssignment.correctAttendance()`(이미 등록된 값을 다른 값으로 바꿀 때만 가능)다.
- **최초 등록 여부는 서비스 레이어에서 별도로 막는다.** `correctAttendance()` 자체는 `NOT_RECORDED` 상태에서의 호출을 막지 않으므로, `AdminWorkAssignmentService`가 호출 전에 `attendanceStatus != NOT_RECORDED`인지 직접 확인한다(4.3절 오류 참고).
- 정정으로 `WorkAssignment.status`가 다시 `SCHEDULED`로 바뀌었는데 소속 `JobPosting`이 이미 `WORK_COMPLETED`(전부 해결됨)였다면, 같은 트랜잭션 안에서 `JobPosting.reopenAfterAttendanceCorrection()`을 호출해 `CLOSED`로 되돌린다. 정확한 판단 기준은 4.4절을 참고한다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`WorkAssignmentException`은 `common.exception.DomainException`을 상속하므로 전역 `GlobalExceptionHandler`가 컨트롤러 종류와 무관하게 처리한다(농가 소유 증빙 심사 때와 달리, 이 도메인은 컨트롤러 범위를 지정하는 별도 어드바이스가 필요 없었다). 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다. 자세한 내용은 5장을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 근무 배정 목록 조회 | `GET` | `/api/admin/work-assignments` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 근무 배정 상세 조회 | `GET` | `/api/admin/work-assignments/{assignmentId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 출결 정정 | `POST` | `/api/admin/work-assignments/{assignmentId}/attendance-correction` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 정정 이력 조회 | `GET` | `/api/admin/work-assignments/{assignmentId}/correction-history` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

## 3. 관련 도메인 개념

### 3.1 `WorkAssignment.status` (`WorkStatus`)

| 값 | 의미 |
|---|---|
| `SCHEDULED` | 근무 예정(또는 출결이 아직 결과 확정 전) |
| `COMPLETED` | 농가가 근무 완료로 확정함(이 상태로는 정정 API가 절대 진입시키지 않음) |
| `NO_SHOW` | 결근 처리됨 |
| `CANCELLED` | 취소됨(정정 대상 아님 — `correctAttendance()`가 자체적으로 막음) |

### 3.2 `WorkAssignment.attendanceStatus` (`AttendanceStatus`)

| 값 | 의미 |
|---|---|
| `NOT_RECORDED` | 아직 출결 미등록(이 상태는 이 API의 대상이 아니다 — 4.3절 참고) |
| `PRESENT` | 출근 |
| `ABSENT` | 결근 |

### 3.3 정정으로 실제 도달 가능한 상태 조합

`correctAttendance()`의 로직상, 정정 후 `WorkAssignment.status`는 **`NO_SHOW` 아니면 `SCHEDULED` 둘 중 하나만** 가능하다(`COMPLETED`로는 절대 안 돌아간다 — 그건 농가만 `completeByFarm()`으로 할 수 있음).

| 정정 방향 | 정정 전 상태 | 정정 후 상태 |
|---|---|---|
| `PRESENT` → `ABSENT` | `SCHEDULED` 또는 `COMPLETED` | `NO_SHOW` |
| `ABSENT` → `PRESENT` | `NO_SHOW` | `SCHEDULED` |

## 4. 출결 정정

### 4.1 요청

```http
POST /api/admin/work-assignments/55/attendance-correction
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "status": "ABSENT",
  "reason": "현장 확인 결과 실제로는 결근했습니다."
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `status` | Enum | O | `PRESENT` 또는 `ABSENT`(`NOT_RECORDED`는 허용 안 됨) |
| `reason` | String | O | 공백만 입력 불가, 최대 1000자 |

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
{
  "id": 1,
  "workAssignmentId": 55,
  "previousWorkStatus": "SCHEDULED",
  "newWorkStatus": "NO_SHOW",
  "previousAttendanceStatus": "PRESENT",
  "newAttendanceStatus": "ABSENT",
  "correctedByUserId": 3,
  "correctedByName": "충북 담당자",
  "reason": "현장 확인 결과 실제로는 결근했습니다.",
  "correctedAt": "2026-08-26T07:10:00Z"
}
```

응답은 `WorkAssignment`가 아니라 이번에 새로 생성된 **정정 이력(`WorkAssignmentCorrection`) 레코드 자체**다 — 정정 전/후 값과 정정자를 한 번에 보여주기 위함이다(사업참여/교육/농가소유증빙 심사와 같은 이유로, `JobPostingReview`와 같은 패턴).

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `status` 누락, 또는 `reason` 누락(공백 포함)·1000자 초과 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨(서비스 재검증 단계) |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `WORK_ASSIGNMENT_NOT_FOUND` | 해당 `assignmentId`의 근무 일정이 없음 |
| `409` | `INVALID_WORK_ASSIGNMENT_STATE` | 아래 세 가지 중 하나 |

`INVALID_WORK_ASSIGNMENT_STATE`로 묶이는 세 가지 상황(메시지로 구분):

```json
{ "code": "INVALID_WORK_ASSIGNMENT_STATE", "message": "아직 최초 출결이 등록되지 않았습니다. 정정이 아니라 최초 등록이 필요합니다." }
```
→ `attendanceStatus == NOT_RECORDED`인 건에 이 API를 호출한 경우. **최초 등록은 `PUT /api/farm/work-assignments/{assignmentId}/attendance`(농가 전용)로만 가능하다.**

```json
{ "code": "INVALID_WORK_ASSIGNMENT_STATE", "message": "취소된 근무의 출결은 정정할 수 없습니다." }
```
→ `WorkStatus == CANCELLED`인 건.

```json
{ "code": "INVALID_WORK_ASSIGNMENT_STATE", "message": "기존 출결과 다른 값으로 정정해야 합니다." }
```
→ `status`에 현재와 같은 값을 보낸 경우(예: 이미 `ABSENT`인데 `ABSENT`로 다시 정정 시도).

### 4.4 공고 상태 재조정 판단 기준

`WorkAssignment.status`는 정정 후 `NO_SHOW` 또는 `SCHEDULED`만 가능하다(3.3절). 이 중 **`SCHEDULED`가 됐을 때만** 소속 공고의 "전부 해결됨" 전제가 깨진다 — 농가 쪽 자동 완료 로직(`WorkAssignmentService.completePostingWhenAllAssignmentsResolved`)이 "미해결"을 정확히 `WorkStatus.SCHEDULED`인 배정이 하나라도 있는지로 판단하기 때문이다(`NO_SHOW`는 "문제 있는 결과"지만 이 기준에서는 이미 해결된 것으로 취급된다).

그래서 이 API는 **정정 후 상태가 `SCHEDULED`이고, 소속 공고가 `WORK_COMPLETED`일 때만** `JobPosting.reopenAfterAttendanceCorrection()`(`WORK_COMPLETED → CLOSED`)을 호출한다. 방금 정정한 배정 하나가 `SCHEDULED`가 됐다는 사실 자체가 "미해결 배정이 최소 1건 존재"를 증명하므로, 같은 공고의 다른 배정들을 다시 세어볼 필요는 없다. 반대로 `ABSENT → PRESENT`가 아닌 방향(`PRESENT → ABSENT`, 결과가 `NO_SHOW`)은 애초에 "미해결" 집합에 들어간 적이 없어 공고 상태를 건드리지 않는다. 공고가 `WORK_COMPLETED`가 아니라 `CLOSED`(아직 완료 처리 전)라면 이 전제 자체가 적용되지 않으므로 아무것도 하지 않는다.

이 재조정은 **응답 본문에 나타나지 않는다**(4.2절 응답은 정정 이력만 보여줌) — 필요하면 공고 상세 조회 API로 별도 확인해야 한다.

## 5. 정정 이력 조회

특정 근무 일정에 대해 지금까지 있었던 모든 정정 이력을 최신순으로 반환한다.

### 5.1 요청

```http
GET /api/admin/work-assignments/55/correction-history
Authorization: Bearer {{adminAccessToken}}
```

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "id": 1,
    "workAssignmentId": 55,
    "previousWorkStatus": "SCHEDULED",
    "newWorkStatus": "NO_SHOW",
    "previousAttendanceStatus": "PRESENT",
    "newAttendanceStatus": "ABSENT",
    "correctedByUserId": 3,
    "correctedByName": "충북 담당자",
    "reason": "현장 확인 결과 실제로는 결근했습니다.",
    "correctedAt": "2026-08-26T07:10:00Z"
  }
]
```

정정이 한 번도 없었던 근무 일정이면 빈 배열(`[]`)을 반환한다(오류가 아니다).

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `WORK_ASSIGNMENT_NOT_FOUND` | 해당 `assignmentId`의 근무 일정이 없음 |

## 6. 인증·인가 오류 응답 주의사항

`401`·`403`(URL 패턴 단계)은 `SecurityConfig`가 직접 응답 본문을 작성한다(`writeError` 메서드).

- `401`: `{ "code": "UNAUTHORIZED", "message": "인증이 필요합니다." }`
- `403`: `{ "code": "ACCESS_DENIED", "message": "접근 권한이 없습니다." }`

`CENTER_ADMIN_ROLE_REQUIRED`(403)는 컨트롤러 진입 이후 서비스 레이어에서 `DomainException`으로 던져지며 전역 `GlobalExceptionHandler`가 처리한다. 형식(`{ code, message }`)은 동일하다.

## 7. 데이터 구조

새 테이블 `work_assignment_corrections`(`WorkAssignmentCorrection` 엔티티, `JobPostingReview`와 동일한 append-only 감사 로그 패턴).

| 컬럼 | 제약 | 설명 |
|---|---|---|
| `id` | PK, Auto Increment | 정정 이력 식별자 |
| `work_assignment_id` | FK, NOT NULL | 정정 대상 근무 일정 |
| `previous_work_status` / `new_work_status` | NOT NULL, enum | 정정 전/후 `WorkStatus` |
| `previous_attendance_status` / `new_attendance_status` | NOT NULL, enum | 정정 전/후 `AttendanceStatus` |
| `corrected_by_user_id` | FK, NOT NULL | 정정한 담당자(`CENTER_ADMIN`만 가능, 엔티티 팩토리에서 검증) |
| `reason` | NULL 허용(엔티티 팩토리가 공백 거부), 최대 1000자 | 정정 사유 |
| `corrected_at` | NOT NULL | 정정 시각(`@CreationTimestamp`) |

## 8. 현재 범위 밖 또는 미구현 기능

- 정정 취소/되돌리기(정정 이력은 append-only라 삭제·수정 API가 없음 — 잘못 정정했다면 반대 방향으로 다시 정정해야 함)
- 여러 근무 일정 일괄 정정
- "정정 대상 후보" 전용 큐는 없지만, 9장의 목록 조회에 `status=NO_SHOW` 필터를 걸면 결근 처리된 건 전체를 유사하게 확인할 수 있다(전용 대기 상태 개념은 여전히 없음)
- 근무 일정 자체의 취소(관리자용), 근태 관련 통계·대시보드
- 목록 조회의 상태 필터 외 추가 필터(농가·도시농부·근무일·공고별 등), 검색어, 정렬 옵션 지정

## 9. 근무 배정 목록 조회

전체 근무 배정을 페이지네이션으로 조회한다. 필터는 상태값(`status`) 하나뿐이며, 생략하면 전체 상태를 반환한다.

### 9.1 요청

```http
GET /api/admin/work-assignments?status=NO_SHOW&page=0&size=20
Authorization: Bearer {{adminAccessToken}}
```

| 쿼리 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---|---|---|---|---|
| `status` | Enum | X | 없음(전체) | `SCHEDULED`, `COMPLETED`, `NO_SHOW`, `CANCELLED` 중 하나 |
| `page` | int | X | `0` | Spring Data 기본 페이지네이션(0부터 시작) |
| `size` | int | X | `20` | Spring Data 기본값 |
| `sort` | String | X | 없음 | 예: `sort=workDate,desc` |

### 9.2 성공 응답

```http
HTTP/1.1 200 OK
```

컨트롤러가 `Page<WorkAssignmentResponse>`를 그대로 반환하므로, 응답 본문은 `content` 배열과 페이지 메타데이터를 함께 담은 형식이다(다른 관리자 목록 API와 동일한 `Page` 직렬화 형식 — `ADMIN_JOB_POSTING_API_SPEC.md` 3.2절 참고).

```json
{
  "content": [
    {
      "id": 55,
      "jobPostingId": 12,
      "jobApplicationId": 101,
      "urbanFarmerUserId": 10,
      "urbanFarmerName": "홍길동",
      "confirmedByUserId": 3,
      "confirmedByName": "충북 담당자",
      "confirmedByContactNumber": "01011112222",
      "farmName": "충주 사과농원",
      "farmAddress": "충청북도 충주시 예시로 1",
      "farmContactNumber": "01012345678",
      "crop": "사과",
      "workType": "수확",
      "workDate": "2026-09-01",
      "startTime": "09:00:00",
      "endTime": "17:00:00",
      "recruitmentCapacity": 5,
      "meetingPlace": "충주시 사과농원 정문",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "supplies": "장갑, 모자",
      "precautions": "미끄럼 주의",
      "status": "NO_SHOW",
      "attendanceStatus": "ABSENT",
      "completedAt": null
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

`content`의 각 항목은 [`WorkAssignmentResponse`](../src/main/java/chungbuk/cityfarmerplus/work/dto/WorkAssignmentResponse.java)로, 4.2절 정정 응답과 달리 근무 배정 자체의 전체 필드(공고·도시농부·농가·임금·현재 상태)를 담는다. 정렬 기본값은 `JpaRepository.findAll(Pageable)`/`findByStatus(status, Pageable)`가 그대로 적용하는 Spring Data 기본 정렬(지정 없으면 무정렬)이다.

### 9.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `INVALID_REQUEST_PARAMETER` | `status` 값이 `WorkStatus` enum에 없는 문자열 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |

## 10. 근무 배정 상세 조회

### 10.1 요청

```http
GET /api/admin/work-assignments/55
Authorization: Bearer {{adminAccessToken}}
```

### 10.2 성공 응답

```http
HTTP/1.1 200 OK
```

9.2절 `content` 배열 안 항목과 동일한 `WorkAssignmentResponse` 형식이다(단일 객체). 상태와 무관하게 조회할 수 있다(`SCHEDULED`, `COMPLETED`, `NO_SHOW`, `CANCELLED` 전부 조회 가능).

### 10.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |
| `403` | `CENTER_ADMIN_ROLE_REQUIRED` | JWT는 유효하지만 DB상 해당 계정이 `CENTER_ADMIN`이 아니거나 삭제됨 |
| `403` | `INACTIVE_ACCOUNT` | 관리자 계정이 정지·탈퇴 상태 |
| `404` | `USER_NOT_FOUND` | JWT의 관리자 ID에 해당하는 회원이 없음 |
| `404` | `WORK_ASSIGNMENT_NOT_FOUND` | 해당 `assignmentId`의 근무 일정이 없음 |
