# 도시농부 교육 인증 API 안내

이 문서의 과거 `/api/urban-farmer/education*` 계약은 폐기되었다. 현재 구현과 프론트 연동은 [notion/05_EDUCATION.md](notion/05_EDUCATION.md)를 정본으로 사용한다. 실시간 수강률과 교육기관 서명 연동의 상세 계약은 [EDUCATION_PROGRESS_API_SPEC.md](EDUCATION_PROGRESS_API_SPEC.md)를 따른다.

- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 로컬 Base URL: `http://localhost:8080`

현재 경로:

| 기능 | Method | URL |
|---|---|---|
| 교육 인증 현황 | GET | `/api/urban-farmers/me/education-certification` |
| 제출 이력 | GET | `/api/urban-farmers/me/education-certification/submissions` |
| 이수증 제출 | POST | `/api/urban-farmers/me/education-certification/submissions` |
| 제출 상세 | GET | `/api/urban-farmers/me/education-certification/submissions/{submissionId}` |
| 증빙 다운로드 | GET | `/api/urban-farmers/me/education-certification/submissions/{submissionId}/documents/{documentId}` |

교육 인증 현황의 과정 항목에는 `progressStatus`, `totalMinutes`,
`completedMinutes`, `remainingMinutes`, `progressPercentage`와 진도 시각 필드가 함께
반환된다. 값은 교육기관의 HMAC 진도 이벤트가 들어오는 즉시 갱신되며, 수강률 100%와
관리자 이수증 승인은 서로 다른 상태다.

모든 요청은 `ROLE_URBAN_FARMER` Bearer JWT가 필요하다. 제출은 JSON `request` 파트와 반복 `documents` 파일 파트로 구성한 `multipart/form-data`다.
