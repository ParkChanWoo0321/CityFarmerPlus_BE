# 도시농부플러스 백엔드

CityFarmerPlus Backend

> 도시농부의 교육·지원부터 농가 모집, 중개센터 매칭, 근무 관리까지 연결하는 서비스

> 도시농부플러스 서비스 로고 사진

[제13회 전국 ICT융합 AI 공모전](https://linkareer.com/activity/330182)에 참가한 4인 팀 프로젝트입니다. ICT 기술로 지역 문제와 주민 생활의 불편을 해결한다는 공모 취지에 맞춰, 충북형 도시농부 사업의 신청·심사·매칭 과정을 서비스로 구현했습니다.

| 구분 | 내용 |
|---|---|
| 팀 구성 | 디자인 1명 · 프론트엔드 1명 · 백엔드 2명 |
| 참여 역할 | 백엔드 개발자 2명 중 1명. 두 개발자가 이 저장소를 공동 사용 |
| 주요 기술 | Java 17 · Spring Boot 4.1.0 · Spring Security · JPA · MySQL |
| 배포 구성 | Docker · Cloud Build · Cloud Run · GCS |

이 문서는 백엔드 팀의 공동 구현 내용을 다룹니다. 동시 매칭, 신청 데이터 일관성, 파일 실패 복구와 외부 이벤트 처리의 설계·검증 근거를 정리했습니다.

[구현 범위](#백엔드-구현-범위) · [아키텍처](#백엔드-아키텍처) · [핵심 설계](#핵심-설계와-문제-해결) · [ERD](#데이터-모델과-erd) · [협업](#협업-방식) · [검증](#테스트와-검증) · [실행](#로컬-실행)

## 백엔드 구현 범위

도시농부는 교육 인증 후 공고에 지원하고, 농가는 지원자에게 선호 의견을 남깁니다. 중개센터는 심사 결과와 근무 조건을 확인해 매칭을 확정합니다. 백엔드는 역할·소유권·업무 상태를 검사해 각 단계에서 가능한 작업을 제한합니다.

| 영역 | 구현 내용 |
|---|---|
| 인증·인가 | JWT·BCrypt, 역할·소유권 검사, 탈퇴 계정의 기존 토큰 차단 |
| 도시농부 | 프로필·근무 선호·참여 신청, 교육 이수증 제출, 공고 지원·취소 |
| 농가 | 프로필·소유 증빙, 모집 공고 관리, 지원자 의견, 출결·근무 완료 |
| 중개센터 | 참여·교육·농가·공고 심사, 최종 매칭, 출결 정정, 대리 접수·감사 이력 |
| 외부 연동 | 교육 진도 웹훅, KAMIS 시세 조회, Local/GCS/S3 파일 저장 |
| 배포 구성 | Docker, Cloud Build, Cloud Run, 마이그레이션·스모크 검사·롤백 스크립트 |

공고 문안과 작업 안내는 `RULE_BASED_V1`, 상담은 키워드 규칙으로 생성합니다. 외부 LLM 연동은 없으며, 결제·정산과 알림·푸시는 구현 범위에 포함하지 않습니다.

## 백엔드 아키텍처

도메인별 패키지를 가진 **단일 Spring Boot 애플리케이션**입니다. 아래 그림은 저장소의 Cloud Run 배포 구성을 기준으로 주요 연결만 표시했습니다.

```mermaid
flowchart TB
    client["도시농부 · 농가 · 중개센터"]
    lms["외부 교육 시스템"]
    api["Cloud Run · Spring Boot"]
    db[("MySQL")]
    files[("GCS")]
    kamis["KAMIS API"]
    client -->|"HTTPS · JWT"| api
    lms -.->|"진도 웹훅 · HMAC"| api
    api -->|"JPA"| db
    api -->|"파일 저장"| files
    api -.->|"시세 조회"| kamis
```

| 구성 | 처리 내용 |
|---|---|
| 인증·요청 처리 | Spring Security에서 JWT와 활성 계정을 확인하고 Controller에서 요청 검증 |
| 업무·영속성 | Service의 트랜잭션, Entity·정책 클래스의 상태 검증, JPA·MySQL의 잠금과 제약조건 |
| 파일 | 배포 설정은 GCS 사용. `FileStorage`로 Local·GCS·S3 호환 구현체 선택 |
| 외부 연동 | 교육 진도 웹훅 수신·서명 검증, KAMIS 시세 조회·메모리 캐시 |
| 비밀값 | Secret Manager에서 DB·JWT 등의 설정을 환경변수로 주입 |

교육 시스템 연결은 구현된 웹훅 수신 계약을 나타냅니다. `open-in-view=false`로 설정하고 서비스 계층에서 응답 DTO를 구성합니다.

[의존성](build.gradle) · [설정](src/main/resources/application.properties) · [파일 저장소](src/main/java/chungbuk/cityfarmerplus/common/storage/FileStorageConfig.java)

## 핵심 설계와 문제 해결

### 1. 동시 매칭의 정원·시간 중복 제어

같은 공고에 대한 동시 요청은 정원을 초과할 수 있고, 서로 다른 공고의 요청은 같은 도시농부를 겹치는 시간에 배정할 수 있습니다.

`AdminJobPostingMatchingService`는 하나의 트랜잭션에서 **공고 → 지원 → 도시농부 사용자 행**을 잠근 뒤 정원과 기존 근무 시간을 확인합니다. 사용자 행을 공통 잠금 대상으로 두어 아직 근무 배정이 없는 경우에도 동일인의 매칭 요청을 직렬화합니다. 여러 행을 잠그는 지원·사용자 쿼리는 ID 순으로 조회합니다.

```mermaid
%%{init: {"sequence": {"actorMargin": 16, "width": 90, "diagramMarginX": 8, "mirrorActors": false}}}%%
sequenceDiagram
    actor admin as 담당자
    participant service as 매칭 서비스
    participant db as MySQL
    admin->>service: 매칭 요청
    service->>db: 공고·지원·사용자 잠금
    service->>db: 정원 · 시간 중복 확인
    alt 조건 위반
        service-->>admin: 오류 · 롤백
    else 매칭 가능
        service->>db: 확정 · 근무 스냅샷
        opt 정원 도달
            service->>db: 마감 · 잔여 지원 미선정
        end
        service-->>admin: 확정 근무 목록
    end
```

같은 날짜의 취소되지 않은 근무를 대상으로 `기존 시작 < 신규 종료 AND 기존 종료 > 신규 시작`이면 시간이 겹친다고 판정합니다. 지원의 공고·도시농부 조합과 근무 배정의 지원 ID에는 UNIQUE 제약을 두었습니다. 정원에 도달하면 공고 마감과 잔여 지원의 미선정 처리를 함께 수행합니다.

[매칭 서비스](src/main/java/chungbuk/cityfarmerplus/admin/jobposting/service/AdminJobPostingMatchingService.java) · [중복 시간 쿼리](src/main/java/chungbuk/cityfarmerplus/work/repository/WorkAssignmentRepository.java) · [사용자 잠금 선행 호출 테스트](src/test/java/chungbuk/cityfarmerplus/admin/jobposting/service/AdminJobPostingMatchingServiceLockTest.java)

### 2. 신청 데이터의 원자적 저장과 근무 조건 보존

통합 신청서는 프로필·근무 선호·연도별 신청서를 같은 트랜잭션에서 저장합니다. 클라이언트가 예상 버전을 보내면 각 데이터의 현재 버전과 비교해 오래된 화면의 수정 요청을 거절합니다. 예상 버전이 생략된 항목은 이 비교를 수행하지 않습니다.

매칭 시에는 농가 정보, 작업일·시간, 집결 장소, 임금, 준비물 등을 `WorkAssignment`에 스냅샷으로 저장합니다. 이후 공고가 수정되어도 확정 당시의 조건을 조회할 수 있습니다. 출결 정정은 담당자만 수행하고 이전·변경 상태, 사유와 처리자를 이력으로 남깁니다.

[통합 신청 저장](src/main/java/chungbuk/cityfarmerplus/urbanfarmer/participation/service/ParticipationFormService.java) · [근무 스냅샷](src/main/java/chungbuk/cityfarmerplus/work/entity/WorkAssignment.java) · [출결 정정](src/main/java/chungbuk/cityfarmerplus/admin/work/service/AdminWorkAssignmentService.java)

### 3. 파일 업로드와 DB 저장 사이의 실패 복구

이수증 파일을 업로드한 뒤 DB 저장이 실패하면 파일만 남을 수 있습니다. 일반 교육 이수증 제출은 파일을 먼저 저장하고 DB 트랜잭션에서 제출 내역을 확정합니다. 이 경로에서 실패하면 삭제 작업을 기록해 보상 삭제를 시도합니다.

| 단계 | 처리 |
|---|---|
| 검증 | 확장자·MIME·실제 헤더·크기 검사, 저장 결과의 크기·SHA-256 대조 |
| DB 저장 | 잠금 상태에서 제출 조건을 다시 확인하고 제출 내역 저장 |
| 실패 보상 | `REQUIRES_NEW`로 삭제 작업을 기록한 뒤 즉시 삭제 시도 |
| 재시도 | 최대 100건씩 잠금 조회. 실패 작업의 다음 시도 대기시간을 30초~1시간으로 증가 |
| 탈퇴 처리 | DB 트랜잭션에 삭제 작업을 기록하고 커밋 이후 파일 삭제 |

다운로드는 소유자·담당자 권한을 검사합니다. 삭제 재시도는 작업이 DB에 기록된 이후를 대상으로 하며, 농가 증빙과 대리 접수의 후속 롤백에는 즉시 삭제만 수행하는 경로가 남아 있습니다.

[교육 파일 제출](src/main/java/chungbuk/cityfarmerplus/education/service/EducationSubmissionService.java) · [보상 삭제](src/main/java/chungbuk/cityfarmerplus/common/storage/FileDeletionScheduler.java) · [재시도 워커](src/main/java/chungbuk/cityfarmerplus/common/storage/FileDeletionWorker.java)

### 4. 교육 웹훅의 위변조·중복·순서 역전 검증

외부 진도 이벤트는 `timestamp + "." + raw body`의 HMAC-SHA256 서명과 기본 ±5분의 시각 허용 범위를 검사합니다. 이미 저장된 제공자·이벤트 ID를 다시 수신하면 본문 해시를 비교합니다. 내용이 같으면 해당 수강의 현재 정보를 반환하고, 다르면 충돌 처리합니다.

과거 이벤트는 이력에 남기되 현재 진도를 덮어쓰지 않습니다. 더 최신 이벤트라도 수강 시간 감소나 완료 상태의 되돌림은 거부합니다. 현재 진도와 수신 이력을 별도 테이블에 저장하고 적용 여부를 기록합니다. 동시 중복 저장은 UNIQUE 제약으로 제한합니다.

수강률과 교육 이수 승인은 별개입니다. 공고 지원 자격은 활성 필수 과정의 최신 이수증 심사 결과와 인정 시간으로 계산합니다.

[서명 검증](src/main/java/chungbuk/cityfarmerplus/education/progress/security/EducationProgressWebhookVerifier.java) · [이벤트 처리](src/main/java/chungbuk/cityfarmerplus/education/progress/service/EducationProgressEventService.java) · [교육 자격 계산](src/main/java/chungbuk/cityfarmerplus/education/service/EducationCertificationProgressCalculator.java)

### 5. 외부 시세 API 장애 대응

KAMIS 시세는 메모리 스냅샷으로 제공합니다. 연결 제한은 2초, 호출 시도당 응답 제한은 5초이며 일시적 장애에는 한 번 재시도합니다. 정상 캐시는 1시간 유지하고, 갱신 실패 시 마지막 성공값을 수집 시점부터 최대 24시간까지 제공합니다. 이때 응답의 `stale=true`로 이전 값임을 알립니다.

`AtomicBoolean`으로 인스턴스 내 한 요청만 캐시를 갱신하게 하고, 실패 후 30초 동안 추가 호출을 제한합니다. 갱신이 진행 중이거나 실패한 상황에서 제공 가능한 캐시도 없으면 오류를 반환합니다. 캐시와 갱신 제어의 범위는 인스턴스 하나입니다.

[시세 서비스](src/main/java/chungbuk/cityfarmerplus/marketprice/service/MarketPriceService.java) · [캐시·동시 갱신 테스트](src/test/java/chungbuk/cityfarmerplus/marketprice/service/MarketPriceServiceTest.java)

### 6. 공고 목록의 반복 조회 줄이기

공고 목록은 `@EntityGraph`로 농가·소유자 정보를 함께 조회합니다. 사용자의 지원 상태와 공고 심사 이력은 현재 페이지의 공고 ID 목록으로 일괄 조회해 응답에 조합합니다. 검색 조건은 JPA Specification으로 구성합니다.

심사 이력은 페이지에 포함된 공고의 전체 이력을 가져온 뒤 최신 항목을 선택합니다. 공고별 최신 한 건만 조회하는 쿼리는 개선 과제로 남아 있으며, 성능 개선 수치는 측정하지 않았습니다.

[공고 저장소](src/main/java/chungbuk/cityfarmerplus/jobposting/repository/JobPostingRepository.java) · [공개 목록](src/main/java/chungbuk/cityfarmerplus/jobposting/service/PublicJobPostingService.java) · [심사 이력 조합](src/main/java/chungbuk/cityfarmerplus/jobposting/service/JobPostingResponseAssembler.java)

## 데이터 모델과 ERD

한 그림에서 긴 테이블명과 전체 컬럼을 읽지 않아도 되도록 업무별로 나눴습니다. 그림에는 짧은 이름과 핵심 컬럼을, 바로 아래에는 실제 테이블명을 적었습니다. `PK`는 기본키, `FK`는 외래키, `UK`는 단일 컬럼 UNIQUE입니다. 점선은 비식별 관계를 나타냅니다.

사용자·심사자 참조, 승인 제출 포인터와 컬렉션 테이블 등 일부 관계는 생략했습니다.

### 모집과 지원

```mermaid
erDiagram
    direction TB
    farm["농가"] {
        bigint id PK
        bigint owner_user_id FK,UK
        varchar status
    }
    posting["공고"] {
        bigint id PK
        bigint farm_profile_id FK
        int capacity
        varchar status
    }
    application["지원"] {
        bigint id PK
        bigint job_posting_id FK
        bigint urban_farmer_user_id FK
        varchar status
    }
    farm ||..o{ posting : "모집"
    posting ||..o{ application : "접수"
```

| 그림 | 실제 테이블 |
|---|---|
| 농가 | `farm_profiles` |
| 공고 | `job_postings` |
| 지원 | `job_applications` |

`owner_user_id`와 `urban_farmer_user_id`는 `users.id`를 참조합니다. 지원은 공고·도시농부 조합당 한 행을 유지하며, 취소 후 재지원은 기존 행의 상태와 스냅샷을 갱신합니다.

### 근무 배정과 정정

```mermaid
erDiagram
    direction TB
    application["지원"] {
        bigint id PK
    }
    assignment["근무 배정"] {
        bigint id PK
        bigint job_application_id FK,UK
        date work_date
        varchar attendance_status
    }
    correction["출결 정정"] {
        bigint id PK
        bigint work_assignment_id FK
        bigint corrected_by_user_id FK
        varchar reason
    }
    application ||..o| assignment : "매칭 확정"
    assignment ||..o{ correction : "정정 이력"
```

| 그림 | 실제 테이블 |
|---|---|
| 지원 | `job_applications` |
| 근무 배정 | `work_assignments` |
| 출결 정정 | `work_assignment_corrections` |

근무 배정의 농가·공고 ID는 스냅샷용 일반 컬럼이며 JPA 외래키가 아닙니다. 확정 당시의 농가 정보·일시·임금 등을 함께 저장하고, 출결 정정은 이전 상태·변경 상태·사유·처리자를 별도 이력으로 남깁니다.

<details>
<summary>교육 이수증 심사 ERD</summary>

```mermaid
erDiagram
    direction TB
    certification["교육 인증"] {
        bigint id PK
        bigint urban_farmer_user_id FK,UK
        varchar status
    }
    submission["이수증 제출"] {
        bigint id PK
        bigint certification_id FK
        bigint education_course_id FK
        int attempt_number
        varchar status
    }
    document["증빙 파일"] {
        bigint id PK
        bigint submission_id FK
        varchar storage_key UK
    }
    certification ||..o{ submission : "제출 이력"
    submission ||..o{ document : "첨부"
```

| 그림 | 실제 테이블 |
|---|---|
| 교육 인증 | `education_certifications` |
| 이수증 제출 | `education_certificate_submissions` |
| 증빙 파일 | `education_certificate_documents` |

이수증 제출의 `education_course_id`는 `education_courses.id`를 참조합니다. 교육 인증 하나에 제출 이력을 쌓고, 각 제출 건에 파일을 연결합니다.

</details>

<details>
<summary>외부 교육 진도 ERD</summary>

```mermaid
erDiagram
    direction TB
    course["교육 과정"] {
        bigint id PK
        int required_hours
        boolean mandatory
    }
    enrollment["수강 진도"] {
        bigint id PK
        bigint education_course_id FK
        bigint urban_farmer_user_id FK
        int completed_minutes
    }
    event["진도 이벤트"] {
        bigint id PK
        bigint education_enrollment_id FK
        varchar provider_event_id
        boolean applied
    }
    course ||..o{ enrollment : "수강"
    enrollment ||..o{ event : "수신 이력"
```

| 그림 | 실제 테이블 |
|---|---|
| 교육 과정 | `education_courses` |
| 수강 진도 | `education_enrollments` |
| 진도 이벤트 | `education_progress_events` |

수강 진도는 현재 상태를, 이벤트는 제공자·이벤트 ID·본문 해시·발생 시각·적용 여부를 기록합니다. 웹훅 진도와 이수증 승인에 따른 지원 자격은 별도로 관리합니다.

</details>

<details>
<summary>복합 UNIQUE 제약과 그 밖의 관계</summary>

| 테이블 | 유일성 기준 |
|---|---|
| `job_applications` | 공고 + 도시농부 |
| `urban_farmer_participation_applications` | 도시농부 + 사업연도 |
| `education_certificate_submissions` | 교육 인증 + 제출 회차 |
| `education_enrollments` | 도시농부 + 과정 / 제공자 + 외부 수강 ID |
| `education_progress_events` | 제공자 + 이벤트 ID |
| `farm_ownership_submissions` | 농가 + 제출 회차 |

- 사용자별 프로필과 근무 선호는 각각 1:1, 사업 참여 신청은 연도별 1:N 관계입니다.
- 농가 소유 증빙은 농가 → 제출 이력 → 증빙 파일로 연결합니다.
- 공고 심사 이력은 행동·사유·당시 제목과 본문을 기록합니다.
- 대리 접수 감사 이력은 관리자·대상 회원과 객체·작업 종류·사유를 기록합니다.
- 파일 삭제 작업은 저장 키와 재시도 상태를 관리하는 독립 테이블입니다.

</details>

## 협업 방식

### 화면 요구사항을 API 계약으로 구체화

화면 연동에 필요한 데이터·상태·오류를 API 계약으로 정리했습니다. [공통 계약](docs/notion/00_COMMON_CONTRACT.md)에 인증, 오류 형식, 페이지네이션, CORS를 모으고, [기능별 명세](docs/notion/README.md)에 요청·응답 예시, 권한, 상태 전이와 제한 사항을 기록했습니다.

‘희망 근무 조건 신청’ 화면은 프로필·근무 선호·사업 참여 신청을 함께 다룹니다. [통합 신청 API](docs/notion/04A_PARTICIPATION_FORM.md)에 세 데이터의 저장·제출 계약과 예상 버전 전달 방식을 명시했습니다.

### 기능 개발과 릴리스를 PR로 구분

교육 진도 연동과 공고 목록 오류 수정은 작업 브랜치에서 `develop`으로 통합한 뒤, 릴리스 PR으로 `main`에 반영했습니다.

교육 진도 연동의 [기능 PR #15](https://github.com/ParkChanWoo0321/CityFarmerPlus_BE/pull/15)에는 API·명세·테스트와 DB 마이그레이션·배포 설정을 포함했습니다. PR 본문에 테스트 결과와 마이그레이션, 비밀값 주입, 후보 리비전 검증 순서를 기록했고, [릴리스 PR #16](https://github.com/ParkChanWoo0321/CityFarmerPlus_BE/pull/16)으로 반영했습니다.

### API 오류를 재현 조건과 회귀 테스트로 남김

농가 공고 목록 API에서 `displayStatus`를 생략하면 400 오류가 발생한 사례가 있었습니다. 선택 필터가 없을 때 `null Specification`을 결합하는 경로를 수정하고, **공고가 없는 농가와 공고가 있는 농가** 모두 필터 없이 조회할 수 있는 통합 테스트를 추가했습니다.

수정은 [PR #24](https://github.com/ParkChanWoo0321/CityFarmerPlus_BE/pull/24), 릴리스는 [PR #25](https://github.com/ParkChanWoo0321/CityFarmerPlus_BE/pull/25)에 기록했습니다.

## 인증과 API 계약

- `URBAN_FARMER`, `FARM`, `CENTER_ADMIN`을 구분하고 역할·소유권·업무 상태를 검사합니다.
- JWT 검증 뒤 DB의 활성 여부와 역할을 확인해 탈퇴한 계정의 기존 토큰을 차단합니다.
- 담당자 계정은 별도 프로비저닝 키를 사용하는 내부 API로 생성합니다.
- 오류는 `code`·`message`, 페이지 목록은 `PageResponse` 형식으로 제공합니다.

[보안 설정](src/main/java/chungbuk/cityfarmerplus/auth/config/SecurityConfig.java) · [활성 계정 검증](src/main/java/chungbuk/cityfarmerplus/auth/config/ActiveAccountFilter.java) · [전체 API 명세](docs/FULL_API_SPEC.md)

## 배포와 운영

다음은 저장소에 구성한 배포 흐름입니다. Cloud Build의 배포 옵션은 기본적으로 꺼져 있으며, `_DEPLOY=true`로 실행할 때 후보 배포와 트래픽 전환을 수행합니다.

```mermaid
flowchart TB
    build["테스트 · 이미지 빌드"]
    registry["Artifact Registry"]
    candidate["후보 배포 · 트래픽 0%"]
    check{"스모크 · 승격 조건"}
    promote["후보에 트래픽 100%"]
    keep["기존 트래픽 유지"]
    verify{"서비스 재검사"}
    done["배포 완료"]
    rollback["소유권 확인 후 롤백"]
    build --> registry --> candidate --> check
    check -->|"통과"| promote
    check -->|"실패"| keep
    promote --> verify
    verify -->|"성공"| done
    verify -->|"실패"| rollback
```

| 운영 과제 | 적용 방식 |
|---|---|
| 배포 간 충돌 | GCS 객체의 generation 조건부 생성으로 배포 잠금 |
| 오래된 빌드 승격 | 후보 검사 후 원격 `main`과 빌드 커밋 일치 확인 |
| 배포 실패 | 현재 트래픽 소유권을 확인한 뒤 직전 리비전으로 롤백 |
| 상태 점검 | `/health`에서 DB `SELECT 1`, `/health/live`에서 생존 확인 |
| 실행·비밀값 | 비루트 Docker 사용자, Secret Manager 환경변수 주입 |
| 스키마 변경 | 운영 `ddl-auto=validate`, 사전 검사 → 백업·체크섬 → SQL 적용 → 사후 검증 |

저장소의 배포 설정은 Cloud Run 동시성 5·최소 0·최대 1 인스턴스, Hikari 최대 커넥션 5입니다. 운영 환경의 가용성·부하 처리량에 대한 실측 수치는 포함하지 않았습니다.

[Cloud Build](cloudbuild.yaml) · [Dockerfile](Dockerfile) · [수동 배포](gcp/deploy.sh) · [스모크 검사](gcp/smoke-public.sh) · [운영 가이드](docs/GCP_DEPLOYMENT.md)

## 테스트와 검증

2026-10-23, 코드 [3455437](https://github.com/ParkChanWoo0321/CityFarmerPlus_BE/commit/3455437abf0c2a0cb6a96ad9cbd0e6f8ce12cac8)에서 Java 17로 실행한 결과입니다.

| 검증 | 결과 |
|---|---|
| `test` | **499건 통과** · 실패 0 · 오류 0 · 스킵 0 |
| `bootJar` | **빌드 성공** |
| `mysqlIntegrationTest` | 별도 MySQL 환경이 필요한 opt-in 테스트 · 위 검증에서 미실행 |

일반 테스트는 JUnit·Mockito·Spring MVC Test와 H2 영속성 테스트를 포함합니다. 권한·소유권, 상태 전이, 버전 충돌, 파일 실패 보상, 웹훅 재전송, 시세 캐시와 동시 갱신을 검증합니다. 매칭 테스트는 Mockito로 사용자 잠금의 선행 호출을 확인하고, 저장소 선언을 검사합니다. 실제 MySQL에서의 병렬 매칭·부하 테스트는 수행하지 않았습니다.

```powershell
.\gradlew.bat test bootJar --no-daemon --max-workers=1
```

<details>
<summary>테스트 보고서와 MySQL 테스트 실행</summary>

보고서는 `build/reports/tests/test/index.html`에서 확인합니다. MySQL 테스트는 엔티티와 일치하는 스키마를 준비하고 전용 테스트 DB의 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 환경변수로 설정해 실행합니다. 잠금과 쓰기가 발생하므로 운영 DB를 대상으로 실행하지 않습니다.

```powershell
.\gradlew.bat mysqlIntegrationTest --no-daemon --max-workers=1
```

</details>

## 남은 개선 과제

| 대상 | 현재 범위와 다음 검증 |
|---|---|
| 매칭 동시성 | 실제 MySQL에서 같은 공고·서로 다른 공고의 동시 요청, 시간 중복·정원 초과·교착상태 검증 |
| 파일 복구 | 농가 증빙·대리 접수 롤백의 재시도 정책 통일, 작업 등록 전 종료로 남는 고아 파일 정리 |
| 백그라운드 작업 | 삭제 워커는 앱 내부 스케줄러. 최소 인스턴스 0·CPU 제한 환경에서도 실행 기회를 확보할 구조 검토 |
| 웹훅 재전송 | 동시 중복 요청의 UNIQUE 충돌을 기존 처리 결과로 응답하도록 보완 |
| 조회·캐시 | 최신 심사 이력만 조회하는 쿼리 측정, 여러 인스턴스에서의 시세 중복 갱신 정책 검토 |

## 로컬 실행

<details>
<summary>Java 17 · MySQL 환경에서 실행하기</summary>

Java 17과 로컬 MySQL이 필요합니다. Gradle은 저장소의 Wrapper를 사용합니다.

```powershell
git clone https://github.com/ParkChanWoo0321/CityFarmerPlus_BE.git
cd CityFarmerPlus_BE
Copy-Item .env.example .env
```

`.env`의 다음 값을 자신의 로컬 환경에 맞게 설정합니다.

| 변수 | 설정 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | 로컬 MySQL 연결 정보 |
| `JWT_SECRET` | 독립적으로 생성한 32바이트 이상의 난수 비밀키 |
| `JPA_DDL_AUTO` | 예제의 `update`는 로컬 개발용. 운영은 마이그레이션 후 `validate` |
| `FILE_STORAGE_TYPE` | 로컬 실행은 `local` |
| `KAMIS_API_KEY`, `KAMIS_CERT_ID` | 시세 API를 사용할 때 설정 |
| `ADMIN_PROVISIONING_KEY` | 담당자 계정 생성 기능을 사용할 때 설정 |
| `EDUCATION_PROGRESS_WEBHOOK_SECRET` | 교육 진도 연동을 사용할 때 설정 |

`JAVA_HOME`이 JDK 17을 가리키는지 확인한 뒤 실행합니다. `.env`는 Git 추적에서 제외되어 있습니다.

```powershell
.\gradlew.bat bootRun
```

다른 터미널에서 DB 연결까지 확인합니다.

```powershell
Invoke-RestMethod http://localhost:8080/health
```

</details>

## 코드 구조와 상세 문서

<details>
<summary>도메인별 패키지 구조</summary>

```text
src/main/java/chungbuk/cityfarmerplus
├── auth             # JWT, 계정, 활성 상태, 탈퇴
├── urbanfarmer      # 프로필, 근무 선호, 사업 참여
├── farm             # 농가 정보, 소유 증빙
├── education        # 과정, 이수증 심사, 진도 웹훅
├── jobposting       # 모집 공고, 검색, 심사 이력
├── application      # 공고 지원, 농가 의견
├── work             # 근무 스냅샷, 출결, 작업 안내
├── admin            # 중개센터 심사, 매칭, 정정, 대리 접수
├── marketprice      # KAMIS 연동, 캐시, 장애 대응
├── ai               # 규칙 기반 공고 문안과 상담
├── dashboard        # 사용자별 현황
├── support          # FAQ
├── common           # 저장소, 삭제 재시도, 검증, 공통 응답
└── health           # DB 연결 및 생존 확인
```

</details>

- [API 명세 인덱스](docs/API_SPEC_INDEX.md) / [기능별 명세](docs/notion/README.md)
- [교육 진도 연동 계약](docs/EDUCATION_PROGRESS_API_SPEC.md)
- [담당자 매칭 API](docs/ADMIN_JOB_POSTING_API_SPEC.md)
- [근무 정정 API](docs/ADMIN_WORK_ASSIGNMENT_API_SPEC.md)
- [GCP 배포·운영 가이드](docs/GCP_DEPLOYMENT.md)
