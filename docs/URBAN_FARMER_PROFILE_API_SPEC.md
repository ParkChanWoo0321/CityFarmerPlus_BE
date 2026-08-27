# 도시농부 프로필 API 안내

이 문서의 과거 `/api/urban-farmer/**` 계약은 폐기되었다. 현재 구현과 프론트 연동은 [notion/02_URBAN_FARMER_PROFILE.md](notion/02_URBAN_FARMER_PROFILE.md)를 정본으로 사용한다.

- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- 로컬 Base URL: `http://localhost:8080`

현재 경로:

| 기능 | Method | URL |
|---|---|---|
| 프로필 등록 | POST | `/api/urban-farmers/me/profile` |
| 내 프로필 조회 | GET | `/api/urban-farmers/me/profile` |
| 내 프로필 수정 | PATCH | `/api/urban-farmers/me/profile` |

모든 요청은 `ROLE_URBAN_FARMER` Bearer JWT가 필요하다.
