# Java LLM·pgvector 연동 가이드

상태: MVP 연동 구현 및 컨테이너 검증 완료, 실제 NIM 운영 전 검증 중

관련 이슈: [#311](https://github.com/woowacourse-teams/2026-Knot/issues/311)

## 기본 경로

Java 백엔드는 Notion credential이나 MCP token을 LLM에 넘기지 않는다. Workspace 소유자가 연결한 Notion 문서는 Import worker가 마지막 성공 publication으로 만든 스냅샷에 저장되고, 색인 완료 후에만 공개된다.

```text
Notion Import
  → imported_pages staging
  → Markdown chunking
  → 임베딩 (Gemini gemini-embedding-001, 1,024차원 — 정정 2026-09-08)
  → PostgreSQL pgvector 색인
  → 성공 시 publication pointer 교체

채팅 질문(웹 채팅 UI — 브라우저·데스크톱 셸 공통, 서버 SSE 경로)
  → 현재 Workspace의 published import run 조회
  → keyword + pgvector 후보 검색
  → 유사도 상위 8개 청크 선별(같은 페이지의 청크 허용, 2026-09-07 정정)
  → 근거 system prompt + 현재 세션 history를 LLM에 전달
  → assistant 저장 + search_reference 저장 + SSE complete

CLI 에이전트 질문(데스크톱 로컬 MCP 서버 경유, 2026-09-09 — 로드맵 S7·S8, 미구현)
  → 사용자의 CLI 코딩 에이전트가 search_documents 도구 호출 → 데스크톱 앱
  → POST /api/v1/workspaces/{workspaceId}/search (Workspace 멤버·published import run 검사)
  → keyword + pgvector 후보 검색 → 상위 8개 청크 + 근거 규칙 문장 응답 (저장 없음, 서버 LLM 호출 없음)
  → 에이전트가 터미널에서 답변 작성
  → (선택) show_answer → POST /api/v1/conversations/{sessionId}/turns 로
    USER + ASSISTANT(generated_by=CLIENT) + search_reference 저장 (로드맵 S2·S10)
```

새 Import가 진행 중이거나 색인에 실패하면 기존 성공 publication을 유지한다. 최초 성공 publication이 없으면 채팅은 LLM을 호출하지 않고 `CHAT_DOCUMENTS_NOT_READY` 오류를 반환한다.

## 실행 설정

기본값은 외부 호출이 없는 `fake` 모드다. LM Studio 또는 NVIDIA NIM을 사용할 때만 `openai-compatible`을, Anthropic Messages API를 사용할 때만 채팅 쪽에 `anthropic`을, Gemini Embedding을 사용할 때만 임베딩 쪽에 `gemini`를 활성화한다. 2026-09-08부터 실제 임베딩은 `gemini`(`gemini-embedding-001`)가 기본 선택이며(로드맵 `B5`), `openai-compatible` 임베딩은 되돌리기용으로 남는다.

provider는 채팅과 임베딩을 따로 켤 수 있다. `LLM_PROVIDER` 하나만 넣으면 두 쪽이 같은 값을 쓰고,
`LLM_CHAT_PROVIDER`·`LLM_EMBEDDING_PROVIDER`를 넣으면 그 쪽만 덮어쓴다. 채팅 HTTP 클라이언트
(`chatLlmHttpClient`)와 임베딩 HTTP 클라이언트(`embeddingLlmHttpClient`)도 provider별로 따로 만들어져,
한쪽만 `fake`로 두면 그쪽은 외부 호출을 하지 않는다.

| 환경 변수 | 예시 | 용도 |
| --- | --- | --- |
| `LLM_PROVIDER` | `openai-compatible` | 채팅·임베딩 공통 provider. 아래 두 키가 없을 때의 fallback이다 |
| `LLM_CHAT_PROVIDER` | `openai-compatible` | 채팅만 따로 지정한다(`fake` \| `openai-compatible` \| `anthropic`). 없으면 `LLM_PROVIDER`를 따른다 |
| `LLM_EMBEDDING_PROVIDER` | `gemini` | 임베딩만 따로 지정한다(`fake` \| `openai-compatible` \| `gemini`). 없으면 `LLM_PROVIDER`를 따른다 |
| `LLM_BASE_URI` | `http://<lm-studio-host>:1234/v1` | 채팅·임베딩 endpoint의 공통 base URI |
| `LLM_API_KEY` | `<secret>` | Authorization header에만 사용 |
| `LLM_MODEL` | `qwen/qwen3.6-27b` | 채팅 모델 |
| `LLM_EMBEDDING_MODEL` | `text-embedding-qwen3-embedding-0.6b:2` | `openai-compatible` 임베딩 모델. `gemini`는 `GEMINI_EMBEDDING_MODEL`을 쓴다 |
| `LLM_EMBEDDING_DIMENSIONS` | `1024` | V13 pgvector 차원 계약. 세 provider 모두 응답 차원이 이 값과 다르면 `SEARCH_PROVIDER_FAILED`. `gemini`는 이 값을 `outputDimensionality`로 보낸다 |
| `LLM_SEARCH_EMBEDDING_BATCH_SIZE` | `16` | Import 색인 시 한 번에 임베딩을 요청할 청크 수(정정 2026-09-08: 64 → 16. Gemini가 1,300자 32건 이상 요청을 429 `RESOURCE_EXHAUSTED`로 거절, 로드맵 Q37·U25) |
| `LLM_SEARCH_MINIMUM_RELEVANCE_SCORE` | `0.35` | 검색 후보를 근거로 채택하기 위한 최소 정규화 점수 |
| `LLM_MAX_TOKENS` | `1024` | 채팅 생성 상한 |
| `LLM_TEMPERATURE` | `0.2` | 채팅 생성 온도 |
| `LLM_REQUEST_TIMEOUT` | `PT30S` | 채팅·임베딩 HTTP timeout |

LM Studio는 `/v1/chat/completions`와 `/v1/embeddings`를 OpenAI-compatible API로 제공해야 한다. 임베딩 모델의 실제 응답 차원은 `1024`여야 한다. API key는 환경 변수나 secret manager로만 주입하고 저장소·프롬프트·로그·SSE에 기록하지 않는다.

NVIDIA NIM을 사용할 때는 같은 adapter에 다음처럼 base URI와 key만 바꾼다.

```text
LLM_PROVIDER=openai-compatible
LLM_BASE_URI=https://integrate.api.nvidia.com/v1
LLM_API_KEY=<NIM secret>
LLM_MODEL=<NIM chat model>
LLM_EMBEDDING_MODEL=<NIM embedding model>
```

### Anthropic 채팅 어댑터

`LLM_CHAT_PROVIDER=anthropic`이면 채팅만 Anthropic Messages API(`POST {base-uri}/v1/messages`, `stream: true`)로 보낸다.
임베딩은 이 설정과 무관하게 `LLM_EMBEDDING_PROVIDER`를 따르므로, 채팅은 Claude·임베딩은 Qwen(`openai-compatible`)으로
섞어 쓸 수 있다. 어댑터는 `chat/infrastructure/anthropic/`에 있고 SDK 없이 JDK `HttpClient`로 직접 호출한다
(`docs/electron-desktop-app-roadmap.md` Q20). `LLM_CHAT_PROVIDER=anthropic`인데 아래 값이 비어 있거나 잘못되면
애플리케이션이 기동 시점에 `LLM_CONFIGURATION_INVALID`로 실패한다.

| 환경 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `ANTHROPIC_API_KEY` | (없음, 필수) | `x-api-key` header에만 사용. 저장소·프롬프트·로그·SSE에 기록하지 않는다 |
| `ANTHROPIC_MODEL` | `claude-opus-5` | 채팅 모델 |
| `ANTHROPIC_EFFORT` | `medium` | `output_config.effort`(`low` \| `medium` \| `high` \| `xhigh` \| `max`). 채팅 QA 측정 후 조정 |
| `ANTHROPIC_MAX_TOKENS` | `4096` | 채팅 생성 상한 |
| `ANTHROPIC_REQUEST_TIMEOUT` | `PT30S` | 응답 header 수신까지의 timeout. 채팅 SSE 30초 타임아웃과 맞춘다 |
| `ANTHROPIC_BASE_URI` | `https://api.anthropic.com` | 테스트·프록시용. 운영에서는 바꾸지 않는다 |

`temperature`는 보내지 않고(Opus 5에서 400), `thinking`은 생략해 모델 기본값을 따른다. 응답 처리와 오류 매핑은 다음과 같다.

| Anthropic 응답 | Knot 처리 |
| --- | --- |
| `content_block_delta` `text_delta` | SSE `chunk`로 전달. thinking 델타·`ping`·`content_block_start/stop`은 무시한다 |
| `message_stop` | 스트림 정상 종료. 이 이벤트 전에 연결이 끊기면 `LLM_STREAM_FAILED` |
| HTTP 401·403, 스트림 `authentication_error`·`permission_error` | `LLM_CONFIGURATION_INVALID` |
| HTTP 429·529, 스트림 `rate_limit_error`·`overloaded_error` | `LLM_RATE_LIMITED`(`Retry-After`는 로그로만 남긴다) |
| 응답 header timeout | `LLM_STREAM_TIMEOUT` |
| `stop_reason=refusal` | `LLM_REFUSED`(`stop_details.category`를 로그에 남긴다) |
| 그 외 실패 | `LLM_STREAM_FAILED` |

어댑터가 던진 오류 코드는 `ChatMessageService`가 그대로 SSE `error` 이벤트에 싣는다. `message_start`·`message_delta`의
`usage` 토큰 수는 INFO 로그로만 남기며 저장하지 않는다. 운영에 `anthropic`을 켜기 전에는 gold set 30문항 재측정과
TTFT 5초 실측(로드맵 GB 게이트)을 통과해야 한다.

### Gemini 임베딩 어댑터

`LLM_EMBEDDING_PROVIDER=gemini`이면 임베딩만 Gemini API(`POST {base-uri}/v1beta/models/{model}:batchEmbedContents`, 헤더 `x-goog-api-key`)로 보낸다.
채팅은 이 설정과 무관하게 `LLM_CHAT_PROVIDER`를 따르므로, 채팅은 Claude·임베딩은 Gemini로 섞어 쓴다. 어댑터는
`search/infrastructure/gemini/`에 있고 SDK 없이 JDK `HttpClient`로 직접 호출한다(로드맵 Q34). `LLM_EMBEDDING_PROVIDER=gemini`인데
아래 값이 비어 있거나 잘못되면 애플리케이션이 기동 시점에 `SEARCH_CONFIGURATION_INVALID`로 실패한다.

| 환경 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `GEMINI_API_KEY` | (없음, 필수) | `x-goog-api-key` header에만 사용. 저장소·프롬프트·로그·응답에 기록하지 않는다 |
| `GEMINI_EMBEDDING_MODEL` | `gemini-embedding-001` | 임베딩 모델. 요청 본문에는 `models/` 접두사를 붙여 보낸다 |
| `GEMINI_REQUEST_TIMEOUT` | `PT30S` | 배치 한 번의 HTTP timeout |
| `GEMINI_RETRY_MAX_ATTEMPTS` | `6` | 색인 배치가 429·503이면 이 횟수까지 보낸다(로드맵 Q42). `1`이면 재시도 없음 |
| `GEMINI_RETRY_INITIAL_DELAY` | `PT5S` | 첫 재시도 전 대기. 이후 2배씩(상한 60초) — 5·10·20·40·60초, 누적 135초. 실측에서 5회(75초)는 한 번 못 넘겼다 |
| `GEMINI_BASE_URI` | `https://generativelanguage.googleapis.com` | 테스트·프록시용. 운영에서는 바꾸지 않는다 |

요청은 `requests[]` 항목마다 `{model, content.parts[].text, taskType, outputDimensionality}`를 넣는다. `taskType`은 색인이
`RETRIEVAL_DOCUMENT`, 검색 질의가 `RETRIEVAL_QUERY`다(로드맵 Q36 — `DocumentEmbeddingClient.embed(texts, EmbeddingTask)`가 용도를
받는다). `outputDimensionality`는 `LLM_EMBEDDING_DIMENSIONS`(1,024)이며, `gemini-embedding-001`은 3,072 미만 벡터를 정규화해 주지
않으므로 어댑터가 응답 `embeddings[].values`를 L2 정규화한 뒤 저장·검색에 쓴다(로드맵 Q35). 한 배치는
`LLM_SEARCH_EMBEDDING_BATCH_SIZE`(16)건이다(로드맵 Q37, 정정 2026-09-08: 64는 429 `RESOURCE_EXHAUSTED`).

| Gemini 응답 | Knot 처리 |
| --- | --- |
| 2xx, `embeddings.length == 요청 건수`, 각 `values.length == 1024` | 정규화 후 순서대로 반환 |
| HTTP 401·403 | `SEARCH_CONFIGURATION_INVALID` |
| HTTP 429·503 (색인 배치) | `GEMINI_RETRY_INITIAL_DELAY`부터 2배씩 대기하며 `GEMINI_RETRY_MAX_ATTEMPTS`회까지 재시도(로드맵 Q42). 다 실패하면 `SEARCH_PROVIDER_FAILED`(색인은 import 실패로 남고 기존 스냅샷 유지) |
| HTTP 429·503 (검색 질의) | 재시도 없이 `SEARCH_PROVIDER_FAILED`(검색 API는 500) |
| 그 외 비 2xx(400·5xx), 건수·차원 불일치, 본문 파싱 실패, 영벡터 | `SEARCH_PROVIDER_FAILED`(재시도 없음) |

오류 본문은 `error.status`만 WARN 로그에 남긴다. 임베딩 공간이 Qwen·fake와 다르므로 `gemini`를 켠 뒤에는 **모든 Workspace의
Notion 동기화를 다시 실행해 재색인**해야 하며, 그 전까지 벡터 검색 점수는 무의미하다(로드맵 Q39·GB 게이트). 요금은
Gemini API 무료 티어가 있고 유료 티어는 입력 1M 토큰당 US$0.15(2026-09-08 확인, [가격 문서](https://ai.google.dev/gemini-api/docs/pricing));
무료 티어 응답은 제품 개선에 쓰일 수 있다고 명시돼 있어 운영 키는 유료 티어로 둔다. 참고: [임베딩 가이드](https://ai.google.dev/gemini-api/docs/embeddings),
[API 레퍼런스](https://ai.google.dev/api/embeddings).

무료 티어 실측(2026-09-08, 로드맵 U25): 1,300자 텍스트 기준 한 요청에 32건 이상이면 즉시 429, 16건 배치는 분당 2번(약 32청크·4만 자)까지만 받고
56초 뒤 회복한다. 그래서 배치 16 + 429 재시도가 기본값이며, 21페이지(611청크)는 무료 티어에서 10분 안팎이고 재시도로도 실패할 수 있다. 결제 계정을 연결한 유료 티어에서는 같은 양이 48초(39회 호출)에 끝났다.
`LLM_SEARCH_EMBEDDING_BATCH_SIZE`는 임베딩 provider의 요청 크기·timeout에 맞춰 조정한다. 모든
배치가 성공하기 전에는 새 import snapshot을 공개하지 않는다. `LLM_SEARCH_MINIMUM_RELEVANCE_SCORE`
미만인 vector·keyword 후보는 답변 근거에서 제외하며, 남은 후보가 없으면 LLM을 호출하지 않고
문서 없음 응답을 반환한다.

## CLI 에이전트 경유 탐색 검색 API (2026-09-09 정정)

데스크톱 탐색 경로는 2026-09-09에 "데스크톱 main이 사용자 LLM 호출"에서 "사용자의 CLI 코딩 에이전트가 데스크톱 로컬 MCP 서버의 도구로 검색 결과를 받아 답변"하는 방식으로 바뀌었다(데스크톱 기획서 6.4, 로드맵 트랙 S). 서버는 어느 경로에서도 사용자를 대신해 LLM을 부르지 않는다.

- 구현됨(`S1`): `POST /api/v1/conversations/{sessionId}/search` — 세션 기준 검색. 서버 LLM을 부르지 않고 검색 단계만 수행한다. 2026-09-09 현재 이 엔드포인트의 소비자(데스크톱 `S3`)는 폐기돼 없으며, 검색·선별·예산 로직과 V14는 `S7`·`S2`가 재사용한다.
  - 검사 순서: 세션 소유자 → 같은 세션의 진행 중 스트림·검색 잠금 → 공개 스냅샷(`CHAT_DOCUMENTS_NOT_READY` 409) → 답변 없는 USER 메시지가 `CHAT_TURN_TIMEOUT`(기본 `PT5M`) 안이면 `CHAT_TURN_IN_PROGRESS` 409.
  - READY: USER 메시지를 저장하고 `{status, userMessageId, groundingRules, chunks[≤8]}`를 돌려준다. `chunks[].content`는 `LLM_SEARCH_MAX_CONTEXT_CHARACTERS`(기본 12,000) 예산에 맞춰 잘린다.
  - NO_RESULT·NEEDS_CLARIFICATION: USER와 안내 ASSISTANT를 같은 트랜잭션에 저장하고 `{status, userMessageId, assistantMessageId, fallbackAnswer}`를 돌려준다.
  - 검색 자체가 실패하면(임베딩 provider 오류 등) 아무것도 저장하지 않고 `SEARCH_*` 코드를 500으로 돌려준다.
- 계획(`S7`, 미구현): `POST /api/v1/workspaces/{workspaceId}/search` — Workspace 멤버·공개 스냅샷 검사, `S1`과 같은 하이브리드 검색·청크 상위 8개·규칙 문장·예산. **저장·턴 검사 없음.** READY가 아니면 안내 문구만 돌려준다. 데스크톱 MCP 도구 `search_documents`가 이것을 부른다(로드맵 Q49).
- 계획(`S2`, 미구현): `POST /api/v1/conversations/{sessionId}/turns` — 에이전트가 만든 질문·답변·근거(≤8)를 USER + ASSISTANT(`generated_by=CLIENT`) + `search_references`로 한 트랜잭션에 저장. 세션 소유자·Workspace JOIN 검증, rank ≤ 8, 진행 중 턴 검사(로드맵 Q23). MCP 도구 `show_answer`(`S10`)가 부른다.

웹 채팅 UI의 SSE 경로(`POST …/messages`)는 브라우저·데스크톱 셸 모두 그대로 쓰며 근거만 청크 8개로 맞췄다(로드맵 Q22).

## PostgreSQL

V13이 `vector` extension과 `search_document_chunks`, `search_references`를 만들고, V14가 `search_references`를 청크 단위(`chunk_index`, rank 1~8, 유일 키 `(message_id, imported_page_id, chunk_index)`)로 넓히며 `chat_messages.generated_by`(`SERVER`/`CLIENT`)를 추가한다. V13 시절 근거 행의 `chunk_index`는 0으로 채워진다. 개발·테스트 PostgreSQL은 pgvector 이미지를 사용한다.

```bash
docker compose up -d postgres
./gradlew test
./gradlew integrationTest
./gradlew acceptanceTest
```

운영 DB가 이미 존재하면 배포 전에 `CREATE EXTENSION vector` 권한과 HNSW index 생성을 확인한다. 임베딩 차원을 바꾸는 경우에는 기존 V13 테이블과 모델 호환성을 별도 migration/ADR로 결정해야 한다.

## Spring AI를 이번 단계에 추가하지 않은 이유

Spring AI 2.0.x는 현재 프로젝트의 Spring Boot 4.1.x와 호환되는 선택지이고, `ChatClient.stream()`도 제공한다. 다만 현재 Knot 채팅은 MVC `SseEmitter`와 pull형 `LlmStream`을 사용하며, Import publication과 custom `search_document_chunks` schema를 이미 직접 통제한다. 이 단계에서 Spring AI를 넣으면 reactive stack과 Spring AI vector store abstraction을 추가로 맞춰야 하므로, 직접 JDK HTTP adapter와 JDBC 검색을 유지했다.

향후 다음 조건이 생기면 Spring AI를 다시 검토한다.

- 채팅 transport를 WebFlux/Reactive streaming으로 전환한다.
- 채팅·임베딩 provider를 여러 개 라우팅해야 한다.
- MCP tool calling과 prompt/tool 관측을 공통 abstraction으로 운영해야 한다.

참고: [Spring AI Getting Started](https://docs.spring.io/spring-ai/reference/getting-started.html), [ChatClient streaming](https://docs.spring.io/spring-ai/reference/api/chatclient.html), [OpenAI-compatible configuration](https://docs.spring.io/spring-ai/reference/2.0-SNAPSHOT/api/chat/openai-chat.html), [PgVector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)

## 검증 범위

- `./gradlew test`: chunking, hybrid ranking, no-result/broad question, embedding response, grounding prompt, Import 색인 실패 경계
- `./gradlew integrationTest`: pgvector vector/keyword query, Workspace 격리, unpublished run 차단, 전체 기존 integration 회귀
- `./gradlew acceptanceTest`: 기존 Notion Import/Page Tree acceptance 회귀
- 기존 #308 실행 기록(역사적): 30 tests passed, 10회 retrieval 구조/source gate 160/160, retrieval p50 343.0ms·p95 968.1ms

벤치마크 결과의 답변 의미·source 직접 관련성은 사람이 원문과 대조해야 한다. Java adapter와 실제 NIM을 연결한 뒤에는 동일 gold set으로 end-to-end TTFT와 answer/source 품질을 다시 측정한다.
