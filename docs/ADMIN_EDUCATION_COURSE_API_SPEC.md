# CityFarmerPlus 관리자 교육 과정 관리 API 명세서

- 문서 버전: 1.0
- 작성일: 2026-08-26
- 구현 기준: 현재 `main` 통합 코드
- 적용 범위: 교육 과정 생성, 수정, 비활성화

## 1. 공통 사항

### 1.1 기본 URL

```text
운영: https://cityfarmerplus-api-82951616760.us-west1.run.app
로컬: http://localhost:8080
```

### 1.2 요청 및 응답 형식

- JSON 요청의 `Content-Type`은 `application/json`이다.
- 모든 API는 다음 인증 헤더가 필요하다.

```http
Authorization: Bearer {{adminAccessToken}}
```

### 1.3 인증 및 권한 규칙

- `/api/admin/**`는 `SecurityConfig`에서 `hasRole("CENTER_ADMIN")`으로 URL 패턴 단위로 이미 보호된다. 이 컨트롤러는 클래스 레벨 `@PreAuthorize("hasRole('CENTER_ADMIN')")`을 추가로 걸어 이중으로 확인한다.
- [`ADMIN_EDUCATION_SUBMISSION_API_SPEC.md`](ADMIN_EDUCATION_SUBMISSION_API_SPEC.md)의 승인·반려 API와 달리, 이 API들은 **서비스 레이어에서 DB 역할을 다시 조회하는 절차가 없다**. `EducationCourse` 엔티티에는 심사자·작성자 정보를 저장하는 필드가 없어(누가 만들고 고쳤는지 기록하지 않음) 관리자 신원을 조회할 필요가 없기 때문이다. 따라서 `CENTER_ADMIN_ROLE_REQUIRED`, `INACTIVE_ACCOUNT`, `USER_NOT_FOUND` 오류는 이 API에는 없다.
- 이 API들은 [`education`](../src/main/java/chungbuk/cityfarmerplus/education) 패키지의 `EducationCourse` 엔티티(`create`/`update`/`deactivate` 도메인 메서드), `EducationCourseRepository`, 응답 DTO(`EducationCourseResponse`)를 그대로 재사용한다.
- **과정은 참조 무결성 때문에 물리 삭제하지 않는다.** 삭제 API는 없고, 비활성화(`active=false`)만 제공한다. 비활성화된 과정은 공개 조회 API(`GET /api/education/courses`)에서 자동으로 제외되지만, 이미 제출된 이수증(`EducationCertificateSubmission`)이나 과거 심사 이력은 그대로 남는다.

### 1.4 공통 오류 응답 형식

```json
{
  "code": "오류 코드",
  "message": "오류 설명"
}
```

`GlobalExceptionHandler`가 처리한다. `DomainException`은 `handleDomainException`이, 엔티티 도메인 검증 실패(`IllegalArgumentException`)는 `handleIllegalArgument`가, Bean Validation 실패(`@Valid`)는 `handleValidation`이 각각 처리하지만 응답 형식은 동일하다. 인증(`401`)·인가(`403`, URL 패턴 단계) 오류는 `SecurityConfig`가 직접 만들며 형식은 동일하다.

## 2. API 목록

| 기능 | Method | URL | 인증 | 성공 상태 |
|---|---|---|---|---|
| 교육 과정 생성 | `POST` | `/api/admin/education/courses` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `201 Created` |
| 교육 과정 수정 | `PATCH` | `/api/admin/education/courses/{courseId}` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |
| 교육 과정 비활성화 | `POST` | `/api/admin/education/courses/{courseId}/deactivate` | Bearer JWT (`ROLE_CENTER_ADMIN`) | `200 OK` |

생성·수정은 필드 값을 그대로 대체하는 전체 교체 방식이며 부분 수정(일부 필드만 전송)을 지원하지 않는다. 두 API 모두 같은 요청 본문 형식(`EducationCourseRequest`)을 사용한다.

## 3. 교육 과정 생성

### 3.1 요청

```http
POST /api/admin/education/courses
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

```json
{
  "title": "농업안전 기초",
  "description": "농작업 안전수칙과 보호구 사용법을 학습합니다.",
  "requiredHours": 8,
  "externalApplicationUrl": "https://education.example.com/basic",
  "mandatory": true,
  "displayOrder": 1
}
```

| 필드 | 타입 | 필수 | 제약 조건 |
|---|---|---|---|
| `title` | String | O | 공백만 입력 불가, 150자 이하 |
| `description` | String | O | 공백만 입력 불가, 2000자 이하 |
| `requiredHours` | int | O | 1 이상(엔티티 도메인 검증, DTO 레벨 제약 없음) |
| `externalApplicationUrl` | String | X | 500자 이하. 생략하면 `null` |
| `mandatory` | boolean | X | 생략하면 `false` |
| `displayOrder` | int | O | 0 이상(엔티티 도메인 검증, DTO 레벨 제약 없음) |

새로 생성된 과정은 항상 `active=true`로 시작한다(생성 API에는 활성 여부를 지정하는 필드가 없다).

### 3.2 성공 응답

```http
HTTP/1.1 201 Created
Content-Type: application/json
```

```json
{
  "id": 3,
  "title": "농업안전 기초",
  "description": "농작업 안전수칙과 보호구 사용법을 학습합니다.",
  "requiredHours": 8,
  "externalApplicationUrl": "https://education.example.com/basic",
  "mandatory": true,
  "active": true,
  "displayOrder": 1,
  "version": 0,
  "createdAt": "2026-08-26T01:00:00Z",
  "updatedAt": "2026-08-26T01:00:00Z"
}
```

필드 설명은 [`notion/05_EDUCATION.md`](notion/05_EDUCATION.md) 4장의 `EducationCourseResponse` 표와 동일하다.

### 3.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `title`·`description` 누락(공백 포함) 또는 길이 초과, `externalApplicationUrl` 500자 초과 |
| `400` | `BAD_REQUEST` | `requiredHours`가 1 미만이거나 `displayOrder`가 0 미만(엔티티 도메인 검증) |
| `400` | `INVALID_REQUEST` | 잘못된 JSON 형식 |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |

엔티티 도메인 검증 오류 예시:

```json
{
  "code": "BAD_REQUEST",
  "message": "교육 시간은 1시간 이상이어야 합니다."
}
```

## 4. 교육 과정 수정

과정명, 설명, 필수 시간, 외부 신청 URL, 필수 여부, 표시 순서를 모두 새 값으로 교체한다. `active` 상태는 이 API로 바꿀 수 없다(5장의 비활성화 API 전용).

### 4.1 요청

```http
PATCH /api/admin/education/courses/3
Authorization: Bearer {{adminAccessToken}}
Content-Type: application/json
```

요청 필드는 3.1과 동일한 `EducationCourseRequest` 형식이다. 생략한 필드는 이전 값이 유지되는 것이 아니라 기본값(문자열은 필수 입력, `mandatory`는 `false` 등)으로 대체되므로, 변경하지 않을 필드도 항상 현재 값을 그대로 채워 보내야 한다.

### 4.2 성공 응답

```http
HTTP/1.1 200 OK
```

3.2와 동일한 `EducationCourseResponse` 형식이며, `version`이 1 증가하고 `updatedAt`이 갱신된다. `active` 값은 요청 이전 상태가 그대로 유지된다.

### 4.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `400` | `VALIDATION_ERROR` | `title`·`description` 누락(공백 포함) 또는 길이 초과, `externalApplicationUrl` 500자 초과 |
| `400` | `BAD_REQUEST` | `requiredHours`가 1 미만이거나 `displayOrder`가 0 미만(엔티티 도메인 검증) |
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |
| `404` | `EDUCATION_COURSE_NOT_FOUND` | 해당 `courseId`의 과정이 없음(비활성 과정도 수정 가능하므로, 존재하지 않을 때만 발생) |
| `409` | `CONCURRENT_UPDATE_CONFLICT` | 두 요청이 같은 과정을 동시에 수정해 낙관적 잠금 충돌 발생 |

과정 없음 예시:

```json
{
  "code": "EDUCATION_COURSE_NOT_FOUND",
  "message": "교육 과정을 찾을 수 없습니다."
}
```

## 5. 교육 과정 비활성화

과정을 물리 삭제하지 않고 `active=false`로 전환한다. 비활성화된 과정은 공개 목록 조회(`GET /api/education/courses`)에서 즉시 제외된다.

### 5.1 요청

```http
POST /api/admin/education/courses/3/deactivate
Authorization: Bearer {{adminAccessToken}}
```

요청 본문은 없다.

### 5.2 성공 응답

```http
HTTP/1.1 200 OK
```

3.2와 동일한 `EducationCourseResponse` 형식이며 `active`가 `false`다. **멱등 동작이다**: 이미 비활성 상태인 과정에 다시 호출해도 오류 없이 `200 OK`와 `active: false`를 반환한다(현재 상태를 확인하는 별도 분기가 없다).

### 5.3 오류 응답

| 상태 | 코드 | 발생 조건 |
|---|---|---|
| `401` | `UNAUTHORIZED` | JWT 누락, 만료 또는 위조 |
| `403` | `ACCESS_DENIED` | `CENTER_ADMIN`이 아닌 계정의 JWT로 접근 |
| `404` | `EDUCATION_COURSE_NOT_FOUND` | 해당 `courseId`의 과정이 없음 |

## 6. 현재 범위 밖 또는 미구현 기능

- 비활성 과정을 다시 활성화하는 API(재활성화는 4장의 수정 API로도 불가능하다. `active` 필드는 생성 시 항상 `true`로 고정되고, 이후에는 5장의 비활성화 API로만 `false`로 내릴 수 있을 뿐 되돌리는 경로가 없다)
- 관리자용 전체 과정 목록·상세 조회 API(비활성 과정 포함). 현재는 공개 API `GET /api/education/courses`(활성 과정만)만 존재한다
- 과정 물리 삭제 API
- 과정 여러 건 일괄 생성·수정·비활성화
- 표시 순서(`displayOrder`) 중복 검증 또는 자동 재정렬
