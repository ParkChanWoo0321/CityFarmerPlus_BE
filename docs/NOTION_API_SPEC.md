# CityFarmerPlus backend-1 API 명세

- 기준일: 2026-08-20
- 실제 HTTP 작업 수: 66개
- 기준 코드: 현재 Controller·DTO·Security·Service
- 상세 필드·오류·상태 규칙: [FULL_API_SPEC.md](FULL_API_SPEC.md)

이 문서는 노션에 바로 복사할 수 있는 현재 backend-1 API 목록이다. 구현 예정 API를 현재 기능처럼 적지 않는다.

---

## 1. 공통 요청 규칙

기본 URL:

```text
http://localhost:8080
```

인증 헤더:

```http
Authorization: Bearer {{accessToken}}
```

인증 없이 호출 가능한 API는 회원가입, 아이디 확인, 로그인, 활성 교육 과정 조회 4개다. 그 외 API는 JWT가 필요하며, 공고 조회와 FAQ도 현재 설정상 인증 대상이다.

JSON 오류 형식:

```json
{
  "code": "ERROR_CODE",
  "message": "오류 설명"
}
```

페이지 응답:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

직접 `page`, `size`를 받는 API의 기본값은 0, 20이고 최대 size는 100이다.

## 2. 역할

| 역할 | 의미 | 공개 가입 |
|---|---|---|
| `URBAN_FARMER` | 도시농부 | 가능 |
| `FARM` | 농가 | 가능 |
| `CENTER_ADMIN` | backend-2 담당자 공통 역할 | 불가능 |

한 계정은 하나의 역할만 가진다. `CENTER_ADMIN`과 심사 상태·심사자 필드는 backend-2 병합을 위한 공통 계약으로 남아 있지만, backend-1에는 담당자 계정 발급이나 담당자 업무 처리 HTTP API가 없다.

## 3. 현재 API 수

| 접근 구분 | 개수 |
|---|---:|
| 공개 | 4 |
| 로그인 사용자 공통 | 9 |
| 도시농부 전용 | 29 |
| 농가 전용 | 24 |
| 합계 | **66** |

---

# 기능 1. 인증·회원 — 7개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 회원가입 | `POST` | `/api/auth/signup` | 공개 | `201` |
| 아이디 중복 확인 | `GET` | `/api/auth/check-id?loginId=` | 공개 | `200` |
| 로그인 | `POST` | `/api/auth/login` | 공개 | `200` |
| 내 정보 조회 | `GET` | `/api/auth/me` | 활성 계정 | `200` |
| 내 정보 수정 | `PATCH` | `/api/auth/me` | 활성 계정 | `200` |
| 회원 탈퇴 | `POST` | `/api/auth/withdrawal` | 활성 계정 | `204` |
| 로그아웃 | `POST` | `/api/auth/logout` | 활성 계정 | `204` |

회원가입 핵심 필드:

- `loginId`: 영문 소문자·숫자·밑줄 4~30자
- `password`: 8~64자, UTF-8 72바이트 이하
- `name`: 최대 50자
- `userType`: `URBAN_FARMER` 또는 `FARM`
- `phoneNumber`: 선택, 숫자 10~11자리
- `birthDate`: 선택, 미래일 불가
- `address`: 선택, 최대 255자

JWT는 HS256이며 기본 만료는 1시간이다. 로그아웃은 클라이언트 JWT 삭제 방식이다.

---

# 기능 2. 도시농부 프로필·희망 근무 조건 — 6개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 프로필 생성 | `POST` | `/api/urban-farmers/me/profile` | `URBAN_FARMER` | `201` |
| 내 프로필 조회 | `GET` | `/api/urban-farmers/me/profile` | `URBAN_FARMER` | `200` |
| 내 프로필 수정 | `PATCH` | `/api/urban-farmers/me/profile` | `URBAN_FARMER` | `200` |
| 희망 조건 조회 | `GET` | `/api/urban-farmers/me/work-preference` | `URBAN_FARMER` | `200` |
| 희망 조건 등록·갱신 | `PUT` | `/api/urban-farmers/me/work-preference` | `URBAN_FARMER` | `200` |
| 희망 조건 삭제 | `DELETE` | `/api/urban-farmers/me/work-preference` | `URBAN_FARMER` | `204` |

프로필은 농업경영체 등록 여부, 경력 횟수, 경력 설명을 가진다. 희망 조건은 충북 시·군 목록, 요일 목록, 작업 유형, 희망 시작일·종료일, 이동 가능 여부, 특이사항이다. 이 API들은 아래 통합 폼과 별개로 유지된다.

---

# 기능 3. 도시농부 사업참여 신청 — 7개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 초안 생성 | `POST` | `/api/urban-farmers/me/participation-applications` | `URBAN_FARMER` | `201` |
| 내 신청 목록 | `GET` | `/api/urban-farmers/me/participation-applications` | `URBAN_FARMER` | `200` |
| 내 신청 상세 | `GET` | `/api/urban-farmers/me/participation-applications/{applicationId}` | `URBAN_FARMER` | `200` |
| 수정 | `PATCH` | `/api/urban-farmers/me/participation-applications/{applicationId}` | `URBAN_FARMER` | `200` |
| 초안 삭제 | `DELETE` | `/api/urban-farmers/me/participation-applications/{applicationId}` | `URBAN_FARMER` | `204` |
| 심사 제출 | `POST` | `/api/urban-farmers/me/participation-applications/{applicationId}/submit` | `URBAN_FARMER` | `200` |
| 신청 취소 | `POST` | `/api/urban-farmers/me/participation-applications/{applicationId}/cancel` | `URBAN_FARMER` | `200` |

상태는 `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`다. backend-1 사용자는 `SUBMITTED`까지 만들 수 있으며 승인·반려 전이는 backend-2 공통 계약이다.

## 디자인용 통합 신청 폼 — 3개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 통합 조회 | `GET` | `/api/urban-farmers/me/participation-forms/{programYear}` | `URBAN_FARMER` | `200` |
| 통합 저장 | `PUT` | `/api/urban-farmers/me/participation-forms/{programYear}` | `URBAN_FARMER` | `200` |
| 통합 제출·재제출 | `POST` | `/api/urban-farmers/me/participation-forms/{programYear}/submit` | `URBAN_FARMER` | `200` |

한 화면에서 프로필·희망 근무 조건·사업참여 신청을 조회하고 하나의 트랜잭션으로 저장한다. 신청이 없으면 조회는 `NOT_STARTED`를 반환한다. 저장 요청은 세 리소스의 expected version을 선택적으로 받아 동시 수정을 감지한다. `SUBMITTED`는 `PUT`으로 수정하고, `REJECTED`는 제출 API로 재제출한다. 승인 후에는 경험·희망 조건만 수정 가능하다.

통합 폼은 편의 API다. 기존 프로필 3개, 희망 근무 조건 3개, 사업참여 신청 7개 API는 삭제되지 않고 독립 API로 유지된다.

---

# 기능 4. 교육 과정·교육 인증 — 6개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 활성 과정 목록 | `GET` | `/api/education/courses` | 공개 | `200` |
| 인증 진행률 | `GET` | `/api/urban-farmers/me/education-certification` | `URBAN_FARMER` | `200` |
| 제출 이력 | `GET` | `/api/urban-farmers/me/education-certification/submissions` | `URBAN_FARMER` | `200` |
| 이수증 제출 | `POST` | `/api/urban-farmers/me/education-certification/submissions` | `URBAN_FARMER` | `201` |
| 제출 상세 | `GET` | `/api/urban-farmers/me/education-certification/submissions/{submissionId}` | `URBAN_FARMER` | `200` |
| 내 파일 다운로드 | `GET` | `/api/urban-farmers/me/education-certification/submissions/{submissionId}/documents/{documentId}` | `URBAN_FARMER` | `200` |

제출은 `multipart/form-data`이며 `request` JSON과 `documents` 1~5개를 보낸다. 활성 필수 과정이 한 개 이상 존재하고 각 과정의 최신 제출이 모두 `APPROVED`여야 공고 지원이 가능하다. backend-1에는 교육 심사 API가 없다.

---

# 기능 5. 농가 프로필·소유 증빙 — 7개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 프로필 생성 | `POST` | `/api/farm-profiles` | `FARM` | `201` |
| 내 프로필 조회 | `GET` | `/api/farm-profiles/me` | `FARM` | `200` |
| 내 프로필 수정 | `PATCH` | `/api/farm-profiles/me` | `FARM` | `200` |
| 증빙 제출 이력 | `GET` | `/api/farm-profiles/me/ownership-submissions` | `FARM` | `200` |
| 증빙 제출 상세 | `GET` | `/api/farm-profiles/me/ownership-submissions/{submissionId}` | `FARM` | `200` |
| 증빙 제출 | `POST` | `/api/farm-profiles/me/ownership-submissions` | `FARM` | `201` |
| 본인 문서 다운로드 | `GET` | `/api/farm-ownership-documents/{documentId}/file` | `FARM` 소유자 | `200` |

농가 프로필에는 농가명, 대표자명, 연락처, 주소, 시·군, 작물, 주요 활동, 사업자번호, 농지 면적이 필요하다. 증빙 제출 시 프로필과 제출 건이 `PENDING_REVIEW`가 되며, 반려 후 재제출은 과거 파일을 유지한 새 회차다. backend-1에는 농가 심사 API가 없다.

---

# 기능 6. AI 초안·모집 공고 — 13개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| AI 공고 문구 미리보기 | `POST` | `/api/ai/job-posting-previews` | 승인된 `FARM` | `200` |
| 공고 생성/즉시 심사 요청 | `POST` | `/api/farm/job-postings?submitForReview={boolean}` | 승인된 `FARM` | `201` |
| 내 공고 목록 | `GET` | `/api/farm/job-postings?displayStatus={status}` | `FARM` | `200` |
| 내 공고 상세 | `GET` | `/api/farm/job-postings/{postingId}` | `FARM` | `200` |
| 심사 이력 | `GET` | `/api/farm/job-postings/{postingId}/review-history` | `FARM` 소유자 | `200` |
| 초안 수정 | `PATCH` | `/api/farm/job-postings/{postingId}` | `FARM` 소유자 | `200` |
| 초안 삭제 | `DELETE` | `/api/farm/job-postings/{postingId}` | `FARM` 소유자 | `204` |
| 심사 요청 | `POST` | `/api/farm/job-postings/{postingId}/submit-review` | `FARM` 소유자 | `200` |
| 심사 철회 | `POST` | `/api/farm/job-postings/{postingId}/withdraw-review` | `FARM` 소유자 | `200` |
| 희망 지원자 조건 수정 | `PATCH` | `/api/farm/job-postings/{postingId}/applicant-preference` | `FARM` 소유자 | `200` |
| 공고 취소 | `POST` | `/api/farm/job-postings/{postingId}/cancel` | `FARM` 소유자 | `200` |
| 모집 중·마감 공고 목록 | `GET` | `/api/job-postings?recruitmentStatus={OPEN|CLOSED|ALL}` | 활성 계정 | `200` |
| 공개 공고 상세 | `GET` | `/api/job-postings/{postingId}?includeClosed={boolean}` | 활성 계정 | `200` |

AI는 현재 외부 LLM이 아닌 규칙 기반 미리보기다. 공개 목록은 keyword, region, crop, 날짜 범위, workType, recruitmentStatus를 지원한다. 공개 응답에는 화면 상태 `recruitmentStatus`, 실제 접수 가능 여부 `acceptingApplications`, 현재 사용자의 지원 요약 `myApplication`이 포함된다. `includeClosed=true` 상세는 이전에 승인·공개된 마감 공고만 허용한다.

공고 상태는 `DRAFT`, `PENDING_REVIEW`, `OPEN`, `CLOSED`, `CANCELLED`, `WORK_COMPLETED`이며 backend-1에는 공고 승인·반려·강제 마감 API가 없다.

---

# 기능 7. 공고 지원·농가 의견 — 6개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 공고 지원 | `POST` | `/api/job-postings/{postingId}/applications` | `URBAN_FARMER` | `201` |
| 내 지원 목록 | `GET` | `/api/urban-farmers/me/job-applications` | `URBAN_FARMER` | `200` |
| 내 지원 상세 | `GET` | `/api/urban-farmers/me/job-applications/{applicationId}` | `URBAN_FARMER` | `200` |
| 지원 취소 | `POST` | `/api/urban-farmers/me/job-applications/{applicationId}/withdraw` | `URBAN_FARMER` | `200` |
| 농가 지원자 목록 | `GET` | `/api/farm/job-postings/{postingId}/applications` | 승인된 `FARM` 소유자 | `200` |
| 농가 의견 등록·수정 | `PATCH` | `/api/farm/job-postings/{postingId}/applications/{applicationId}/opinion` | 승인된 `FARM` 소유자 | `200` |

도시농부는 같은 시간대 여러 공고에 지원할 수 있다. 지원 시 지역·요일·희망 시작일·희망 종료일·가능 작업 유형·이동 가능 여부·경험 횟수를 스냅샷으로 저장하며 재지원 시 최신 값으로 갱신한다. 농가 의견은 `NONE`, `PREFERRED`, `NOT_PREFERRED`이며 최종 수락·거절이 아니다. `MATCHED`, `NOT_MATCHED` 상태는 공통 모델에 남아 있지만 backend-1에는 최종 매칭 API가 없다.

---

# 기능 8. 근무·출결·작업 안내 — 6개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 도시농부 근무 목록 | `GET` | `/api/urban-farmers/me/work-assignments` | `URBAN_FARMER` | `200` |
| 도시농부 근무 상세 | `GET` | `/api/urban-farmers/me/work-assignments/{assignmentId}` | `URBAN_FARMER` | `200` |
| 작업 안내 | `GET` | `/api/urban-farmers/me/work-assignments/{assignmentId}/guide` | `URBAN_FARMER` | `200` |
| 농가 배정 목록 | `GET` | `/api/farm/work-assignments` | 승인된 `FARM` | `200` |
| 출결 최초 등록 | `PUT` | `/api/farm/work-assignments/{assignmentId}/attendance` | 승인된 `FARM` 소유자 | `200` |
| 근무 완료 | `POST` | `/api/farm/work-assignments/{assignmentId}/complete` | 승인된 `FARM` 소유자 | `200` |

도시농부 근무 목록은 `view=ALL|UPCOMING|PAST`를 받으며 기본값은 `ALL`이다. `UPCOMING`은 서울 시간 기준 종료 전인 `SCHEDULED` 근무를 가까운 순으로, `PAST`는 완료·결근·취소 또는 종료 지난 예정 근무를 최신 순으로 반환한다. 종료 시각과 현재 시각이 같으면 `PAST`다.

출결은 작업 시작 후 `PRESENT` 또는 `ABSENT`로 최초 한 번 등록한다. 근무 완료는 종료 시각 이후 `PRESENT` 상태에서 농가가 확정한다. 출결 정정 상태와 이력 엔티티는 공통 계약이지만 backend-1에는 정정 API가 없다.

---

# 기능 9. 홈 — 2개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| 도시농부 홈 | `GET` | `/api/urban-farmers/me/home` | `URBAN_FARMER` | `200` |
| 농가 홈 | `GET` | `/api/farm/me/home` | `FARM` | `200` |

도시농부 홈은 교육 상태, 최근 사업참여 ID·상태·제출시각, 희망 지역·요일, 예정 근무와 최근 공고를 반환한다. 농가 홈은 프로필, DB 상태별·화면 표시상태별 공고 개수, 최신 심사 결과가 포함된 최근 공고와 예정 근무를 반환한다.

---

# 기능 10. FAQ·AI 상담 — 3개

| 기능 | Method | URL | 권한 | 성공 |
|---|---|---|---|---|
| FAQ | `GET` | `/api/support/faqs` | 활성 계정 | `200` |
| 상담 메시지 | `POST` | `/api/ai/support/messages` | 활성 계정 | `200` |
| 내 상담 이력 | `GET` | `/api/ai/support/messages` | 활성 계정 | `200` |

AI 상담도 현재 규칙 기반이며 확정되지 않은 행정 답변에는 공식 확인 필요 여부를 표시한다.

---

## 4. 파일 정책

교육 이수증과 농가 소유 증빙의 공통 정책:

- 허용: PDF, JPG, JPEG, PNG
- 개별 파일: 최대 10 MiB
- 요청당: 1~5개, 총 30 MiB
- 서버 multipart 한도: 파일당 12MB, 요청당 35MB
- 확장자·MIME·파일 시그니처 교차 검증
- 반려 후 재제출 시 과거 회차와 파일 메타데이터 보존
- 회원 탈퇴 시 실제 파일 삭제 작업 등록 및 실패 재시도

## 5. backend-2 병합 경계

다음 상태와 필드는 현재 응답·DB 공통 계약에 존재하지만 backend-1에서 처리하는 HTTP API는 없다.

- `CENTER_ADMIN`
- 승인·반려와 심사자·심사 시각·반려 사유
- 공고 승인·반려·마감 이력
- 최종 매칭과 확정 담당자
- 출결 정정과 정정 이력

backend-1 단독 실행에서는 제출을 `PENDING_REVIEW`로 만들 수 있지만 이후 심사, 공고를 `OPEN`으로 만드는 처리, 최종 매칭, 출결 정정을 완료할 수 없다.

## 6. 제외 범위

- 네이버·카카오 소셜 로그인
- 알림함·푸시·문자·알림톡
- 정산 기록·정산 상태·CSV
- 결제·송금·계좌 관리
- SUPER_ADMIN
- 채팅

공고의 `wageAmount`, `wageUnit`은 작업 조건 표시이며 결제 기능이 아니다.

## 7. Postman 변수

```text
baseUrl
accessToken
urbanFarmerAccessToken
farmAccessToken
postingId
applicationId
assignmentId
submissionId
documentId
```
