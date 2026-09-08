# Electron + Claude 구독 OAuth + 백엔드 RAG 아키텍처의 Knot 적용 가능성 검토

- 기준일: 2026-09-04
- 재확인: 2026-09-07 — Agent SDK overview 조항 문구는 동일하다. 확인 대상에 `legal-and-compliance`와 Agent SDK 구독 크레딧 지원 문서를 추가하고 2.1·5.1·8·9절을 보강했다. 1절 판정은 바뀌지 않았다
- 기준 커밋: `develop` `b1d4801` (`[BE] 채팅 답변 출처 조회 API 구현 (#351)`)
- 검토 대상: 「Electron + Claude 구독 OAuth + 백엔드 RAG 아키텍처」 문서(이하 "제안")
- 검토 범위: `backend/`, `frontend/`, `docs/`, `.github/workflows/`, Anthropic 공식 문서(code.claude.com, docs.claude.com)
- 후속 문서: [`electron-desktop-app-tech-plan.md`](./electron-desktop-app-tech-plan.md)(설계), [`electron-desktop-app-roadmap.md`](./electron-desktop-app-roadmap.md)(실행 정본)
- 관련 문서: [`adr/271-llm-search-architecture-benchmark.md`](./adr/271-llm-search-architecture-benchmark.md), [`llm-search-feature-spec.md`](./llm-search-feature-spec.md), [`llm-java-integration.md`](./llm-java-integration.md), [`adr/232-shared-subscription-token-local-ai-review.md`](./adr/232-shared-subscription-token-local-ai-review.md)

## 1. 결론

**제안은 현재 형태로 Knot에 적용할 수 없다.** 사유는 세 층위이며, 첫 번째만으로도 진행이 막힌다.

| 층위 | 판정 | 요지 |
| --- | --- | --- |
| 정책 | 차단 | Agent SDK 공식 문서가 "사전 승인 없이 서드파티 제품이 claude.ai 로그인·구독 한도를 제공하는 것"을 명시적으로 금지한다. `legal-and-compliance`는 여기에 더해 자격증명·세션 토큰의 수집·저장·중개를 금지하고 로그인이 Anthropic 자체 플로우에서 끝나야 한다고 못박는다. 제안의 핵심 전제(사용자 본인 구독으로 모델 실행)와 앱 내 로그인 안이 모두 여기에 걸린다. 같은 문서에 승인 없이 열린 좁은 경로가 하나 있으나 제안과 형태가 다르다(5.1). |
| 제품 형태 | 대규모 신규 개발 | Knot은 Cloudflare Workers에 배포되는 웹 SPA다. Electron 코드·의존성·서명·배포 파이프라인이 전혀 없다. 제안은 데스크톱 앱을 새로 만드는 일이다. |
| 백엔드 계약 | 역전 | Knot 백엔드는 검색뿐 아니라 LLM 호출, SSE 스트리밍, 답변·출처 영속화, 문서 준비 게이트, Workspace 격리 검증까지 소유한다. 제안은 이를 클라이언트로 옮기고 백엔드를 검색 전용으로 축소하므로, 이미 구현·검증된 계약이 대부분 무효화된다. |

**이미 충족하는 부분**: 제안의 설계 원칙 중 "벡터는 백엔드 밖으로 나가지 않는다", "모델은 검색된 텍스트 청크만 받는다", "credential은 모델에 넘기지 않는다"는 Knot이 서버 측에서 이미 지키고 있다. 제안이 "MVP용"으로 분류한 "사전 주입" 방식(Main이 먼저 검색해 프롬프트에 삽입, 툴 없음)이 곧 Knot의 현행 구조다. 위치만 Electron Main이 아니라 Spring 백엔드다.

**권고**: 목적이 "채팅 모델을 Claude로 바꾸는 것"이라면 Electron 없이 백엔드의 `LlmClient` 추상화에 Anthropic Messages API 어댑터를 추가하는 것이 가장 작은 변경이다(7절 B안·C안). 데스크톱 앱은 제품 요구가 생길 때 별도 결정으로 분리한다.

## 2. 검토 근거

### 2.1 Anthropic 공식 문서 확인 결과 (2026-09-04, 2026-09-07 보강)

| 확인 항목 | 결과 | 근거 |
| --- | --- | --- |
| Agent SDK에서 Claude Pro/Max 구독 OAuth 사용 | **금지(명시)** | Agent SDK overview: "Unless previously approved, Anthropic does not allow third party developers to offer claude.ai login or rate limits for their products, including agents built on the Claude Agent SDK. Use the API key authentication methods described in the Quickstart instead." — https://code.claude.com/docs/en/agent-sdk |
| Agent SDK가 지원하는 자격증명 | API 키(`ANTHROPIC_API_KEY`) 및 Bedrock·Vertex·Foundry 등 서드파티 provider | https://code.claude.com/docs/en/agent-sdk/quickstart |
| `claude setup-token`(`CLAUDE_CODE_OAUTH_TOKEN`) | Claude Code CLI 문서에만 있음. Agent SDK 문서에 없음. bare mode는 이 변수를 읽지 않음 | https://code.claude.com/docs/en/authentication |
| Claude Code 바이너리 번들·재배포 | SDK가 플랫폼별 npm optional dependency로 번들. `pathToClaudeCodeExecutable`로 외부 바이너리 지정 가능 | https://code.claude.com/docs/en/agent-sdk/hosting |
| Electron/asar 패키징 지침 | 공식 문서 없음 | — |
| 서드파티 앱에서 로그인 플로우 프로그래매틱 트리거 | 공식 문서 없음 | — |
| `createSdkMcpServer`, `tool`, `mcpServers`, `allowedTools`, `resume`, `systemPrompt`, `includePartialMessages`, `alwaysLoad` | 현재 옵션명과 일치 | https://code.claude.com/docs/en/agent-sdk/typescript, https://code.claude.com/docs/en/agent-sdk/mcp |
| SDK가 `claude` 서브프로세스를 띄우고 stdio로 통신, 세션 JSONL은 로컬 디스크 | 명시 | https://code.claude.com/docs/en/agent-sdk/hosting |
| 자격증명 저장 위치 | macOS Keychain, Linux `~/.claude/.credentials.json`(0600) | https://code.claude.com/docs/en/authentication |
| 자격증명·세션 토큰 취급 (2026-09-07 추가) | **금지(명시)** | Legal and compliance: "Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through Free, Pro, or Max plan credentials on behalf of their users. Moreover, developers may not collect, store, or intermediate Claude.ai credentials or session tokens — sign-in to a Claude account must complete through Anthropic's own flow." — https://code.claude.com/docs/en/legal-and-compliance |
| Claude Code를 제품에 preinstall·실행 (2026-09-07 추가) | 사전 승인과 별개로 **조건부 허용** | 같은 문서 "Can customers offer Claude Code in their products?": 상용 ToS 동의 + ① 바이너리 미변경, 내장 인증 수단 제거·비활성화 금지 ② 개발사가 대납·재판매·중개하지 않고 최종 사용자가 각자 자격증명(API 키, Claude 구독, Bedrock·Vertex·Foundry)으로 인증 |
| 최종 사용자가 자기 구독으로 로그인 (2026-09-07 추가) | 변경 없는 Claude Code 바이너리에 한해 **허용(명시)** | 같은 문서: "Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription, including where a platform hosts Claude Code" |
| Pro·Max 한도의 전제 (2026-09-07 추가) | 개인의 통상 사용 | 같은 문서: "Advertised usage limits for Pro and Max plans assume ordinary, individual usage of Claude Code and the Agent SDK." OAuth는 "intended exclusively for purchasers of Claude Free, Pro, Max, Team, and Enterprise subscription plans" |
| 서드파티 앱이 사용자 구독으로 인증하는 형태 (2026-09-07 추가) | 과금 카테고리로 존재. 구독 usage limit에서 차감 | 지원 문서 "Use the Claude Agent SDK with your Claude plan"(최종 수정 2026-06-16). 월 크레딧 도입은 2026-06-15 공지로 보류: "Claude Agent SDK, `claude -p`, and third-party app usage still draw from your subscription's usage limits" — https://support.claude.com/en/articles/15036540-use-the-claude-agent-sdk-with-your-claude-plan |
| 사전 승인 신청 절차 (2026-09-07 추가) | 공개된 폼·심사 기준·SLA 없음. 영업팀 문의가 유일한 문서화 창구 | Legal and compliance 말미: "For questions about permitted authentication methods for your use case, please contact sales" — https://www.anthropic.com/contact-sales |

제안 문서 7절이 "정책이 2026년 들어 여러 번 변경됨, 배포 직전 재확인"이라고 적어 둔 항목이 바로 이 조항이다. 현재 문구는 "허용 범위"가 아니라 "사전 승인 없이는 금지"다.

제안 8절의 Aside 대응표는 검증하지 않았다. Aside가 "previously approved" 상태인지, 별도 계약이 있는지는 공개 문서로 알 수 없고, 다른 제품이 그렇게 동작한다는 사실이 Knot의 허용 근거가 되지 않는다.

### 2.2 Knot 코드베이스 조사 범위

- backend: `chat/`, `search/`, `auth/`, `workspace/` 패키지, Flyway V5·V13 마이그레이션, `application.properties`, `build.gradle`
- frontend: `package.json`, `webpack.config.js`, `wrangler.jsonc`, `src/shared/api/**`, `src/modules/{widgets,features}/chat/**`, `src/shared/routes/**`, `src/shared/hooks/domain/chat/**`
- 워크플로우: `deploy-frontend-dev.yml`, `deploy-frontend-prod.yml`, `backend-ci.yml`, `deploy-backend-dev.yml`

## 3. Knot 현재 구조 (비교에 필요한 부분만)

### 3.1 제품 형태·배포

| 항목 | 현재 |
| --- | --- |
| 프론트 | React 19 + webpack 5 웹 SPA. `webpack.config.js`에 `target` 없음(브라우저 기본). Electron 관련 코드·의존성 0건 |
| 프론트 배포 | Cloudflare Workers 정적 자산(`frontend/wrangler.jsonc`의 `assets` + SPA fallback). `deploy-frontend-{dev,prod}.yml`이 `wrangler-action`으로 배포 |
| 백엔드 | Spring Boot 4.1, Java 25. LLM·HTTP 서드파티 SDK 없음(JDK `HttpClient` 직접 사용) |
| 운영 오리진 | `https://knoted.kr` (CORS 기본 허용 오리진) |

### 3.2 인증

| 항목 | 현재 |
| --- | --- |
| 로그인 | GitHub OAuth2 단독 (`window.location.href`로 전체 이동) |
| 토큰 | HS256 JWT를 **쿠키**로만 전달. 쿠키명 `__Host-KNOT_ACCESS_TOKEN`, `HttpOnly` `Secure` `SameSite=Lax` |
| 필터 | `JwtAuthenticationFilter`가 쿠키에서만 토큰을 읽음. Bearer 헤더 경로 없음 |
| CSRF | 활성. `X-XSRF-TOKEN` 헤더, `GET /api/v1/auth/csrf`로 발급. 프론트 axios 인터셉터가 변경 메서드에 자동 부착 |
| CORS | `AUTH_CORS_ALLOWED_ORIGINS`(기본 `https://knoted.kr`), 메서드 GET/POST/PUT/OPTIONS, `allowCredentials=true` |

### 3.3 채팅 파이프라인 (백엔드가 소유)

```text
POST /api/v1/conversations/{sessionId}/messages   (SseEmitter, 30s timeout)
  → ChatSessionAccessPolicy (세션 소유자 + Workspace 멤버)
  → ActiveChatStreamRegistry (세션당 동시 스트림 1개)
  → 사용자 메시지 저장
  → requirePublishedSnapshot  (없으면 CHAT_DOCUMENTS_NOT_READY 409, LLM 미호출)
  → PublishedDocumentSearchService
       broad-question 분류 → 벡터(0.7) + 키워드(0.3) 하이브리드 → 0.35 미만 제외
       → 문서 중복 제거 후 top-3
  → SearchContext.groundingPrompt (근거 규칙 + 근거 문서 ≤ 10,000자)
  → LlmClient (openai-compatible /chat/completions, 기본 fake)
  → SSE chunk 스트리밍
  → saveAssistantWithReferences (assistant 메시지 + search_references 같은 트랜잭션)
  → SSE complete(messageId)
GET  /api/v1/messages/{messageId}/sources           (출처 조회, Workspace 조인 검증)
```

- 임베딩: `OpenAiCompatibleEmbeddingClient` → `/embeddings`, Qwen3-Embedding 1,024차원 하드 게이트. 색인은 Notion import 워커가 `MarkdownChunker`(1,200자/겹침 180자)로 청크를 만들어 `search_document_chunks`(`vector(1024)`, HNSW cosine)에 저장.
- 출처: `search_references` 테이블. `reference_rank 1..3` CHECK, `(message_id, imported_page_id)` UNIQUE, Workspace 조인 INSERT…SELECT로 다른 Workspace 문서를 출처로 저장할 수 없게 강제.
- 프롬프트: `SearchContext.GROUNDING_INSTRUCTION`(근거 없으면 "찾지 못했다", 충돌 시 확정 금지 등 기능 기획서 7절 정책)이 서버 코드로 고정.
- 피드백: `chat_feedback` 테이블만 있고 API 없음(프론트 `AnswerFeedback`도 로컬 state).

### 3.4 프론트 채팅 계층

- `streamChatMessageApi`(fetch + async generator + `parseSseEvents`)가 `chunk`/`complete`/`error` 이벤트를 yield. `useSendChatMessageMutation`이 소비하고 `useChatStream` + `chatStreamContext`(`WorkspaceLayout`)가 진행 중 턴을 보관.
- 출처 UI(`SourceButton`, `SearchReferenceList`)는 있으나 데이터는 `MessageList/mock.ts`·`SearchReferenceList/mock.ts` 하드코딩. `GET /messages/{id}/sources` 연동 전.
- 테스트: vitest + msw(미등록 요청은 실패), Playwright는 webpack dev 서버만 띄우고 msw로 구동.

## 4. 제안 ↔ Knot 대응표

| 제안 구성요소 | Knot 현재 | 격차 | 판정 |
| --- | --- | --- | --- |
| Electron 앱 + Main 프로세스 | 없음. 웹 SPA | 앱 셸, 서명·공증, 자동 업데이트, 플랫폼별 빌드 전부 신규 | 신규 개발 |
| 채팅 웹뷰(로컬 번들 또는 원격 URL) | `knoted.kr` SPA의 `/workspace/:id/chat` | 원격 URL 변형이면 페이지 자체는 재사용 가능 | 부분 가능 |
| Main이 Agent SDK `query()`로 모델 호출 | 백엔드 `LlmClient`가 호출 | 호출 위치가 서버 → 사용자 PC로 이동 | 역전 |
| `~/.claude` 구독 크리덴셜 사용 | 서버 `LLM_API_KEY` 환경변수 | 정책상 사전 승인 필요 | 차단 |
| in-process MCP `search_knowledge` 툴 | 백엔드가 LLM 호출 전에 검색해 system prompt에 주입(툴 없음) | 검색을 REST로 노출해야 함. 현재 검색 서비스는 HTTP 엔드포인트가 없음 | 신규 API |
| `POST /v1/search` | 없음 | `PublishedDocumentSearchService`를 컨트롤러로 노출 | 신규 API |
| `POST /v1/index` | Notion import 워커가 색인 | 별도 엔드포인트 불필요. 그대로 사용 | 충족 |
| 백엔드 임베딩(별도 키) | Qwen 임베딩, 백엔드 보관 키 | 일치 | 충족 |
| 벡터가 클라이언트로 안 나감 | `search_references`·청크 응답에 벡터 없음 | 일치 | 충족 |
| 서비스 JWT Bearer로 백엔드 호출 | `__Host-` HttpOnly 쿠키 + CSRF | Main은 쿠키 JWT를 정상 경로로 못 받음. Bearer 필터 없음 | 인증 변경 |
| SDK `resume`로 세션 관리 | `chat_sessions`·`chat_messages` DB | 세션 진실이 로컬 JSONL과 DB 둘로 갈라짐 | 충돌 |
| 스트림을 웹뷰에 IPC/WS로 전달 | 백엔드 SSE → 브라우저 | 프론트 `streamChatMessageApi`·`useChatStream` 재작성 | 재작성 |
| 답변·출처 저장 | 백엔드가 스트림 종료 시 같은 트랜잭션에 저장 | 클라이언트가 답변을 만들어 서버에 써야 함 | 무결성 문제 |

## 5. 항목별 상세

### 5.1 정책·약관 (차단 사유)

- Agent SDK overview의 조항은 "사용자 본인 구독"인지 "공유 토큰"인지를 구분하지 않는다. 서드파티 제품이 claude.ai 로그인이나 구독 rate limit을 제공하는 것 자체를 사전 승인 대상으로 둔다. 제안 1절의 "앱은 토큰을 만지지 않으므로 중개가 아니다"라는 논리는 이 조항을 우회하지 못한다. 조항은 토큰 취급 방식이 아니라 제품이 구독 한도를 쓰게 하는 행위를 겨눈다.
- `claude setup-token`은 CI·스크립트용 Claude Code CLI 기능으로 문서화돼 있고, Agent SDK 문서에는 지원 언급이 없다. 제안 7절의 "터미널 로그인 안내 또는 앱 내 xterm.js 로그인"도 결국 같은 구독 크리덴셜을 SDK가 읽게 하는 것이라 정책 문제가 동일하다.
- Knot 팀은 이미 [`ADR 232`](./adr/232-shared-subscription-token-local-ai-review.md)에서 개인 구독 토큰 공유의 약관 위험을 부정 결과로 기록했다(당시 표현은 "저촉될 가능성"이었고, 2026-09-07 재확인으로 명시 문구가 확인돼 해당 ADR을 정정했다). 제안은 개발 도구가 아니라 최종 사용자 제품에 같은 위험을 옮기는 것이므로 노출 범위가 훨씬 넓다.
- **(2026-09-07 추가)** [`legal-and-compliance`](https://code.claude.com/docs/en/legal-and-compliance)는 같은 금지를 더 구체적으로 적는다. "Anthropic does not permit third-party developers to offer Claude.ai login into their own applications, or to route requests through Free, Pro, or Max plan credentials on behalf of their users. Moreover, developers may not collect, store, or intermediate Claude.ai credentials or session tokens — sign-in to a Claude account must complete through Anthropic's own flow." 제안 7절의 앱 내 xterm.js 로그인은 앞 절반에, 토큰을 Main이 보관·중개하는 변형은 뒤 절반에 직접 걸린다.
- **(2026-09-07 추가) 승인 없이 열린 좁은 경로가 하나 있다.** 같은 문서의 "Can customers offer Claude Code in their products?"는 상용 ToS 동의와 두 조건 아래 제품에 Claude Code를 동봉·실행하는 것을 허용한다. ① Claude Code 바이너리를 변경하지 않고 내장 인증 수단을 제거·비활성화하지 않는다. ② 개발사가 대납·재판매·중개하지 않고 최종 사용자가 각자 자격증명으로 인증한다. 이어서 "Nor does it prevent an end user from signing in to the unmodified Claude Code binary with their own Claude subscription, including where a platform hosts Claude Code"로 구독 로그인까지 명시한다. 다만 이 경로는 **제품이 변경 없는 Claude Code를 그대로 실행하고 사용자가 그것을 Claude Code로서 쓰는 형태**다. Agent SDK를 제품 로직에 묻어 Knot 채팅 UI로 감싸는 D안과 형태가 다르므로 D안의 대체가 되지 않는다. Knot이 이 경로를 쓰려면 제품이 "Knot 채팅"이 아니라 "Claude Code를 띄워 주는 셸"이 되어야 한다.
- **(2026-09-07 추가) 공개 문서 사이에 긴장이 있다.** 지원 문서 [`Use the Claude Agent SDK with your Claude plan`](https://support.claude.com/en/articles/15036540-use-the-claude-agent-sdk-with-your-claude-plan)은 "Third-party apps that authenticate with your Claude subscription through the Agent SDK"를 과금 대상 카테고리로 적고, 2026-06-15 공지는 그 사용량이 구독 usage limit에서 차감된다고 한다. 즉 "서드파티 앱이 사용자 구독으로 인증"이 아예 존재하지 않는 형태로 다뤄지지는 않는다. 이것이 Agent SDK overview의 "사전 승인 없이는 금지"와 어떻게 맞물리는지는 공개 문서만으로 확정할 수 없다. **이 미확정 지점이 8절 1번(서면 사전 승인)을 형식 절차가 아니라 실제 필요로 만든다.**
- **(2026-09-07 추가) 사전 승인 신청 절차는 공개돼 있지 않다.** 신청 폼, 심사 기준, 처리 기간 어느 것도 문서화된 것이 없다. `legal-and-compliance` 말미의 "For questions about permitted authentication methods for your use case, please contact sales"(https://www.anthropic.com/contact-sales)가 유일한 문서화된 창구이며, 같은 문서가 예외를 "Unless we've mutually agreed otherwise"로 표현하므로 개별 상용 계약을 뜻한다. 검색에 걸리는 Development Partner Program은 모델 개선용 데이터 공유 프로그램이라 이 승인과 무관하다.
- 폴백으로 제시된 `ANTHROPIC_API_KEY` 직접 입력(BYO key)은 정책상 허용된 경로다. 그러나 이 경로를 쓰면 Electron·Agent SDK를 둘 이유가 사라지고, 백엔드가 키를 보관해 호출하는 C안(7절)과 같은 결과가 된다.

### 5.2 제품 형태·배포

- Electron 도입 시 새로 필요한 것: 앱 셸·preload·IPC, macOS 서명·공증과 Windows 서명, 자동 업데이트 채널, 플랫폼별 SDK 바이너리(`@anthropic-ai/claude-agent-sdk-*` optional dependency) 동봉, `asarUnpack`·실행 권한 처리(공식 지침 없음, `pathToClaudeCodeExecutable`로 우회 가능), 크래시·로그 수집. FE 팀의 현재 배포 자산은 Cloudflare Workers 워크플로우 2개뿐이다.
- Knot의 사용자는 브라우저로 `knoted.kr`에 접속한다. 채팅을 Electron에서만 제공하면 웹 사용자는 핵심 기능(AI 문서 검색)을 잃는다. 웹 경로를 유지하면 백엔드 LLM 파이프라인과 Electron 파이프라인 두 벌을 운영해야 한다.
- 로컬 번들 페이지 변형은 오리진이 `file://`이라 `__Host-` 쿠키를 받을 수 없고 CORS 허용 오리진 검사도 통과하지 못한다. 원격 URL 변형만 현실적인데, 이 경우 채팅 페이지는 지금의 SPA 그대로이고 Electron이 추가로 하는 일은 Main의 모델 호출뿐이다.

### 5.3 인증 경계

- 제안은 Main이 "서비스 JWT"를 Bearer 헤더로 보낸다고 가정한다. Knot의 JWT는 HttpOnly 쿠키이고 백엔드 필터는 쿠키만 읽는다. Main이 백엔드를 호출하려면 다음 중 하나가 필요하다.
  - Electron `session.cookies.get`으로 HttpOnly 쿠키를 꺼내 `Cookie` 헤더로 재전송하고, CSRF 토큰도 `GET /api/v1/auth/csrf`로 받아 붙인다. 기술적으로 가능하지만 HttpOnly로 JS에서 격리한 토큰을 Node 프로세스로 옮기는 설계다.
  - 백엔드에 디바이스 토큰 발급 API와 Bearer 인증 경로를 추가한다. 인증 계층 변경이며 `ADR 314`(인증 진입 분기) 등 기존 결정을 다시 열게 된다.
- CSRF는 Main의 POST 검색 호출에도 적용된다. 현재 CORS 허용 메서드에 DELETE·PATCH가 없다는 점은 Main(브라우저 아님)에는 영향이 없다.

### 5.4 채팅 파이프라인·데이터 무결성

제안대로 모델 호출을 클라이언트로 옮기면 백엔드가 지금 하는 일 중 다음이 클라이언트로 가거나 새 API가 필요하다.

| 현재 백엔드 책임 | 제안에서의 위치 | 문제 |
| --- | --- | --- |
| 문서 준비 게이트(`CHAT_DOCUMENTS_NOT_READY`) | 검색 API 응답으로 옮김 | 가능. 단 클라이언트가 게이트를 무시하고 모델을 호출할 수 있음 |
| 하이브리드 검색·top-3·점수 컷 | 검색 API | `PublishedDocumentSearchService`를 컨트롤러로 노출하면 됨 |
| 근거 규칙 system prompt | Electron Main의 `systemPrompt` | 답변 정책(찾지 못함·충돌·범위 넓음)이 앱 버전마다 달라질 수 있음. 서버에서 일괄 수정 불가 |
| 세션 히스토리 조립 | Main이 DB 히스토리를 받아 조립하거나 SDK `resume`에 의존 | `resume`는 로컬 JSONL 기반이라 다른 기기·재설치 후 복원 불가 |
| assistant 메시지 저장 | 클라이언트가 답변 텍스트를 서버에 POST | 서버가 검증할 수 없는 "assistant" 콘텐츠를 사용자가 임의로 쓸 수 있음. 피드백·품질 평가 데이터의 신뢰성이 사라짐 |
| `search_references` 저장 | 클라이언트가 사용한 출처 ID를 보고 | 서버가 Workspace 조인으로 검증은 가능하나, 모델이 실제로 그 근거를 썼는지는 보증 불가 |
| SSE `chunk/complete/error` 계약 | 폐기 | 프론트 `streamChatMessageApi`, `useSendChatMessageMutation`, `useChatStream`, msw SSE 핸들러, 관련 테스트 재작성 |
| 세션당 스트림 1개 제한, 30초 타임아웃 | Main | 서버 보호 목적이 사라지고 클라이언트 자율에 맡겨짐 |
| TTFT·단계별 계측 | Main | 기능 기획서 12절 인수 조건("end-to-end 첫 답변 청크 시간과 단계별 시간 기록")을 서버가 만족할 수 없음 |

`GET /api/v1/messages/{messageId}/sources`는 그대로 쓸 수 있지만, 저장 주체가 서버에서 클라이언트로 바뀌므로 최근 커밋에서 추가한 Workspace 격리 회귀 테스트의 전제(서버가 검색 결과를 그대로 저장)가 바뀐다.

### 5.5 세션·다기기

- 기능 기획서 12절: "세션을 새로고침·재로그인 후에도 복원하고 현재 세션의 후속 질문만 사용한다." Knot은 이를 `chat_sessions`·`chat_messages`로 만족한다.
- 제안 5.1·5.2는 SDK `result.session_id`를 `resume`로 이어 붙인다. 이 세션은 사용자 PC의 `~/.claude` 아래 JSONL이다. 다른 PC나 웹에서 열면 없다. 결국 `resume`를 버리고 매 턴 DB 히스토리를 프롬프트에 다시 넣어야 하며, 그러면 제안의 세션 설계는 남지 않는다.
- 세션 ID가 둘(`chat_sessions.id`와 SDK session_id)이 되어 매핑 저장이 추가로 필요하다.

### 5.6 품질·지연·검증 자산

- `ADR 271`과 `llm-search-ab-test-report.md`의 벤치마크는 Qwen 임베딩 + `qwen/qwen3.6-27b` 기준이다. 채팅 모델이 Claude로 바뀌면 30개 이상 독립 질문 gold set과 사람 평가를 다시 돌려야 한다. 이는 모델을 서버에서 바꿔도 동일하므로 제안 고유의 비용은 아니다.
- 제안 고유의 지연 요인: `query()`마다 Claude Code 서브프로세스 기동, 툴 루프로 인한 모델 왕복 2회(검색 요청 → 툴 결과 → 답변). Knot 현행은 검색을 먼저 끝내고 모델을 1회 호출한다. 5초 TTFT 목표 대비 실측이 없다.
- `ADR 271`은 "질의마다 실시간 MCP 툴 호출"을 지연 때문에 기본 경로에서 제외했다. 제안의 툴 대상은 Notion이 아니라 로컬 pgvector(약 0.2초)라 같은 결론을 그대로 적용할 수는 없지만, 툴 루프 자체가 왕복을 늘린다는 방향은 같다.
- 모델 재검색(제안 3절의 "5~9 반복")은 장점이지만, 서버 측에서도 Messages API tool use로 동일하게 구현할 수 있다.

### 5.7 사용자 요건·운영

- 제안은 모든 사용자가 Claude Pro/Max 이상 구독과 Claude Code 로그인 상태를 갖춰야 한다. Knot의 권한 모델(기능 기획서 4절)은 "소유자가 승인한 문서를 Workspace 구성원이 함께 검색"이다. 구독이 없는 팀원은 검색을 못 하고, 구독 한도(5시간 창)는 Knot이 관측·제어할 수 없다.
- 로그인 UX를 프로그래매틱하게 띄우는 공식 방법이 없다. 제안 7절의 xterm.js 패널은 사용자에게 터미널 로그인을 노출하는 것이다.
- 지원 창구가 분산된다. "답변이 안 나온다"의 원인이 Knot 백엔드, 사용자 PC의 Claude Code 상태, 구독 한도 중 어디인지 서버 로그로 알 수 없다.

### 5.8 제안 문서의 기술 서술 정확성

| 서술 | 판정 | 비고 |
| --- | --- | --- |
| SDK가 번들 바이너리를 서브프로세스로 띄우고 `~/.claude`(macOS는 키체인)를 읽는다 | 정확 | hosting·authentication 문서와 일치 |
| `createSdkMcpServer`, `tool`, `mcpServers`, `allowedTools`, `resume`, `systemPrompt`, `includePartialMessages` | 정확 | 현재 옵션명 |
| `tools: []`로 내장 툴 제외 | 옵션 존재 | `tools`는 문자열 배열 또는 preset |
| `allowedTools` 누락 시 모델이 툴을 안 씀 | 정확 | `mcp__{server}__{tool}` 형식 |
| tool search 지연 로딩과 `alwaysLoad: true` | 정확 | mcp 문서 "Connection timing" |
| `stream_event` + `content_block_delta`로 텍스트 스트리밍 | 성립 | `includePartialMessages: true`일 때 원시 API 스트림 이벤트가 `stream_event`로 전달됨. 문서 예시는 `assistant` 메시지 단위 |
| Anthropic에 임베딩 API가 없어 백엔드가 별도 키로 임베딩 | 정확 | Knot의 Qwen 임베딩 구조와 일치 |
| "변형되지 않은 바이너리 + 본인 구독 로그인은 허용 범위였다" | 현재 문서와 불일치 | 2.1절 인용 참조 |
| `claude setup-token`을 앱 로그인 폴백으로 사용 | 문서 근거 없음 | CLI 문서만 존재, SDK bare mode는 읽지 않음 |
| Electron `asarUnpack` 처리 | 공식 지침 없음 | `pathToClaudeCodeExecutable`로 외부 경로 지정은 가능 |
| Aside가 같은 구조로 구독을 쓴다 | 검증 불가 | Knot의 허용 근거로 쓸 수 없음 |

## 6. 이미 충족하거나 가져올 수 있는 것

- 토큰 경계: Notion OAuth 토큰을 `ContentSourceSecretProtector`로 암호화 보관하고 모델 요청에 넣지 않는다. 제안 1절의 원칙과 같은 방향이다.
- 벡터 경계: `search_document_chunks`의 벡터는 어떤 API 응답에도 실리지 않는다. `search_references`는 문서 메타데이터와 점수만 돌려준다.
- 사전 주입 방식: 제안 6절 표의 세 번째 행이 Knot 현행이다. 제안이 단점으로 든 "매 턴 강제 검색, 재검색 불가"는 사실이며, 필요하면 서버 측 tool use로 재검색 루프를 넣을 수 있다.
- 검색 REST 노출: 제안의 `POST /v1/search`는 정책과 무관하게 유용할 수 있다. `PublishedDocumentSearchService`를 컨트롤러로 노출하면 벤치마크 도구(`tools/llm-benchmark`)나 향후 다른 클라이언트가 같은 검색을 재사용할 수 있다. 단 현재 요구는 없으므로 별도 Issue로 다룬다.
- 원격 MCP 서버(제안 6절 두 번째 행): 백엔드가 MCP transport를 제공하면 팀원의 Claude Code나 Cursor에서 Knot 지식베이스를 쓸 수 있다. 이 경우 모델 호출은 각자의 개발 도구 안에서 일어나므로 Knot 제품이 구독을 제공하는 것이 아니다. 개발자용 부가 기능으로는 검토 여지가 있다.

## 7. 대안 비교

| 안 | 모델 호출 위치 | 인증·과금 | 정책 | 변경 범위 | 비고 |
| --- | --- | --- | --- | --- | --- |
| A. 현행 유지 | 백엔드 → OpenAI 호환(LM Studio / NVIDIA NIM) | `LLM_API_KEY` 서버 보관 | 문제 없음 | 없음 | `llm-java-integration.md` 기준 NIM 운영 검증 진행 중 |
| B. 백엔드 Anthropic 어댑터 | 백엔드 → Anthropic Messages API | Console API 키 서버 보관, Knot 과금 | 허용 경로 | `AnthropicLlmClient`(+`LlmStream` 구현) 추가, `LlmClientConfig`에 `llm.provider=anthropic` 분기, 설정 키 | 임베딩은 Qwen 그대로. `SearchContext.groundingPrompt`가 `system`, 히스토리가 `messages`로 1:1 대응. 스트리밍은 SSE `text_delta`를 기존 pull형 `LlmStream`으로 감쌈 |
| C. Workspace BYO API 키 | 백엔드 → Anthropic (Workspace 소유자 키) | 소유자가 Console 키 등록, 백엔드가 Notion 토큰과 같은 방식으로 암호화 보관 | 허용 경로 | B + 키 등록 API·UI, Workspace별 키 선택, 폐기·재등록 | 팀별 과금. 키 유출 시 폐기 UX 필요. `ADR 271`의 "Workspace별 credential 선택" 경계를 재사용 |
| D. 제안 (Electron + 구독) | 사용자 PC Main → Agent SDK | 사용자 구독 | **사전 승인 없이 금지** | Electron 앱 신규, 검색·저장 API 신설, 인증 경로 추가, FE 스트림 계층 재작성, 웹 경로 별도 유지 | 5절 전부 해당 |

권고 순서는 B → C다. 두 안 모두 기존 `LlmClient`·`LlmStream` 추상화가 provider 교체를 전제로 설계돼 있어 채팅 파이프라인·SSE 계약·영속화·테스트를 건드리지 않는다. 모델은 현재 문서 기준 `claude-opus-5`를 기본으로 두고 `effort`로 비용을 조정한다. Java에는 공식 SDK(`com.anthropic:anthropic-java`)가 있으나, 현재 백엔드가 JDK `HttpClient`만 쓰고 있으므로 SDK 도입 여부는 구현 Issue에서 정한다.

## 8. 그래도 D안을 진행한다면: 전제 조건

아래가 모두 충족되기 전에는 착수하지 않는다.

1. Anthropic으로부터 "claude.ai 로그인·구독 한도를 제품에 제공"하는 것에 대한 서면 사전 승인. 승인 범위(구독 종류, 사용자 수, 재배포 형태)를 명시. 문서화된 창구는 영업팀 문의뿐이다(https://www.anthropic.com/contact-sales). 5.1절의 미확정 지점 때문에 "구독 로그인을 승인해 달라"보다 **"우리 형태가 허용 범위에 드는지"**로 묻는 편이 답을 얻기 쉽다.
2. 제품 형태 결정: 데스크톱 전용인지, 웹과 병행인지. 병행이면 백엔드 LLM 경로 유지 비용을 수용.
3. 백엔드 신규 API: `POST /api/v1/workspaces/{id}/search`(검색 노출), 클라이언트 생성 assistant 메시지·출처 저장 API(서버 검증 규칙 포함), 디바이스 토큰 발급·Bearer 인증 경로.
4. 무결성 정책: 클라이언트가 쓴 답변을 서버가 어떻게 표시·검증·감사할지. 피드백 데이터의 신뢰 범위.
5. 품질 재검증: 30개 이상 독립 질문 gold set을 Claude + 툴 루프 구성으로 재측정. TTFT 5초 목표 실측.
6. 공통 Issue 계약(`docs/harness/issue-planning.md`)상 인증·외부 credential·데이터 경계를 바꾸는 고위험 작업이므로 `$knot-issue-planning` → 인터뷰 → Grill → ADR 경로를 거친다.

## 9. 참고

- Agent SDK overview (정책 조항): https://code.claude.com/docs/en/agent-sdk
- Agent SDK quickstart (인증): https://code.claude.com/docs/en/agent-sdk/quickstart
- Agent SDK hosting (서브프로세스·바이너리): https://code.claude.com/docs/en/agent-sdk/hosting
- Agent SDK TypeScript 레퍼런스: https://code.claude.com/docs/en/agent-sdk/typescript
- Agent SDK MCP (SDK MCP 서버, `alwaysLoad`): https://code.claude.com/docs/en/agent-sdk/mcp
- Claude Code 인증 (`setup-token`, 크리덴셜 저장): https://code.claude.com/docs/en/authentication
- Legal and compliance (자격증명 사용 조항, Claude Code 동봉 조건, 영업팀 문의): https://code.claude.com/docs/en/legal-and-compliance
- Use the Claude Agent SDK with your Claude plan (서드파티 앱 구독 인증 과금, 2026-06-15 보류 공지): https://support.claude.com/en/articles/15036540-use-the-claude-agent-sdk-with-your-claude-plan
- Anthropic 영업팀 문의: https://www.anthropic.com/contact-sales
- Knot: [`adr/271-llm-search-architecture-benchmark.md`](./adr/271-llm-search-architecture-benchmark.md), [`llm-search-feature-spec.md`](./llm-search-feature-spec.md), [`llm-java-integration.md`](./llm-java-integration.md), [`adr/232-shared-subscription-token-local-ai-review.md`](./adr/232-shared-subscription-token-local-ai-review.md)
