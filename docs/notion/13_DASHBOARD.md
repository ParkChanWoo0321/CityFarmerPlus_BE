# 홈 대시보드 API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 2개

## 개요

- 도시농부 홈: 교육 상태, 최신 사업 참여 신청, 근무 희망 조건 등록 여부, 예정 근무, 최근 공고
- 농가 홈: 농가 프로필, 공고 상태별 수, 최근 공고, 예정 근무
- 대시보드는 기존 기능의 요약 조회이며 상태를 변경하지 않는다.

## 인증

| URL | 권한 | 추가 조건 |
|---|---|---|
| `/api/urban-farmers/me/home` | `URBAN_FARMER` | 활성 계정 |
| `/api/farm/me/home` | `FARM` | 활성 계정 + 농가 프로필 존재. 프로필 승인은 필수가 아님 |

JWT: `Authorization: Bearer {accessToken}`

## 1. 도시농부 홈

### 요청

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| URL | `/api/urban-farmers/me/home` |
| Body | 없음 |
| 성공 | `200 OK` |

### 응답 필드

| 필드 | 의미 |
|---|---|
| `educationStatus` | `NOT_SUBMITTED`, `PENDING_REVIEW`, `PARTIALLY_APPROVED`, `APPROVED`, `REJECTED` |
| `latestParticipationApplicationId` | 서울 기준 현재 연도 사업 참여 신청 ID. 없으면 `null` |
| `latestParticipationStatus` | 현재 연도 사업 참여 신청 상태. 없으면 `null` |
| `participationProgramYear` | 현재 연도 신청의 사업 연도. 없으면 `null` |
| `participationSubmittedAt` | 현재 연도 신청 제출 시각. 없으면 `null` |
| `workPreferenceRegistered` | 근무 희망 조건 등록 여부 |
| `preferredRegions` | 희망 근무 지역 목록 |
| `availableDays` | 희망 근무 요일 목록 |
| `upcomingWork` | 미래 작업일 또는 오늘 종료 시각 전인 `SCHEDULED` 근무 최대 5개, 날짜/시간 오름차순 |
| `recentOpenPostings` | 현재 지원 가능한 공고 최대 5개 |

`latestParticipationStatus`: `DRAFT`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`

### 응답 JSON

```json
{
  "educationStatus": "APPROVED",
  "latestParticipationApplicationId": 401,
  "latestParticipationStatus": "SUBMITTED",
  "participationProgramYear": 2026,
  "participationSubmittedAt": "2026-06-25T00:00:00Z",
  "workPreferenceRegistered": true,
  "preferredRegions": ["CHUNGJU"],
  "availableDays": ["MONDAY", "WEDNESDAY"],
  "upcomingWork": [
    {
      "id": 701,
      "jobPostingId": 101,
      "jobApplicationId": 501,
      "urbanFarmerUserId": 301,
      "urbanFarmerName": "홍길동",
      "confirmedByUserId": 9001,
      "confirmedByName": "중개센터 담당자",
      "confirmedByContactNumber": "01098765432",
      "farmName": "푸른농가",
      "farmAddress": "충북 청주시 상당구 ...",
      "farmContactNumber": "01012345678",
      "crop": "감자",
      "workType": "수확 보조",
      "workDate": "2026-08-20",
      "startTime": "09:00:00",
      "endTime": "16:00:00",
      "recruitmentCapacity": 5,
      "meetingPlace": "농장 입구",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "supplies": "작업 장갑",
      "precautions": "안전수칙 준수",
      "status": "SCHEDULED",
      "attendanceStatus": "NOT_RECORDED",
      "completedAt": null
    }
  ],
  "recentOpenPostings": [
    {
      "id": 102,
      "farmProfileId": 16,
      "farmName": "행복농가",
      "cityCounty": "CHUNGJU",
      "crop": "옥수수",
      "workType": "수확",
      "workDate": "2026-08-22",
      "startTime": "08:00:00",
      "endTime": "14:00:00",
      "capacity": 4,
      "meetingPlace": "농장 입구",
      "wageAmount": 12000,
      "wageUnit": "HOURLY",
      "supplies": "모자",
      "precautions": "수분 섭취",
      "farmMessage": null,
      "applicantPreference": "초보자 가능",
      "title": "옥수수 수확 작업자 모집",
      "description": "옥수수 수확 작업입니다.",
      "beginnerGuide": null,
      "approvedAt": "2026-08-11T09:00:00Z",
      "recruitmentStatus": "OPEN",
      "acceptingApplications": true,
      "myApplication": {
        "applicationId": 502,
        "status": "APPLIED"
      }
    }
  ]
}
```

> 사업참여 신청 요약은 서울 기준 현재 연도만 조회한다. `upcomingWork`는 시작 시각이 지났더라도 종료 시각 전이면 포함되며, 오늘 종료 시각이 지난 일정은 `SCHEDULED` 상태여도 제외된다.

### 대표 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | JWT 누락·무효, 현재 계정 불일치 |
| 403 | `ACCESS_DENIED` | 도시농부가 아닌 JWT 역할 |
| 403 | `URBAN_FARMER_REQUIRED` | 보안 필터 통과 직후 계정 역할이 바뀐 경쟁 상황을 서비스가 다시 방어 |
| 404 | `USER_NOT_FOUND` | 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 |

## 2. 농가 홈

### 요청

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| URL | `/api/farm/me/home` |
| Body | 없음 |
| 성공 | `200 OK` |

농가 프로필이 `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`, `INACTIVE` 중 어느 상태이든 프로필이 존재하면 홈 조회는 가능하다.

### 응답 JSON

```json
{
  "farmProfile": {
    "id": 15,
    "farmName": "푸른농가",
    "representativeName": "김농부",
    "contactNumber": "01012345678",
    "farmAddress": "충북 청주시 상당구 ...",
    "cityCounty": "CHEONGJU",
    "crops": ["감자", "배추"],
    "mainActivities": "감자와 배추를 재배합니다.",
    "businessRegistrationNumber": "1234567890",
    "farmAreaPyeong": 1500,
    "status": "APPROVED",
    "reviewerId": 9001,
    "reviewerName": "중개센터 담당자",
    "reviewedAt": "2026-08-10T02:00:00Z",
    "rejectionReason": null,
    "createdAt": "2026-08-01T01:00:00Z",
    "updatedAt": "2026-08-10T02:00:00Z"
  },
  "postingCounts": {
    "DRAFT": 1,
    "PENDING_REVIEW": 1,
    "OPEN": 2,
    "CLOSED": 3,
    "CANCELLED": 1,
    "WORK_COMPLETED": 5
  },
  "displayPostingCounts": {
    "PENDING": 1,
    "APPROVED": 2,
    "CLOSED": 8,
    "REJECTED": 1
  },
  "recentPostings": [
    {
      "id": 101,
      "farmProfileId": 15,
      "farmName": "푸른농가",
      "cityCounty": "CHEONGJU",
      "farmAddress": "충북 청주시 상당구 ...",
      "contactNumber": "01012345678",
      "crop": "감자",
      "workType": "수확 보조",
      "workDate": "2026-08-20",
      "startTime": "09:00:00",
      "endTime": "16:00:00",
      "capacity": 3,
      "meetingPlace": "농장 입구",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "supplies": "작업 장갑",
      "precautions": "안전수칙 준수",
      "farmMessage": null,
      "applicantPreference": "초보자 가능",
      "title": "감자 수확 보조",
      "description": "모집 설명",
      "beginnerGuide": null,
      "status": "OPEN",
      "displayStatus": "APPROVED",
      "reviewRequestedAt": "2026-08-10T03:00:00Z",
      "approvedAt": "2026-08-10T04:00:00Z",
      "closedAt": null,
      "cancelledAt": null,
      "createdAt": "2026-08-10T02:30:00Z",
      "updatedAt": "2026-08-10T04:00:00Z",
      "latestReviewAction": "APPROVED",
      "latestReviewReason": null,
      "latestReviewedAt": "2026-08-10T04:00:00Z"
    }
  ],
  "upcomingWork": [
    {
      "id": 701,
      "jobPostingId": 101,
      "jobApplicationId": 501,
      "urbanFarmerUserId": 301,
      "urbanFarmerName": "홍길동",
      "confirmedByUserId": 9001,
      "confirmedByName": "중개센터 담당자",
      "confirmedByContactNumber": "01098765432",
      "farmName": "푸른농가",
      "farmAddress": "충북 청주시 상당구 ...",
      "farmContactNumber": "01012345678",
      "crop": "감자",
      "workType": "수확 보조",
      "workDate": "2026-08-20",
      "startTime": "09:00:00",
      "endTime": "16:00:00",
      "recruitmentCapacity": 5,
      "meetingPlace": "농장 입구",
      "wageAmount": 100000,
      "wageUnit": "DAILY",
      "supplies": "작업 장갑",
      "precautions": "안전수칙 준수",
      "status": "SCHEDULED",
      "attendanceStatus": "NOT_RECORDED",
      "completedAt": null
    }
  ]
}
```

### 집계 기준

- `postingCounts`: 내 농가 공고를 모든 `JobPostingStatus`별로 각각 count
- `displayPostingCounts`: 화면 배지 기준 `PENDING`, 현재 지원 가능한 `APPROVED`, `CLOSED`(근무 완료 및 작업 시작 시각이 지난 `OPEN` 포함), 최신 심사 기준 `REJECTED`
- `recentPostings`: `updatedAt` 최신순 최대 5개, 모든 상태 포함
- `upcomingWork`: 미래 작업일 또는 오늘 종료 시각 전인 `SCHEDULED`, 날짜/시간 오름차순 최대 5개

### 개인정보 범위

- `farmProfile`은 본인 농가 전용으로 주소, 연락처, 사업자번호, 심사자가 포함된다.
- `upcomingWork`에는 매칭된 도시농부의 이름/ID와 확정자 정보가 포함된다.
- 로그인 ID, 비밀번호, 지원자 전화번호, 서류 파일은 반환하지 않는다.

### 대표 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | 인증 실패 |
| 403 | `ACCESS_DENIED` | 농가가 아닌 JWT 역할 |
| 403 | `FARM_ROLE_REQUIRED` | 보안 필터 통과 직후 계정 역할이 바뀐 경쟁 상황을 서비스가 다시 방어 |
| 404 | `FARM_PROFILE_NOT_FOUND` | 계정에 농가 프로필이 없음 |

## 오류 응답 형식

```json
{
  "code": "ACCESS_DENIED",
  "message": "접근 권한이 없습니다."
}
```

## Postman 예시

```bash
# 도시농부 홈
curl "{{baseUrl}}/api/urban-farmers/me/home" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}"

# 농가 홈
curl "{{baseUrl}}/api/farm/me/home" \
  -H "Authorization: Bearer {{farmAccessToken}}"
```

## 현재 제한과 중개센터 역할 경계

- 대시보드는 매칭·승인·반려·마감을 처리하지 않고 현재 DB 결과를 조회만 한다.
- 농가·공고 승인과 지원자 매칭은 현재 통합된 `CENTER_ADMIN` 중개센터 API가 처리한다.
- 대시보드의 `recentOpenPostings`는 공개 공고 응답과 같이 농가 전체 주소/연락처를 숨긴다. 매칭 후 `upcomingWork`에서는 현장 연락을 위해 해당 정보가 노출된다.
