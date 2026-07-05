# ragtest

Java 21, Spring Boot 3.5.x 기반 실제 공공데이터 청년정책 RAG 테스트 프로젝트입니다.

이 프로젝트의 기본 흐름은 샘플 정책 데이터를 사용하지 않습니다. 관리자 기능으로 실제 공공데이터 API에서 정책을 먼저 수집하고, 청년 관련 정책만 `vector_store`에 인덱싱한 뒤 사용자가 이미 인덱싱된 실제 정책 데이터에 질문합니다.

문제 원인 분석과 해결 과정은 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)에 정리되어 있습니다.

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- PostgreSQL + pgvector
- OpenAI Chat / Embedding
- Spring Boot static resources

## RAG 동작 방식

RAG는 질문 시점에 외부 공공데이터 API를 매번 호출하지 않습니다.

1. 관리자 기능 또는 스케줄러가 실제 공공데이터 API를 호출합니다.
2. 목록조회와 가능한 상세조회를 합쳐 정책 데이터를 정규화합니다.
3. 청년 관련 여부를 판정해 DB에 저장합니다.
4. 별도 관리자 인덱싱 Job이 `youthRelated=true`인 실제 정책만 벡터 인덱싱합니다.
5. 사용자는 인덱싱된 실제 정책 데이터 안에서 자연어로 질문합니다.

검색 대상 조건은 `sourceType != SAMPLE`, `youthRelated = true`, `indexed = true`입니다.

## Environment Variables

저장소 루트의 `.env.example`을 `.env`로 복사한 뒤 실제 키를 입력합니다. `.env`는 Git에 커밋되지 않습니다.

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

`.env` 내용:

```properties
OPENAI_API_KEY=실제_OPENAI_API_KEY
OPENAI_CHAT_MODEL=gpt-4.1-mini
DATA_GO_KR_SERVICE_KEY=실제_공공데이터포털_인증키

# 선택 사항
# DATA_GO_KR_PUBLIC_SERVICE_KEY=
# DATA_GO_KR_LOCAL_WELFARE_KEY=
# DATA_GO_KR_CENTRAL_WELFARE_KEY=
# YOUTH_CENTER_API_KEY=
```

공공데이터포털 키는 `DATA_GO_KR_SERVICE_KEY` 하나만 설정해도 세 API가 모두 동작하도록 fallback됩니다. API별 키를 따로 설정하면 해당 API별 키가 우선 적용됩니다.

`application.yaml`은 다음 구조를 사용합니다.

```yaml
spring:
  config:
    import: optional:file:.env[.properties]

external-api:
  youth-center:
    api-key: ${YOUTH_CENTER_API_KEY:}
  data-go-kr:
    service-key: ${DATA_GO_KR_SERVICE_KEY:}
    public-service-key: ${DATA_GO_KR_PUBLIC_SERVICE_KEY:${DATA_GO_KR_SERVICE_KEY:}}
    local-welfare-key: ${DATA_GO_KR_LOCAL_WELFARE_KEY:${DATA_GO_KR_SERVICE_KEY:}}
    central-welfare-key: ${DATA_GO_KR_CENTRAL_WELFARE_KEY:${DATA_GO_KR_SERVICE_KEY:}}
```

## 다른 PC 또는 Mac에서 처음 실행

필수 설치 항목:

- Git
- JDK 21 (`java -version`으로 확인)
- Docker Desktop 또는 Docker Engine + Compose 플러그인

저장소를 처음 받는 경우:

```bash
git clone https://github.com/STUDIOYM-bb/ragtest.git
cd ragtest
cp .env.example .env            # macOS/Linux
# Copy-Item .env.example .env    # Windows PowerShell
```

`.env`에 `OPENAI_API_KEY`, `DATA_GO_KR_SERVICE_KEY`를 입력한 다음 PostgreSQL/pgvector를 실행합니다.

```bash
docker compose up -d
```

애플리케이션 실행:

macOS/Linux:

```bash
chmod +x gradlew
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

macOS에서 Docker Desktop을 사용한다면 Docker Desktop이 완전히 시작된 뒤 `docker compose up -d`를 실행해야 합니다.

브라우저에서 접속합니다.

```text
http://localhost:8080
```

화면에서 다음 순서로 확인합니다.

1. `환경변수 상태 확인`
2. `공공데이터 API 전체 수집 시작` 후 Job 성공 확인
3. `실제 정책 데이터 인덱싱 시작` 후 Job 성공 확인
4. `RAG 데이터 상태 확인`
5. 사용자 질문 입력 후 `질문하기`

## 기존 클론에서 최신 코드 받기

로컬 수정이 없다면 다음 순서로 갱신합니다.

```bash
git pull origin main
docker compose up -d
./gradlew bootRun                 # macOS/Linux
# .\gradlew.bat bootRun           # Windows PowerShell
```

`.env`는 pull로 생성되거나 갱신되지 않으므로, 새 환경변수가 추가되면 `.env.example`과 비교해서 직접 반영해야 합니다. PostgreSQL 데이터는 Docker named volume `ragtest-postgres-data`에 유지됩니다.

## Main APIs

```text
GET  /api/admin/config/status
GET  /api/admin/rag/status
POST /api/admin/rag/index
POST /api/admin/rag/reindex-real
POST /api/admin/rag/index-source/{sourceType}
POST /api/admin/ingest/public-service
POST /api/admin/ingest/local-welfare
POST /api/admin/ingest/central-welfare
POST /api/admin/ingest/all
GET  /api/admin/jobs/{jobId}
GET  /api/admin/jobs/latest
GET  /api/admin/jobs/running
POST /api/rag/ask
GET  /api/policies
```

## data.go.kr API 분리

- 행정안전부 대한민국 공공서비스 정보
  - Client: `PublicServiceApiClient`
  - Normalizer: `PublicServiceNormalizer`
  - SourceType: `PolicySourceType.PUBLIC_SERVICE`
  - Key: `DATA_GO_KR_PUBLIC_SERVICE_KEY`, fallback `DATA_GO_KR_SERVICE_KEY`

- 한국사회보장정보원 지자체복지서비스
  - Client: `LocalWelfareApiClient`
  - Normalizer: `LocalWelfareNormalizer`
  - SourceType: `PolicySourceType.LOCAL_WELFARE`
  - Key: `DATA_GO_KR_LOCAL_WELFARE_KEY`, fallback `DATA_GO_KR_SERVICE_KEY`

- 한국사회보장정보원 중앙부처복지서비스
  - Client: `CentralWelfareApiClient`
  - Normalizer: `CentralWelfareNormalizer`
  - SourceType: `PolicySourceType.CENTRAL_WELFARE`
  - Key: `DATA_GO_KR_CENTRAL_WELFARE_KEY`, fallback `DATA_GO_KR_SERVICE_KEY`

중앙부처복지서비스는 `NationalWelfarelistV001`, `NationalWelfaredetailedV001` 경로를 사용합니다. 지자체복지서비스는 현재 공공데이터포털 Swagger의 `LocalGovernmentWelfareInformations/LcgvWelfarelist`, `LcgvWelfaredetailed` 경로를 사용합니다.

각 수집은 목록을 조회한 뒤 정책별 상세조회 결과를 병합합니다. 상세조회 한 건이 실패해도 목록 정보로 저장을 계속하며, 실패 내용은 raw payload의 `_detailFetchError`에 남깁니다. 수집은 DB 저장까지만 수행하고 OpenAI Embedding이나 `vector_store` 인덱싱은 호출하지 않습니다.

수집과 인덱싱 API는 메모리 기반 백그라운드 Job을 시작한 뒤 `jobId`를 즉시 반환합니다. 프론트는 `/api/admin/jobs/{jobId}`를 1.5초 간격으로 조회해 진행률과 성공/실패를 표시합니다. 서버 재시작 시 메모리 Job 이력은 초기화됩니다.

기본 수집 제한은 `maxPages=1`, `pageSize=20`, `maxItems=50`이며 각각 최대 5, 100, 300입니다. 기본 인덱싱 제한은 `limit=30`, 최대 100입니다. 미인덱싱 정책이 남아 있으면 상태 화면의 `unindexedYouthPolicies`를 확인하고 인덱싱을 다시 실행합니다.

`실제 정책 데이터 재인덱싱`은 기존 `vector_store` 내용을 트랜잭션 안에서 정리하고, 실제 API 출처이면서 청년 관련인 정책만 다시 인덱싱합니다.

## 테스트 케이스

1. 공공데이터 API 전체 수집 Job 성공 후 `RAG 데이터 상태 확인`에서 출처별 청년정책과 미인덱싱 건수를 확인하고, 별도 인덱싱 Job을 실행합니다.
2. `27살이 받을 수 있는 청년 정책 알려줘` 질문 시 실제 API에서 수집된 `youthRelated=true`, `indexed=true` 정책만 sources에 표시되는지 확인합니다.
3. `경기도 수원시에 사는 27살 취업준비생이 받을 수 있는 청년 정책 알려줘` 질문 시 `region=경기도 수원시`가 추출되고 전국/경기도/수원시 적용 가능 정책만 sources에 포함되는지 확인합니다.
4. 실제 API 수집 전 질문 시 다음 메시지가 표시되는지 확인합니다.

```text
아직 수집된 청년정책 데이터가 없습니다. 관리자 테스트에서 실제 공공데이터 API 수집을 먼저 실행해주세요.
```

질문 API는 외부 공공데이터 수집이나 정책 인덱싱을 수행하지 않습니다. 질문 embedding 1회, 기존 `vector_store` 검색, 최종 Chat Completion 1회만 수행합니다.

## 테스트와 문제 해결

전체 자동 테스트:

```bash
./gradlew test                 # macOS/Linux
# .\gradlew.bat test           # Windows PowerShell
```

- `Connection refused: localhost:5432`: `docker compose ps`로 PostgreSQL 상태를 확인합니다.
- 8080 또는 5432 포트 충돌: 해당 포트를 사용하는 프로세스를 종료하거나 `application.yaml`/Compose 포트 매핑을 조정합니다.
- 공공데이터 API 401/403: 인증키 값, 해당 API 활용신청 승인 여부, 키 인코딩 상태를 공공데이터포털에서 확인합니다.
- 인덱싱 실패: `OPENAI_API_KEY`와 OpenAI API 사용 가능 상태를 확인합니다.
- 중앙부처 개발계정은 호출 한도가 작을 수 있습니다. 목록+상세조회가 각각 트래픽을 사용하므로 반복 수집에 주의합니다.

공공 API 응답 필드는 제공기관 개편으로 바뀔 수 있습니다. 특히 지자체/중앙부처 상세 응답의 지원대상·선정기준·신청방법·공식 링크 필드가 raw payload에 존재하지만 정규화 결과가 비어 있으면, 현재 활용신청에 연결된 Swagger/활용가이드의 실제 응답 필드명을 다시 대조해야 합니다.
