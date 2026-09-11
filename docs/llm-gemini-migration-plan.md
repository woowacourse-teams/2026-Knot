# Knot RAG Gemini 전환 계획

## 1. 문서 상태

- 상태: 계획 (구현 전, Issue 미생성)
- 작성일: 2026-09-11
- 기준 코드: `test/donut-rag` `b1d4801` — 이 문서의 파일 줄 번호는 이 커밋 기준이다.
- 목적: 문서 검색 RAG의 임베딩 모델과 답변 생성 LLM을 LM Studio Qwen에서 Google Gemini로 바꾸는 작업 범위, 순서, 검증 기준을 정한다.
- 관련 문서: `docs/llm-java-integration.md`, `docs/adr/271-llm-search-architecture-benchmark.md`, `docs/adr/284-notion-import-snapshot-publication.md`
- 선행 절차: 구현 전에 `/knot-issue-planning`으로 BE Issue를 만들고 ADR 필요 여부를 판정받는다(10절).

## 2. 목표와 범위

### 목표

1. Notion 문서 청크 임베딩과 질문 임베딩을 Gemini 임베딩 모델로 만든다.
2. 채팅 답변을 Gemini 모델로 스트리밍 생성한다.
3. 채팅과 임베딩 provider를 따로 전환할 수 있게 해 단계적 전환과 부분 롤백을 가능하게 한다.
4. SSE 계약, 사용자에게 보이는 오류 코드, pgvector 스키마, 검색 순위 규칙은 바꾸지 않는다.

### 포함

- provider 설정 분리
- Gemini 임베딩 어댑터
- Gemini 채팅 어댑터
- 설정 기본값, 로컬 설정, dev 배포 설정
- 전환 후 색인 재생성 절차
- 연동 문서 갱신

### 제외

- 검색 알고리즘, 청크 크기, top-k, 관련도 기준 변경
- pgvector 차원 변경과 DB 마이그레이션
- `origin/test/donut-lag`의 다른 변경(Anthropic 어댑터, V14 청크 단위 근거, 데스크톱 경로)
- 새 오류 코드 추가 등 프론트엔드 계약 변경
- Spring AI 도입 — `docs/llm-java-integration.md`의 "Spring AI를 이번 단계에 추가하지 않은 이유" 판단을 유지한다.
- Notion을 다시 호출하지 않고 임베딩만 다시 만드는 재색인 기능(13절 후속 과제)

## 3. 현재 구조

경로 앞의 `backend/src/main/java/com/knot/backend/`는 생략한다.

| 구성 | 위치 | 현재 동작 |
| --- | --- | --- |
| provider 스위치 | `backend/src/main/resources/application.properties:37` | `llm.provider` 하나가 채팅과 임베딩을 함께 전환한다. 값은 `fake`(기본)와 `openai-compatible`뿐이다. |
| 채팅 클라이언트 | `chat/infrastructure/LlmClientConfig.java:12`, `OpenAiCompatibleLlmClient`, `OpenAiCompatibleLlmStream` | `/chat/completions` SSE를 읽는다. `[DONE]` 없이 스트림이 끝나면 실패로 본다(`OpenAiCompatibleLlmStream.java:66-70`). |
| 채팅 fake | `chat/infrastructure/FakeLlmClient.java:11` | provider가 `fake`이거나 없을 때 켜진다. |
| 임베딩 클라이언트 | `search/infrastructure/EmbeddingClientConfig.java:20`, `OpenAiCompatibleEmbeddingClient` | `/embeddings`를 호출한다. 채팅 설정이 만든 `llmHttpClient` 빈과 `LlmProperties`를 빌려 쓴다. |
| 임베딩 호출부 | `search/application/SearchIndexingService.java:51`(색인), `search/application/PublishedDocumentSearchService.java:94`(질의) | 색인과 질의를 구분하지 않고 `embed(texts)`를 부른다. |
| 차원 계약 | `db/migration/V13__create_search_chunks_and_references.sql:10`, `SearchIndexingService.java:31`, `PublishedDocumentSearchService.java:206` | 컬럼이 `vector(1024)`이고, 설정 차원이 1024가 아니면 거부한다. |
| 프롬프트 구성 | `chat/application/ChatMessageService.java:268` | 첫 메시지는 SYSTEM(근거 프롬프트), 그 뒤는 세션 전체 이력이다. |
| 사용자 메시지 저장 | `ChatMessageService.java:86` | LLM 호출 전에 USER 메시지를 저장한다. 답변이 실패하면 다음 요청 이력에 USER가 연달아 생긴다. |
| 오류 노출 | `ChatMessageService.java:225-231` | 문서 준비 전(`CHAT_DOCUMENTS_NOT_READY`)을 제외한 스트림 중 실패는 모두 `LLM_STREAM_FAILED`로 나간다. |

현재 구조에서는 채팅만 다른 provider로 바꾸면 `llmHttpClient` 빈이 사라져 임베딩 빈 생성이 실패한다.

### 재사용할 수 있는 커밋

`origin/test/donut-lag`(`b1d4801`보다 137커밋 앞섬)에 provider 분리와 Gemini 임베딩 어댑터가 이미 구현되어 있다. Gemini 채팅 어댑터는 어느 브랜치에도 없다.

| 커밋 | 내용 | `b1d4801` 위 단독 적용 시뮬레이션 |
| --- | --- | --- |
| `d4765e46` | 채팅·임베딩 provider 설정 분리 | 충돌 없음 |
| `21b00cc5` | 분리된 provider별 클라이언트 선택 테스트 | 충돌 없음 |
| `c0e89f44` | `EmbeddingTask`, Gemini 임베딩 어댑터, 색인 배치 재시도 | `EmbeddingClientConfig.java`, `application.properties` 충돌 |
| `21314936` | Gemini 임베딩 어댑터·설정·재시도 테스트 | `ChatSearchAcceptanceTest.java`(이 브랜치에 없음), `PublishedDocumentSearchServiceTest.java`, `EmbeddingClientConfigTest.java` 충돌 |

시뮬레이션은 `git merge-tree`로 각 커밋을 `b1d4801`에 따로 적용한 결과다. 앞 커밋을 먼저 적용하면 일부 충돌은 사라질 수 있다.

## 4. 결정 사항

| 항목 | 결정 | 근거 |
| --- | --- | --- |
| 호출 방식 | Gemini 네이티브 REST API | 아래 "선택하지 않은 대안" 참고 |
| 임베딩 모델 | `gemini-embedding-001` | taskType으로 색인·질의 벡터를 구분할 수 있다. `test/donut-lag`에서 실제 색인에 사용해 봤다. |
| 임베딩 차원 | `outputDimensionality=1024` | V13 컬럼, HNSW 인덱스, 차원 검사를 그대로 쓴다. 1024는 허용 범위(128~3072) 안이다. |
| 채팅 모델 | `gemini-3.8-flash`, 환경변수로 교체 가능 | 2026-09-11 공식 문서 기준 최신 stable Flash |
| thinking | `thinkingLevel=low`, 설정값 | 첫 응답 5초 목표. thinking 토큰은 출력 한도와 지연을 함께 소모한다. |
| temperature | 보내지 않음(모델 기본값) | Gemini 3은 기본값 1.0 유지를 권고한다. |
| 설정 구조 | `llm.chat.provider`와 `llm.embedding.provider`로 분리하고, `llm.provider`는 두 키의 fallback으로 남긴다. | 기존 환경변수와 호환되고, 채팅과 임베딩을 따로 전환·롤백할 수 있다. |
| 기본 provider | `fake` 유지 | 테스트 컨텍스트와 외부 호출 없는 로컬 기동이 이 기본값을 전제로 한다. |
| 인증 | `x-goog-api-key` 헤더 | URL 쿼리 `?key=`는 접근 로그와 예외 메시지에 남을 수 있어 쓰지 않는다. |

### 선택하지 않은 대안

| 대안 | 제외 이유 |
| --- | --- |
| OpenAI 호환 엔드포인트(`/v1beta/openai/`)에 base URI와 키만 교체 | 현재 임베딩 클라이언트가 `dimensions`를 보내지 않아 기본 3,072차원 응답이 1024 검증에서 실패한다. taskType과 정규화를 제어할 수 없고, 호환 레이어는 beta다. |
| Gemini Embedding 2(`gemini-embedding-2-preview`) | preview이고, taskType 대신 입력 앞에 지시문을 붙이는 방식이라 어댑터가 달라진다. 001과 임베딩 공간이 호환되지 않아 나중에 바꾸면 재색인이 다시 필요하다. |
| 권장 차원(768, 1536, 3072)으로 변경 | V13 컬럼·HNSW 인덱스 마이그레이션과 ADR이 필요하다. 1024도 허용 범위라 비용 대비 이득이 작다. |

## 5. Gemini API 계약

2026-09-11 공식 문서 기준이다. 모델 목록과 파라미터는 바뀔 수 있으므로 구현 시작 시점에 14절 문서를 다시 확인한다.

### 5.1 임베딩

- 요청: `POST {base}/v1beta/models/{model}:batchEmbedContents`, 헤더 `x-goog-api-key`
- 본문: `requests[]`마다 `model`(`models/{model}`), `content.parts[].text`, `taskType`, `outputDimensionality`
- taskType: 색인은 `RETRIEVAL_DOCUMENT`, 질의는 `RETRIEVAL_QUERY` (001 전용 파라미터)
- 응답: `embeddings[].values`
- 정규화: 001은 3,072 미만 차원의 벡터를 정규화하지 않는다. 어댑터가 L2 정규화해야 코사인 점수와 `llm.search.minimum-relevance-score`가 의미를 가진다.
- 입력 한도: 텍스트당 2,048토큰. 현재 청크(1,200자 + 제목)는 한도 안이다.
- 모델이 다르면 임베딩 공간이 호환되지 않는다.

### 5.2 채팅 스트리밍

- 요청: `POST {base}/v1beta/models/{model}:streamGenerateContent?alt=sse`, 헤더 `x-goog-api-key`, `Accept: text/event-stream`
- 본문 예시:

```json
{
  "systemInstruction": { "parts": [{ "text": "<근거 프롬프트>" }] },
  "contents": [
    { "role": "user", "parts": [{ "text": "이전 질문" }] },
    { "role": "model", "parts": [{ "text": "이전 답변" }] },
    { "role": "user", "parts": [{ "text": "현재 질문" }] }
  ],
  "generationConfig": {
    "maxOutputTokens": 4096,
    "thinkingConfig": { "thinkingLevel": "low" }
  }
}
```

- 응답: `data: <GenerateContentResponse JSON>` 이벤트가 반복된다. 텍스트는 `candidates[0].content.parts[].text`이고, 사고 요약 part에는 `thought: true`가 붙는다.
- 종료: **`[DONE]` 이벤트가 없다.** 마지막 이벤트의 `candidates[0].finishReason`(`STOP`, `MAX_TOKENS`, `SAFETY` 등)으로 끝을 판단한다.
- 프롬프트 차단: `promptFeedback.blockReason`
- 토큰: `maxOutputTokens`에 thinking 토큰이 포함된다. 사용량은 `usageMetadata.thoughtsTokenCount`로 확인한다.
- thinking은 완전히 끌 수 없다(`minimal`도 보장하지 않음). Gemini 3은 `low`, `medium`, `high`를 지원하고 `minimal`은 일부 모델만 지원한다. `thinkingLevel` 값의 표기(소문자·enum 이름)는 구현 시 API 레퍼런스로 확정한다.
- temperature: Gemini 3은 기본값 1.0 유지를 권고한다. 낮추면 반복이나 품질 저하가 생길 수 있다.
- thought signature: 문서는 이력을 직접 관리할 때 이전 응답의 thought 블록을 그대로 다시 보내라고 안내한다. Knot DB에는 답변 텍스트만 저장되므로 보낼 수 없고, 텍스트 대화에서 누락 시 요청이 거부되는지는 문서에 명시가 없다. 8절 M4에서 실측한다.

## 6. 작업 단계

### 6.1 단계 1: provider 설정 분리

방법: `d4765e46`, `21b00cc5`를 cherry-pick한다.

변경:

- `application.properties`
  - `llm.chat.provider=${LLM_CHAT_PROVIDER:${llm.provider}}`
  - `llm.embedding.provider=${LLM_EMBEDDING_PROVIDER:${llm.provider}}`
- `FakeLlmClient`: 조건을 `llm.chat.provider`로 바꾼다.
- `LlmClientConfig`: 조건을 `llm.chat.provider`로 바꾸고, HttpClient 빈 이름을 `chatLlmHttpClient`로 바꾼다.
- `EmbeddingClientConfig`: 조건을 `llm.embedding.provider`로 바꾸고, `LlmProperties`를 직접 활성화하고, 전용 `embeddingLlmHttpClient` 빈을 쓴다.
- 테스트: `LlmClientConfigTest`, `EmbeddingClientConfigTest` 추가

완료 조건:

- `LLM_PROVIDER`만 설정한 기존 환경이 이전과 똑같이 뜬다.
- `llm.chat.provider=fake`와 `llm.embedding.provider=openai-compatible` 조합이 뜬다.
- `./gradlew test`가 통과한다.

### 6.2 단계 2: 임베딩 용도 구분과 Gemini 임베딩 어댑터

방법: `c0e89f44`를 적용하고 충돌 2파일을 직접 해결한다. 이 커밋은 인터페이스 변경과 어댑터 추가를 한 커밋에 담고 있으므로, 7절처럼 나누려면 `git cherry-pick -n`으로 적용한 뒤 나눠 커밋한다. 테스트는 `21314936`을 통째로 가져오지 않고 파일 단위로 가져온다(`git show origin/test/donut-lag:<경로>`).

충돌 해결 원칙:

- `application.properties`
  - 가져온다: `llm.gemini.*` 블록, `llm.embedding.model` 주석, `llm.search.embedding-batch-size` 기본값 16
  - 가져오지 않는다: `llm.anthropic.*`, `llm.search.top-k=8`, `llm.search.max-context-characters=12000`, `chat.turn-timeout` — 다른 커밋의 변경이다.
- `EmbeddingClientConfig.java`: `@EnableConfigurationProperties`에 `GeminiEmbeddingProperties`를 추가하고 gemini HttpClient·클라이언트 빈을 추가한다. Anthropic 관련 코드가 섞이지 않았는지 확인한다.

변경:

- `search/application/EmbeddingTask.java` 신규 (`DOCUMENT`, `QUERY`)
- `DocumentEmbeddingClient.embed(List<String> texts, EmbeddingTask task)`로 시그니처 변경
- 호출부: `SearchIndexingService`는 `DOCUMENT`, `PublishedDocumentSearchService`는 `QUERY`를 넘긴다.
- `FakeDocumentEmbeddingClient`, `OpenAiCompatibleEmbeddingClient`: 인자만 받고 무시한다.
- `search/infrastructure/gemini/` 신규: `GeminiEmbeddingClient`, `GeminiEmbeddingProperties`(`llm.gemini.*`), `GeminiRetrySleeper`

어댑터 동작:

- 배치 하나를 `batchEmbedContents` 요청 1회로 보낸다.
- 응답 개수·차원 불일치, 숫자가 아닌 값, 노름 0은 `SEARCH_PROVIDER_FAILED`로 처리한다.
- 응답 벡터마다 L2 정규화한다.
- 429·503은 색인(`DOCUMENT`)일 때만 지수 백오프로 재시도한다(5초 시작, 간격 최대 60초, 최대 6회). 질의는 채팅 지연을 막기 위해 재시도하지 않는다.
- 401·403은 `SEARCH_CONFIGURATION_INVALID`, 그 밖의 실패는 `SEARCH_PROVIDER_FAILED`로 처리한다.
- 로그에는 HTTP 상태와 응답 본문의 `error.status`만 남긴다.

테스트:

- 가져옴: `GeminiEmbeddingClientTest`, `GeminiEmbeddingPropertiesTest`, `EmbeddingClientConfigTest`의 gemini 케이스
- 수정: `SearchIndexingServiceTest`, `PublishedDocumentSearchServiceTest`, `OpenAiCompatibleEmbeddingClientTest` — 시그니처와 task 전달 검증
- `ChatSearchAcceptanceTest`는 이 브랜치에 없으므로 가져오지 않는다.

완료 조건:

- 색인 요청은 `RETRIEVAL_DOCUMENT`, 질의 요청은 `RETRIEVAL_QUERY`로 나가는 것이 테스트로 고정된다.
- 키가 요청 본문과 URL에 없다.
- `./gradlew test`가 통과한다.

### 6.3 단계 3: Gemini 채팅 어댑터 (신규)

패키지: `com.knot.backend.chat.infrastructure.gemini`. 기존 `OpenAiCompatibleLlmClient`·`OpenAiCompatibleLlmStream`의 HTTP 처리와 close 구조를 따른다.

#### 설정 `GeminiLlmProperties` (prefix `llm.gemini`)

| 키 | 기본값 | 검증 |
| --- | --- | --- |
| `base-uri` | `https://generativelanguage.googleapis.com` | 절대 http(s) URI. 임베딩과 공유한다. |
| `api-key` | `${GEMINI_API_KEY:}` | 비어 있지 않음. 임베딩과 공유한다. |
| `request-timeout` | `PT30S` | 양수. 임베딩과 공유한다. |
| `chat-model` | `gemini-3.8-flash` | 비어 있지 않음. `models/` 접두사가 있어도 같은 값으로 처리한다. |
| `max-output-tokens` | `4096` | 1 이상 |
| `thinking-level` | `low` | `minimal`, `low`, `medium`, `high` 중 하나 |

검증에 실패하면 `LLM_CONFIGURATION_INVALID`를 던진다. 빈 생성 시점에 검증하므로 키가 없으면 기동이 실패한다.

#### 빈 등록 `LlmClientConfig`

- 클래스 수준 `@ConditionalOnProperty`를 메서드 수준으로 옮긴다. `origin/test/donut-lag`의 `LlmClientConfig` 구조를 참고하되 Anthropic 빈은 가져오지 않는다.
- `llm.chat.provider=gemini`일 때 `chatLlmHttpClient`와 `GeminiLlmClient` 빈을 등록한다.

#### 요청 매핑 `GeminiRequestMapper`

| Knot `LlmMessage` | Gemini 요청 |
| --- | --- |
| `SYSTEM` (근거 프롬프트) | `systemInstruction.parts[].text`. SYSTEM이 여럿이면 빈 줄로 이어 붙인다. |
| `USER` | `contents[]`의 `role: "user"` |
| `ASSISTANT` | `contents[]`의 `role: "model"` |

- 연속된 같은 role은 한 content로 합친다. 실패한 답변 뒤에 USER가 연달아 있는 이력(3절)을 그대로 보내지 않기 위해서다.
- `contents`가 비면 호출하지 않고 `LLM_STREAM_FAILED`로 처리한다.
- `generationConfig`에는 `maxOutputTokens`와 `thinkingConfig.thinkingLevel`만 넣는다. `temperature`는 보내지 않는다.

#### 스트림 해석 `GeminiLlmStream`

- `data:` 줄을 모아 빈 줄에서 이벤트 하나로 JSON 파싱한다. 이벤트 하나가 여러 `data:` 줄로 와도 처리한다.
- 이벤트마다 다음 순서로 처리한다.
  1. `error` 필드가 있으면 실패
  2. `promptFeedback.blockReason`이 있으면 실패
  3. `candidates[0].content.parts[]` 중 `thought`가 true가 아닌 part의 `text`를 이어 붙여 chunk로 반환
  4. `candidates[0].finishReason`이 있으면, 같은 이벤트의 텍스트를 내보낸 뒤 입력 스트림을 닫고 종료 판정

종료 판정:

| 상황 | 처리 |
| --- | --- |
| `finishReason=STOP` | 정상 완료 |
| `finishReason=MAX_TOKENS` | 정상 완료 + 경고 로그 (12절 D1 결정에 따른다) |
| 그 밖의 `finishReason` (`SAFETY` 등) | 실패 |
| `finishReason` 없이 EOF | 실패 — 기존 "`[DONE]` 없는 EOF는 실패" 정책을 유지한다. |
| 텍스트를 한 글자도 받지 못하고 종료 | `finishReason`과 관계없이 실패. thinking이 출력 한도를 다 쓴 경우를 포함한다. |
| 읽는 중 `close()` | 대기 중인 읽기를 즉시 풀고 예외를 전파하지 않는다. |

오류와 로그:

- 어댑터 내부 실패는 모두 `LLM_STREAM_FAILED`다. 사용자에게는 원래 `ChatMessageService.java:229-231`에서 `LLM_STREAM_FAILED`로 나가므로 프론트엔드 계약은 바뀌지 않는다.
- `start()`에서 2xx가 아니면 응답 본문의 `error.status`만 로그로 남기고 `LLM_STREAM_FAILED`를 던진다. 사용자가 기다리는 스트림이라 재시도하지 않는다.
- 완료 시 `finishReason`과 `usageMetadata`의 `promptTokenCount`, `candidatesTokenCount`, `thoughtsTokenCount`를 로그로 남긴다(비용·지연 분석용).
- 프롬프트, 문서 내용, 키는 로그에 남기지 않는다.

#### 테스트

`GeminiLlmClientTest`, `GeminiRequestMapperTest`, `GeminiLlmPropertiesTest`를 추가한다. HTTP는 기존 `OpenAiCompatibleLlmClientTest`의 `HttpServer` 스텁 방식을 쓴다.

- 키는 `x-goog-api-key` 헤더에만 있고 URL과 본문에 없다.
- SYSTEM은 `systemInstruction`, ASSISTANT는 `model`로 매핑되고, 연속 USER는 합쳐진다.
- `temperature`는 보내지 않고 `thinkingLevel`과 `maxOutputTokens`는 보낸다.
- 여러 이벤트의 텍스트가 순서대로 chunk로 전달된다.
- `thought: true` part는 전달되지 않는다.
- `STOP` 이벤트에 담긴 텍스트까지 전달하고 정상 종료한다.
- `finishReason` 없는 EOF, `SAFETY`, `promptFeedback.blockReason`, 텍스트 0자, 2xx가 아닌 상태는 `LLM_STREAM_FAILED`다.
- 응답이 멈춘 상태에서 `close()`하면 읽기가 즉시 끝난다.
- 설정 누락과 잘못된 thinking level은 `LLM_CONFIGURATION_INVALID`다.
- `LlmClientConfigTest`: `llm.chat.provider=gemini`에서 `GeminiLlmClient`가 선택된다.

완료 조건: 위 테스트와 `./gradlew test`가 통과한다.

### 6.4 단계 4: 설정 기본값과 로컬 설정

`application.properties`의 LLM 블록에서 추가·변경되는 부분:

```properties
# LLM (fake is the safe local default; enable openai-compatible or gemini explicitly)
# llm.provider is the legacy single switch and remains the fallback for both sides.
llm.provider=${LLM_PROVIDER:fake}
llm.chat.provider=${LLM_CHAT_PROVIDER:${llm.provider}}
llm.embedding.provider=${LLM_EMBEDDING_PROVIDER:${llm.provider}}
# (기존 llm.base-uri ~ llm.search.* 유지)
llm.search.embedding-batch-size=${LLM_SEARCH_EMBEDDING_BATCH_SIZE:16}
# Gemini (used only when a provider is gemini; the key comes from the environment)
llm.gemini.base-uri=${GEMINI_BASE_URI:https://generativelanguage.googleapis.com}
llm.gemini.api-key=${GEMINI_API_KEY:}
llm.gemini.request-timeout=${GEMINI_REQUEST_TIMEOUT:PT30S}
llm.gemini.chat-model=${GEMINI_CHAT_MODEL:gemini-3.8-flash}
llm.gemini.max-output-tokens=${GEMINI_MAX_OUTPUT_TOKENS:4096}
llm.gemini.thinking-level=${GEMINI_THINKING_LEVEL:low}
llm.gemini.embedding-model=${GEMINI_EMBEDDING_MODEL:gemini-embedding-001}
llm.gemini.retry-max-attempts=${GEMINI_RETRY_MAX_ATTEMPTS:6}
llm.gemini.retry-initial-delay=${GEMINI_RETRY_INITIAL_DELAY:PT5S}
```

- `embedding-batch-size` 기본값 64→16은 openai-compatible에도 적용된다. `test/donut-lag`에서 1,200자 청크 32개 이상을 한 요청으로 보냈을 때 429 `RESOURCE_EXHAUSTED`가 났기 때문이다(2026-09-08). LM Studio 색인 속도가 문제되면 환경변수로 올린다.
- 로컬 `application-local.properties`(gitignore 대상)에는 이미 `llm.chat.provider`, `llm.embedding.provider`, `llm.gemini.api-key` 키가 있을 수 있다. 단계 1 이후에는 `llm.chat.provider` 값에 맞는 빈이 없으면 기동이 실패하므로 `gemini` 또는 `fake`로 맞춘다.
- 외부 호출 없이 띄울 때는 두 provider를 모두 `fake`로 둔다.

완료 조건:

- 로컬에서 `LLM_PROVIDER=gemini`와 `GEMINI_API_KEY`만으로 기동된다.
- provider가 `gemini`인데 키가 비면 기동이 실패한다.

### 6.5 단계 5: dev 배포 설정 (dev에서 실제 Gemini를 쓸 때만)

현재 상태:

- `.github/workflows/deploy-backend-dev.yml:243-245`는 env 파일을 초대 키 2개로만 만들고, `:1187-1191`에서 서버의 `/etc/knot/knot-backend-dev.env`를 이 파일로 교체한다. 이 파일에 Gemini 키를 직접 넣으면 다음 배포에서 사라진다.
- `application-dev.properties`에는 LLM 설정이 없다.
- DB 계정, GitHub OAuth 값은 이 워크플로 밖의 서버 설정에서 들어오고 있다. 그 위치는 확인이 필요하다.

선택지(12절 D5):

- A. 워크플로로 관리: GitHub Actions secret `GEMINI_API_KEY`를 등록하고, 배포 단계 `env`와 필수값 검사(`: "${GEMINI_API_KEY:?...}"`), env 파일 생성에 `LLM_PROVIDER=gemini`와 `GEMINI_API_KEY` 줄을 추가한다.
- B. 서버 설정으로 관리: DB·OAuth 값과 같은 서버 환경 파일에 넣는다. 워크플로는 바꾸지 않는다.

완료 조건: dev 배포 후 health가 통과하고, 채팅 1회가 성공하고, 로그에 키가 없다.

### 6.6 단계 6: 색인 재생성

임베딩 모델이 바뀌면 기존 벡터와 새 질의 벡터를 비교할 수 없다. 차원이 같아서 **오류 없이 엉뚱한 문서를 근거로 고르게 된다.** Qwen 벡터든 fake 해시 벡터든 기존 색인은 모두 재생성 대상이다.

절차:

1. 임베딩 provider를 `gemini`로 바꿔 배포·기동한다.
2. 워크스페이스마다 Notion 동기화를 다시 실행한다: `POST /api/v1/workspaces/{workspaceId}/imports` (프론트엔드 동기화 요청과 같은 API).
3. 새 import run의 색인이 성공하면 publication이 교체된다(ADR 284). 실패하면 기존 publication이 유지된다.
4. 워크스페이스마다 채팅 1회로 출처가 질문과 관련 있는지 확인한다.

- 1번과 2번 사이에는 기존 스냅샷으로 검색하므로 품질이 떨어진다. 로컬·dev는 감수하고, 보존할 사용자 데이터가 있으면 전환 시점을 공지한다(12절 D3).
- 임베딩 provider를 되돌릴 때도 같은 재생성이 필요하다.

### 6.7 단계 7: 문서 갱신

- `docs/llm-java-integration.md`
  - 기본 경로 흐름의 `Qwen embedding`을 `Gemini embedding`으로 바꾼다.
  - 실행 설정 표를 `LLM_PROVIDER`, `LLM_CHAT_PROVIDER`, `LLM_EMBEDDING_PROVIDER`, `GEMINI_*` 기준으로 바꾸고, openai-compatible(LM Studio, NIM)은 대안으로 남긴다.
  - 차원·정규화·taskType 계약과 모델 변경 시 재색인 필요성을 추가한다.
- ADR은 10절 판정 결과를 따른다.

## 7. 커밋 단위

| 순서 | 커밋 메시지(안) | 포함 |
| --- | --- | --- |
| 1 | `feat: 채팅·임베딩 LLM provider 설정을 분리` | 단계 1 코드 (`d4765e46`) |
| 2 | `test: 분리된 provider별 클라이언트 선택 검증 추가` | `21b00cc5` |
| 3 | `feat: 임베딩 요청에 색인·질의 용도를 전달` | `EmbeddingTask`, 인터페이스, 호출부, 기존 구현체와 기존 테스트 수정 |
| 4 | `feat: 임베딩 Gemini 어댑터와 색인 배치 재시도 추가` | `search/infrastructure/gemini`, `EmbeddingClientConfig`, 설정 |
| 5 | `test: Gemini 임베딩 어댑터·설정·배치 재시도 검증 추가` | Gemini 임베딩 테스트 |
| 6 | `feat: 채팅 LLM에 Gemini 스트리밍 어댑터 추가` | `chat/infrastructure/gemini`, `LlmClientConfig`, 설정 |
| 7 | `test: Gemini 채팅 어댑터 검증 추가` | Gemini 채팅 테스트 |
| 8 | `chore: dev 배포에 Gemini 설정 전달` | 워크플로 (6.5 선택지 A일 때만) |
| 9 | `docs: LLM 연동 문서를 Gemini 기준으로 갱신` | 단계 7 |

작업 브랜치는 Issue 번호가 정해진 뒤 `CONTRIBUTING.md`의 `be/feature/#<번호>` 형식으로 만든다.

## 8. 검증 계획

### 8.1 자동 테스트

- `./gradlew test`: 어댑터, 설정, 요청 매핑, 서비스 단위
- `./gradlew integrationTest`: pgvector 검색·Workspace 격리 회귀 (fake provider)
- `./gradlew acceptanceTest`: 기존 수락 회귀
- 실행 환경: JDK 25, `docker compose up -d postgres`

### 8.2 실제 키 실측 (로컬)

| # | 확인 항목 | 통과 기준 |
| --- | --- | --- |
| M1 | 동기화 1회 색인 | 모든 배치 성공. 429 재시도가 났다면 로그로 횟수를 기록한다. |
| M2 | 질의 임베딩과 검색 | 질문에 대한 출처가 질문과 직접 관련 있다. |
| M3 | 첫 응답 시간(TTFT) | 5초 목표. `thinkingLevel` `low`와 `minimal`(지원 시)을 비교해 기록한다. |
| M4 | 2턴 이상 대화 | thought signature 없는 이력으로 요청이 거부되지 않는다. 거부되면 11절 R3 대응을 적용한다. |
| M5 | 출력 한도 | 긴 답변 질문에서 `MAX_TOKENS` 발생 여부와 `thoughtsTokenCount`를 기록한다. |
| M6 | 차단 응답 | 차단으로 끝나면 사용자에게 `LLM_STREAM_FAILED`가 가고 부분 답변이 저장되지 않는다. |
| M7 | 키 노출 | 애플리케이션 로그, SSE, DB 어디에도 키가 없다. |
| M8 | 취소 | 스트리밍 중 연결을 끊으면 Gemini 요청이 닫히고 같은 세션에서 다시 질문할 수 있다. |

## 9. 롤아웃과 롤백

1. 로컬: 단계 1~4 구현 → 자동 테스트 → M1~M8 실측
2. dev: 단계 5 → 배포 → 단계 6 재동기화 → M2, M3, M7 확인

롤백:

- 채팅만 되돌리기: `LLM_CHAT_PROVIDER`를 `fake` 또는 `openai-compatible`로 바꿔 재기동한다. 색인에는 영향이 없다.
- 임베딩 되돌리기: `LLM_EMBEDDING_PROVIDER`를 바꾸고 단계 6 재생성을 다시 한다.
- 기존 어댑터를 지우지 않으므로 코드 되돌림 없이 환경변수만으로 전환할 수 있다.

## 10. Issue·ADR 절차

- 구현 전에 `/knot-issue-planning`으로 BE Issue를 만든다. Issue에는 템플릿의 `구현 기능 설명`, `TODO`, `메모`만 쓴다.
- ADR 필요 여부는 판정기 결과를 따른다. 판정에 넘길 자료:
  - ADR 271은 Qwen 임베딩 + pgvector RAG를 MVP 기본 경로로 적었고, 최종 운영 모델을 정할 자료가 생기면 재논의하도록 했다.
  - `docs/llm-java-integration.md`는 임베딩 모델 호환성을 별도 ADR로 결정하라고 적었다.
  - 문서 청크를 보내는 외부 대상이 LM Studio·NIM에서 Google로 바뀐다.
- ADR이 필요하면 실제 Issue 번호로 구현 브랜치에서 `Proposed`로 만든다(`harness/materialize_adr.py`).

## 11. 위험과 대응

| # | 위험 | 대응 |
| --- | --- | --- |
| R1 | 무료 등급 한도로 색인 중 429 | 배치 16과 색인 재시도로 완화한다. 계속 실패하면 결제가 연결된 키를 쓴다. `test/donut-lag`에서는 결제 연결 후 611청크 색인이 106초에 끝났다(2026-09-08). |
| R2 | thinking 때문에 TTFT가 5초를 넘음 | `thinkingLevel`을 낮추고 flash-lite 계열과 비교한다(M3). |
| R3 | 이력에 thought signature가 없어 2턴째 요청이 거부됨 | 이전 대화를 `contents` 대신 `systemInstruction` 안의 텍스트 이력으로 넣도록 매퍼만 바꾼다. |
| R4 | 출력 한도에서 답변이 잘리거나 빈 답변 | `maxOutputTokens` 4096, 텍스트 0자 실패 처리, `MAX_TOKENS` 경고 로그 |
| R5 | 전환 후 재동기화 전까지 엉뚱한 근거 | 6.6 절차를 따르고 전환 시점을 공지한다. |
| R6 | 무료(unpaid) 등급 입력이 Google 제품 개선에 쓰일 수 있음 | 팀 Notion 문서를 보내므로 결제가 연결된 키를 쓴다. 도입 전 Gemini API 약관 원문을 다시 확인한다. |
| R7 | 모델 ID 폐기·변경 | 모델 ID를 설정값으로만 두고 코드에 고정하지 않는다. |
| R8 | 키 노출 | 헤더로만 보내고, 로그는 상태 코드와 `error.status`만 남기며, 테스트로 고정한다. |

## 12. 미결정 사항

| # | 질문 | 추천 기본값 |
| --- | --- | --- |
| D1 | `MAX_TOKENS`로 끝난 답변을 저장할지, 실패로 처리할지 | 저장 + 경고 로그 (`test/donut-lag` Anthropic 어댑터와 같은 정책) |
| D2 | 채팅 모델과 thinking 수준 | `gemini-3.8-flash` + `low`로 시작하고 M3 결과로 조정 |
| D3 | 기존 색인 처리 | 워크스페이스별 재동기화로 충분하다고 본다(보존할 사용자 데이터가 없다는 전제). |
| D4 | `test/donut-lag` 코드 반영 방식 | 단계 1은 cherry-pick, 단계 2는 `cherry-pick -n` 후 충돌 파일만 직접 해결 |
| D5 | dev 키 관리 위치 (6.5 A/B)와 dev 적용 시점 | 로컬 실측 M1~M8 통과 후 적용. 위치는 기존 DB·OAuth 값 관리 방식에 맞춘다. |
| D6 | 결제 연결 키 준비 주체 | 미정 |

## 13. 후속 과제

- 청크 행에 임베딩 모델명을 저장해 모델 불일치를 감지한다. 지금은 모델이 달라도 조용히 품질만 떨어진다.
- Notion을 다시 호출하지 않고 기존 `imported_pages`로 임베딩만 다시 만드는 재색인 작업
- `docs/llm-search-benchmark-gold-set.md`로 Qwen 대비 답변·출처 품질을 다시 측정한다(ADR 271 재논의 조건).
- 차단(`SAFETY`)과 일반 실패를 사용자에게 구분해 보여줄지 결정한다(프론트엔드 계약 변경).

## 14. 참고 자료

- [Gemini API 모델 목록](https://ai.google.dev/gemini-api/docs/models)
- [Gemini 임베딩](https://ai.google.dev/gemini-api/docs/embeddings)
- [generateContent API 레퍼런스](https://ai.google.dev/api/generate-content)
- [Thinking](https://ai.google.dev/gemini-api/docs/thinking)
- [Gemini 3 개발자 가이드](https://ai.google.dev/gemini-api/docs/gemini-3)
- [OpenAI 호환](https://ai.google.dev/gemini-api/docs/openai)
