# 자주 묻는 질문(FAQ) API

- 기준일: 2026-08-20
- 기준: 현재 `main` 통합 코드
- 로컬 Base URL: `http://localhost:8080`
- 운영 Base URL: `https://cityfarmerplus-api-82951616760.us-west1.run.app`
- API 수: 1개

## 개요

- 회원과 서비스 정책을 빠르게 확인할 수 있도록 FAQ 목록을 반환한다.
- 현재 FAQ는 DB가 아닌 `FaqService` 정적 목록 6개다.
- 조회만 지원하며 관리자 등록·수정·삭제 API는 없다.

## 인증

- `SecurityConfig`의 공개 조회 대상이므로 JWT 없이 호출할 수 있다.
- Authorization 헤더를 보냈다면 JWT와 현재 계정 상태를 정상 검증한다.

## FAQ 목록 조회

| 항목 | 내용 |
|---|---|
| Method | `GET` |
| URL | `/api/support/faqs` |
| Request Body | 없음 |
| Query Parameter | 없음 |
| 성공 | `200 OK` |

### 응답 필드

| 필드 | 형식 | 설명 |
|---|---|---|
| `category` | string | FAQ 분류 |
| `question` | string | 질문 |
| `answer` | string | 현재 정책 답변 |

### 응답 JSON

```json
[
  {
    "category": "회원",
    "question": "교육을 받기 전에도 가입할 수 있나요?",
    "answer": "네. 회원가입은 가능하지만 담당자가 교육 이수증을 승인하기 전에는 공고에 지원할 수 없습니다."
  },
  {
    "category": "교육",
    "question": "교육 이수증은 어떤 파일로 제출하나요?",
    "answer": "PDF, JPG, JPEG, PNG 파일을 제출할 수 있습니다. 제출한 파일은 담당자가 확인합니다."
  },
  {
    "category": "지원",
    "question": "같은 시간의 여러 공고에 지원할 수 있나요?",
    "answer": "네. 여러 공고에 지원할 수 있지만 같은 공고에는 중복 지원할 수 없습니다."
  },
  {
    "category": "매칭",
    "question": "농가가 수락하면 바로 매칭되나요?",
    "answer": "아닙니다. 농가 의견은 담당자가 참고하며 최종 매칭은 담당자가 확정합니다."
  },
  {
    "category": "농가",
    "question": "작성한 공고는 바로 공개되나요?",
    "answer": "아닙니다. 담당자 검토와 승인을 받은 공고만 도시농부에게 공개됩니다."
  },
  {
    "category": "인건비",
    "question": "서비스에서 인건비를 결제하나요?",
    "answer": "아닙니다. 공고의 인건비 정보만 안내하며 실제 지급과 결제는 서비스 밖에서 당사자 간에 처리합니다."
  }
]
```

> 응답 순서는 현재 서비스 코드에 정의된 순서다. 클라이언트가 category 순서나 정렬을 가정하면 안 된다.

## 상태 코드와 오류

| HTTP | `code` | 조건 |
|---:|---|---|
| 200 | - | FAQ 목록 조회 성공 |
| 401 | `UNAUTHORIZED` | 선택적으로 보낸 JWT가 만료·위조됨 |
| 401 | `INVALID_ACCOUNT` | 선택적으로 보낸 JWT 사용자/역할과 현재 DB 계정이 불일치 |

오류 형식:

```json
{
  "code": "UNAUTHORIZED",
  "message": "인증이 필요합니다."
}
```

## Postman 예시

```bash
curl "{{baseUrl}}/api/support/faqs"
```

Postman:

1. Method `GET`
2. URL `{{baseUrl}}/api/support/faqs`
3. Authorization → `No Auth`
4. Body 없음

## 현재 제한과 중개센터 역할 경계

- FAQ 내용은 코드 배포 없이 수정할 수 없다.
- 카테고리 필터, 검색, 페이징이 없다.
- FAQ의 “담당자”는 농가·공고·교육 승인과 매칭을 처리하는 현재 통합 `CENTER_ADMIN` 중개센터 기능을 뜻한다.
- 이 API 자체에는 중개센터 전용 작업이 없다.
- FAQ 등록·수정·삭제 API는 현재 제공하지 않는다.
