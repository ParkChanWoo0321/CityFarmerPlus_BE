# 근무 일정·출근·작업 안내 API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 6개

## 기능 범위

- 도시농부: 내 확정 근무 일정 목록/상세, 작업 안내 조회
- 농가: 내 농가의 확정 근무 목록, 출근/결근 등록, 근무 완료 확정
- 근무 일정은 이 사용자 API들이 생성하지 않는다. 현재 통합된 `CENTER_ADMIN` 매칭 API가 지원자를 확정할 때 생성한다.

## 인증

- JWT 필수
- 도시농부 URL: `URBAN_FARMER`, 활성 계정
- 농가 URL: `FARM`, 활성 계정 + `APPROVED` 농가 프로필
- 자기와 연결된 근무 일정만 조회·변경 가능

## 엔드포인트

| Method | URL | 권한 | 기능 | 성공 |
|---|---|---|---|---:|
| `GET` | `/api/urban-farmers/me/work-assignments` | URBAN_FARMER | 내 근무 목록 | 200 |
| `GET` | `/api/urban-farmers/me/work-assignments/{assignmentId}` | URBAN_FARMER | 내 근무 상세 | 200 |
| `GET` | `/api/urban-farmers/me/work-assignments/{assignmentId}/guide` | URBAN_FARMER | 작업 준비·안전 안내 | 200 |
| `GET` | `/api/farm/work-assignments` | FARM | 농가 근무 목록 | 200 |
| `PUT` | `/api/farm/work-assignments/{assignmentId}/attendance` | FARM | 출근/결근 등록·동일 값 재시도 | 200 |
| `POST` | `/api/farm/work-assignments/{assignmentId}/complete` | FARM | 농가 근무 완료 확정 | 200 |

## 상태 값

### `status` (`WorkStatus`)

- `SCHEDULED`: 근무 예정
- `COMPLETED`: 농가가 근무 완료 확정
- `NO_SHOW`: 결근
- `CANCELLED`: 근무 일정 취소

### `attendanceStatus`

- `NOT_RECORDED`: 미등록
- `PRESENT`: 출근
- `ABSENT`: 결근

## `WorkAssignmentResponse` 형식

```json
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
  "supplies": "작업 장갑, 모자",
  "precautions": "안전수칙을 준수해 주세요.",
  "status": "SCHEDULED",
  "attendanceStatus": "NOT_RECORDED",
  "completedAt": null
}
```

- 공고·농가 정보는 매칭 시점에 근무 일정으로 복사되며, 이후 공고/농가 프로필 변경에 따라 자동 수정되지 않는 스냅샷이다.
- `recruitmentCapacity`는 매칭 당시 공고 모집 인원이다. 새 근무 배정은 공고 capacity를 저장하고, 필드 추가 전 생성된 기존 DB 행은 `null`일 수 있다.
- `confirmedBy*`는 `CENTER_ADMIN`이 매칭을 확정한 사용자 정보다. 매칭 정보가 없으면 `null`이다.
- 현재 DTO는 확정자의 연락처까지 도시농부와 농가 근무 응답에 포함한다. 실제 운영 전 공개 필요성을 검토해야 한다.
- `wageAmount`/`wageUnit`는 안내 스냅샷으로, 서비스가 결제·송금·정산하지 않는다.

## 1. 도시농부 내 근무 목록

`GET /api/urban-farmers/me/work-assignments?view=UPCOMING&page=0&size=20`

- `view`: `ALL`(기본), `UPCOMING`, `PAST`
- `page`: 기본 0, 0 이상
- `size`: 기본 20, 1~100
- `ALL`: 모든 상태를 포함한다. 기존 API와 동일하게 작업일 내림차순 → 시작 시간 내림차순이다.
- `UPCOMING`: 서울 시간 기준 종료 전인 `SCHEDULED` 근무만 반환한다. 진행 중 근무도 포함하며 작업일 → 시작 시간 가까운 순이다.
- `PAST`: `COMPLETED`, `NO_SHOW`, `CANCELLED` 또는 종료 시각이 지난 `SCHEDULED` 근무를 반환한다. 작업일 → 시작 시간 최신 순이다.
- 작업 종료 시각과 현재 시각이 같으면 `UPCOMING`이 아니라 `PAST`에 포함된다.

```json
{
  "content": [
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
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

오류: `VALIDATION_ERROR`(400), 잘못된 `view`의 `INVALID_REQUEST_PARAMETER`(400), `UNAUTHORIZED`/`INVALID_ACCOUNT`(401), `ACCESS_DENIED`/`URBAN_FARMER_REQUIRED`(403).

## 2. 도시농부 내 근무 상세

`GET /api/urban-farmers/me/work-assignments/{assignmentId}`

응답은 상단 `WorkAssignmentResponse` 전체 형식이다.

```json
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
```

오류: `WORK_ASSIGNMENT_NOT_FOUND`(404), `WORK_ASSIGNMENT_NOT_OWNER`(403), `INVALID_REQUEST_PARAMETER`(400).

## 3. 도시농부 작업 안내

`GET /api/urban-farmers/me/work-assignments/{assignmentId}/guide`

- 해당 도시농부 본인의 근무만 가능
- 외부 AI가 아닌 `RULE_BASED_V1` 규칙 기반 안내
- 조회 시점에 근무 스냅샷으로 생성하며 별도 DB에 저장하지 않는다.

```json
{
  "workAssignmentId": 701,
  "workSummary": "감자 수확 보조 작업입니다. 09:00까지 농장 입구로 모여주세요.",
  "officialPrecautions": "농기계 주변에서는 안전거리를 유지해 주세요.",
  "preparationChecklist": ["작업 장갑", "물", "개인 상비약", "모자", "장화"],
  "recommendedClothing": ["편한 긴소매 작업복", "모자", "미끄럼 방지 작업화"],
  "safetyRules": ["작업 전 스트레칭을 해주세요.", "물을 자주 마시고 무리하지 마세요.", "도구 사용 전 농가의 안전 설명을 들어주세요."],
  "workSteps": ["집결 장소에서 출석과 작업 구역을 확인합니다.", "농가의 시범과 안전 설명을 듣습니다.", "안내받은 구역에서 천천히 작업합니다.", "수확물이나 도구를 지정된 장소에 정리합니다.", "작물에 상처가 나지 않도록 양손으로 조심스럽게 수확합니다."],
  "beginnerTip": "처음이라도 괜찮습니다. 모르는 작업은 임의로 진행하지 말고 농가에 바로 물어보세요.",
  "generator": "RULE_BASED_V1"
}
```

> `supplies`는 쉼표와 줄바꿈 기준으로 개별 체크 항목이 되며, 빈 값과 중복은 제거된다. `officialPrecautions`에는 농가가 공고에 작성한 주의사항 스냅샷이 그대로 제공된다.

오류: 상세 조회와 같은 `WORK_ASSIGNMENT_NOT_FOUND`, `WORK_ASSIGNMENT_NOT_OWNER`.

## 4. 농가 근무 목록

`GET /api/farm/work-assignments?page=0&size=20`

- 해당 농가 프로필 ID와 연결된 근무만 반환
- 페이징/정렬/응답 형식은 도시농부 목록과 동일

```json
{
  "content": [
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
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```

오류: `VALIDATION_ERROR`(400), `FARM_PROFILE_NOT_FOUND`(404), 인증/역할 오류.

## 5. 농가 출근/결근 등록

`PUT /api/farm/work-assignments/{assignmentId}/attendance`

### 요청

- 작업 시작 시각 이후에만 등록 가능
- 최초 상태 변경은 `SCHEDULED` + `NOT_RECORDED`인 일정에서만 가능
- `status`: `PRESENT` 또는 `ABSENT`
- `NOT_RECORDED`를 요청값으로 보낼 수 없음
- 같은 농가 소유자가 기존 `attendanceStatus`와 같은 값을 재시도하면 `200` + 현재 `WorkAssignmentResponse`
- 동일 값 재시도는 멱등적이며 출결·근무·지원 상태, 최초 기록 시각, 기록자를 변경하지 않음
- 이미 등록된 값과 다른 `PRESENT`/`ABSENT`로 바꾸려는 요청은 `INVALID_WORK_ASSIGNMENT_STATE`(409)

```json
{
  "status": "PRESENT"
}
```

### 응답

```json
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
  "attendanceStatus": "PRESENT",
  "completedAt": null
}
```

`ABSENT`를 등록하면 근무 `status` = `NO_SHOW`, 지원 `status` = `NO_SHOW`로 함께 변경된다.

프론트엔드는 네트워크 타임아웃으로 첫 응답을 받지 못했더라도 같은 `status`로 안전하게 재시도할 수 있다. 기존 값과 다른 정정은 농가 사용자 API가 아니라 `POST /api/admin/work-assignments/{assignmentId}/attendance-correction`으로 처리한다.

오류: `VALIDATION_ERROR`/`INVALID_REQUEST`(400), `INVALID_WORK_ASSIGNMENT_STATE`(409, `NOT_RECORDED` 요청·기존과 다른 출결 변경 요청 등), `WORK_ASSIGNMENT_NOT_FOUND`(404), `WORK_ASSIGNMENT_NOT_OWNER`(403), `CONCURRENT_UPDATE_CONFLICT`(409).

## 6. 농가 근무 완료 확정

`POST /api/farm/work-assignments/{assignmentId}/complete`

- Body 없음
- 작업 `endTime` 이후에만 가능
- 근무 `status` = `SCHEDULED`, `attendanceStatus` = `PRESENT`여야 함
- 성공 시 근무 `COMPLETED`, 지원 `WORK_COMPLETED`
- 해당 공고의 `SCHEDULED` 근무가 더 없고 공고가 `CLOSED`면 공고도 `WORK_COMPLETED`

```json
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
  "status": "COMPLETED",
  "attendanceStatus": "PRESENT",
  "completedAt": "2026-08-20T07:10:00Z"
}
```

오류: `INVALID_WORK_ASSIGNMENT_STATE`(409), `WORK_ASSIGNMENT_NOT_FOUND`(404), `WORK_ASSIGNMENT_NOT_OWNER`(403), `CONCURRENT_UPDATE_CONFLICT`(409).

## 공통 오류 형식

```json
{
  "code": "INVALID_WORK_ASSIGNMENT_STATE",
  "message": "작업 종료 전에는 근무 완료를 확정할 수 없습니다."
}
```

## Postman 예시

```bash
# 도시농부 근무 목록
curl "{{baseUrl}}/api/urban-farmers/me/work-assignments?view=UPCOMING&page=0&size=20" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}"

# 작업 안내
curl "{{baseUrl}}/api/urban-farmers/me/work-assignments/701/guide" \
  -H "Authorization: Bearer {{urbanFarmerAccessToken}}"

# 농가가 출근 등록
curl -X PUT "{{baseUrl}}/api/farm/work-assignments/701/attendance" \
  -H "Authorization: Bearer {{farmAccessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"status":"PRESENT"}'

# 농가가 근무 완료 확정
curl -X POST "{{baseUrl}}/api/farm/work-assignments/701/complete" \
  -H "Authorization: Bearer {{farmAccessToken}}"
```

## 현재 제한과 중개센터 역할

- 매칭 확정과 `WorkAssignment` 생성은 현재 통합된 `CENTER_ADMIN` API가 처리한다. 사용자 API는 이미 생성된 일정을 조회·처리한다.
- 농가는 출근/결근을 최초 등록하고 같은 값만 멱등적으로 재시도할 수 있다. 오등록 정정과 이에 따른 지원·공고 상태 복구는 `POST /api/admin/work-assignments/{assignmentId}/attendance-correction`이 처리한다.
- 도시농부는 자기의 출근 상태를 직접 변경할 수 없다.
- 작업 안내는 규칙 기반이므로 농가가 작성한 공고와 현장 지침을 우선해야 한다.
