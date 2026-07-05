# ragtest 트러블슈팅 기록

이 문서는 `ragtest`를 실제 공공데이터 기반 청년정책 RAG 서비스로 전환하면서 발생했거나 코드 검증 중 발견한 문제를 기록한다. 각 항목은 증상, 원인 탐색, 분석, 검토한 해결책, 최종 해결, 적용 효과 순서로 정리했다.

## 최종 구조 요약

```text
관리자 수집 요청
  -> 즉시 jobId 반환
  -> 백그라운드에서 공공 API 목록/상세 조회
  -> Policy 저장 및 youthRelated 판정
  -> indexed=false 유지

관리자 인덱싱 요청
  -> 즉시 jobId 반환
  -> 백그라운드에서 제한 수량 조회
  -> 정책별 OpenAI Embedding 생성
  -> vector_store 저장
  -> indexed=true 변경

사용자 질문
  -> 질문 조건 추출
  -> 질문 embedding 1회
  -> 기존 vector_store 검색
  -> 실제 Policy 재검증 및 조건 필터링
  -> Chat Completion 1회
```

질문 요청은 공공데이터 수집과 정책 인덱싱을 수행하지 않는다.

## 주요 문제와 최종 해결책

| 문제 | 핵심 원인 | 최종 해결 |
|---|---|---|
| API 키가 미설정으로 표시됨 | 프로젝트 루트에 `.env`가 없거나 서버를 재시작하지 않음 | `.env.example`을 `.env`로 복사하고 실제 키 입력 후 완전 재시작 |
| 서버 실행 시 8080 충돌 | 이전 `ragtest` Java 프로세스가 계속 포트를 점유 | 점유 PID와 명령행을 확인한 후 해당 프로젝트 프로세스만 종료 |
| 전체 수집 버튼이 3분 이상 응답 없음 | 수집·상세조회·DB 저장·Embedding·인덱싱을 한 HTTP 요청에서 실행 | 수집/인덱싱 분리 + 메모리 Job + 1.5초 polling |
| 어디서 지연되는지 알 수 없음 | 작업 단계와 진행 상태가 외부에 노출되지 않음 | Job 타입, 상태, 진행률, 메시지, 결과, 오류, 시간을 API로 제공 |
| 기존 SAMPLE 또는 오래된 벡터가 검색될 가능성 | vector_store와 Policy 상태가 분리되어 있고 과거 데이터가 남음 | SQL metadata 필터와 Policy 재조회 필터를 이중 적용하고 재인덱싱으로 정리 |
| 수원 질문에 성남 정책이 포함될 수 있음 | 광역단체 토큰 하나만 같아도 지역이 일치한다고 판단 | 행정구역 계층 비교로 변경하여 전국/경기도/수원시만 허용 |
| 복지 API 응답에서 정책 목록을 못 찾을 수 있음 | API마다 `data`, `item`, `wantedList` 등 wrapper 구조가 다름 | ID와 정책명을 가진 객체를 재귀적으로 찾는 공통 추출 로직 적용 |
| PostgreSQL JSON metadata의 policyId 파싱 실패 가능 | JDBC가 JSON을 문자열이 아닌 `PGobject`로 반환할 수 있음 | SQL에서 `metadata::text`로 명시 변환 후 JSON 파싱 |
| 다른 PC/macOS에서 실행 절차가 불명확 | 로컬 설정, 실행 권한, Compose 파일이 환경마다 달랐음 | `.env.example`, 단일 `compose.yaml`, Gradle Wrapper 실행 권한, OS별 명령 문서화 |

## 1. 환경변수가 설정되지 않았다고 표시된 문제

### 증상

관리자 화면에서 다음 값이 모두 `미설정`으로 표시됐다.

- `OPENAI_API_KEY`
- `DATA_GO_KR_SERVICE_KEY`
- API별 공공데이터 키

수집 버튼을 누르면 다음과 같은 오류가 발생했다.

```text
DATA_GO_KR_PUBLIC_SERVICE_KEY 또는 DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.
```

### 원인 탐색

다음을 확인했다.

1. 실행 작업 디렉터리가 저장소 루트인지 확인
2. 저장소 루트의 `.env` 존재 여부 확인
3. `application.yaml`의 `spring.config.import` 확인
4. 현재 실행 중인 Java 프로세스가 어느 프로젝트에서 시작됐는지 확인

확인 결과 작업 디렉터리는 올바르지만 프로젝트 루트에 `.env`가 없었다.

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

여기서 `optional:`은 `.env`가 없어도 서버 시작을 허용한다. 따라서 서버는 정상 실행되지만 값은 빈 문자열이 된다.

### 검토한 방법

1. `application.yaml`에 키를 직접 작성
   - 즉시 동작하지만 비밀키가 Git에 포함될 위험이 있어 제외했다.
2. IntelliJ Run Configuration에만 환경변수 등록
   - 로컬에서는 동작하지만 다른 PC, 터미널 실행, CI에서 재현하기 어렵다.
3. 저장소 루트 `.env` 사용
   - Git에서는 제외하면서 IntelliJ와 Gradle 실행이 같은 값을 사용할 수 있다.

### 최종 해결

세 번째 방법을 기본 방식으로 선택했다.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
notepad .env
```

macOS/Linux:

```bash
cp .env.example .env
```

최소 설정:

```properties
OPENAI_API_KEY=실제키
DATA_GO_KR_SERVICE_KEY=실제키
```

`.env` 생성 또는 수정 후에는 실행 중인 서버를 완전히 종료하고 다시 실행해야 한다. Spring의 환경 설정은 애플리케이션 시작 시 읽히므로 브라우저 새로고침만으로 반영되지 않는다.

### 효과

- 비밀키를 소스에 하드코딩하지 않는다.
- `DATA_GO_KR_SERVICE_KEY` 하나로 세 공공 API가 동작한다.
- API별 키가 있으면 해당 키가 공통 키보다 우선한다.
- IntelliJ, Gradle, Windows, macOS에서 같은 설정 방식을 사용할 수 있다.

## 2. 포트 8080 충돌 문제

### 증상

IntelliJ에서 애플리케이션을 실행했지만 다음 오류로 종료됐다.

```text
Web server failed to start. Port 8080 was already in use.
```

### 원인 탐색

Windows에서 다음 기준으로 조사했다.

1. `Get-NetTCPConnection`으로 8080 LISTEN 프로세스 확인
2. `OwningProcess`로 PID 확인
3. `Win32_Process.CommandLine`로 해당 PID가 현재 `ragtest`인지 검증

실제 원인은 이전 `bootRun`에서 실행된 `ragtest` Java 프로세스가 종료되지 않은 상태에서 IntelliJ가 두 번째 인스턴스를 실행한 것이었다.

### 주의한 점

PID만 보고 무조건 종료하면 다른 사용자의 서버나 다른 프로젝트를 종료할 수 있다. 따라서 프로세스 명령행에 현재 저장소 경로와 `RagtestApplication`이 포함됐는지 확인한 후 종료했다.

### 최종 해결

기존 `ragtest` 프로세스만 종료하고 8080 포트가 해제됐는지 다시 확인했다. 이후 IntelliJ에서 단일 인스턴스만 실행했다.

수동 확인 명령:

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
Get-CimInstance Win32_Process -Filter "ProcessId=<PID>"
Stop-Process -Id <PID>
```

### 효과

- 포트 변경으로 문제를 우회하지 않고 중복 실행의 실제 원인을 제거했다.
- 애플리케이션 주소를 계속 `http://localhost:8080`으로 유지했다.

## 3. 전체 수집 요청이 몇 분 동안 응답하지 않는 문제

### 증상

`공공데이터 API 전체 수집 및 인덱싱` 버튼을 누르면 3분 이상 HTTP 응답이 없었다. 브라우저에는 작업 단계가 표시되지 않아 서버가 멈춘 것처럼 보였다.

### 기존 실행 경로 분석

한 요청에서 다음 작업을 순차 실행했다.

1. 행정안전부 목록조회
2. 정책별 상세조회
3. 지자체복지 목록/상세조회
4. 중앙부처복지 목록/상세조회
5. Policy와 raw payload 저장
6. 정책별 OpenAI Embedding 호출
7. vector_store 저장

외부 HTTP 호출 횟수가 많고 각 API의 응답시간이 합산되므로 요청 시간이 길어지는 것이 정상적인 구조였다. 단순히 프론트 버튼에 `수집 중...`을 표시하거나 HTTP timeout을 늘리는 방식은 지연 원인을 해결하지 못한다.

### 검토한 방법

1. HTTP timeout만 증가
   - 연결 종료는 줄지만 사용자 경험과 장애 분석은 개선되지 않는다.
2. 수집량만 감소
   - 테스트 시간은 줄지만 구조적 결합은 남는다.
3. 수집과 인덱싱을 분리하고 동기 API 유지
   - 병목 구분은 가능하지만 각 요청은 여전히 오래 열린다.
4. 수집/인덱싱 분리 + 백그라운드 Job + polling
   - HTTP 요청을 즉시 종료하고 단계, 진행률, 실패 원인을 확인할 수 있다.

### 가장 적합했던 방법

네 번째 방법을 선택했다. 오래 걸리는 관리자 작업과 사용자 HTTP 요청의 생명주기를 분리하는 것이 근본 해결책이기 때문이다.

### 최종 해결

`AdminJobManager`가 작업을 ExecutorService에서 실행한다. 시작 API는 UUID `jobId`를 바로 반환한다.

```json
{
  "success": true,
  "data": {
    "jobId": "...",
    "status": "RUNNING",
    "progressPercent": 0,
    "message": "공공데이터 API 전체 수집 작업을 시작했습니다."
  },
  "message": "작업 시작"
}
```

프론트는 1.5초마다 다음 API를 호출한다.

```text
GET /api/admin/jobs/{jobId}
```

Job에는 다음 상태를 저장한다.

- 타입
- `RUNNING`, `SUCCESS`, `FAILED`
- 진행률
- 현재 단계 메시지
- 결과 또는 오류
- 시작/종료 시간

### 검증 결과

실제 키를 사용하는 제한된 테스트에서 다음 결과를 확인했다.

- 수집 Job 시작 응답: 83ms
- 행정안전부 정책 1건 수집: `SUCCESS`
- 수집 결과의 `indexedCount`: 0
- 인덱싱 Job 시작 응답: 77ms
- 정책 1건 인덱싱: `SUCCESS`

### 효과

- 브라우저 요청이 수 분 동안 열린 상태로 남지 않는다.
- 수집과 인덱싱 병목을 구분할 수 있다.
- 사용자는 진행률과 현재 API 단계를 확인할 수 있다.
- 실패 시 HTTP timeout 대신 Job의 `errorMessage`에서 원인을 확인할 수 있다.

## 4. 수집과 인덱싱이 결합돼 있던 문제

### 원인

각 ingest controller 메서드가 수집 서비스 호출 직후 `PolicyIndexingService`를 호출했다. 따라서 공공데이터 키만 점검하려 해도 OpenAI 키와 Embedding API 상태에 영향을 받았다.

### 최종 해결

API 책임을 다음과 같이 분리했다.

```text
POST /api/admin/ingest/*
  공공 API 호출 + DB 저장 + youthRelated 판정만 수행

POST /api/admin/rag/index
  실제 청년정책 중 indexed=false 정책만 인덱싱

POST /api/admin/rag/reindex-real
  기존 벡터 정리 + 실제 청년정책 재인덱싱
```

수집된 청년정책은 `indexed=false`로 변경한다. 기존 벡터가 남아 있더라도 RAG의 Policy 재조회 조건에서 제외된다. 이후 인덱싱 Job이 해당 정책의 기존 벡터를 삭제하고 새 벡터를 삽입한 후 `indexed=true`로 변경한다.

### 효과

- 수집 API는 OpenAI API를 호출하지 않는다.
- OpenAI 장애가 공공데이터 저장을 막지 않는다.
- `youthRelatedPolicies`, `indexedYouthPolicies`, `unindexedYouthPolicies`로 단계별 상태를 확인할 수 있다.

## 5. 작업량이 너무 커지는 문제

### 원인

고정 상수로 여러 페이지와 최대 100건 이상을 처리하고, 목록마다 상세조회까지 수행했다. 중앙부처 API는 개발계정 호출 한도도 작을 수 있어 반복 테스트 비용이 컸다.

### 최종 해결

수집 옵션을 `IngestOptions`로 분리하고 입력값을 안전 범위로 제한했다.

| 파라미터 | 기본값 | 최대값 |
|---|---:|---:|
| `maxPages` | 1 | 5 |
| `pageSize` | 20 | 100 |
| `maxItems` | 50 | 300 |

인덱싱은 기본 30건, 최대 100건으로 제한했다.

```text
POST /api/admin/ingest/all?maxPages=1&pageSize=20&maxItems=50
POST /api/admin/rag/index?limit=30
```

재인덱싱 시에는 모든 실제 청년정책의 `indexed` 상태와 vector_store를 일관되게 정리한 뒤 제한된 수량만 다시 인덱싱한다. 남은 정책은 다음 Job에서 처리한다.

### 효과

- API 트래픽과 OpenAI 비용을 예측할 수 있다.
- 작은 단위로 실패 정책을 찾기 쉽다.
- 상태 화면의 미인덱싱 건수를 기준으로 반복 실행할 수 있다.

## 6. SAMPLE과 기존 vector_store 데이터 오염 문제

### 문제

과거 샘플 정책과 중복 벡터가 DB에 남아 있을 수 있었다. vector_store 검색 결과만 신뢰하면 metadata가 오래됐거나 Policy 상태가 변경된 데이터가 sources에 포함될 수 있다.

### 검토한 방법

1. 프론트에서만 SAMPLE 숨김
   - API 직접 호출과 벡터 검색에는 효과가 없다.
2. vector_store metadata만 필터링
   - 오래된 metadata나 누락된 metadata에 취약하다.
3. metadata 필터 + Policy DB 재조회 조건
   - 저장소 양쪽에서 검증할 수 있다.

### 최종 해결

세 번째 방법을 적용했다.

벡터 검색 조건:

```text
sourceType != SAMPLE
youthRelated = true
indexed = true
```

검색 결과의 `policyId`로 Policy를 다시 조회할 때도 같은 조건을 확인한다. 재인덱싱 Job은 기존 vector_store를 정리하고 실제 청년정책만 다시 삽입한다.

### 효과

- 기존 SAMPLE이 DB에 남아 있어도 RAG sources에 포함되지 않는다.
- 정책이 청년 대상에서 제외되거나 미인덱싱 상태로 바뀌면 오래된 벡터도 사용되지 않는다.

## 7. PostgreSQL JSON metadata 파싱 문제

### 문제와 분석

`JdbcTemplate.queryForList()`로 PostgreSQL `json` 컬럼을 조회하면 드라이버 설정에 따라 문자열 대신 `PGobject`가 반환될 수 있다. 이를 Jackson의 일반 객체 변환에 맡기면 JSON 내부의 `policyId`가 아니라 `PGobject` 자체 구조를 변환할 가능성이 있었다.

### 최종 해결

SQL 조회 시 JSON을 명시적으로 텍스트로 변환했다.

```sql
select content, metadata::text as metadata
from vector_store
```

이후 Jackson으로 문자열 JSON을 파싱해 `policyId`를 읽는다.

### 효과

- PostgreSQL JDBC 반환 타입 차이에 영향을 덜 받는다.
- 벡터 결과와 Policy 엔티티 연결이 안정적으로 동작한다.

## 8. 지역 필터가 다른 시 정책을 허용하던 문제

### 증상

질문 지역이 `경기도 수원시`일 때 `경기도 성남시` 정책도 광역단체 토큰이 같다는 이유로 통과할 수 있었다.

### 원인

기존 비교는 요청과 정책 지역의 토큰 중 하나라도 같으면 일치로 판단했다. `경기도`가 같다는 사실만으로 서로 다른 시를 구분하지 못했다.

### 최종 해결

행정구역 계층을 기준으로 비교했다.

- `전국`: 허용
- `경기도`: 수원시에 적용되는 상위 지역이므로 허용
- `수원시`, `경기도 수원시`: 허용
- `경기도 성남시`: 하위 지역이 다르므로 제외
- 질문에 지역이 없으면 지역 필터를 적용하지 않음

행정구역 접미사(`특별시`, `광역시`, `도`, `시`, `군`, `구`)를 제거한 이름도 함께 비교한다.

### 효과

수원 질문에서 다른 시 전용 정책이 sources에 섞이는 오탐을 줄였다.

## 9. 실제 공공 API endpoint와 응답 wrapper 문제

### endpoint 확인

중앙부처복지서비스의 예전 경로에는 `V001`이 빠져 있었다. 최신 경로를 다음과 같이 반영했다.

```text
/NationalWelfareInformationsV001/NationalWelfarelistV001
/NationalWelfareInformationsV001/NationalWelfaredetailedV001
```

지자체복지서비스는 공공데이터포털의 실제 Swagger 경로를 사용한다.

```text
/LocalGovernmentWelfareInformations/LcgvWelfarelist
/LocalGovernmentWelfareInformations/LcgvWelfaredetailed
```

### 응답 구조 분석

API마다 목록 wrapper가 `data`, `item`, `wantedList`처럼 다를 수 있다. 특정 wrapper 이름만 찾으면 정상 XML을 받아도 목록이 0건으로 처리될 수 있었다.

### 최종 해결

JSON/XML 트리를 재귀 탐색하면서 다음 필드를 직접 가진 객체를 정책 item으로 판단했다.

- ID: `서비스ID`, `serviceId`, `servId`, `id`
- 이름: `서비스명`, `serviceName`, `servNm`, `title`

상세조회 실패는 전체 수집을 중단하지 않고 목록 데이터로 저장하며 `_detailFetchError`를 raw payload에 기록한다.

### 효과

- 세 API의 서로 다른 wrapper 구조를 같은 수집 흐름에서 처리한다.
- 단일 상세조회 장애가 전체 Job 실패로 확대되는 것을 줄였다.

## 10. 공공데이터 인증키 fallback과 인코딩 문제

### 문제

공공데이터포털은 인코딩된 인증키와 디코딩된 인증키를 제공할 수 있다. 이미 `%2F` 등이 포함된 키를 다시 URL encode하면 `%252F`가 되어 인증 실패가 발생할 수 있다.

### 최종 해결

- 이미 `%xx` 형태를 포함한 키는 그대로 사용
- 디코딩 키는 URL encode 후 사용
- 공통 키 하나로 세 Client가 동작하도록 fallback 구성
- API별 키가 설정되면 해당 값을 우선 사용

```yaml
public-service-key: ${DATA_GO_KR_PUBLIC_SERVICE_KEY:${DATA_GO_KR_SERVICE_KEY:}}
local-welfare-key: ${DATA_GO_KR_LOCAL_WELFARE_KEY:${DATA_GO_KR_SERVICE_KEY:}}
central-welfare-key: ${DATA_GO_KR_CENTRAL_WELFARE_KEY:${DATA_GO_KR_SERVICE_KEY:}}
```

### 효과

- 다른 PC에서 키 형식을 잘못 선택해 발생하는 인증 오류 가능성을 줄였다.
- 필요할 때 API별 승인 키를 독립적으로 교체할 수 있다.

## 11. 크로스 플랫폼 실행 문제

### 발견한 문제

- `compose.yaml`과 `docker-compose.yml`이 동시에 있어 Docker Compose가 경고를 출력했다.
- macOS/Linux에서 `gradlew` 실행 권한이 없을 수 있었다.
- `.env`는 보안상 Git에 포함되지 않으므로 clone 직후 누락되기 쉬웠다.
- Windows와 macOS의 Gradle 실행 명령이 다르다.

### 최종 해결

- Compose 파일을 `compose.yaml` 하나로 통일
- Git에 `gradlew` 실행 권한 반영
- `.env.example` 제공
- README에 Windows/macOS/Linux 명령을 분리해 작성
- PostgreSQL 데이터는 named volume `ragtest-postgres-data`에 유지

### clone 후 실행

macOS/Linux:

```bash
git clone https://github.com/STUDIOYM-bb/ragtest.git
cd ragtest
cp .env.example .env
docker compose up -d
./gradlew bootRun
```

Windows PowerShell:

```powershell
git clone https://github.com/STUDIOYM-bb/ragtest.git
Set-Location ragtest
Copy-Item .env.example .env
docker compose up -d
.\gradlew.bat bootRun
```

### 효과

Gradle이나 PostgreSQL을 프로젝트별로 별도 설치하지 않고 JDK 21, Docker, Git만으로 동일한 실행 절차를 사용할 수 있다.

## 12. clean 작업이 로그 파일 잠금으로 실패한 문제

### 증상

Windows에서 `gradlew clean`이 `build/bootRun.out`, `build/bootRun.err`를 삭제하지 못해 실패한 적이 있었다.

### 원인

기존 `bootRun` 프로세스가 build 디렉터리의 로그 파일을 계속 점유하고 있었다. 코드 컴파일 실패가 아니라 Windows 파일 잠금 문제였다.

### 최종 해결

1. 실행 중인 기존 `bootRun` 프로세스를 확인
2. 필요한 경우 애플리케이션을 정상 종료
3. 단순 코드 검증은 `gradlew test`로 계속 진행
4. 최종 검증 전에 점유 프로세스를 정리하고 `gradlew clean build` 실행

### 효과

파일 잠금과 실제 빌드 오류를 구분했고, 최종적으로 clean build 성공을 확인했다.

## 13. 질문 요청이 관리자 작업을 실행하지 않는지 검증

### 검증 방법

실제 인덱싱 데이터로 질문 전후의 관리자 Job 개수를 비교하고 sources의 sourceType을 확인했다.

질문:

```text
27살이 받을 수 있는 청년 정책 알려줘
```

결과:

- 질문 성공
- sources 3건
- SAMPLE source 0건
- 질문 전후 관리자 Job 개수 변화 없음

### 결론

질문 시점에는 다음 두 OpenAI 호출만 발생한다.

1. 사용자 질문 embedding 1회
2. 최종 답변 Chat Completion 1회

공공데이터 수집, 정책별 embedding, vector_store 재인덱싱은 발생하지 않는다.

## 운영 점검 순서

문제가 발생하면 다음 순서로 확인한다.

1. `docker compose ps`로 PostgreSQL 확인
2. 관리자 `환경변수 상태 확인`
3. `GET /api/admin/jobs/running`으로 중복 작업 확인
4. 수집 Job 실행 및 `FAILED`의 `errorMessage` 확인
5. RAG 상태의 `youthRelatedPolicies`, `indexedYouthPolicies`, `unindexedYouthPolicies` 확인
6. 미인덱싱이 있으면 제한형 인덱싱 Job 반복 실행
7. 8080 충돌 시 점유 PID와 명령행 확인
8. 코드 변경 후 `./gradlew clean build` 또는 `.\gradlew.bat clean build` 실행

## 현재 남은 한계

- Job 상태는 메모리에 저장되므로 서버 재시작 시 이력이 사라진다.
- 여러 서버 인스턴스를 운영하면 Job 중복 제어를 공유할 수 없다. 운영 환경에서는 Redis, DB 또는 전용 Queue가 필요하다.
- 개별 수집 Job 진행률은 정책 건별이 아니라 API 단계 중심이다.
- 외부 공공 API와 OpenAI의 장애 또는 지연 자체를 제거할 수는 없다. 대신 Job 상태와 오류로 관찰 가능하게 만들었다.
- 공공 API 제공기관이 응답 필드나 endpoint를 변경하면 최신 Swagger와 raw payload를 다시 비교해야 한다.
