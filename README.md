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

저장소 루트의 `.env.example`을 `.env`로 복사한 뒤 실제 키를 입력합니다. `.env.example`은 예시 파일이므로 실제 키를 넣지 않습니다. 실제 실행 키는 프로젝트 루트의 `.env`에만 입력하고, `.env`는 Git에 커밋하지 않습니다.

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
DATA_GO_KR_SERVICE_KEY=실제_공공데이터포털_API_KEY
DATA_GO_KR_YOUTH_POLICY_KEY=실제_공공데이터포털_온통청년_API_KEY
DATA_GO_KR_YOUTH_POLICY_BASE_URL=공공데이터포털_온통청년_API_요청_URL

# 온통청년 공식 사이트 키가 있을 때만
YOUTH_CENTER_API_KEY=실제_온통청년_공식_OPEN_API_KEY
```

`.env.example`은 placeholder만 유지합니다. 실제 키를 `.env.example`에 넣으면 안 됩니다.

```properties
OPENAI_API_KEY=여기에_OPENAI_API_KEY_입력
OPENAI_CHAT_MODEL=gpt-4.1-mini

DATA_GO_KR_SERVICE_KEY=여기에_공공데이터포털_인증키_입력

# 공공데이터포털 한국고용정보원_온통청년_청년정책API를 따로 관리할 경우
# DATA_GO_KR_YOUTH_POLICY_KEY=
# DATA_GO_KR_YOUTH_POLICY_BASE_URL=

# 온통청년 공식 사이트 OPEN API 키
# YOUTH_CENTER_API_KEY=

# 선택 사항: API별로 키를 따로 관리하고 싶을 때만 사용
# DATA_GO_KR_PUBLIC_SERVICE_KEY=
# DATA_GO_KR_LOCAL_WELFARE_KEY=
# DATA_GO_KR_CENTRAL_WELFARE_KEY=
```

공공데이터포털 키는 `DATA_GO_KR_SERVICE_KEY` 하나만 설정해도 data.go.kr 계열 API에 fallback됩니다. API별 키를 따로 설정하면 해당 키가 우선 적용됩니다.

## 온통청년 API 두 종류

1. 온통청년 공식 사이트 API
   - env: `YOUTH_CENTER_API_KEY`
   - endpoint: `https://www.youthcenter.go.kr/opi/youthPlcyList.do`
   - parameter: `openApiVlak`
   - client: `YouthCenterOfficialApiClient`

2. 공공데이터포털 한국고용정보원_온통청년_청년정책API
   - env: `DATA_GO_KR_YOUTH_POLICY_KEY` 또는 fallback `DATA_GO_KR_SERVICE_KEY`
   - endpoint: `DATA_GO_KR_YOUTH_POLICY_BASE_URL`
   - parameter: `serviceKey`
   - client: `DataGoKrYouthPolicyApiClient`

공공데이터포털 키를 `YOUTH_CENTER_API_KEY`에 넣고 공식 endpoint를 호출하면 302 또는 HTML 응답이 올 수 있습니다. 이 경우 공공데이터포털 마이페이지/활용신청 상세의 실제 요청 URL을 `DATA_GO_KR_YOUTH_POLICY_BASE_URL`에 넣고, `공공데이터포털 온통청년 API 수집` 또는 `온통청년 수집 자동`을 사용해야 합니다.

공공데이터포털 온통청년 API 설정:

1. 프로젝트 루트에 `.env` 파일 생성
2. `DATA_GO_KR_YOUTH_POLICY_KEY` 또는 `DATA_GO_KR_SERVICE_KEY` 입력
3. 공공데이터포털 활용신청 상세의 요청 URL을 `DATA_GO_KR_YOUTH_POLICY_BASE_URL`에 입력
4. 서버 재시작
5. 프론트에서 `환경변수 상태 확인` 클릭
6. `dataGoKrYouthPolicyBaseUrlConfigured=true` 확인
7. `공공데이터 온통청년 원본 확인`으로 JSON/XML 응답 여부 확인
8. `온통청년 수집 자동` 또는 `공공데이터포털 온통청년 API 수집` 실행

`application.yaml`은 `optional:file:.env[.properties]`만 읽습니다. `.env.example`은 예시 파일이며 실제 키를 넣어도 실행 설정에 반영되지 않습니다. `.env`를 수정한 뒤에는 실행 중인 서버를 재시작해야 새 키가 반영됩니다.

`application.yaml`은 다음 구조를 사용합니다.

```yaml
spring:
  config:
    import: optional:file:.env[.properties]

external-api:
  youth-center:
    official-api-key: ${YOUTH_CENTER_API_KEY:}
    official-base-url: https://www.youthcenter.go.kr/opi/youthPlcyList.do
  data-go-kr:
    service-key: ${DATA_GO_KR_SERVICE_KEY:}
    youth-policy-key: ${DATA_GO_KR_YOUTH_POLICY_KEY:${DATA_GO_KR_SERVICE_KEY:}}
    youth-policy-base-url: ${DATA_GO_KR_YOUTH_POLICY_BASE_URL:}
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

`.env`에 `OPENAI_API_KEY`, `DATA_GO_KR_SERVICE_KEY`, `YOUTH_CENTER_API_KEY`를 입력한 다음 PostgreSQL/pgvector를 실행합니다.

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

1. `cp .env.example .env`
2. `.env`에 `OPENAI_API_KEY`, `DATA_GO_KR_SERVICE_KEY`, `YOUTH_CENTER_API_KEY` 입력
3. `docker compose up -d`
4. `./gradlew bootRun`
5. `http://localhost:8080` 접속
6. `환경변수 상태 확인`
7. `온통청년 수집 자동`
8. `RAG 데이터 상태 확인`
9. `실제 정책 데이터 인덱싱 시작` 또는 `POST /api/admin/rag/index-source/YOUTH_CENTER?limit=30` 실행
10. 사용자 질문 입력 후 `질문하기`

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
POST /api/admin/ingest/youth-center
POST /api/admin/ingest/youth-center-official
POST /api/admin/ingest/youth-policy-data-go-kr
POST /api/admin/ingest/all
GET  /api/admin/jobs/{jobId}
GET  /api/admin/jobs/latest
GET  /api/admin/jobs/running
POST /api/rag/ask
POST /api/admin/debug/search-candidates
GET  /api/admin/debug/youth-center/raw?query=청년&pageIndex=1&display=10
GET  /api/admin/debug/youth-center-official/raw?query=청년&pageIndex=1&display=10
GET  /api/admin/debug/youth-policy-data-go-kr/raw?query=청년&pageNo=1&numOfRows=10
GET  /api/policies?keyword=&region=&sourceType=&youthOnly=true&indexedOnly=false&sampleExcluded=true
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

- 온통청년 공식 사이트 청년정책 Open API
  - Client: `YouthCenterOfficialApiClient`
  - Normalizer: `YouthPolicyNormalizer`
  - SourceType: `PolicySourceType.YOUTH_CENTER`
  - Key: `YOUTH_CENTER_API_KEY`
  - Parameter: `openApiVlak`
  - 공식 목록 endpoint: `https://www.youthcenter.go.kr/opi/youthPlcyList.do`

- 공공데이터포털 한국고용정보원_온통청년_청년정책API
  - Client: `DataGoKrYouthPolicyApiClient`
  - Normalizer: `YouthPolicyNormalizer`
  - SourceType: `PolicySourceType.YOUTH_CENTER`
  - Key: `DATA_GO_KR_YOUTH_POLICY_KEY`, fallback `DATA_GO_KR_SERVICE_KEY`
  - Parameter: `serviceKey`
  - Base URL: `DATA_GO_KR_YOUTH_POLICY_BASE_URL`

`DATA_GO_KR_YOUTH_POLICY_KEY` 또는 `DATA_GO_KR_SERVICE_KEY`를 공식 온통청년 엔드포인트에 보내지 않습니다. 두 API는 이름은 비슷하지만 endpoint와 인증 파라미터가 다릅니다.

온통청년 연동 기준은 [공식 Open API 이용안내](https://www.youthcenter.go.kr/cmnFooter/openapiIntro/oaiGuide)와 [공식 API 문서](https://www.youthcenter.go.kr/cmnFooter/openapiIntro/oaiDoc)에서 확인할 수 있습니다.

중앙부처복지서비스는 `NationalWelfarelistV001`, `NationalWelfaredetailedV001` 경로를 사용합니다. 지자체복지서비스는 현재 공공데이터포털 Swagger의 `LocalGovernmentWelfareInformations/LcgvWelfarelist`, `LcgvWelfaredetailed` 경로를 사용합니다.

각 수집은 목록을 조회한 뒤 정책별 상세조회 결과를 병합합니다. 상세조회 한 건이 실패해도 목록 정보로 저장을 계속하며, 실패 내용은 raw payload의 `_detailFetchError`에 남깁니다. 수집은 DB 저장까지만 수행하고 OpenAI Embedding이나 `vector_store` 인덱싱은 호출하지 않습니다.

수집과 인덱싱 API는 메모리 기반 백그라운드 Job을 시작한 뒤 `jobId`를 즉시 반환합니다. 프론트는 `/api/admin/jobs/{jobId}`를 1.5초 간격으로 조회해 진행률과 성공/실패를 표시합니다. 서버 재시작 시 메모리 Job 이력은 초기화됩니다.

기본 수집 제한은 `maxPages=3`, `pageSize=50`, `maxItems=150`이며 각각 최대 5, 100, 300입니다. 기본 인덱싱 제한은 `limit=30`, 최대 100입니다. 관리자 화면에서 값을 줄여 빠르게 검증할 수 있습니다. 미인덱싱 정책이 남아 있으면 상태 화면의 `unindexedYouthPolicies`를 확인하고 인덱싱을 다시 실행합니다.

## 하이브리드 검색과 조건 매칭

질문은 규칙 기반으로 지역, 나이, 대상, 학업상태, 취업상태, 생애단계, 경제상태, 관심분야와 검색 키워드로 변환됩니다. 요청 JSON에 `region`, `age`, `employmentStatus`를 직접 보내면 직접 입력값이 우선합니다. 추출되지 않은 값은 `null`이며 필터에 사용하지 않습니다.

후보는 다음 두 경로에서 생성한 뒤 `policyId`로 병합합니다.

1. 질문 embedding 1회로 pgvector 후보 조회
2. 정책명, 요약, 지원대상, 선정기준, 신청방법, 카테고리, 지역에 대한 DB 키워드 후보 조회
3. 지역·나이·대상 상태의 명확한 불일치는 제외
4. 정보가 불충분하면 제외하지 않고 `CHECK_REQUIRED`로 분류
5. 출처, 벡터/키워드 동시 적중, 사용자 조건, 관심분야를 점수화해 재정렬

관리자 화면의 `검색 후보 디버그`는 vector/keyword/merged/final/excluded 후보와 점수·사유를 표시합니다. 정책 목록에서는 키워드, 지역, sourceType, 청년 관련 여부, 인덱싱 여부, SAMPLE 제외 조건으로 실제 DB 수집 상태를 확인할 수 있습니다.

## Google 검색과 결과가 다른 이유

Google은 웹 전체를 검색하지만 이 프로젝트는 관리자가 수집하고 인덱싱한 공공 API 데이터만 검색합니다. API에 없는 정책, 아직 수집하지 않은 정책, `indexed=false` 정책은 답변에 나오지 않습니다. 품질을 높이려면 수집 source와 수집량을 늘리고 미인덱싱 건수를 해소해야 합니다. 온통청년 API를 연결하면 청년정책 전용 후보가 늘어납니다. 관리자 화면의 `온통청년 수집 자동` 또는 `공공데이터포털 온통청년 API 수집`으로 DB에 저장한 뒤, `실제 정책 데이터 인덱싱 시작` 또는 `온통청년 정책만 인덱싱`을 실행해야 검색에 반영됩니다.

`실제 정책 데이터 재인덱싱`은 기존 `vector_store` 내용을 트랜잭션 안에서 정리하고, 실제 API 출처이면서 청년 관련인 정책만 다시 인덱싱합니다.

## 테스트 케이스

1. `GET /api/admin/config/status`에서 `dataGoKrYouthPolicyConfigured=true` 또는 `youthCenterOfficialConfigured=true`인지 확인하고, `dataGoKrYouthPolicyBaseUrlConfigured`도 확인합니다.
2. 공식 API raw 확인: `GET /api/admin/debug/youth-center-official/raw?query=청년&pageIndex=1&display=10`. 정상 XML이면 공식 API 수집 가능, 302이면 공식 API 키/endpoint 문제 또는 공공데이터포털 키 혼동입니다.
3. 공공데이터포털 온통청년 raw 확인: `GET /api/admin/debug/youth-policy-data-go-kr/raw?query=청년&pageNo=1&numOfRows=10`. `DATA_GO_KR_YOUTH_POLICY_BASE_URL`이 없으면 설정 오류가 반환됩니다.
4. `POST /api/admin/ingest/youth-policy-data-go-kr?maxPages=1&pageSize=20&maxItems=30`로 공공데이터포털 온통청년 데이터를 수집합니다.
5. `POST /api/admin/ingest/youth-center?maxPages=1&pageSize=20&maxItems=30`은 자동 선택입니다. data.go.kr 설정이 있으면 data.go.kr을 우선 사용하고, 없으면 공식 API를 사용합니다.
6. `GET /api/admin/rag/status`에서 `bySourceType.YOUTH_CENTER.total > 0`, `youthCenterCollectedCount > 0`인지 확인합니다.
7. `POST /api/admin/rag/index-source/YOUTH_CENTER?limit=30` 실행 후 `bySourceType.YOUTH_CENTER.indexed`가 증가하는지 확인합니다.
8. `경기도에 사는 20살 청년이 받을 수 있는 정책 알려줘` 질문 시 `YOUTH_CENTER` 정책이 후보 또는 sources에 포함될 수 있고, 지역/나이 조건이 명확히 맞지 않는 정책은 제외되는지 확인합니다.
9. 실제 API 수집 전 질문 시 다음 메시지가 표시되는지 확인합니다.

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
- 온통청년 공식 API 302/HTML 응답: 공공데이터포털 키를 `YOUTH_CENTER_API_KEY`에 넣은 경우일 수 있습니다. 공공데이터포털 온통청년 API 설정(`DATA_GO_KR_YOUTH_POLICY_BASE_URL`, `DATA_GO_KR_YOUTH_POLICY_KEY`)을 사용하세요.
- 공공데이터포털 온통청년 설정 오류: `DATA_GO_KR_YOUTH_POLICY_BASE_URL`은 `.env.example`이 아니라 `.env`에 넣고 서버를 재시작해야 합니다. 활용신청 상세의 요청 URL 또는 Swagger 요청 주소를 사용합니다.
- 온통청년 응답 파싱 실패 또는 HTML 응답: 관리자 화면의 `온통청년 공식 원본 확인` 또는 `공공데이터 온통청년 원본 확인`을 눌러 `apiType`, `contentType`, `looksLikeXml`, `looksLikeJson`, `looksLikeHtml`, `bodyPreview`, `requestUrlMasked`를 확인합니다.
- 인덱싱 실패: `OPENAI_API_KEY`와 OpenAI API 사용 가능 상태를 확인합니다.
- 중앙부처 개발계정은 호출 한도가 작을 수 있습니다. 목록+상세조회가 각각 트래픽을 사용하므로 반복 수집에 주의합니다.

공공 API 응답 필드는 제공기관 개편으로 바뀔 수 있습니다. 특히 지자체/중앙부처 상세 응답의 지원대상·선정기준·신청방법·공식 링크 필드가 raw payload에 존재하지만 정규화 결과가 비어 있으면, 현재 활용신청에 연결된 Swagger/활용가이드의 실제 응답 필드명을 다시 대조해야 합니다.
