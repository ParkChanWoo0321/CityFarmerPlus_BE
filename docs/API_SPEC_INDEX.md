# CityFarmerPlus API 명세 인덱스

- 문서 버전: 4.0
- 갱신일: 2026-08-27
- 기준: 현재 `main` 통합 코드의 Controller·DTO·Validation·Security·Service·Exception
- 현재 HTTP 작업 수: 111개 (`OPTIONS` preflight 제외, 메서드 매핑 기준)

## 1. 기준 문서

| 문서 | 용도 | 현황 |
|---|---|---|
| [FULL_API_SPEC.md](FULL_API_SPEC.md) | 전체 111개 API 통합 계약 | 사용자 67 + 관리자·내부 발급 42 + health 2의 method/path 정본 |
| [AUTH_API_SPEC.md](AUTH_API_SPEC.md) | 회원가입, JWT, 내 정보, 탈퇴 | 현재 코드 반영 |
| [FARM_PROFILE_API_SPEC.md](FARM_PROFILE_API_SPEC.md) | 농가 프로필 생성·조회·수정 | 현재 코드 반영 |
| [FARM_OWNERSHIP_SUBMISSION_API_SPEC.md](FARM_OWNERSHIP_SUBMISSION_API_SPEC.md) | 농가 소유 증빙 제출·이력·파일 조회 | 현재 코드 반영 |
| [MARKET_PRICE_API_SPEC.md](MARKET_PRICE_API_SPEC.md) | KAMIS 최근 조사 가격 조회 | 현재 코드 반영 |
| [NOTION_API_SPEC.md](NOTION_API_SPEC.md) | 노션 복사용 사용자 API 요약본 | 사용자 화면용 축약본 |
| [notion/04A_PARTICIPATION_FORM.md](notion/04A_PARTICIPATION_FORM.md) | 디자인 한 화면용 통합 신청 폼 3 API | 현재 코드 반영 |
| [notion/10_PUBLIC_JOB_POSTING.md](notion/10_PUBLIC_JOB_POSTING.md) | 모집 중·마감 공고 검색과 내 지원 요약 | 현재 코드 반영 |
| [notion/11_JOB_APPLICATION.md](notion/11_JOB_APPLICATION.md) | 공고 지원과 지원 시점 조건 스냅샷 | 현재 코드 반영 |
| [ADMIN_DASHBOARD_API_SPEC.md](ADMIN_DASHBOARD_API_SPEC.md) | 관리자 대시보드 | 현재 코드 반영 |
| [ADMIN_PARTICIPATION_APPLICATION_API_SPEC.md](ADMIN_PARTICIPATION_APPLICATION_API_SPEC.md) | 사업참여 심사 | 현재 코드 반영 |
| [ADMIN_EDUCATION_COURSE_API_SPEC.md](ADMIN_EDUCATION_COURSE_API_SPEC.md) | 교육 과정 관리 | 현재 코드 반영 |
| [ADMIN_EDUCATION_SUBMISSION_API_SPEC.md](ADMIN_EDUCATION_SUBMISSION_API_SPEC.md) | 교육 제출 심사·증빙 다운로드 | 현재 코드 반영 |
| [ADMIN_FARM_OWNERSHIP_API_SPEC.md](ADMIN_FARM_OWNERSHIP_API_SPEC.md) | 농가 소유 증빙 심사·다운로드 | 현재 코드 반영 |
| [ADMIN_JOB_POSTING_API_SPEC.md](ADMIN_JOB_POSTING_API_SPEC.md) | 공고 심사·수정·매칭 | 현재 코드 반영 |
| [ADMIN_WORK_ASSIGNMENT_API_SPEC.md](ADMIN_WORK_ASSIGNMENT_API_SPEC.md) | 근무 조회·출결 정정 | 현재 코드 반영 |
| [ADMIN_PROXY_REGISTRATION_API_SPEC.md](ADMIN_PROXY_REGISTRATION_API_SPEC.md) | 관리자 대리 접수 | 현재 코드 반영 |

새 프론트 연동과 Postman 컬렉션은 사용자·관리자 모두 `FULL_API_SPEC.md`를 1차 정본으로 사용한다. 위 기능별 문서는 요청·응답 예시, 상태 전이와 운영 설명을 더 자세히 보는 보조 명세다. 관리자 42개는 `/api/admin/**` 41개와 운영 기본 비활성인 `POST /api/internal/center-admins` 1개이며, 내부 발급 계약은 통합본 13장과 `AUTH_API_SPEC.md`에 함께 기록한다.

## 2. 현재 구현 현황

| 코드 영역 | HTTP 작업 수 |
|---|---:|
| 사용자·공개 API | 67 |
| `/api/admin/**` CENTER_ADMIN API | 41 |
| 내부 담당자 발급 API | 1 |
| health | 2 |
| 합계 | **111** |

기능별 집계:

| 영역 | HTTP 작업 수 | 주요 범위 |
|---|---:|---|
| 인증·회원 | 7 | 회원가입, 로그인, 내 정보, 수정, 탈퇴, 로그아웃 |
| 도시농부 프로필·희망 근무 조건·사업참여 | 16 | 프로필 3, 근무 조건 3, 사업참여 7, 통합 신청 폼 3 |
| 교육 과정·교육 인증 | 6 | 공개 과정 1, 제출·진행률 5 |
| 농가 프로필·소유 증빙 | 7 | 프로필 3, 제출·이력·다운로드 4 |
| 모집 공고·AI 초안 | 13 | AI 1, 농가 공고 10, 공고 조회 2 |
| 공고 지원·농가 의견 | 6 | 지원 4, 농가 후보 의견 2 |
| 근무·출결·작업 안내 | 6 | 도시농부 3, 농가 3 |
| 홈 | 2 | 도시농부 홈, 농가 홈 |
| FAQ·AI 상담 | 3 | FAQ 1, 상담 2 |
| 농산물 가격 | 1 | KAMIS 최근 조사 가격 조회 |
| health | 2 | readiness(`/health`), liveness(`/health/live`) |
| 관리자 | 42 | 계정 발급, 대시보드, 심사, 과정, 공고·매칭, 출결 정정, 대리 접수 |
| 합계 | **111** | Controller 메서드 매핑 기준 |

## 3. 인증 기준

인증 없이 호출 가능한 API:

- `POST /api/auth/signup`
- `GET /api/auth/check-id`
- `POST /api/auth/login`
- `GET /api/education/courses`
- `GET /api/job-postings`
- `GET /api/job-postings/{postingId}`
- `GET /api/support/faqs`
- `GET /api/market-prices/latest`
- `GET /health`
- `GET /health/live`
- `POST /api/internal/center-admins` (provisioning key가 설정된 동안만)
- 모든 경로의 `OPTIONS` preflight

그 외 API에는 다음 헤더가 필요하다.

```http
Authorization: Bearer {{accessToken}}
```

공개 공고 조회에 유효한 JWT를 선택적으로 보내면 현재 사용자의 지원 이력이 응답에 추가된다. 공개 API라도 잘못된 Bearer JWT를 보내면 401을 반환한다.

## 4. 사용자·중개센터 통합 상태

현재 `main` 통합 코드에는 사용자 기능과 중개센터 기능이 함께 들어 있다. 다음 계약은 동일한 DB와 트랜잭션 경계로 연결된다.

- `CENTER_ADMIN` 사용자 유형
- 농가·교육·사업참여·공고의 승인·반려 상태
- 심사자 ID·이름, 심사 시각, 반려 사유
- 공고 심사 이력
- 지원의 `MATCHED`, `NOT_MATCHED` 상태와 확정 담당자
- 출결 정정 상태와 정정 이력 모델

`/api/admin/**`는 `CENTER_ADMIN` JWT가 필요하다. 예외적으로 최초 담당자 발급 API는 별도 provisioning key가 설정된 동안만 열리며, 운영 기본값은 비활성화다. 관리자 기능은 사업참여·교육·농가 심사, 증빙 다운로드, 공고 심사·매칭, 근무 정정, 대리 접수와 대시보드를 포함한다.

## 5. 핵심 구현 흐름

도시농부:

```text
회원가입·로그인
→ 개별 API로 프로필·희망 근무 조건·사업참여 신청을 작성하거나 통합 폼으로 한 번에 저장·제출
→ 필수 교육별 이수증 제출
→ CENTER_ADMIN 심사 결과가 모두 APPROVED
→ OPEN 공고 조회·지원
→ 농가 선호 의견
→ CENTER_ADMIN 최종 매칭
→ 근무 일정·작업 안내 조회
```

농가:

```text
회원가입·로그인
→ 농가 프로필
→ 소유 증빙 제출
→ CENTER_ADMIN 심사 결과가 APPROVED
→ 공고 초안·심사 요청
→ CENTER_ADMIN 승인으로 OPEN
→ 지원자 의견
→ CENTER_ADMIN 최종 매칭
→ 출결·근무 완료
```

## 6. 확정 제외 범위

- 네이버·카카오 등 소셜 로그인
- 알림함, 푸시, 문자, 알림톡
- 정산 기록·상태·CSV
- 결제·송금·계좌 관리
- SUPER_ADMIN
- 농가·도시농부 채팅

공고의 임금 필드는 작업 조건 표시용이며 지급이나 결제를 처리하지 않는다.
