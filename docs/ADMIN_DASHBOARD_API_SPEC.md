# CityFarmerPlus 관리자 대시보드 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-27
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 중개센터 담당자 대시보드 집계 조회

## 1. 공통 사항

### 1.1 기본 URL

```text
운영: https://cityfarmerplus-api-82951616760.us-west1.run.app
로컬: http://localhost:8080
```

### 1.2 요청 및 응답 형식

- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{adminAccessToken}}
```

### 1.3 인증 및 권한 규칙

- `/api/admin/**`는 `SecurityConfig`에서 `hasRole("CENTER_ADMIN")`으로 URL 패턴 단위로 이미 보호된다. 이 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('CENTER_ADMIN')")`을 추가로 걸어 이중으로 확인한다.
- **다른 관리자 API(승인/반려/정정 등)와 달리, 이 API는 서비스 레이어에서 JWT의 관리자 ID를 DB로 재조회하지 않는다.** 단순 집계 조회라 "누가 조회했는지"를 기록하거나 활성 계정 여부를 다시 검증할 필요가 없기 때문이다 — `AdminJobPostingService.list()`/`getReviewHistory()`와 같은 이유로 같은 패턴을 따른다. 그래서 `CENTER_ADMIN_ROLE_REQUIRED`/`INACTIVE_ACCOUNT`/`USER_NOT_FOUND` 오류는 이 API에는 없다.
- 이 API는 여러 도메인의 리포지토리(`ParticipationApplicationRepository`, `EducationCertificateSubmissionRepository`, `FarmProfileRepository`, `JobPostingRepository`, `JobApplicationRepository`, `WorkAssignmentRepository`, `UserRepository`)에 이미 있던 `countByStatus` 계열 카운트 쿼리를 그대로 재사용한다. 새로 추가한 쿼리는 `ParticipationApplicationRepository.countByStatus`뿐이다.
- 11개의 카운트 쿼리는 하나의 `@Transactional(readOnly = true)` 메서드 안에서 **순차적으로** 실행된다. 전부 상태 컬럼 기준 단순 `COUNT(*)`라 병렬화할 정도의 비용이 아니라고 판단했다(현재 데이터 규모 기준).

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만든다. 자세한 내용은 3.3절을 참고한다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 대시보드 집계 조회 | `GET` | `/api/admin/dashboard` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

## 3. 대시보드 집계 조회

### 3.1 요청

```http
GET /api/admin/dashboard
Authorization: Bearer {{adminAccessToken}}
```

쿼리 파라미터와 요청 본문은 없다.

### 3.2 성공 응답

```http
HTTP/1.1 200 OK
Content-Type: application/json
```

```json
{
  "submittedParticipationApplications": 4,
  "pendingEducationSubmissions": 7,
  "pendingFarmOwnershipSubmissions": 2,
  "pendingJobPostings": 3,
  "openJobPostings": 12,
  "pendingJobApplications": 9,
  "scheduledWorkAssignments": 21,
  "completedWorkAssignments": 58,
  "activeUrbanFarmerCount": 134,
  "activeFarmCount": 26,
  "activeCenterAdminCount": 3
}
```

각 필드가 정확히 무엇을 세는지는 다음과 같다.

| 필드 | 의미 | 집계 기준 |
|---|---|---|
| `submittedParticipationApplications` | 제출된 사업참여 신청 수 | `ParticipationApplication.status == SUBMITTED` 전체 건수(연도 구분 없이 전체 `programYear` 합산, `ParticipationApplicationRepository.countByStatus`) |
| `pendingEducationSubmissions` | 심사 대기 이수증 수 | `EducationCertificateSubmission.status == PENDING_REVIEW`인 제출 건수(`EducationCertificateSubmissionRepository.countByStatus` — 탈퇴·정지 계정의 제출은 이 카운트에서도 자동 제외됨, 심사 목록 API와 동일 조건) |
| `pendingFarmOwnershipSubmissions` | 심사 대기 농가 소유 증빙 수 | 소유 증빙 제출(`FarmOwnershipSubmission`) 자체가 아니라 **`FarmProfile.status == PENDING_REVIEW`인 농가 프로필 수**(`FarmProfileRepository.countByStatus`) — 관리자용 목록 API(`GET /api/admin/farm-profiles?status=PENDING_REVIEW`)와 동일한 기준이라 그 목록의 건수와 항상 일치한다 |
| `pendingJobPostings` | 심사 대기 모집 공고 수 | `JobPosting.status == PENDING_REVIEW`인 공고 수(`JobPostingRepository.countByStatus`) |
| `openJobPostings` | 모집 중 공고 수 | `JobPosting.status == OPEN`인 공고 수(`JobPostingRepository.countByStatus`) — 작업 시작 시각이 이미 지난 공고도 상태가 아직 `OPEN`이면 포함된다(시간 기준 추가 필터 없음) |
| `pendingJobApplications` | 매칭 대기 지원 수 | `JobApplication.status == APPLIED`인 지원 수(`JobApplicationRepository.countByStatus`) — 아직 매칭도 `NOT_MATCHED` 전환도 되지 않은 건 |
| `scheduledWorkAssignments` | 예정 근무 수 | `WorkAssignment.status == SCHEDULED`인 근무 배정 수(`WorkAssignmentRepository.countByStatus`) |
| `completedWorkAssignments` | 완료 근무 수 | `WorkAssignment.status == COMPLETED`인 근무 배정 수(`WorkAssignmentRepository.countByStatus`) |
| `activeUrbanFarmerCount` | 활성 도시농부 회원 수 | `User.userType == URBAN_FARMER && accountStatus == ACTIVE`(`UserRepository.countByUserTypeAndAccountStatus`) |
| `activeFarmCount` | 활성 농가 회원 수 | `User.userType == FARM && accountStatus == ACTIVE`(`UserRepository.countByUserTypeAndAccountStatus`) |
| `activeCenterAdminCount` | 활성 중개센터 담당자 수 | `User.userType == CENTER_ADMIN && accountStatus == ACTIVE`(`UserRepository.countByUserTypeAndAccountStatus`) |

모든 필드는 조회 시점 스냅샷이며, 응답 자체에는 집계 시각이 별도로 포함되지 않는다.

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근(URL 패턴 단계) |

## 4. 현재 범위 밖 또는 미구현 기능

- 집계 결과 캐싱(매 요청마다 11개 쿼리를 그대로 다시 실행한다)
- 기간별 추이·통계(일별/주별 변화량, 그래프용 시계열 데이터)
- 항목 클릭 시 상세 목록으로 바로 연결되는 링크·필터 파라미터(각 카운트에 대응하는 기존 목록 API를 별도로 호출해야 한다)
- 사업참여 신청 수의 연도(`programYear`)별 분리 집계(현재는 전체 연도 합산)
- 응답에 집계 시각(`generatedAt` 등) 포함
