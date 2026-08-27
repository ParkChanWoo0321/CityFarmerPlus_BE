# CityFarmerPlus 기능별 API 명세서

- 기준일: 2026-08-28
- 기준: 현재 `main` 통합 구현 코드
- 전체 HTTP API: 112개 (`OPTIONS` preflight 제외)
- 이 폴더의 사용자·공개·연동 API 문서: 68개. 관리자 42개와 health 2개는 아래 별도 문서 참조

각 문서는 다른 문서 없이도 노션 페이지 하나에 그대로 복사할 수 있도록 작성했다.

## 문서 목록

| 순서 | 기능 | API 수 | 문서 |
|---:|---|---:|---|
| 0 | 공통 인증·오류·페이지·CORS 계약 | - | [00_COMMON_CONTRACT.md](00_COMMON_CONTRACT.md) |
| 1 | 회원·인증 | 7 | [01_AUTH.md](01_AUTH.md) |
| 2 | 도시농부 프로필 | 3 | [02_URBAN_FARMER_PROFILE.md](02_URBAN_FARMER_PROFILE.md) |
| 3 | 희망 근무 조건 | 3 | [03_WORK_PREFERENCE.md](03_WORK_PREFERENCE.md) |
| 4 | 사업참여 신청 | 7 | [04_PARTICIPATION_APPLICATION.md](04_PARTICIPATION_APPLICATION.md) |
| 4A | 통합 사업참여 신청 폼 | 3 | [04A_PARTICIPATION_FORM.md](04A_PARTICIPATION_FORM.md) |
| 5 | 교육 과정·교육 인증·진도 연동 | 7 | [05_EDUCATION.md](05_EDUCATION.md) |
| 6 | 농가 프로필 | 3 | [06_FARM_PROFILE.md](06_FARM_PROFILE.md) |
| 7 | 농가 소유 증빙 | 4 | [07_FARM_OWNERSHIP.md](07_FARM_OWNERSHIP.md) |
| 8 | AI 공고 미리보기 | 1 | [08_AI_JOB_POSTING.md](08_AI_JOB_POSTING.md) |
| 9 | 농가 공고 관리 | 10 | [09_FARM_JOB_POSTING.md](09_FARM_JOB_POSTING.md) |
| 10 | 공고 검색·상세 조회 | 2 | [10_PUBLIC_JOB_POSTING.md](10_PUBLIC_JOB_POSTING.md) |
| 11 | 공고 지원·지원자 의견 | 6 | [11_JOB_APPLICATION.md](11_JOB_APPLICATION.md) |
| 12 | 근무 일정·출결·작업 안내 | 6 | [12_WORK_ASSIGNMENT.md](12_WORK_ASSIGNMENT.md) |
| 13 | 역할별 홈 | 2 | [13_DASHBOARD.md](13_DASHBOARD.md) |
| 14 | 자주 묻는 질문 | 1 | [14_FAQ.md](14_FAQ.md) |
| 15 | AI 행정 상담 | 2 | [15_AI_SUPPORT.md](15_AI_SUPPORT.md) |
| 16 | KAMIS 최근 조사 가격 | 1 | [16_MARKET_PRICE.md](16_MARKET_PRICE.md) |
|  | **합계** | **68** |  |

관리자 API는 저장소 `docs` 루트의 다음 문서를 노션에 각각 복사한다.

- `ADMIN_DASHBOARD_API_SPEC.md`
- `ADMIN_PARTICIPATION_APPLICATION_API_SPEC.md`
- `ADMIN_EDUCATION_COURSE_API_SPEC.md`
- `ADMIN_EDUCATION_SUBMISSION_API_SPEC.md`
- `ADMIN_FARM_OWNERSHIP_API_SPEC.md`
- `ADMIN_JOB_POSTING_API_SPEC.md`
- `ADMIN_WORK_ASSIGNMENT_API_SPEC.md`
- `ADMIN_PROXY_REGISTRATION_API_SPEC.md`

## 노션에 복사하는 방법

1. 노션에서 기능별 새 페이지를 만든다.
2. 해당 Markdown 파일의 전체 내용을 복사한다.
3. 노션 페이지 본문에 붙여넣는다.
4. 프론트엔드 팀은 각 문서의 `현재 제한`과 `상태 전이`를 함께 확인한다.

## 문서 범위

- 현재 코드에 존재하는 HTTP API만 기록했다.
- 통합 신청 폼 3개는 디자인의 단일 화면을 위한 트랜잭션 API다. 기존 프로필 3개, 희망 근무 조건 3개, 사업참여 신청 7개 API도 삭제되지 않고 독립 API로 유지된다.
- 이 폴더의 00~16 문서는 사용자 API만 다루며, 구현된 `/api/admin/**` 42개는 위 8개 관리자 전용 명세가 정본이다.
- `CENTER_ADMIN`과 승인·반려 상태는 통합 코드에서 실제 관리자 API로 처리한다.
- 소셜 로그인, 알림, 정산·CSV, 결제 기능은 확정 제외 범위다.
- 이 인덱스는 2026-08-28 통합 코드의 문서 분할 구조를 반영한다. 이후 API 변경 시 관련 사용자 또는 관리자 명세를 함께 갱신한다.
