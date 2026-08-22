# Render 무료 배포 가이드

- 대상: CityFarmerPlus Spring Boot API
- 배포 방식: Render Web Service + Docker
- 데이터베이스: 외부 MySQL
- 영구 파일: S3 호환 비공개 오브젝트 스토리지
- Health Check: `GET /health`

이 문서는 포트폴리오 데모 환경을 위한 배포 계약이다. Render는 Free 인스턴스를 운영용으로 사용하지 말 것을 안내하고 있으므로, 실제 개인정보나 민감한 농가 증빙을 저장하는 운영 환경으로 간주하면 안 된다.

## 1. 최종 배포 전 조건

배포 설정과 인프라는 지금 준비할 수 있지만, 공개 URL의 최종 배포 기준 브랜치는 다음 조건을 모두 충족한 `main`이다.

- `backend-1`이 `develop`에 병합되어 있다.
- `backend-2`의 심사·최종 매칭·근무 배정 기능까지 `develop`에 병합되어 있다.
- 검증을 마친 `develop`이 `main`에 병합되어 있다.
- 배포할 정확한 `main` 커밋에서 전체 테스트가 통과한다.
- 외부 MySQL과 S3 호환 저장소를 빈 데모 데이터로 검증한다.
- 실제 비밀번호, JWT 비밀키, S3 접근 키는 Git 또는 문서에 저장하지 않는다.

`backend-2` 병합 전 배포는 Docker·Health Check·DB·파일 저장소 연결을 확인하는 임시 배포일 뿐, 완성본 배포가 아니다.

## 2. Render Free에서 알아야 할 제한

- 15분 동안 수신 트래픽이 없으면 Web Service가 내려간다.
- 다음 요청에서 다시 올라오는 데 약 1분이 걸릴 수 있다.
- 재배포, 재시작, 휴면 전환 시 로컬 파일시스템의 변경분이 사라진다.
- Free Web Service에는 Persistent Disk를 연결할 수 없다.
- Free 사용량은 워크스페이스당 월 750 인스턴스 시간이며, 대역폭과 빌드 시간에도 별도 한도가 적용된다.
- 외부 DB나 오브젝트 스토리지로 비정상적으로 많은 외부 트래픽을 발생시키면 Free 서비스가 정지될 수 있다.

따라서 MySQL은 외부 영구 DB를 사용하고, 농가 소유 증빙 파일은 `FILE_STORAGE_TYPE=s3`로 저장해야 한다. `FILE_STORAGE_TYPE=local`은 로컬 개발이나 파일 유실을 허용하는 일회성 Smoke Test에만 사용한다.

공식 문서:

- [Render Free 인스턴스 제한](https://render.com/docs/free)
- [Render Web Service와 PORT](https://render.com/docs/web-services)
- [Render Health Check](https://render.com/docs/health-checks)
- [Render 영구 디스크](https://render.com/docs/disks)

## 3. 외부 MySQL 준비

현재 애플리케이션은 MySQL Connector/J를 사용한다. Render의 무료 관리형 데이터베이스는 PostgreSQL이므로 코드 변경 없이 연결할 수 없다. 별도의 외부 MySQL 데이터베이스를 먼저 생성해야 한다.

외부 서비스가 `mysql://...` 형식의 주소를 제공하더라도 `DB_URL`에는 JDBC 형식이 필요하다.

```text
jdbc:mysql://DB_HOST:3306/cityfarmerplus?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&sslMode=VERIFY_IDENTITY
```

- 실제 호스트, 포트, DB 이름과 SSL 옵션은 DB 제공자의 안내를 따른다. 공개 인터넷에서는 제공자 CA를 신뢰하도록 구성하고 서버 인증서와 호스트 이름을 검증하는 `VERIFY_IDENTITY`를 우선한다.
- URL, 사용자명, 비밀번호를 문서나 Git에 기록하지 않고 Render Environment에 각각 등록한다.
- 운영용 URL에서는 `createDatabaseIfNotExist=true`를 제거한다. 권한이 제한된 외부 DB에서는 DB 생성 권한 때문에 시작에 실패할 수 있고, 의도하지 않은 DB 생성을 숨길 수도 있다.
- 빈 데이터베이스 이름과 계정을 배포 전에 직접 만든다.
- 외부 DB의 네트워크 허용 목록, 동시 연결 수, 휴면·만료·백업 정책을 확인한다.

### 스키마 초기화 정책

이 프로젝트에는 아직 운영 마이그레이션 도구가 없으므로 `spring.jpa.hibernate.ddl-auto` 사용을 엄격히 제한한다.

1. `backend-2`까지 모두 병합한 최종 스키마로 새로 만든 폐기 가능한 데모 DB에서만 `JPA_DDL_AUTO=update`로 최초 1회 기동한다.
2. 필요한 테이블 생성과 API Smoke Test를 확인한다.
3. Render 환경 변수를 `JPA_DDL_AUTO=validate`로 바꾸고 다시 배포한다.
4. 이후 스키마 변경은 Flyway 같은 버전 관리형 마이그레이션을 도입한 뒤 적용한다.

기존 데이터가 있는 DB, 공유 DB 또는 실제 운영 DB에 `update`를 장기간 사용하지 않는다. `validate`는 스키마를 생성하지 않으므로 첫 기동 전에 스키마가 준비되어 있어야 한다.

## 4. Render 환경 변수

Render Dashboard의 Web Service `Environment`에 다음 값을 등록한다. `.env.example`은 키 이름만 참고하는 로컬 예시이며 실제 값을 복사해 커밋하지 않는다.

### 서버, DB, 인증, CORS

| 변수 | 필수 | Render 값/규칙 |
|---|---:|---|
| `PORT` | 자동 | Render가 주입한다. 애플리케이션은 이 포트로 바인딩하며 직접 고정하지 않는다. |
| `DB_URL` | O | 외부 MySQL JDBC URL. `createDatabaseIfNotExist` 제거, 제공자 SSL 정책 적용 |
| `DB_USERNAME` | O | 전용 DB 계정 |
| `DB_PASSWORD` | O | Render Environment에만 저장하고 Git에 기록하지 않음 |
| `JPA_DDL_AUTO` | O | 최초 폐기 가능 DB 1회만 `update`, 이후 `validate` |
| `JPA_SHOW_SQL` | 권장 | `false`; SQL과 개인정보가 로그에 노출되지 않게 한다. |
| `JWT_SECRET` | O | 독립적으로 생성한 최소 32 random bytes 이상의 값. 예시 문자열 재사용 금지 |
| `JWT_ISSUER` | O | 예: `https://cityfarmerplus-api.onrender.com`; 토큰 발급·검증에서 동일하게 유지 |
| `JWT_ACCESS_TOKEN_EXPIRATION` | 권장 | 예: `1h` |
| `CORS_ALLOWED_ORIGINS` | O | 실제 프론트엔드 Origin을 쉼표로 구분. 예: `https://example.pages.dev,https://example.com` |
| `FILE_CLEANUP_FIXED_DELAY` | 선택 | 삭제 재시도 주기(ms), 기본 `60000` |
| `FILE_CLEANUP_INITIAL_DELAY` | 선택 | 최초 삭제 재시도 지연(ms), 기본 `60000` |

`CORS_ALLOWED_ORIGINS`에는 경로(`/login`)가 아닌 `scheme://host[:port]`만 넣고 끝의 `/`를 제거한다. 현재 서버는 `/api/**`에 지정 Origin을 적용하고 credential cookie를 사용하지 않으며, 인증은 Bearer JWT 헤더로 처리한다.

JWT 비밀키는 비밀번호 관리자나 신뢰할 수 있는 암호학적 난수 생성기로 새로 만든다. 노출되었거나 Git에 들어간 키는 즉시 폐기·교체해야 하며, 키 교체 시 기존 Access Token은 더 이상 유효한 것으로 신뢰하면 안 된다.

### 파일 저장소 공통

| 변수 | Render 권장값 | 설명 |
|---|---|---|
| `FILE_STORAGE_TYPE` | `s3` | `local` 또는 `s3` |
| `FILE_STORAGE_ROOT` | 설정하지 않음 | `local`에서만 사용하는 경로. 로컬 기본값은 `./data/uploads` |

### S3 호환 저장소

`FILE_STORAGE_TYPE=s3`이면 아래 값을 설정한다.

| 변수 | 필수 | 설명 |
|---|---:|---|
| `S3_ENDPOINT` | O | S3 호환 서비스 endpoint. 예: R2의 계정별 S3 API endpoint |
| `S3_REGION` | O | 제공자 region. R2는 일반적으로 `auto` |
| `S3_BUCKET` | O | 미리 만든 비공개 bucket 이름 |
| `S3_ACCESS_KEY_ID` | O | 전용 접근 키 ID |
| `S3_SECRET_ACCESS_KEY` | O | 전용 비밀 접근 키. Render Environment에만 저장하고 Git에 기록하지 않음 |
| `S3_PATH_STYLE` | 권장 | 기본 `true`. 제공자의 주소 지정 방식에 맞게 설정 |
| `S3_PREFIX` | 선택 | 예: `cityfarmerplus/prod`; 기본 빈 문자열 |

S3 bucket은 공개하지 않는다. 애플리케이션이 권한을 확인한 뒤 파일을 내려주므로, 키에는 해당 bucket/prefix의 업로드, 조회, 삭제에 필요한 최소 권한만 부여한다.

`S3_ENDPOINT`는 유효한 `http://` 또는 `https://` URL이어야 한다. Render에서는 자격 증명을 보호하도록 반드시 제공자의 `https://` endpoint를 사용한다. `http://`는 로컬 S3 에뮬레이터처럼 격리된 개발 환경에만 허용한다. `S3_PREFIX`는 앞뒤 `/` 없이 `cityfarmerplus/prod`처럼 설정하고, 각 경로 구간에는 영문자·숫자로 시작하는 영문자·숫자·점·밑줄·하이픈만 사용한다.

Cloudflare R2를 사용할 때는 AWS SDK의 chunked transfer encoding을 끄고, SDK 2.30 이상이 자동으로 붙이는 선택적 CRC32 checksum을 `WHEN_REQUIRED`로 제한한다. 현재 애플리케이션의 S3 client에 이 호환 설정이 포함되어 있으며, 업로드 파일의 SHA-256은 별도로 계산해 메타데이터와 DB 계약에 유지한다.

호환성 근거:

- [Cloudflare R2의 AWS SDK for Java 설정](https://developers.cloudflare.com/r2/examples/aws/aws-sdk-java/)
- [Cloudflare R2 S3 API checksum 호환표](https://developers.cloudflare.com/r2/api/s3/api/)
- [AWS SDK for Java 2.x checksum 설정](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html)

`S3_PREFIX`는 원격 object key 앞에만 붙고 DB에 저장되는 논리 `storageKey`에는 포함되지 않는다. 파일이 저장된 뒤 bucket, endpoint 또는 prefix를 바꾸면 기존 파일을 같은 위치로 옮기지 않는 한 다운로드할 수 없으므로 값을 고정한다.

## 5. Local과 S3 저장 계약

| 항목 | `local` | `s3` |
|---|---|---|
| 주 사용처 | 개발 PC, 일회성 Smoke Test | Render Free와 공유 배포 환경 |
| 실제 저장 위치 | `FILE_STORAGE_ROOT` 하위 | `S3_BUCKET`의 선택한 `S3_PREFIX` 하위 |
| DB `storageKey` | 상대 논리 키 | Local과 동일한 상대 논리 키 |
| 재시작 후 보존 | 개발 PC에서는 가능, Render Free에서는 불가 | 오브젝트 스토리지 정책에 따라 보존 |
| 보안 | OS 파일 권한 필요 | 비공개 bucket과 최소 권한 키 필요 |

DB에는 파일 메타데이터와 논리 `storageKey`가 남는다. Render Free에서 `local`을 사용하면 파일만 사라져 DB 행은 존재하지만 다운로드가 `OWNERSHIP_DOCUMENT_READ_ERROR`로 실패하는 불일치가 생길 수 있다. 이 상태를 정상 운영으로 간주하면 안 된다.

파일 저장과 MySQL 트랜잭션은 하나의 분산 트랜잭션이 아니다. 저장 후 DB 반영 실패 시 보상 삭제하고, 삭제 실패는 재시도 작업으로 처리하지만 운영 전 고아 object·누락 object 모니터링 정책을 별도로 마련해야 한다.

## 6. Render 서비스 생성

저장소에 포함된 `render.yaml`은 최종 서비스 브랜치를 `main`으로 지정한다. 따라서 배포 준비 코드가 `main`에 반영되기 전에는 Blueprint를 만들지 않는다. 병합 전 인프라 Smoke Test가 꼭 필요하면 Blueprint 대신 Web Service를 수동 생성하고 해당 준비 브랜치를 임시로 지정한다. 수동 생성 시에는 다음 값을 사용한다.

| 항목 | 값 |
|---|---|
| Service Type | Web Service |
| Runtime | Docker |
| Branch | 준비 중에는 별도 배포 브랜치, 최종본은 `main` |
| Instance Type | Free |
| Health Check Path | `/health` |
| Auto-Deploy | 준비 단계 `Off`; CI가 생기면 테스트 성공 후 배포하도록 `checksPass` 검토 |

1. GitHub 저장소를 Render에 연결한다.
2. `render.yaml`이 들어간 커밋이 `main`에 반영된 것을 확인한 뒤 Blueprint를 생성한다. 현재 Blueprint는 의도하지 않은 즉시 배포를 막기 위해 `autoDeployTrigger: off`로 고정되어 있으므로 검증할 정확한 커밋을 수동 배포한다.
3. 위 환경 변수를 등록하되 비밀값은 Dashboard에서만 입력한다. Blueprint의 `JWT_SECRET`은 `generateValue`로 생성되며, 서비스를 수동 생성한다면 최소 32 random bytes 이상의 값을 직접 생성한다. `sync: false`는 Blueprint를 처음 생성할 때만 값을 입력받고 기존 Blueprint 갱신에서는 해당 값을 변경하지 않으므로, 이후 변경은 Dashboard에서 직접 수행한다.
4. 외부 MySQL과 S3 bucket을 먼저 준비한다.
5. 최초 배포 로그에서 애플리케이션이 Render의 `PORT`로 시작했는지 확인한다.
6. `https://SERVICE.onrender.com/health`가 인증 없이 `200`과 `{"status":"UP"}`을 반환하는지 확인한다.

`/health`는 프로세스가 HTTP 요청을 받을 수 있는지만 확인하는 최소 liveness endpoint이며 DB나 S3 연결 상태를 검사하지 않는다. 비밀값이나 상세 내부 정보도 노출하지 않는다. DB와 S3는 아래 API Smoke Test로 별도 확인한다. Render HTTP Health Check는 5초 안의 `2xx` 또는 `3xx` 응답을 정상으로 본다.

## 7. 배포 후 검증

### 기본 API

1. `/health`가 `200`과 `{"status":"UP"}`인지 확인한다.
2. 도시농부와 농가 계정의 가입·로그인·JWT 인증을 확인한다.
3. 허용한 프론트 Origin의 preflight와 실제 API 요청이 성공하는지 확인한다.
4. 허용하지 않은 Origin은 CORS 응답 헤더를 받지 못하는지 확인한다.
5. Render 로그에 DB 비밀번호, JWT 비밀키, S3 비밀키, Access Token이 출력되지 않는지 확인한다.

### DB 재시작 검증

1. 데모 계정을 하나 만든다.
2. Render에서 동일 커밋을 수동 재배포한다.
3. 재배포 후 같은 계정으로 로그인한다.
4. DB 데이터가 유지되는지 확인한다.

### 업로드 재시작 검증

1. `FARM` 계정과 농가 프로필을 준비한다.
2. `POST /api/farm-profiles/me/ownership-submissions`에 작은 PDF 또는 이미지를 업로드한다.
3. 응답의 문서 ID로 `GET /api/farm-ownership-documents/{documentId}/file`을 호출해 내려받은 파일의 크기와 내용을 확인한다.
4. Render에서 동일 커밋을 수동 재배포한다.
5. 같은 문서 ID를 다시 내려받아 내용이 같은지 확인한다.
6. 15분 이상 수신 트래픽이 없어 Free 서비스가 휴면한 뒤 첫 요청의 Cold Start를 기다리고 다시 다운로드한다.

재배포와 휴면 뒤에도 파일이 유지되어야 S3 영속성 검증을 통과한 것이다. `local` 설정으로 이 검증을 통과할 것으로 기대하면 안 된다.

## 8. 최종 공개 체크리스트

- [ ] `backend-2`까지 `develop`에 병합했다.
- [ ] 검증을 마친 `develop`을 `main`에 병합했다.
- [ ] 병합된 정확한 커밋에서 전체 테스트가 통과했다.
- [ ] `render.yaml`이 `main`에 있고 Auto-Deploy가 준비 단계에서는 꺼져 있다.
- [ ] 외부 MySQL JDBC URL에서 `createDatabaseIfNotExist`를 제거했다.
- [ ] 최초 스키마 확인 뒤 `JPA_DDL_AUTO=validate`로 전환했다.
- [ ] `JWT_SECRET`을 32 random bytes 이상으로 새로 생성했다.
- [ ] `JWT_ISSUER`와 실제 API URL이 일치한다.
- [ ] `CORS_ALLOWED_ORIGINS`를 실제 프론트 Origin으로 제한했다.
- [ ] Render에서 `FILE_STORAGE_TYPE=s3`를 사용한다.
- [ ] S3 bucket이 비공개이고 전용 키가 최소 권한만 가진다.
- [ ] `/health`가 공개 상태에서 정상 응답한다.
- [ ] DB와 업로드 파일의 재배포·Cold Start 이후 보존을 확인했다.
- [ ] 저장소, 배포 설정, 로그 어디에도 실제 비밀값이 없다.

Render Free의 Cold Start와 비영구 로컬 파일시스템은 장애가 아니라 상품 제약이다. 사용자에게 첫 요청 지연을 안내하고, 실제 운영으로 전환할 때는 유료 인스턴스·백업·모니터링·마이그레이션·비밀키 교체 정책을 함께 마련한다.
