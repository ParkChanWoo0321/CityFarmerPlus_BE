# AI 행정 상담 API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 2개

## 개요

- 회원의 질문을 규칙 기반으로 분류하고 안내 답변을 생성한다.
- 질문과 답변을 사용자별로 DB에 저장하고 내 이력을 조회한다.
- 현재는 LLM/외부 AI API가 아닌 `RuleBasedSupportAnswerGenerator`를 사용한다.
- 법적·행정적 확정 답변이 아니며, 확인이 필요한 답변은 `officialConfirmationRequired: true`로 표시한다.

## 인증과 개인정보

- JWT 필수
- 특정 역할 제한 없음. 활성 회원이면 사용 가능
- 상담 이력은 JWT 사용자 ID로 필터링하여 본인 것만 반환
- 응답에 사용자 ID/이름/연락처는 포함되지 않음
- 계좌번호, 비밀번호, 주민등록번호 등 민감한 정보를 질문에 입력하지 말아야 함

## 엔드포인트

| Method | URL | 기능 | 성공 |
|---|---|---|---:|
| `POST` | `/api/ai/support/messages` | 질문 전송 및 답변 생성/저장 | 200 |
| `GET` | `/api/ai/support/messages` | 내 상담 이력 | 200 |

## `SupportMessageResponse` 형식

| 필드 | 형식 | 설명 |
|---|---|---|
| `id` | long | 저장된 문의 ID |
| `question` | string | 앞뒤 공백을 제거해 저장한 질문 |
| `category` | string | 규칙이 판단한 카테고리 |
| `answer` | string | 규칙 기반 안내 |
| `officialConfirmationRequired` | boolean | 공식 담당자/기관 확인 필요 여부 |
| `createdAt` | instant | 저장 시각 |

## 1. 상담 질문 전송

### 요청

`POST /api/ai/support/messages`

```json
{
  "message": "교육 이수증은 어떻게 제출하나요?"
}
```

| 필드 | 필수 | 검증 |
|---|---:|---|
| `message` | O | 공백 불가, 최대 1000자 |

서비스는 `message.trim()` 결과를 질문으로 저장한다.

### 응답

`200 OK`

```json
{
  "id": 801,
  "question": "교육 이수증은 어떻게 제출하나요?",
  "category": "교육",
  "answer": "교육 안내에서 필수 과정을 확인한 뒤 외부 교육 사이트에서 수강하세요. 8시간 이상의 교육을 이수하고 PDF 또는 이미지 이수증을 제출하면 담당자가 검토합니다. 승인 전에는 공고 지원이 제한됩니다.",
  "officialConfirmationRequired": false,
  "createdAt": "2026-08-11T12:00:00Z"
}
```

### 분류 규칙과 우선순위

질문은 아래 순서로 가장 먼저 일치한 규칙 하나에 분류된다.

1. `계좌`, `송금`, `결제`, `정산`, `지급` 포함 → `민감 업무`, `officialConfirmationRequired: true`
2. `교육`, `이수증`, `수료증` 포함 → `교육`
3. `공고 작성`, `공고 등록`, `공고 생성`, `공고 게시`, `사람 모집`, `인력 모집` 중 하나를 포함하거나, `농가`와 `모집`/`공고`를 함께 포함하면서 `지원`/`취소`를 포함하지 않음 → `농가 공고`
4. `장갑`, `준비`, `복장`, `안전` 포함 → `근무 준비`
5. `지원`, `취소`, `공고` 포함 → `공고 지원`
6. 어느 규칙에도 해당하지 않음 → `일반 안내`, `officialConfirmationRequired: true`

분류는 자연어 이해 모델이 아니라 단순 포함 키워드 규칙이므로 복합 질문에서는 위 우선순위가 적용된다.

### 민감 업무 응답 예시

```json
{
  "id": 802,
  "question": "계좌로 인건비를 송금해 주세요.",
  "category": "민감 업무",
  "answer": "이 서비스는 결제·송금·정산을 처리하지 않습니다. 금전 관련 내용은 공고의 농가 또는 공식 담당자에게 직접 확인해 주세요. 계좌번호 같은 개인정보는 채팅에 입력하지 마세요.",
  "officialConfirmationRequired": true,
  "createdAt": "2026-08-11T12:05:00Z"
}
```

### 대표 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 누락, 공백, 1000자 초과 |
| 400 | `INVALID_REQUEST` | JSON 형식 오류 |
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | JWT 누락·무효 또는 현재 계정 불일치 |
| 404 | `USER_NOT_FOUND` | 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 |

> 서비스에도 비활성 계정 검증이 있지만, 일반적인 HTTP 호출에서는 `ActiveAccountFilter`가 컨트롤러/서비스보다 먼저 응답하므로 비활성 계정은 `401 INVALID_ACCOUNT`로 처리된다.

## 2. 내 상담 이력

### 요청

`GET /api/ai/support/messages?page=0&size=20`

| Query | 기본 | 검증 |
|---|---:|---|
| `page` | 0 | 0 이상 |
| `size` | 20 | 1~100 |

- `createdAt` 내림차순, 같은 시각이면 `id` 내림차순
- JWT 사용자 본인의 문의만 조회

### 응답

```json
{
  "content": [
    {
      "id": 802,
      "question": "계좌로 인건비를 송금해 주세요.",
      "category": "민감 업무",
      "answer": "이 서비스는 결제·송금·정산을 처리하지 않습니다. 금전 관련 내용은 공고의 농가 또는 공식 담당자에게 직접 확인해 주세요. 계좌번호 같은 개인정보는 채팅에 입력하지 마세요.",
      "officialConfirmationRequired": true,
      "createdAt": "2026-08-11T12:05:00Z"
    },
    {
      "id": 801,
      "question": "교육 이수증은 어떻게 제출하나요?",
      "category": "교육",
      "answer": "교육 안내에서 필수 과정을 확인한 뒤 외부 교육 사이트에서 수강하세요. 8시간 이상의 교육을 이수하고 PDF 또는 이미지 이수증을 제출하면 담당자가 검토합니다. 승인 전에는 공고 지원이 제한됩니다.",
      "officialConfirmationRequired": false,
      "createdAt": "2026-08-11T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1,
  "hasNext": false
}
```

### 대표 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | `page`, `size` 범위 오류 |
| 400 | `INVALID_REQUEST_PARAMETER` | 정수 형식 오류 |
| 401 | `UNAUTHORIZED` / `INVALID_ACCOUNT` | 인증 실패 |
| 404 | `USER_NOT_FOUND` | 보안 필터 통과 직후 계정이 사라진 경쟁 상황을 서비스가 다시 방어 |

> 비활성 계정의 일반 HTTP 응답은 `401 INVALID_ACCOUNT`이다.

## 오류 응답 형식

```json
{
  "code": "VALIDATION_ERROR",
  "message": "문의 내용은 1000자 이하여야 합니다."
}
```

## Postman 예시

```bash
# 질문 전송
curl -X POST "{{baseUrl}}/api/ai/support/messages" \
  -H "Authorization: Bearer {{accessToken}}" \
  -H "Content-Type: application/json" \
  -d '{"message":"교육 이수증은 어떻게 제출하나요?"}'

# 내 이력
curl "{{baseUrl}}/api/ai/support/messages?page=0&size=20" \
  -H "Authorization: Bearer {{accessToken}}"
```

Postman 질문 전송:

1. Method `POST`
2. URL `{{baseUrl}}/api/ai/support/messages`
3. Authorization → Bearer Token → `{{accessToken}}`
4. Body → raw → JSON

## 현재 제한과 중개센터 확장 경계

- 현재 답변은 키워드 규칙 기반이므로 질문을 완전히 이해하거나 개별 사례를 판단하지 못한다.
- `officialConfirmationRequired` 값은 확인 필요성을 알리는 표시일 뿐, 자동으로 담당자에게 문의를 전달하지 않는다.
- 담당자 질문 수신·답변·배정 API는 현재 없다. 해당 행정 상담을 운영하려면 향후 `CENTER_ADMIN` 중개센터 기능으로 별도 확장해야 한다.
- 상담 이력 삭제 API는 현재 없다. 또한 `SupportInquiry`를 삭제하는 회원 탈퇴 `AccountDataCleaner`가 구현되어 있지 않아 탈퇴 후에도 DB 행은 유지된다. 단, 탈퇴 계정으로는 인증할 수 없어 이력 API에 접근할 수 없다.
