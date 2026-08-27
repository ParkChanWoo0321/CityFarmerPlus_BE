# AI 모집 공고 문구 미리보기 API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 1개

> 이 문서는 현재 `AiJobPostingController`, 요청/응답 DTO, `AiJobPostingService`, `RuleBasedJobPostingTextGenerator` 구현을 기준으로 작성했다.

## 기능 범위

- 농가가 입력한 작물, 작업, 일정을 기준으로 공고 문구를 **미리보기** 한다.
- 현재 외부 AI API를 호출하지 않고 `RULE_BASED_V1` 규칙 기반 생성기를 사용한다.
- 응답은 편집용 초안이며, 이 API는 공고를 DB에 저장하거나 게시하지 않는다.
- “공고문 다시 생성”은 이 API를 재호출한다.
- “발송 신청”은 결과를 검토·수정한 뒤 `POST /api/farm/job-postings?submitForReview=true`를 호출한다. 한 트랜잭션에서 공고 생성과 심사 요청이 처리된다.
- 미리보기와 실제 저장 모두 `Asia/Seoul` 기준 작업 시작 시각이 현재 이후인지 동일하게 검증한다.

## 인증과 권한

- JWT 필수
- `Authorization: Bearer {accessToken}`
- `FARM` 역할만 호출 가능
- 활성 계정이면서 농가 프로필이 `APPROVED`여야 한다.

## 엔드포인트

### 공고 문구 미리보기

| 항목 | 내용 |
|---|---|
| Method | `POST` |
| URL | `/api/ai/job-posting-previews` |
| 성공 코드 | `200 OK` |
| 권한 | `FARM` + 승인된 농가 |
| Content-Type | `application/json` |

#### 요청 필드

| 필드 | 형식 | 필수 | 검증 |
|---|---|---:|---|
| `crop` | string | O | 공백 불가, 최대 50자 |
| `workType` | string | O | 공백 불가, 최대 100자 |
| `workDate` | `YYYY-MM-DD` | O | 오늘 또는 미래 |
| `startTime` | `HH:mm[:ss]` | O | `endTime`보다 빨라야 함 |
| `endTime` | `HH:mm[:ss]` | O | `startTime`보다 느려야 함 |
| `capacity` | integer | O | 1~1000 |
| `meetingPlace` | string | O | 공백 불가, 최대 255자 |
| `supplies` | string/null | X | 최대 1000자 |
| `precautions` | string/null | X | 최대 2000자 |

#### 요청 JSON

```json
{
  "crop": "감자",
  "workType": "수확",
  "workDate": "2026-08-20",
  "startTime": "09:00",
  "endTime": "16:00",
  "capacity": 3,
  "meetingPlace": "충북 청주시 상당구 농장 입구",
  "supplies": "개인 물병",
  "precautions": "작업 중 수분을 충분히 섭취해 주세요."
}
```

#### 응답 JSON

```json
{
  "title": "감자 수확 작업자를 모집합니다",
  "description": "2026-08-20에 수확 작업을 함께해 주실 도시농부 3명을 모집합니다. 작업은 09:00부터 16:00까지 진행되며, 처음 참여하시는 분도 안내를 받으며 작업할 수 있습니다.",
  "supplies": "개인 물병\n작업 장갑, 모자, 편한 작업복, 미끄럼 방지 작업화",
  "precautions": "작업 중 수분을 충분히 섭취해 주세요.\n작업 전 스트레칭을 하고, 충분히 물을 마시며 무리하지 마세요. 작업 도구를 사용할 때 주변 사람과 안전거리를 유지해 주세요.",
  "beginnerGuide": "집결 장소에서 농가의 작업 설명을 먼저 듣고, 무리하지 않는 범위에서 천천히 작업해 주세요. 모르는 부분은 바로 농가에 확인해 주세요.",
  "generator": "RULE_BASED_V1"
}
```

> 생성 문구는 규칙과 입력값에 따라 달라질 수 있다. `supplies`, `precautions`를 입력하면 입력 내용 뒤에 규칙 기반 제안이 줄바꿈으로 추가된다.

#### 대표 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 필수값 누락, 길이/범위/날짜 검증 실패 |
| 400 | `INVALID_REQUEST` | JSON 형식, 날짜나 시간 형식 오류 |
| 400 | `INVALID_JOB_POSTING_DETAILS` | 종료 시간이 시작 시간보다 늦지 않음 |
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | JWT 누락·무효 또는 현재 계정/역할 불일치 |
| 403 | `ACCESS_DENIED` | `FARM` 역할이 아님 |
| 403 | `FARM_APPROVAL_REQUIRED` | 농가 프로필이 승인 상태가 아님 |
| 404 | `USER_NOT_FOUND` | 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 |
| 404 | `FARM_PROFILE_NOT_FOUND` | 계정에 연결된 농가 프로필을 찾을 수 없음 |

오류 응답 형식:

```json
{
  "code": "INVALID_JOB_POSTING_DETAILS",
  "message": "종료 시간은 시작 시간보다 늦어야 합니다."
}
```

#### Postman 예시

1. Method를 `POST`로 선택한다.
2. URL에 `{{baseUrl}}/api/ai/job-posting-previews`를 입력한다.
3. Authorization 탭에서 `Bearer Token`을 선택하고 `{{farmAccessToken}}`을 입력한다.
4. Body → raw → JSON에 위 요청 JSON을 입력한다.

```bash
curl -X POST "{{baseUrl}}/api/ai/job-posting-previews" \
  -H "Authorization: Bearer {{farmAccessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"crop":"감자","workType":"수확","workDate":"2026-08-20","startTime":"09:00","endTime":"16:00","capacity":3,"meetingPlace":"충북 청주시 상당구 농장 입구","supplies":null,"precautions":null}'
```

## 현재 제한

- 이름에 AI가 포함되지만 현재는 LLM이 아닌 규칙 기반 생성기다.
- 생성 결과는 자동 저장·게시·승인 요청되지 않는다.
- 공고 승인, 반려, 마감과 지원자 매칭은 현재 통합된 `CENTER_ADMIN` 중개센터 API가 처리하며 이 미리보기 API에는 포함되지 않는다.
- 요청 내용에 민감한 개인정보를 입력하지 말아야 한다.
