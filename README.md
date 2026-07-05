# ragtest

Java 21, Spring Boot 3.5.x 기반 대한민국 청년정책/복지정책 RAG 테스트 MVP입니다.

현재 구현은 공공데이터포털의 **행정안전부 대한민국 공공서비스 정보 API**에서 실제 정책 데이터를 수집하고, OpenAI Embedding으로 변환한 뒤 PostgreSQL pgvector에 저장합니다. 사용자는 브라우저 화면에서 정책 질문을 입력하고, 검색된 정책 데이터만 근거로 한 한국어 RAG 답변을 확인할 수 있습니다.

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- Gradle Groovy
- PostgreSQL 16
- pgvector
- Spring AI
- OpenAI Chat / Embedding
- Spring Boot static resources

## Environment Variables

필수:

```bash
OPENAI_API_KEY=your-openai-api-key
DATA_GO_KR_SERVICE_KEY=your-data-go-kr-service-key
```

선택:

```bash
OPENAI_CHAT_MODEL=gpt-4.1-mini
YOUTH_CENTER_API_KEY=your-youth-center-api-key
```

API 키는 코드에 하드코딩하지 않고 `src/main/resources/application.yaml`에서 환경변수 placeholder로 읽습니다.

## Run

PostgreSQL pgvector 실행:

```bash
docker compose -f docker-compose.yml up -d
```

애플리케이션 실행:

```bash
./gradlew bootRun
```

Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

브라우저 접속:

```text
http://localhost:8080
```

## Test Flow

1. `http://localhost:8080` 접속
2. `실제 공공서비스 API 수집 및 인덱싱` 클릭
3. 정책 질문 입력
4. `질문하기` 클릭
5. 답변과 근거 정책 sources 확인
6. `저장된 정책 목록 불러오기`로 DB 저장 정책 확인

## Main APIs

```text
POST /api/admin/ingest/public-service
POST /api/admin/rag/index
POST /api/rag/ask
GET  /api/policies
GET  /api/policies/{policyId}
```

## Current Scope

- 실제 동작 검증 완료:
  - 행정안전부 대한민국 공공서비스 정보 API
  - 청년/취업/구직/주거/월세/자산 키워드 기반 정책 수집
  - PostgreSQL 정책 저장
  - OpenAI Embedding 생성
  - pgvector 저장
  - RAG 질문/답변

- 구조만 준비된 영역:
  - 온통청년 API
  - 지자체복지서비스 API
  - 중앙부처복지서비스 API

공공데이터포털 API는 API별 활용신청/승인이 필요할 수 있습니다.

## Notes

- Spring Security는 사용하지 않습니다.
- Swagger는 사용하지 않고 정적 웹 화면으로 테스트합니다.
- RAG 답변은 검색된 정책 CONTEXT만 근거로 생성하도록 프롬프트를 구성했습니다.
- 정책 재인덱싱 시 기존 같은 `policyId` vector row를 삭제한 뒤 새로 저장합니다.
- 로컬 IntelliJ 설정과 API 키가 들어갈 수 있는 `.idea`는 Git에 포함하지 않습니다.
