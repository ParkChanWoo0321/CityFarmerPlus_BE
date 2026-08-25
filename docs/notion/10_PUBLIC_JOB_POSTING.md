# 모집 공고 공개 조회 API

- 기준일: 2026-08-20
- 기준: 현재 `backend-1` 코드
- 로컬 Base URL: `http://localhost:8080`
- API 수: 2개

> `PublicJobPostingController`, `PublicJobPostingService`, 검색 Specification과 `PublicJobPostingResponse` 구현 기준이다. 이름은 Public이지만 현재 Security 설정상 익명 API가 아니며 Bearer JWT가 필요하다.

## 인증과 노출 범위

- 활성 계정의 JWT 필수
- 특정 역할 제한 없음
- 농가 프로필이 `APPROVED`이고 농가 계정이 활성 상태여야 함
- 담당자 승인 이력인 `approvedAt`이 있어야 함
- `DRAFT`, `PENDING_REVIEW`, `CANCELLED` 공고는 검색 조건과 관계없이 비공개
- 응답에는 농가명·시군·집결 장소가 포함되지만 농가 전체 주소·대표자명·전화번호는 포함되지 않음

## 엔드포인트

| Method | URL | 기능 | 성공 |
|---|---|---|---:|
| `GET` | `/api/job-postings` | 모집 중·마감 공고 목록과 검색 | 200 |
| `GET` | `/api/job-postings/{postingId}` | 모집 중 공고 또는 명시적으로 요청한 마감 공고 상세 | 200 |

## 공개 모집 상태

| `recruitmentStatus` | 의미 |
|---|---|
| `OPEN` | DB 상태가 `OPEN`이고 `Asia/Seoul` 기준 작업 시작 전 |
| `CLOSED` | DB 상태가 `CLOSED`/`WORK_COMPLETED`이거나, DB 상태는 `OPEN`이지만 작업 시작 시각이 지남 |
| `ALL` | 위 `OPEN`과 `CLOSED`를 함께 조회 |

`OPEN`은 쿼리 기본값이다. `ALL` 목록은 모집 중 공고를 먼저 배치하고, 각 그룹 안에서는 작업일·시작시간 오름차순, 승인시각 내림차순으로 정렬한다.

## `PublicJobPostingResponse`

```json
{
  "id": 101,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-09-20",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "충북 청주시 상당구 농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": "작업 장갑, 모자",
  "precautions": "물을 충분히 섭취해 주세요.",
  "farmMessage": "안전하게 함께 일해요.",
  "applicantPreference": "초보자도 가능합니다.",
  "title": "감자 수확 보조 작업자를 모집합니다",
  "description": "감자 수확을 함께할 분을 모집합니다.",
  "beginnerGuide": "농가의 설명을 듣고 천천히 작업해 주세요.",
  "approvedAt": "2026-08-13T02:00:00Z",
  "recruitmentStatus": "OPEN",
  "acceptingApplications": true,
  "myApplication": {
    "applicationId": 501,
    "status": "APPLIED"
  }
}
```

- `wageUnit`: `HOURLY`, `DAILY`. 임금은 안내 정보이며 결제·송금·정산 기능은 없다.
- 현재 사용자가 해당 공고에 지원한 이력이 없으면 `myApplication`은 `null`이다.
- 지원 취소·매칭 등 이력이 있으면 `myApplication.status`에 현재 지원 상태가 그대로 반환된다.
- `acceptingApplications`는 응답 시점에 실제 지원 접수가 가능한지를 나타낸다. 지원 자격인 교육 인증까지 미리 판정하지는 않으며 최종 검증은 지원 API에서 수행한다.

## 1. 공고 목록

### 요청

`GET /api/job-postings`

| Query | 필수 | 기본값 | 의미/검증 |
|---|---:|---:|---|
| `keyword` | X | - | 제목·작물·작업 종류·농가명·집결 장소·지역 enum/한국어명 통합 부분검색, 100자 이하 |
| `region` | X | - | 충북 시·군 enum 정확 일치 |
| `crop` | X | - | 앞뒤 공백과 대소문자를 무시한 작물명 정확 일치, 50자 이하 |
| `dateFrom` | X | - | `YYYY-MM-DD`, 작업일 하한 포함 |
| `dateTo` | X | - | `YYYY-MM-DD`, 작업일 상한 포함 |
| `workType` | X | - | 앞뒤 공백과 대소문자를 무시한 부분검색, 100자 이하 |
| `recruitmentStatus` | X | `OPEN` | `OPEN`, `CLOSED`, `ALL` |
| `page` | X | 0 | 0 이상 |
| `size` | X | 20 | 1~100 |

지역 enum:

`CHEONGJU`, `CHUNGJU`, `JECHEON`, `BOEUN`, `OKCHEON`, `YEONGDONG`, `JEUNGPYEONG`, `JINCHEON`, `GOESAN`, `EUMSEONG`, `DANYANG`

요청 예:

```http
GET /api/job-postings?keyword=청주&region=CHEONGJU&crop=감자&dateFrom=2026-09-01&dateTo=2026-09-30&workType=수확&recruitmentStatus=ALL&page=0&size=20
Authorization: Bearer {{accessToken}}
```

### 응답

```json
{
  "content": [
    {
      "id": 101,
      "farmProfileId": 15,
      "farmName": "푸른농가",
      "cityCounty": "CHEONGJU",
      "crop": "감자",
      "workType": "수확 보조",
      "workDate": "2026-09-20",
      "startTime": "09:00:00",
      "endTime": "16:00:00",
      "capacity": 3,
      "meetingPlace": "충북 청주시 상당구 농장 입구",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "supplies": "작업 장갑, 모자",
      "precautions": "물을 충분히 섭취해 주세요.",
      "farmMessage": "안전하게 함께 일해요.",
      "applicantPreference": "초보자도 가능합니다.",
      "title": "감자 수확 보조 작업자 모집",
      "description": "모집 설명",
      "beginnerGuide": "작업 안내",
      "approvedAt": "2026-08-13T02:00:00Z",
      "recruitmentStatus": "OPEN",
      "acceptingApplications": true,
      "myApplication": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

`dateFrom > dateTo`를 별도로 거부하지 않는다. 두 조건이 동시에 적용되므로 일반적으로 빈 목록이 반환된다.

### 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 문자열 길이, `page`, `size` 범위 오류 |
| 400 | `INVALID_REQUEST_PARAMETER` | 지역·상태 enum, 날짜·숫자 형식 오류 |
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | JWT 누락·무효 또는 현재 계정 불일치 |

## 2. 공고 상세

### 요청

```http
GET /api/job-postings/{postingId}?includeClosed=false
```

| Query | 필수 | 기본값 | 의미 |
|---|---:|---:|---|
| `includeClosed` | X | `false` | `true`이면 이전에 승인·공개된 마감 공고도 조회 허용 |

- `includeClosed=false`: 현재 `OPEN`이고 작업 시작 전인 공고만 조회
- `includeClosed=true`: 위 공고와 함께 `CLOSED`, `WORK_COMPLETED`, 작업 시작 시각이 지난 `OPEN` 조회 허용
- 승인 이력이 없는 공고, 취소 공고, 미승인·비활성 농가 공고는 `true`여도 조회 불가

성공 응답은 목록 요소와 같은 `PublicJobPostingResponse` 단건이다. 마감 공고는 다음과 같이 표시된다.

```json
{
  "id": 99,
  "farmProfileId": 15,
  "farmName": "푸른농가",
  "cityCounty": "CHEONGJU",
  "crop": "감자",
  "workType": "수확 보조",
  "workDate": "2026-08-10",
  "startTime": "09:00:00",
  "endTime": "16:00:00",
  "capacity": 3,
  "meetingPlace": "농장 입구",
  "wageAmount": 100000,
  "wageUnit": "DAILY",
  "supplies": null,
  "precautions": null,
  "farmMessage": null,
  "applicantPreference": null,
  "title": "감자 수확 보조",
  "description": "모집 설명",
  "beginnerGuide": null,
  "approvedAt": "2026-08-01T02:00:00Z",
  "recruitmentStatus": "CLOSED",
  "acceptingApplications": false,
  "myApplication": {
    "applicationId": 490,
    "status": "WORK_COMPLETED"
  }
}
```

### 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 400 | `INVALID_REQUEST_PARAMETER` | ID나 boolean 형식 오류 |
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | 인증 실패 |
| 404 | `JOB_POSTING_NOT_OPEN` | 없거나 요청 범위에서 공개할 수 없는 공고 |

없는 ID와 비공개 공고를 같은 404로 처리해 비공개 공고의 존재 여부를 노출하지 않는다.

## Postman 예시

```bash
# 모집 중 기본 목록
curl "{{baseUrl}}/api/job-postings?crop=감자&region=CHEONGJU&page=0&size=20" \
  -H "Authorization: Bearer {{accessToken}}"

# 모집 중과 마감 목록
curl "{{baseUrl}}/api/job-postings?recruitmentStatus=ALL&page=0&size=20" \
  -H "Authorization: Bearer {{accessToken}}"

# 마감 공고 상세
curl "{{baseUrl}}/api/job-postings/99?includeClosed=true" \
  -H "Authorization: Bearer {{accessToken}}"
```

## 현재 제한과 backend-2 경계

- 이 API는 조회만 제공한다. 공고 승인·반려와 명시적 마감은 backend-2 중개센터 기능이다.
- 지원자 매칭과 모집 인원 충족 마감도 backend-2에서 연결한다.
- `capacity`는 모집 정원이지만 현재 지원자 수·매칭 인원 수는 공개 응답에 포함되지 않는다.
- 익명 공개 페이지가 필요하다면 별도의 Security 정책 변경이 필요하다.
