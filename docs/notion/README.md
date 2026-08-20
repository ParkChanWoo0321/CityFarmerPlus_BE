# CityFarmerPlus 기능별 API 명세서

- 기준일: 2026-08-20
- 기준: 현재 `backend-1` 작업 폴더의 구현 코드
- Controller: 21개 (예외 처리 Advice 제외)
- HTTP API: 66개 (`OPTIONS` preflight 제외)

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
| 5 | 교육 과정·교육 인증 | 6 | [05_EDUCATION.md](05_EDUCATION.md) |
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
|  | **합계** | **66** |  |

## 노션에 복사하는 방법

1. 노션에서 기능별 새 페이지를 만든다.
2. 해당 Markdown 파일의 전체 내용을 복사한다.
3. 노션 페이지 본문에 붙여넣는다.
4. 프론트엔드 팀은 각 문서의 `현재 제한`과 `상태 전이`를 함께 확인한다.

## 문서 범위

- 현재 코드에 존재하는 HTTP API만 기록했다.
- 통합 신청 폼 3개는 디자인의 단일 화면을 위한 트랜잭션 API다. 기존 프로필 3개, 희망 근무 조건 3개, 사업참여 신청 7개 API도 삭제되지 않고 독립 API로 유지된다.
- backend-1에서 제거한 중개센터 계정 발급 및 `/api/admin/**` API는 기록하지 않았다.
- `CENTER_ADMIN`과 승인·반려 상태는 backend-2 병합을 위한 공통 데이터 계약으로만 설명한다.
- 소셜 로그인, 알림, 정산·CSV, 결제 기능은 확정 제외 범위다.
- 이 문서는 2026-08-20 기준 `backend-1` 커밋의 API 계약을 반영한다. 이후 API 변경 시 관련 문서를 함께 갱신한다.
