# CityFarmerPlus API 명세 인덱스

- 문서 버전: 3.1
- 갱신일: 2026-08-20
- 기준: 현재 backend-1의 Controller·DTO·Security·Service 코드
- 현재 HTTP 작업 수: 66개

## 1. 기준 문서

| 문서 | 용도 | 현황 |
|---|---|---|
| [FULL_API_SPEC.md](FULL_API_SPEC.md) | 노션·프론트·Postman용 전체 통합 명세 | 현재 66개 HTTP 작업 기준 |
| [AUTH_API_SPEC.md](AUTH_API_SPEC.md) | 회원가입, JWT, 내 정보, 탈퇴 | 현재 코드 반영 |
| [FARM_PROFILE_API_SPEC.md](FARM_PROFILE_API_SPEC.md) | 농가 프로필 생성·조회·수정 | 현재 코드 반영 |
| [FARM_OWNERSHIP_SUBMISSION_API_SPEC.md](FARM_OWNERSHIP_SUBMISSION_API_SPEC.md) | 농가 소유 증빙 제출·이력·파일 조회 | 현재 코드 반영 |
| [NOTION_API_SPEC.md](NOTION_API_SPEC.md) | 노션 복사용 요약본 | 현재 66개 HTTP 작업 기준 |
| [notion/04A_PARTICIPATION_FORM.md](notion/04A_PARTICIPATION_FORM.md) | 디자인 한 화면용 통합 신청 폼 3 API | 현재 코드 반영 |
| [notion/10_PUBLIC_JOB_POSTING.md](notion/10_PUBLIC_JOB_POSTING.md) | 모집 중·마감 공고 검색과 내 지원 요약 | 현재 코드 반영 |
| [notion/11_JOB_APPLICATION.md](notion/11_JOB_APPLICATION.md) | 공고 지원과 지원 시점 조건 스냅샷 | 현재 코드 반영 |

새 프론트 연동과 Postman 컬렉션은 `FULL_API_SPEC.md`를 기준으로 한다.

## 2. 현재 구현 현황

| 접근 구분 | HTTP 작업 수 |
|---|---:|
| 인증 없이 호출 가능 | 4 |
| 로그인 사용자 공통 | 9 |
| 도시농부 전용 | 29 |
| 농가 전용 | 24 |
| 합계 | **66** |

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
| 합계 | **66** | Controller 메서드 매핑 기준 |

## 3. 인증 기준

인증 없이 호출 가능한 API:

- `POST /api/auth/signup`
- `GET /api/auth/check-id`
- `POST /api/auth/login`
- `GET /api/education/courses`
- 모든 경로의 `OPTIONS` preflight

그 외 API에는 다음 헤더가 필요하다.

```http
Authorization: Bearer {{accessToken}}
```

이름이 Public인 공고 조회 컨트롤러와 FAQ도 현재 전역 보안 설정상 Bearer JWT가 필요하다.

## 4. backend-1과 backend-2 경계

backend-1에는 중개센터 전용 업무 API와 담당자 내부 계정 발급 API가 없다.

다만 다음은 backend-2 병합 시 같은 DB와 상태 모델을 사용하기 위한 공통 계약으로 유지한다.

- `CENTER_ADMIN` 사용자 유형
- 농가·교육·사업참여·공고의 승인·반려 상태
- 심사자 ID·이름, 심사 시각, 반려 사유
- 공고 심사 이력
- 지원의 `MATCHED`, `NOT_MATCHED` 상태와 확정 담당자
- 출결 정정 상태와 정정 이력 모델

따라서 backend-1 단독 실행에서는 다음 작업을 HTTP로 완료할 수 없다.

- 담당자 계정 생성
- 사업참여·교육·농가 소유 증빙 승인 및 반려
- 공고 승인·반려·마감
- 최종 매칭
- 출결 정정
- 담당자 대리 접수와 대시보드

## 5. 핵심 구현 흐름

도시농부:

```text
회원가입·로그인
→ 개별 API로 프로필·희망 근무 조건·사업참여 신청을 작성하거나 통합 폼으로 한 번에 저장·제출
→ 필수 교육별 이수증 제출
→ backend-2 심사 결과가 모두 APPROVED
→ OPEN 공고 조회·지원
→ 농가 선호 의견
→ backend-2 최종 매칭
→ 근무 일정·작업 안내 조회
```

농가:

```text
회원가입·로그인
→ 농가 프로필
→ 소유 증빙 제출
→ backend-2 심사 결과가 APPROVED
→ 공고 초안·심사 요청
→ backend-2 승인으로 OPEN
→ 지원자 의견
→ backend-2 최종 매칭
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
