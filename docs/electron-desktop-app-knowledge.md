# Knot 데스크톱 앱(Electron) 기술 지식 정리

- 기준일: 2026-09-04
- 기준 커밋: `develop` `b1d4801`
- 용도: [`electron-desktop-app-tech-plan.md`](./electron-desktop-app-tech-plan.md)(기술 기획서)를 쓰기 위해 수집한 사실·지식의 정본. 기획서는 이 문서를 `지식 §n`으로 참조한다.
- 선행 문서: [`llm-electron-subscription-architecture-review.md`](./llm-electron-subscription-architecture-review.md)
- 실행 정본: [`electron-desktop-app-roadmap.md`](./electron-desktop-app-roadmap.md) — 이 문서의 미확인 항목(8절) 중 작업을 막는 것은 로드맵 6절에서 추적한다.
- 부록: [`electron-desktop-app-packaging-research.md`](./electron-desktop-app-packaging-research.md) — 패키징·서명·배포·업데이트·CI 상세 조사(633줄). 본문 3절은 그 요약이다.

## 0. 수집 방법과 표기

| 영역 | 방법 |
| --- | --- |
| Knot 저장소 사실(1절) | `backend/`, `frontend/`, `.github/`, `docs/`, `harness/`를 직접 읽음. 값은 파일에서 인용, 경로:라인 표기 |
| Electron·표준·GitHub·Spring·Apple·Microsoft(2~5절) | 공식 문서를 2026-09-04에 WebFetch/curl로 직접 조회. 버전은 npm registry·GitHub Releases API로 재확인 |
| Claude API(6절) | Claude Code 번들 `claude-api` 스킬(모델 표 캐시 2026-06-24) + 검토 문서의 2026-09-04 공식 문서 확인 |

표기 범례:

- **[확인]/[문서]** 출처에서 직접 읽은 내용. 인용은 원문 유지.
- **[추론]** 문서를 조합하거나 Knot 상황에 적용한 해석. 구현 전 실측이 필요할 수 있다.
- **[비공식]** GitHub 이슈·서드파티 자료. 공식 문서가 아님.
- **미확인** 조사했으나 확인하지 못한 항목. 각 절 끝과 8절에 모았다.

문서 지도:

| 절 | 내용 |
| --- | --- |
| 1 | Knot 현재 상태: 제품·배포, 인증, API, 채팅·LLM, 프론트 구조, 워크플로우·컨벤션, 문서·ADR, 즉시 걸리는 제약, 프론트 빌드 |
| 2 | Electron 코어·보안: 버전, 프로세스·IPC, 보안 체크리스트·Fuses, 원격 vs 로컬, 세션·쿠키·네트워크, 커스텀 스킴, OS 통합, 개발 도구 |
| 3 | 패키징·서명·배포·업데이트·CI·테스트·관측 요약(부록 참조) |
| 4 | 데스크톱 인증 패턴: RFC 8252/7636/9700/OAuth 2.1/8628, GitHub OAuth 변경, 로그인 패턴 4종, Spring Security, 쿠키 규칙, 토큰 저장 |
| 5 | 셸 대안 비교(Electron/Tauri/PWA), 원격 로드 사례, 프론트 라이브러리 특이사항, 원격 MCP 서버 |
| 6 | Claude API·Anthropic 정책: 모델·가격, 파라미터, 스트리밍, tool use, Java SDK, 비용 |
| 7 | 참고 링크 총목록 |
| 8 | 결정에 영향을 주는 미확인 항목 통합 |

## 1. Knot 현재 상태 (저장소 사실, develop `b1d4801`, 2026-09-04 조사)

모든 항목은 파일에서 직접 읽은 값이다. 경로는 저장소 루트 기준, 백엔드 Java 경로는 `backend/src/main/java/com/knot/backend/` 이하를 생략했다.

### 1.1 제품·도메인·배포 형태

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 제품 | "팀 프로젝트에서 문서화의 병목화를 최소화 해주는 앱". 핵심 기능은 Notion 문서를 색인한 AI 문서 검색 채팅 | `frontend/CLAUDE.md:3`, `docs/llm-search-feature-spec.md` |
| 루트 README | 0바이트(제품 설명 없음) | `README.md` |
| 프론트 오리진 | 운영 `https://knoted.kr`, 개발 `https://dev.knoted.kr` (Cloudflare Workers 정적 자산 + SPA fallback) | `frontend/wrangler.jsonc`, `deploy-backend-dev.yml:1204` |
| 백엔드 API 오리진 | **정정 2026-09-06(`A1` 실측)**: 배포된 dev SPA(`https://dev.knoted.kr`)가 실제로 이동하는 오리진은 `https://dev-api.knoted.kr`이다. `frontend/.env.local:3`의 `https://api.dev.knoted.kr`은 gitignore된 로컬 파일 값이고 배포 빌드는 `vars.API_BASE_URL_DEV`를 쓴다 — 둘이 다르다. 운영은 `vars.API_BASE_URL_PROD`(GitHub 변수)라 저장소에 값이 없음. **프론트와 API는 크로스 오리진**(같은 오리진 프록시 아님) | Electron 셸 네비게이션 로그(`~/Library/Logs/Knot/main.log`, GitHub 로그인 버튼 클릭 시 `https://dev-api.knoted.kr/oauth2/authorization/github`), `frontend/.env.local`, `httpClient/index.ts:67` 주석 |
| Notion OAuth 302 체인(앱 창 실측) | **2026-09-08(`A1` 실측, 부분)**: `POST …/notion-oauth-authorizations` 응답의 `authorizationUrl`은 `https://api.notion.com/v1/oauth/authorize?client_id=…&response_type=code&owner=user&redirect_uri=<callback>&state=…`이고, 이 URL은 302로 `https://app.notion.com/install-integration?response_type=code&client_id=…&redirect_uri=…&state=…&owner=user`에 보낸다. `www.notion.so`는 관측되지 않았다. 동의 이후 홉은 셸이 2홉을 차단해 앱 창 안에서 미측정 → **2026-09-08 23:21 재측정**: `app.notion.com` 추가 뒤 `install-integration` → `api/v3/sessionSync?returnUrl=…&sessionSyncId=…&csrfNonce=…` → `api/v3/sessionSyncCallback?status=unauthenticated&returnUrl=…` → `install-integration?…&session_sync_attempted=1`(전부 `app.notion.com`)까지 통과. 미로그인이라 로그인 화면이 떴고 IdP 버튼은 `window.open("https://app.notion.com/verifyNoPopupBlockerHtmlAndRedirect?redirectUri=https://app.notion.com/microsoftpopupredirect?callbackType=popup&redirectToAuth=true&popupFlowId=…")`을 연다. 검증 페이지는 `window.opener`가 없으면 `window.close()`한다(본문 curl 확인). `<idp>popupredirect` 302: microsoft → `login.microsoftonline.com/common/oauth2/v2.0/authorize`, google → `accounts.google.com/o/oauth2/v2/auth`, apple → `appleid.apple.com/auth/authorize`(form_post), 복귀는 각각 `app.notion.com/<idp>popupcallback`. **2026-09-08 23:41 종단 통과**: 자식 창 팝업 → `login.microsoftonline.com/common/oauth2/v2.0/authorize` → `…/common/login` → `app.notion.com/microsoftpopupcallback?code=…`, 메인 창 동의 → `localhost:8080/api/v1/notion/oauth/callback?code=…&state=…` → 302 `localhost:3000/workspace/6/notion-connection?result=connected`. 차단 0건, DB 연결 행·동기화 COMPLETED | Electron 셸 네비게이션 로그(`~/Library/Logs/Knot/main.log` 2026-09-07 11:37·2026-09-08 10:33 `will-redirect` 차단 기록, 2026-09-08 23:21 `새 창 요청 거부` 3건, 23:40:35~23:41:09 종단 통과 기록), `app.notion.com/verifyNoPopupBlockerHtmlAndRedirect`·`*popupredirect` curl 실측(2026-09-08), `workspace/infrastructure/notion/oauth/HttpNotionOAuthClient.java` `createAuthorizationUri` |
| 백엔드 런타임 | Spring Boot 4.1.0, Java 25(temurin), PostgreSQL + pgvector(`pgvector/pgvector:pg18`), Flyway, springdoc 3.1.0 | `backend/build.gradle:3,13`, `backend/compose.yml` |
| 백엔드 배포 | develop → NCP 단일 서버(SSH/SCP, systemd `knot-backend.service`, 포트 8080), main → AWS CodeDeploy(`buildspec.yml`, `backend/appspec.yml`) | `deploy-backend-dev.yml`, ADR 212·328 |
| 리버스 프록시·TLS 설정 | 저장소에 없음(서버 밖 관리) | 전체 탐색 |
| Electron·Tauri 관련 코드·의존성 | 0건 | `grep -rniE "electron|tauri"` |

### 1.2 인증 (JWT 쿠키·OAuth·CSRF·CORS)

**JWT 쿠키**

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 쿠키 이름 | `__Host-KNOT_ACCESS_TOKEN` (access), `KNOT_NICKNAME_TOKEN` (온보딩) | `global/config/JwtProperties.java:17-18` |
| 속성 | `HttpOnly=true`, `Secure=${JWT_COOKIE_SECURE:true}`, `SameSite=Lax`, `Path=/` | `auth/presentation/AuthCookieManager.java:78-82` |
| 만료 | access `PT1H`, 온보딩 `PT10M`. **리프레시 토큰 없음**(만료 시 재로그인) | `JwtProperties.java:13-14`, `auth/domain/AuthTokenProvider.java` |
| 알고리즘 | HS256, issuer `https://knoted.kr`, audience `knot-api`, 타입 `ACCESS`/`ONBOARDING` | `auth/infrastructure/jwt/JwtProvider.java:31-35` |
| `__Host-` 검증 | `secure=false`면 기동 실패 | `JwtProvider.validateCookieName` `:213-223` |
| 필터 | `JwtAuthenticationFilter.findToken`이 **쿠키에서만** 읽음. `Authorization: Bearer` 경로 없음. 파싱 실패는 익명 통과 | `auth/infrastructure/jwt/JwtAuthenticationFilter.java:57-73` |
| 로그아웃 | `POST /api/v1/auth/logout`(LogoutFilter), 두 쿠키를 `Duration.ZERO`로 만료. 성공 핸들러 미지정이라 Spring 기본 302. **프론트에 로그아웃 호출 코드 없음** | `SecurityConfig.java:142`, `JwtLogoutHandler.java:22-23` |
| 세션 정책 | `IF_REQUIRED`(OAuth2 authorization request 저장용). 로그인 성공·실패 핸들러가 세션을 `invalidate()` | `SecurityConfig.java:135`, `OAuth2AuthenticationSuccessHandler.java:104-110` |

**GitHub OAuth2 로그인**

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 진입 | `GET {API}/oauth2/authorization/github` — 프론트가 `window.location.href`로 전체 이동 | `GithubLoginButton/index.tsx:12,29-31` |
| 콜백 | `/login/oauth2/code/github` (Spring 기본) | 같은 파일 주석 `:10` |
| scope | `read:user`, 클라이언트 `${GITHUB_CLIENT_ID}`/`${GITHUB_CLIENT_SECRET}` | `application.properties:32-34` |
| 성공 분기 | `requiresNickname()`이면 nickname 쿠키 + `nickname-redirect-uri`, 아니면 access 쿠키 + `success-redirect-uri` | `OAuth2AuthenticationSuccessHandler.java:49-94` |
| 리다이렉트 설정 | `auth.oauth2.success-redirect-uri=${AUTH_SUCCESS_REDIRECT_URI:/api/v1/auth/me}`, `nickname-redirect-uri=${...:/onboarding}`, `failure-redirect-uri=${...:/login?error=oauth2}` — **고정 문자열 3개, 요청별 목적지 지정 불가** | `application.properties:93-96`, ADR 314 |
| 실패 | 예외 타입 로그 후 컨텍스트 정리, 실패 URI로 302. 성공 핸들러 내부 예외도 같은 경로 | `OAuth2AuthenticationFailureHandler.java:34-59` |
| 온보딩 완료 | `POST /api/v1/auth/nickname`이 `@CookieValue` 온보딩 토큰을 받아 access 쿠키 발급 | `AuthController.java:41-63` |
| 프론트 진입 분기 | `/`의 `EntryRedirect`가 `useMeQuery` + `useWorkspacesQuery`로 `/login`·`/workspace`·`/workspace/:id` 결정 | `src/shared/routes/EntryRedirect/index.tsx:29-77` |

**CSRF**

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 저장소 | `CookieCsrfTokenRepository.withHttpOnlyFalse()`, 쿠키 `XSRF-TOKEN`(HttpOnly=false, Secure, SameSite=Lax, Path=/) | `SecurityConfig.java:88-93` |
| 헤더 | `X-XSRF-TOKEN` | `OpenApiConfig.java:30` |
| 발급 | `GET /api/v1/auth/csrf` → `{"token": "..."}` (permitAll) | `AuthController.java:36-39`, `CsrfTokenResponse.java` |
| 적용 | GET/HEAD/OPTIONS/TRACE 제외 전부. 프론트는 post/put/patch/delete에 부착, 403이면 1회 재발급 재시도 | `httpClient/index.ts:12,73-79,87-103` |
| SSE fetch | `streamChatMessageApi`가 `getCsrfToken()`으로 같은 토큰을 헤더에 넣음 | `.../messages/index.ts:110-124` |

**CORS**

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 설정 키 | `auth.cors.allowed-origins=${AUTH_CORS_ALLOWED_ORIGINS:${AUTH_CORS_ALLOWED_ORIGIN:https://knoted.kr}}` | `application.properties:65` |
| dev 주입값 | `https://dev.knoted.kr,http://localhost:3000` | `deploy-backend-dev.yml:1204` |
| 메서드 | `GET, POST, PUT, OPTIONS` (DELETE·PATCH 없음) | `SecurityConfig.java:57-64` |
| 헤더 | 허용 `Content-Type`, `X-XSRF-TOKEN`; 노출 `Retry-After`; `allowCredentials=true`; 경로 `/**` | `SecurityConfig.java:65-78` |
| 검증 | 오리진이 비었거나 `*`이면 기동 실패(`OAUTH_CONFIGURATION_INVALID`) | `SecurityConfig.java:50-54,84-86` |

**permitAll 경로**: `/oauth2/**`, `/login/**`, `/api/v1/auth/nickname`, `/api/v1/auth/csrf`, `/actuator/health`, `/error`, GET `/api/v1/invitations/*`, GET `/api/v1/notion/oauth/callback`, (dev profile) swagger 경로. 나머지는 `authenticated()`. 401은 `AuthAuthenticationEntryPoint`(JSON), 403은 `AuthAccessDeniedHandler`(JSON).

### 1.3 API 엔드포인트 (전체)

| 메서드 | 경로 | 비고 |
| --- | --- | --- |
| GET | `/api/v1/auth/me` | 로그인 판정에 사용 |
| GET | `/api/v1/auth/csrf` | permitAll |
| POST | `/api/v1/auth/nickname` | permitAll, 온보딩 쿠키 필요 |
| POST | `/api/v1/auth/logout` | LogoutFilter, 302 |
| GET | `/oauth2/authorization/github` | Spring OAuth2 진입 |
| POST/GET | `/api/v1/workspaces`, GET `/api/v1/workspaces/{id}` | |
| POST/GET | `/api/v1/workspaces/{id}/invitations`, GET `/invitation`, POST `/invitations/reissue` | |
| GET | `/api/v1/invitations/{tokenOrCode}` | permitAll |
| POST | `/api/v1/invitations/accept` | |
| PUT | `/api/v1/members/me/last-viewed-workspace` | |
| POST | `/api/v1/workspaces/{id}/notion-oauth-authorizations` | `notion.oauth.enabled=true`일 때만 |
| GET | `/api/v1/notion/oauth/callback` | permitAll |
| GET | `/api/v1/workspaces/{id}/notion-connection`, `/notion-pages/tree` | |
| POST | `/api/v1/workspaces/{id}/imports`; GET `/api/v1/imports/{runId}`; POST `/imports/{runId}/retry` | |
| POST/GET | `/api/v1/workspaces/{id}/conversations` | |
| GET | `/api/v1/conversations/{sessionId}` | |
| **POST** | **`/api/v1/conversations/{sessionId}/messages`** | `text/event-stream`, SseEmitter 30초 |
| GET | `/api/v1/messages/{messageId}/sources` | 출처 조회 |
| GET | `/actuator/health` | |

OpenAPI: `/v3/api-docs`, `/swagger-ui.html`. 기본 비활성(`knot.api-docs.enabled=false`), `application-dev.properties`에서만 활성. 보안 스키마 `accessTokenCookie`·`csrfTokenHeader`. Try-it-out 비활성.

### 1.4 채팅·LLM 파이프라인 (백엔드 소유)

**LLM 추상화**

```java
public interface LlmClient { LlmStream start(LlmRequest request); }
public interface LlmStream extends AutoCloseable { boolean hasNext(); String next(); void close(); }
```

| 구현체 | 조건 | 경로 |
| --- | --- | --- |
| `FakeLlmClient` | `llm.chat.provider=fake` (기본, `matchIfMissing=true`) | `chat/infrastructure/FakeLlmClient.java:11` |
| `OpenAiCompatibleLlmClient` | `llm.chat.provider=openai-compatible` → `POST {base-uri}/chat/completions` (`stream:true`) | `chat/infrastructure/LlmClientConfig.java:20-34` |
| `AnthropicLlmClient` | `llm.chat.provider=anthropic` → `POST {llm.anthropic.base-uri}/v1/messages` (`stream:true`, `x-api-key`·`anthropic-version: 2023-06-01`, `output_config.effort`, `temperature`·`thinking` 없음). `AnthropicLlmStream`은 `text_delta`만 chunk로 넘기고 `message_stop`에서 끝난다. HTTP 401/403·스트림 `authentication_error`/`permission_error` → `LLM_CONFIGURATION_INVALID`, 429/529·`rate_limit_error`/`overloaded_error` → `LLM_RATE_LIMITED`, `stop_reason=refusal` → `LLM_REFUSED`, header 타임아웃 → `LLM_STREAM_TIMEOUT`. `cache_control` 없음(로드맵 U18) | `chat/infrastructure/LlmClientConfig.java:41-55`, `chat/infrastructure/anthropic/AnthropicLlmClient.java:21,79-86`, `AnthropicLlmStream.java:78-83,123,161`, `chat/domain/ChatErrorCode.java:141-151` |
| HTTP | JDK `HttpClient`(`connectTimeout`, `followRedirects(NEVER)`). 채팅은 `chatLlmHttpClient`(provider별 `@Bean` 메서드가 같은 이름으로 하나만 등록), 임베딩은 `embeddingLlmHttpClient`로 빈이 나뉜다. 서드파티 LLM SDK 없음(Anthropic도 직접 호출, 로드맵 Q20) | `LlmClientConfig.java:20-24,41-45,61`, `EmbeddingClientConfig.java:19-27` |
| 임베딩 | `llm.embedding.provider`가 `gemini`면 `GeminiEmbeddingClient`(`POST {llm.gemini.base-uri}/v1beta/models/{model}:batchEmbedContents`, `x-goog-api-key`, `taskType` 색인 `RETRIEVAL_DOCUMENT`/질의 `RETRIEVAL_QUERY`, `outputDimensionality=llm.embedding.dimensions`, 응답을 L2 정규화. 401/403 → `SEARCH_CONFIGURATION_INVALID`, 그 외 → `SEARCH_PROVIDER_FAILED`. 색인 배치의 429·503은 `llm.gemini.retry-*`대로 지수 백오프 재시도, 질의는 재시도 없음 — 정정 2026-09-08 로드맵 Q42), `openai-compatible`이면 `OpenAiCompatibleEmbeddingClient`(접속 설정 `llm.base-uri`·`api-key`·`request-timeout`은 채팅과 공유), `fake`(기본)면 `FakeDocumentEmbeddingClient`. 세 구현 모두 1,024차원 하드 게이트(V13). `DocumentEmbeddingClient.embed(texts, EmbeddingTask)`로 용도를 받으며 `gemini`만 이를 `taskType`으로 쓴다(정정 2026-09-08, `B5`) | `search/infrastructure/EmbeddingClientConfig.java`, `search/infrastructure/gemini/GeminiEmbeddingClient.java` |

> 정정 2026-09-06(`B0` 구현): provider 스위치가 `llm.provider` 하나에서 `llm.chat.provider`/`llm.embedding.provider` 둘로 나뉘었고 `llmHttpClient` 공유 빈이 사라졌다. `llm.provider`는 두 키의 fallback으로 남는다(`application.properties:38-40`).
>
> 정정 2026-09-07(`B1` 구현): `LlmClientConfig`의 provider 조건이 클래스 수준에서 메서드 수준으로 내려가 `anthropic` 분기가 붙었다. `ChatErrorCode`에 `LLM_RATE_LIMITED`·`LLM_REFUSED`가 추가됐고, `ChatMessageService`는 `LlmClient`·`LlmStream`이 던진 `ChatException`의 코드를 `LLM_STREAM_FAILED`로 바꾸지 않고 SSE `error`에 그대로 싣는다(`ChatMessageService.java:209`, 로드맵 7절 1번 예외). `usage` 토큰 수는 INFO 로그로만 남는다.
>
> 정정 2026-09-07(`S1` 구현): 데스크톱 경유 탐색용 검색 API `POST /api/v1/conversations/{sessionId}/search`(`chat/presentation/ChatSearchController.java`, `chat/application/ChatSearchService.java`)가 추가됐다. 소유자 검증 → `ActiveChatStreamRegistry` 잠금 → 스냅샷 검사 → DB 기준 진행 중 턴 검사(마지막 메시지가 USER이고 `chat.turn-timeout` 안이면 409 `CHAT_TURN_IN_PROGRESS`) → 검색 → USER 저장(READY가 아니면 안내 ASSISTANT도 `saveFallbackTurn`으로 같은 트랜잭션) 순서이며 LLM을 부르지 않는다. `PublishedDocumentSearchService.selectSources`는 페이지 중복 제거 없이 청크 상위 `top-k`(8)를 돌려주고, `SearchContext.contextReferences()`가 `groundingPrompt()`와 같은 예산(12,000자)으로 본문을 잘라 응답 `chunks`에 싣는다. 검색 질의 조립은 `ChatSearchQueryComposer`로 빠져 SSE 경로와 공유한다. `ChatMessage.generatedBy`는 항상 `SERVER`로 저장된다(`CLIENT`는 `S2`).
>
> 정정 2026-09-09(탐색 경로 MCP 방식 확정): 위 `S1` 엔드포인트의 유일한 소비자였던 데스크톱 `S3`(main의 사용자 LLM 호출)이 폐기돼 현재 소비자가 없다. 데스크톱은 이제 LLM을 호출하지 않고 로컬 MCP 서버(`S8`)의 `search_documents` 도구가 Workspace 기준 검색 API `POST /api/v1/workspaces/{workspaceId}/search`(`S7`, 저장·턴 검사 없음 — 2026-09-09 구현됨: `chat/presentation/WorkspaceSearchController.java`, `chat/application/WorkspaceSearchService.java`. `WorkspaceQueryService.findDetail`로 존재·멤버를 검사하고 `PublishedDocumentSearchService.search(workspaceId, content)`를 이력 없이 호출한다)를 부르며, 에이전트 답변 저장은 `POST /api/v1/conversations/{sessionId}/turns`(`S2`, 미구현)로 한다. `S1`의 검색·선별·예산 로직과 V14는 그대로 재사용한다(로드맵 4.5절).

**설정 키(`llm.*`)**: `provider`(`fake`, 레거시 fallback), `chat.provider`(`${llm.provider}`), `embedding.provider`(`${llm.provider}`), `base-uri`(`http://localhost:1234/v1`), `api-key`(빈값), `model`(`qwen/qwen3.6-27b`), `max-tokens`(1024), `temperature`(0.2), `request-timeout`(PT30S), `embedding.model`(`text-embedding-qwen3-embedding-0.6b:2`), `embedding.dimensions`(1024), `search.chunk-size`(1200), `search.chunk-overlap`(180), `search.candidate-limit`(50), `search.top-k`(8, 정정 2026-09-07 `S1`: 3 → 8), `search.max-context-characters`(12000, 정정 2026-09-07 `S1`: 10000 → 12000), `search.embedding-batch-size`(16, 정정 2026-09-08: 64 → 16 — 로드맵 Q37·U25, 64는 Gemini 429 `RESOURCE_EXHAUSTED`), `search.minimum-relevance-score`(0.35), `anthropic.base-uri`(`https://api.anthropic.com`), `anthropic.api-key`(빈값), `anthropic.model`(`claude-opus-5`), `anthropic.effort`(`medium`), `anthropic.max-tokens`(4096), `anthropic.request-timeout`(PT30S), `gemini.base-uri`(`https://generativelanguage.googleapis.com`), `gemini.api-key`(빈값, `GEMINI_API_KEY`), `gemini.embedding-model`(`gemini-embedding-001`), `gemini.request-timeout`(PT30S), `gemini.retry-max-attempts`(6, 추가 2026-09-08 Q42), `gemini.retry-initial-delay`(PT5S, 추가 2026-09-08 Q42) — `application.properties`의 `llm.*` 블록(정정 2026-09-08 `B5`: `gemini.*` 4개 추가, `embedding.model`은 `openai-compatible` 전용). 바인딩: `LlmProperties`, `AnthropicLlmProperties`, `GeminiEmbeddingProperties`, `EmbeddingProperties`, `SearchProperties`. 채팅 쪽 키는 `chat.turn-timeout`(PT5M, `ChatProperties`, 추가 2026-09-07 `S1`) 하나다.

**SSE 이벤트 계약** (`chat/presentation/ChatSseStreamListener.java`)

| 이벤트 | data | 레코드 |
| --- | --- | --- |
| `chunk` | `{"delta":"..."}` | `ChatChunkEvent(String delta)` |
| `complete` | `{"messageId":123}` | `ChatCompleteEvent(long messageId)` |
| `error` | `{"code":"...","message":"..."}` | `ChatErrorEvent(String code, String message)` |

**파이프라인 순서** (`ChatMessageService.sendMessage`/`streamAnswer`): 세션 소유자·Workspace 멤버 검증 → `ActiveChatStreamRegistry.tryAcquire`(세션당 1개, 인메모리 → 단일 인스턴스 전제) → `requirePublishedSnapshot`(없으면 `CHAT_DOCUMENTS_NOT_READY` 409, LLM 미호출) → 사용자 메시지 저장 → 히스토리 조회 → 검색(직전 최대 4개 메시지·4,000자로 질의 구성) → `SearchContext.groundingPrompt()`를 SYSTEM 메시지로 → `LlmClient.start` → chunk 스트리밍 → `saveAssistantWithReferences`(같은 트랜잭션) → `complete(messageId)`. SSE 타임아웃 30초(`SSE_TIMEOUT_MILLIS`), 타임아웃 시 `LLM_STREAM_TIMEOUT`. 스레드 풀 `chatStreamExecutor` core 2/max 8/queue 100.

**LLM 운영 상태** (`docs/llm-java-integration.md`): 기본 `fake`, 실제는 LM Studio(`http://<host>:1234/v1`) 또는 NVIDIA NIM(`https://integrate.api.nvidia.com/v1`)을 `openai-compatible`로. Spring AI 미도입. API 키는 환경변수·secret manager로만 주입, 저장소·프롬프트·로그·SSE에 기록 금지. 기준 커밋에서 "MVP 연동 구현 및 컨테이너 검증 완료, 실제 NIM 운영 전 검증 중".

**DB(Flyway V1~V14, V7·V8 결번)**: `members`, `oauth_identities`, `workspaces`, `workspace_members`, `workspace_invitations`, `chat_sessions`, `chat_messages`(V14: `generated_by` `SERVER`/`CLIENT`), `chat_feedback`, `content_source_authorizations`, `content_source_connections`, `content_import_runs`, `imported_pages`, `imported_page_publications`, `search_document_chunks`(`vector(1024)`, HNSW cosine), `search_references`(V14: `chunk_index`, rank 1~8, 유일 키 `(message_id, imported_page_id, chunk_index)`).

### 1.5 프론트엔드 구조 (라우트·API 계층·테스트)

**진입**: `src/index.tsx` — `API_MOCKING === "true"`면 `@api/mock/browser`를 동적 import해 `mockWorker.start()` 후 렌더. 프로바이더: StrictMode → emotion ThemeProvider + GlobalStyle → QueryClientProvider → `<App/>`(= `RouterProvider`) + ReactQueryDevtools.

**라우트** (`src/shared/routes/routes.tsx`, `createBrowserRouter`)

```
CenteredLayout
├─ /                       EntryRedirect
├─ GuestGuard → /login     LoginPage
├─ /onboarding, /onboarding/complete
├─ /invite/:token, /workspace/:id/join, /join-error
└─ AuthGuard → /workspace, /workspace/create, /workspace/:id/invite,
               /workspace/:id/notion-connection, /workspace/code
WorkspaceLayout (ChatStreamProvider, useWorkspaceEntry가 401/403/404 자체 판정)
├─ /workspace/:workspaceId            WorkspaceHomePage
├─ /workspace/:workspaceId/chat       ChatPage
└─ /workspace/:workspaceId/chat/:sessionId  ChatPage
```

경로 상수는 `PATH_ROUTE.ts`, `getRouterPath({routeKey, params})`로 생성. `AuthGuard`는 "인증 쿠키는 httpOnly라 JS가 읽을 수 없으므로 로그인 여부도 서버에 물어봐야 합니다"(`useMeQuery` 401 → `/login`).

**API 계층**

| 항목 | 값 | 근거 |
| --- | --- | --- |
| axios | `baseURL: process.env.API_BASE_URL`, `timeout 10_000`, `withCredentials: true` | `src/shared/api/httpClient/index.ts:24-33` |
| SSE | `streamChatMessageApi` = fetch(`credentials: "include"`, `Accept: text/event-stream`, `X-XSRF-TOKEN`) + `ReadableStream` reader + `TextDecoder(stream:true)` + `parseSseEvents`(프레임 `\n\n`, `\r\n` 정규화, `:` 주석 무시) → async generator가 `chunk`/`complete`/`error`만 yield | `.../conversations/[sessionId]/messages/index.ts`, `src/shared/api/sse/parseSseEvents/index.ts` |
| 절대 URL | `new URL(path, process.env.API_BASE_URL \|\| window.location.origin)` | 같은 파일 `:55-56` |
| DTO | `src/shared/api/dto/*.ts` 9개, Raw 인터페이스 + `new` DTO 클래스 쌍. 규칙 `frontend/.claude/rules/dto-guide.md` | |
| mock | `mock/browser.ts`가 `devAuthHandlers`·`devNotionOAuthHandlers`를 앞에 두고 `handlers` 20개 합성. dev 인증은 `KNOT_MOCK_AUTH` 쿠키(`member`/`onboarding`). OAuth 진입 302는 webpack devServer 미들웨어가 처리 | `src/shared/api/mock/**`, `webpack.config.js:48-67` |
| `process.env` 사용처 | 앱 코드에서는 `API_BASE_URL`, `API_MOCKING` 2개뿐. 둘 다 `DefinePlugin`으로 빌드 시 인라인 | `webpack.config.js:208-215` |

**테스트**: vitest(jsdom, `globals`, msw `onUnhandledRequest: "error"`, `include: src/**/*.test.{ts,tsx}, src/**/test.{ts,tsx}`, `src/__test__/**` 제외) / Playwright(`testDir src/__test__`, `BASE_URL http://localhost:3000`, `storageState`로 `KNOT_MOCK_AUTH=member` 쿠키 주입, `webServer: pnpm dev --no-open`, chromium만). `vitest.config.ts`가 webpack alias 12개와 svgr `size` 템플릿을 1:1 재현.

**`index.html`**: CSP meta 없음, `<title>Document</title>`, Pretendard 폰트를 `cdn.jsdelivr.net`에서 로드(오프라인·CSP 설계 시 고려), favicon·manifest 없음. `public/`에는 `mockServiceWorker.js`만 있고 프로덕션 빌드에 복사되지 않음.

**규칙·스킬**: `frontend/.claude/rules/*.md` 11개(`agent-guide`, `api-guide`, `component-abstract-pattern`, `component-colocation-pattern`, `dto-guide`, `general-code-convention`, `hook-guide`, `query-hooks`, `segment-pattern`, `shared-layer`, `test-strategy`). 스킬 `commit`, `create-pr-content`, `explain-diff-html`, `project-structure`, `review`. 에이전트 `code-reviewer`, `senior-frontend-implementer`, `tdd-guide`. `scripts/*.mjs` 4개는 `claude -p` 래퍼(토큰 `frontend/.env.local`의 `CLAUDE_CODE_OAUTH_TOKEN`).

### 1.6 워크플로우·컨벤션

| 워크플로우 | 요지 |
| --- | --- |
| `deploy-frontend-dev.yml` / `deploy-frontend-prod.yml` | `develop`/`main` push(`frontend/**`) + `workflow_dispatch`. Node 22, pnpm(action-setup v4), `pnpm install --frozen-lockfile`, `API_BASE_URL=${{ vars.API_BASE_URL_DEV|PROD }}`(비면 warning), `pnpm build`, `cloudflare/wrangler-action@v3`(environment `dev`/`prod`, secrets `CLOUDFLARE_API_TOKEN`·`CLOUDFLARE_ACCOUNT_ID`). timeout 15분 |
| `backend-ci.yml` | PR 전체 + main/develop push. Java 25, `spotlessCheck` → `test` → `integrationTest` → `acceptanceTest` → `bootJar`. 실패 시 Discord |
| `deploy-backend-dev.yml` | develop `backend/**` push → Backend CI 성공 대기 → NCP SSH 배포, systemd drop-in으로 `SPRING_PROFILES_ACTIVE=dev`, `AUTH_CORS_ALLOWED_ORIGINS`, `EnvironmentFile=/etc/knot/knot-backend-dev.env`. `.last-known-good` 스냅샷 롤백 |
| `governance.yml` | PR 이벤트마다 `validate_governance.py --config .github/knot-conventions.yml` |
| `issue-harness.yml` | `.agents/**`, `.claude/**`, `docs/adr/**`, `docs/harness/**`, `harness/**` 변경 시 `harness/tests` 실행 |
| `project-branch-status.yml` | 브랜치 생성 시 `#번호`를 뽑아 Project Status를 In Progress로 |
| `backend-pr.yml` | BE PR Discord 알림 전용 |

**컨벤션** (`.github/knot-conventions.yml`, `CONTRIBUTING.md`)

- PR/Issue 제목: `^\[(BE|FE)\] \S.*`
- 브랜치: `^(be|fe)/(feature|bugfix|chore|docs|hotfix|refactor|release)/#\d+$` (예 `fe/feature/#42`, 셸에서는 따옴표 필수)
- PR 필수 섹션 `## 관련 이슈`(`#번호`), `## 작업 내용`(비어 있으면 실패). 라벨은 자동 검증 대상 아님
- Issue 템플릿 섹션: `## 구현 기능 설명`, `## TODO`, `## 메모`
- **area는 `be|fe` 둘뿐** → 데스크톱 패키지를 어디에 두든 브랜치·제목 접두는 둘 중 하나를 써야 한다(새 area 추가는 governance 변경)

### 1.7 문서·ADR 결정 (기획에 영향을 주는 것)

| 문서 | 요지 | Electron 기획 시사점 |
| --- | --- | --- |
| `llm-search-feature-spec.md` 4절 | 권한 모델 "Workspace 소유자가 승인한 문서를 구성원이 함께 검색". Notion 토큰은 백엔드 암호화 보관, 모델에 전달 금지 | 구독 없는 팀원도 검색해야 하므로 모델 호출은 서버가 소유 |
| 같은 문서 7절 | 사실·결정 근거·충돌·정보 없음·너무 넓은 질문 등 답변 정책 표 | 프롬프트 정책은 서버 코드에 고정(앱 버전별 분기 금지) |
| 같은 문서 12절 | "세션을 새로고침·재로그인 후에도 복원", "end-to-end 첫 답변 청크 시간과 단계별 시간 기록", "5초 초과 시 상태 표시" | 세션 진실은 DB, 계측은 서버 |
| 같은 문서 9절·ADR 271 | 검색 p50 RAG 200ms, LM Studio live smoke test end-to-end 38~77초(5초 목표 미충족). 30개 이상 독립 질문 gold set | 모델 교체 시 재측정 필수 |
| ADR 232 | 구독 토큰 공유는 "Anthropic 계정 공유 약관에 저촉될 가능성" 부정 결과로 기록 | 최종 사용자 제품에서 구독 사용 금지 근거 |
| ADR 314 | 로그인 후 목적지 분기는 프론트 홈 경로가, 백엔드는 고정 리다이렉트 3개만. 재논의 조건에 "로그인 이후 목적지가 외부에서 지정되는 흐름 추가 시" | 딥링크 콜백을 넣으면 ADR 314 재논의 |
| ADR 254 | 콘텐츠 소스 연결은 공급자 중립 계약, OAuth state 256-bit·10분·HMAC, 토큰 AES-GCM envelope(key version) | 디바이스 토큰 저장 시 같은 암호화 관행 재사용 |
| ADR 212·328 | dev는 NCP 단일 서버 in-place 배포, health polling, 자동 롤백 | 백엔드 API 추가는 기존 CD로 충분 |
| `docs/harness/issue-planning.md` | 고위험 신호(`data`, `security`, `external`, `cross-boundary`, `core-flow`, `shared`, `hard-to-reverse`) 하나라도 있으면 인터뷰(6항목 근거) → Grill(8범주) → ADR(대안 2개 이상·장기 영향·반복 참조 모두 충족 시). Issue 본문은 3섹션만, snapshot은 OS 임시 파일 | 데스크톱 앱·인증 변경·LLM 어댑터는 전부 고위험 경로 |
| `docs/adr/README.md` | ADR 형식 10항목, Issue 단계에서는 ADR 파일 생성 금지, 구현 브랜치에서 `Proposed`로 생성, 팀 승인 후 `Accepted` | 본 기획 문서는 ADR이 아니라 ADR의 재료 |

### 1.8 Electron 기획에 즉시 걸리는 제약 (요약)

| 제약 | 근거 |
| --- | --- |
| `file://`·`app://` 같은 비-`knoted.kr` 오리진은 CORS 허용 목록에 없고 `*`도 금지 | `SecurityConfig.java:50-54,84-86` |
| Bearer 인증 경로 부재(쿠키만) | `JwtAuthenticationFilter.java:57-73` |
| 모든 변경 요청에 `X-XSRF-TOKEN` 필요 | `SecurityConfig.java:88-93,131-134` |
| OAuth 로그인은 API 오리진으로의 전체 페이지 이동 + 백엔드 302 체인 | `GithubLoginButton/index.tsx`, `OAuth2AuthenticationSuccessHandler.java` |
| 로그인 후 목적지 고정 3개(딥링크 미지원) | `application.properties:93-96`, ADR 314 |
| 액세스 토큰 1시간, 리프레시 없음 | `JwtProperties.java:13` |
| `API_BASE_URL`은 빌드 시 인라인(런타임 변경 불가) | `webpack.config.js:208-215` |
| SSE 30초 타임아웃, 세션당 스트림 1개(인메모리) | `ChatMessageController.java:24`, `ActiveChatStreamRegistry` |
| 폰트 CDN 의존·CSP meta 없음 | `frontend/index.html` |
| Governance area `be|fe`만 허용 | `.github/knot-conventions.yml` |
| 인증·외부 credential·데이터 경계 변경은 고위험 → 인터뷰 + Grill + ADR | `risk-policy.md`, `issue-planning.md` |

### 1.9 프론트엔드 빌드·툴체인 사실 (직접 확인, 2026-09-04)

| 항목 | 값 | 근거 |
| --- | --- | --- |
| 패키지명·모듈 형식 | `frontend`, `"type": "module"` (ESM) | `frontend/package.json` |
| Node | `engines.node >=22`, `.nvmrc` = 22, 로컬 v22.21.1 | `frontend/package.json`, `frontend/.nvmrc` |
| 패키지 매니저 | `pnpm@11.20.0` (packageManager 필드), 로컬 10.33.0 설치 | `frontend/package.json` |
| 번들러 | webpack 5.109 + webpack-cli 7 + webpack-dev-server 6, babel-loader 10 (`@babel/*` 8) | `frontend/package.json` |
| 언어 | TypeScript 7.0.2, `pnpm tsc` 후 `webpack --mode production` | `scripts.build` |
| UI | React 19.2, react-dom 19.2, react-router 8.3, @emotion/react·styled 11, @tanstack/react-query 5.102, axios 1.19 | `dependencies` |
| 테스트 | vitest 4.1 + jsdom 30 + @testing-library/react 16, msw 2.15(`public/mockServiceWorker.js`), Playwright 1.62 | `devDependencies`, `msw.workerDirectory` |
| 배포 CLI | wrangler 4.124 | `devDependencies` |
| 산출물 | `dist/bundle.js` 단일 번들 + `dist/index.html`, `publicPath: "/"`, `clean: true` | `webpack.config.js` `output` |
| 소스맵 | dev `eval-source-map`, prod `source-map`(별도 `.map`) | `webpack.config.js` `devtool` |
| dev 서버 | 포트 3000, `historyApiFallback`, `hot`, `open`, mock 모드에서 `/oauth2/authorization/github`를 302로 가로채 `KNOT_MOCK_AUTH` 쿠키 발급 | `webpack.config.js` `devServer` |
| 환경변수 | `.env.development`/`.env.production`을 `process.loadEnvFile`로 로드. `API_MOCKING`("true"/"false"), `API_BASE_URL`을 `DefinePlugin`으로 인라인. mock 모드면 `API_BASE_URL=""`(같은 오리진) | `webpack.config.js` `plugins` |
| 경로 별칭 | `@`, `@pages`, `@widgets`, `@features`, `@routes`, `@api`, `@composites`, `@primitives`, `@constants`, `@provider`, `@hooks`, `@utils` | `resolve.alias` |
| SVG | `@svgr/webpack` 컴포넌트 변환(`size` prop 템플릿), `?url`이면 asset | `module.rules` |
| 폰트·이미지 | `asset/resource`(`assets/[name][ext]`), 이미지 `asset` | `module.rules` |
| Cloudflare | `wrangler.jsonc`: `assets.directory=./dist`, `not_found_handling=single-page-application`, env `dev`(`knot-frontend-dev`)·`prod`(`knot-frontend`), `compatibility_date=2026-08-01` | `frontend/wrangler.jsonc` |
| webpack `target` | 미지정(브라우저 기본 `web`) | `webpack.config.js` |
| Electron 관련 코드·의존성 | 없음 | `package.json` 전체 |

Electron 기획에 미치는 함의:

- renderer는 지금의 `dist/`를 그대로 쓰거나 원격 `https://knoted.kr`을 로드한다. 둘 다 webpack `target`은 `web`을 유지해야 sandbox renderer에서 Node 전역을 기대하지 않는다.
- `process.env.API_BASE_URL`이 빌드 시점에 인라인되므로 데스크톱 전용 빌드는 별도 `.env`(또는 CI vars)로 API 오리진을 고정해야 한다.
- msw의 `mockServiceWorker.js`는 dev 전용이며 Electron dev 실행 시에도 `API_MOCKING=true`로 같은 mock 경로를 재사용할 수 있다. 단 mock OAuth 302 미들웨어는 webpack dev 서버에만 있으므로 Electron dev도 dev 서버 URL(`http://localhost:3000`)을 로드해야 한다.
- 단일 `bundle.js`는 로컬 번들 방식에서 `app://` 스킴으로 서빙하기 쉽다(코드 스플리팅 없음).
- Node 22 + pnpm 11 + ESM이 기본이므로 Electron main도 ESM(`.mjs` 또는 `"type": "module"`)으로 작성할 수 있다. preload는 sandbox 조건을 확인해야 한다(2절 참조).

## 2. Electron 코어·보안 (공식 문서 조사, 2026-09-04)

표기: **[확인]** 공식 문서·릴리즈 페이지·Electron 소스에서 직접 확인, **[추론]** 문서·웹 표준으로부터 추론, **[비공식]** GitHub 이슈·서드파티.

### 2.1 버전·릴리즈 [확인]

> 정정 2026-09-06: `electron` npm `latest`가 **44.2.0**(배포 2026-09-04)이다. 근거: `npm view electron version`, `npm view electron time`. 이전 값 44.1.1(2026-09-02 조사)은 배포일도 2026-09-01T18:25Z로 확인됐다. Chromium·Node 버전은 44.2.0 릴리즈 노트를 재확인하지 않았으므로 44.1.1 조사값을 유지한다.

| 채널 | 버전 | 릴리즈일 | Chromium | Node.js | V8 |
| --- | --- | --- | --- | --- | --- |
| 최신 안정(major 44) | **44.2.0** | 2026-09-04 | 152.0.7977.65 | 24.19.0 | 15.2 계열 |
| 지원(major 43) | 43.6.0 | 2026-09-04 | 150.0.7871.250 | 24.20.0 | — |
| 지원(major 42) | 42.11.1 | 2026-09-02 | 148.0.7778.280 | 24.19.0 | — |
| 다음 메이저 45 | alpha 2026-08-27 → beta 09-29 → **stable 2026-10-20** | | M156 | 24.20.0 | |

- 8주마다 메이저(4주 alpha + 4주 beta). 최신 3개 메이저만 지원(현재 44·43·42, 45 출시 시 42 EOL 2026-10-20).
- 44부터 macOS 13+ 필수, Windows ia32·Linux armv7l 프리빌트 제거.
- 출처: https://releases.electronjs.org/ , https://releases.electronjs.org/schedule , https://www.electronjs.org/blog/electron-44-0 , https://www.electronjs.org/docs/latest/tutorial/electron-timelines

**개발 환경·설치**

- `electron` npm 패키지 `engines.node >= 22.12.0`(v44.1.1). Electron은 자체 Node 런타임을 번들하므로 시스템 Node는 개발 도구용. Knot의 Node 22 요건과 호환.
- **42부터 postinstall 다운로드 제거**: 첫 `npx electron` 실행 시 동적으로 바이너리 다운로드. `--ignore-scripts` 가능, 사전 다운로드는 `npx install-electron --no`. `ELECTRON_SKIP_BINARY_DOWNLOAD` 미지원.
- 미러: `ELECTRON_MIRROR` + `ELECTRON_CUSTOM_DIR` + `ELECTRON_CUSTOM_FILENAME`. 캐시: macOS `~/Library/Caches/electron/`, Linux `~/.cache/electron/`, Windows `%LOCALAPPDATA%/electron/Cache`(`electron_config_cache`로 변경). CI 캐시 키에 활용.
- 출처: https://www.electronjs.org/docs/latest/tutorial/installation , https://www.electronjs.org/docs/latest/breaking-changes

**최근 breaking changes(40~44) 중 기획에 영향 있는 것**

| 버전 | 변경 | 영향 |
| --- | --- | --- |
| 44 | `clipboard` 모듈 renderer에서 제거, W3C Clipboard API 정렬(Promise·`ClipboardItem`) | 초대 링크 복사는 `navigator.clipboard`(웹과 동일)로 충분 |
| 44 | `net.WebSocket`, `windowStatePersistence: true`(실험), `webFrameMain.printToPDF()` 추가 | 창 위치·크기 기억에 활용 가능 |
| 43 | main이 Node 스타트업 스냅샷으로 부팅, preload 바이트코드 캐시(성능) | — |
| 43 | Linux frameless 기본 둥근 모서리, `dialog` 기본 경로 Downloads | — |
| **42** | **macOS 알림이 `UNNotification` API 사용 → 미서명 앱은 알림 미표시·`failed` 이벤트** | 알림 기능은 서명 전제 |
| 42 | `electron` postinstall 제거 | CI 설치 스크립트 |
| 40 | renderer `clipboard` deprecated | — |
| 28 | ESM 지원 시작 | main ESM 가능 |

`contextIsolation`(12+ 기본 true), `sandbox`(20+ 기본 true), `nodeIntegration`(5+ 기본 false) 기본값은 40~44에서 변경 없음.

### 2.2 프로세스 모델·IPC [확인]

| 프로세스 | 역할 | 제약 |
| --- | --- | --- |
| main | 앱 진입점, Node 환경, `app`·`BrowserWindow`·네이티브 API | 하나뿐 |
| renderer | 창마다 별도 프로세스, 웹 표준 코드 | Node API 직접 접근 불가(기본) |
| preload | 웹 콘텐츠 로드 전 renderer에서 실행, `contextBridge`로만 API 노출 | sandbox 시 `require`는 `electron` 일부(`contextBridge`, `ipcRenderer`, `webFrame`, `webUtils`, `crashReporter`, `nativeImage`)와 `events`, `timers`, `url`만. CommonJS 분할 불가 → 번들러로 단일 파일 |
| utility process | `utilityProcess.fork` — 신뢰할 수 없는 서비스·CPU 작업·크래시 위험 컴포넌트 격리, MessagePort 통신 | Fuses 문서가 `child_process.fork` 대신 권고 |

IPC 패턴

```js
// main
ipcMain.handle('knot:openExternal', (event, url) => { /* senderFrame 검증 후 */ })
// preload
contextBridge.exposeInMainWorld('knotDesktop', {
  openExternal: (url) => ipcRenderer.invoke('knot:openExternal', url),
  onDeepLink: (cb) => ipcRenderer.on('knot:deep-link', (_e, payload) => cb(payload)),
})
```

- `ipcRenderer.invoke`/`ipcMain.handle`(양방향, Promise), `send`/`on`(단방향), `webContents.send`(main→renderer). renderer 간 직접 통신은 불가(main 중계 또는 `MessageChannelMain` 포트 전달, 포트는 `postMessage`로만 전달).
- `contextBridge`를 넘는 값은 프로토타입이 제거되고 Structured Clone 호환만 통과. `ipcRenderer` 전체를 노출하면 빈 객체가 되며, `ipcRenderer.on` 콜백을 그대로 노출하면 `event.sender`로 누출되므로 래퍼 필수.
- `IpcMainInvokeEvent.senderFrame`은 프레임이 이동·파괴되면 `null`일 수 있다. `new URL(frame.url).origin` 비교로 sender 검증.
- ESM(28+): main은 `.mjs` 또는 `"type": "module"`. **ESM은 비동기 로드라 진입점 import의 side effect만 `ready` 전에 실행**되므로 동적 `import()` 뒤에는 `app.whenReady()`가 이미 지났을 수 있다. **sandboxed preload는 ESM import 불가**(ESM preload는 `sandbox: false` 필요) → preload는 CJS 단일 번들 유지.
- 출처: https://www.electronjs.org/docs/latest/tutorial/process-model , /tutorial/ipc , /api/context-bridge , /tutorial/sandbox , /tutorial/esm , /tutorial/message-ports , /api/utility-process

### 2.3 보안 체크리스트 (공식 Security 문서 20항목) [확인]

출처: https://www.electronjs.org/docs/latest/tutorial/security

| # | 항목 | Knot 적용 |
| --- | --- | --- |
| 1 | 보안 콘텐츠만 로드(HTTPS/WSS) | `https://knoted.kr`, `https://api.*.knoted.kr`만 |
| 2 | 원격 콘텐츠에 Node 통합 금지 | `nodeIntegration: false`(기본) 유지. "Under no circumstances should you load and execute remote code with Node.js integration enabled" |
| 3 | Context Isolation | 기본 `true` 유지 |
| 4 | 프로세스 sandbox | 기본 `true` 유지, `app.enableSandbox()`로 전체 강제 |
| 5 | 세션 권한 요청 처리 | `session.setPermissionRequestHandler`에서 `new URL(wc.getURL())`의 오리진이 `knoted.kr`일 때만 `notifications`·`clipboard-read` 등 허용, 나머지 `false` |
| 6 | `webSecurity` 유지 | 기본 true |
| 7 | CSP 정의 | 원격 로드면 서버 응답 헤더가 CSP. 앱은 `onHeadersReceived`로 보강 가능. 로컬 번들이면 `<meta http-equiv>` 또는 헤더 주입 |
| 8~10 | `allowRunningInsecureContent`·`experimentalFeatures`·`enableBlinkFeatures` 사용 금지 | 기본값 유지 |
| 11~12 | `<webview>`·`allowpopups` 금지, `will-attach-webview` 검증 | `webviewTag` 기본 false 유지 |
| 13 | 네비게이션 제한 | `will-navigate`에서 허용 오리진 외 `preventDefault` |
| 14 | 새 창 생성 제한 | `setWindowOpenHandler`에서 URL 오리진이 허용 목록 안이면 `{action:'allow', overrideBrowserWindowOptions:{webPreferences:{sandbox, contextIsolation, nodeIntegration:false, webviewTag:false}}}`(preload 없음), 밖이면 `{action:'deny'}` + 검증된 URL만 `shell.openExternal`. 공식 항목은 "disable or limit"(정정 2026-09-08, 기획서 4.5·로드맵 Q46 — OAuth 로그인 팝업이 `window.opener`를 요구한다) |
| 15 | `shell.openExternal` 검증 | `https:`·`mailto:`만, 임의 명령 실행 위험 |
| 16 | 최신 Electron | 44, 메이저 1개씩 추적 |
| 17 | IPC sender 검증 | `event.senderFrame` origin 검사 |
| 18 | `file://` 대신 커스텀 프로토콜 | 로컬 번들 시 `app://` + `protocol.handle` |
| 19 | Fuses 점검 | 아래 표 |
| 20 | 신뢰할 수 없는 콘텐츠에 Electron API 비노출 | preload API 최소화 |

**Fuses(`@electron/fuses`, Forge `plugin-fuses`)**

| Fuse | 기본 | 권장 | 비고 |
| --- | --- | --- | --- |
| `RunAsNode` | on | **off** | `ELECTRON_RUN_AS_NODE`로 Node로 실행되는 것 차단 |
| `EnableCookieEncryption` | off | **on** | 디스크 쿠키 암호화(단방향). macOS는 Keychain 사용 → 코드 서명 필요 |
| `EnableNodeOptionsEnvironmentVariable` | on | **off** | `NODE_OPTIONS`·`NODE_EXTRA_CA_CERTS` 무시 |
| `EnableNodeCliInspectArguments` | on | **off** | `--inspect` 차단 |
| `EnableEmbeddedAsarIntegrityValidation` | off | **on** | macOS(16+)·Windows(30+) asar 해시 검증. Forge 7.4+/packager 18.3.1+가 자동 설정 |
| `OnlyLoadAppFromAsar` | off | **on** | 무결성 검증과 함께 켜면 비검증 코드 로드 불가 |
| `GrantFileProtocolExtraPrivileges` | on | **off** | `file://`로 페이지를 서빙하지 않으므로 |
| `LoadBrowserProcessSpecificV8Snapshot` | off | 선택 | |
| `WasmTrapHandlers` | on | 유지 | |

서명된 앱에서는 사용자가 fuse를 바꿀 수 없다. 출처: https://www.electronjs.org/docs/latest/tutorial/fuses , /tutorial/asar-integrity , https://www.electronforge.io/config/plugins/fuses

### 2.4 원격 URL 로드 vs 로컬 번들 로드

| 관점 | A. `loadURL('https://knoted.kr')` | B. 로컬 번들(`app://`) |
| --- | --- | --- |
| 오리진 | `https://knoted.kr` — 쿠키·SameSite·`__Host-`·CSRF·CORS가 **브라우저와 동일** | `app://knot` — API와 cross-site. SameSite=Lax 쿠키 미전송, CORS 허용 오리진 추가 필요 |
| 배포 | 웹 릴리즈로 즉시 반영, 셸만 드물게 업데이트 | 프런트 번들이 앱에 포함 → 앱 자동 업데이트 필수, 백엔드와 버전 스큐 관리 |
| 오프라인 | 빈 화면(`did-fail-load` 처리 필요) | 셸은 뜨나 API 없이는 기능 없음 |
| 보안 | 사이트 XSS가 preload API 호출 능력이 됨 → 노출 API 최소·sender 검증 필수. CSP는 서버 소유 | ASAR 무결성으로 코드 검증, CSP를 앱이 통제 |
| 공식 근거 | Security #1·2·5·13·14·17 | Security #18, fuse `GrantFileProtocolExtraPrivileges` off |
| **커스텀 스킴 쿠키** | 해당 없음 | **[비공식·중요]** Electron 이슈 #27981(2021 open, 2026-07-28 갱신): 커스텀 프로토콜 페이지 오리진의 쿠키는 미지원("can't without a fairly significant effort") |

결론 [추론]: 백엔드 쿠키 정책을 바꾸지 않는 한 A가 마찰이 가장 적다. B는 인증 전달 방식(Bearer 또는 main 프록시) 변경이 선행돼야 한다.

### 2.5 세션·쿠키·네트워크 [확인]

- `session.defaultSession`(ready 이후), `session.fromPartition('persist:knot')`로 디스크 영속 파티션. `ses.getStoragePath()`.
- `ses.cookies.get({url, name, domain, path, secure, session, httpOnly})` → `Cookie[]`. **문서상 HttpOnly 쿠키 읽기 제한 없음** → main은 HttpOnly 값을 읽을 수 있다[추론]. HttpOnly는 `document.cookie` 접근만 막는 속성(MDN). [비공식] 이슈 #22345: `sameSite=strict; secure; httpOnly` 쿠키가 `url` 필터 없이 누락된 사례 → `url: 'https://api...'`를 명시해 조회.
- `ses.cookies.set({url(필수), name, value, domain, path, secure, httpOnly, expirationDate, sameSite: 'unspecified'|'no_restriction'|'lax'|'strict'(기본 lax)})`, `remove(url, name)`, `flushStore()`, `'changed'` 이벤트(cause: `inserted`, `explicit`, `overwrite`, `expired`, `evicted`, `expired-overwrite`, `inserted-no-change-overwrite`, `inserted-no-value-change-overwrite`).
- `ses.webRequest.onBeforeSendHeaders(filter, (details, cb) => cb({requestHeaders}))`로 **헤더 주입 가능**. `onHeadersReceived`로 응답 헤더(CSP 등) 수정. 세션·이벤트당 리스너 1개만.
- `net.fetch(input, init)`(기본 세션, `ses.fetch()`로 세션 지정): Chromium 네트워크 스택(시스템 프록시·PAC), `data:`/`blob:` 미지원, `integrity` 무시. `net.request` → `ClientRequest`에 `credentials: 'include'|'omit'|'same-origin'`, `useSessionCookies`(기본 false) 옵션. `net.isOnline()`은 true여도 불확실. `net.WebSocket`(44).
- 인증서: `ses.setCertificateVerifyProc`, `app.on('certificate-error')`(신뢰 시에만 `callback(true)`), `--ignore-certificate-errors`는 운영 금지.
- 프록시: `ses.setProxy`, `--proxy-server`, `--proxy-bypass-list`.
- SameSite·`__Host-` 규칙은 Electron 문서에 없고 Chromium 규칙 그대로(MDN: `__Host-`는 Secure·Domain 없음·Path=/; Chrome은 SameSite 미지정을 Lax로 취급).
- `disable-site-isolation-trials` 스위치는 Electron 문서에 없는 Chromium 스위치이며 SameSite 쿠키 규칙을 우회하지 못하고 Spectre류 방어를 없앤다 → 사용 금지.
- renderer의 `fetch().body` 스트리밍·`EventSource`는 Chromium과 동일[추론]. [비공식] 이슈 #44458은 `nodeIntegration: true`에서만 발생한 `EventSource` 회귀(수정됨) → 기본 설정 무관.
- main의 전역 `fetch`(Node undici)는 `response.body`(`ReadableStream<Uint8Array>`)로 SSE를 스트리밍하고 `AbortSignal`로 취소된다 — 2026-09-08 폐기된 `S3` 구현에서 vitest(Node 22.21)로 실측(§8 U24, 무효). 사실 자체는 유효하지만 데스크톱은 이제 LLM을 호출하지 않는다(로드맵 불변 계약 2번, 2026-09-09). main이 서버 API를 부를 때는 Chromium 세션·쿠키가 섞이지 않도록 `net.fetch`가 아니라 이 전역 `fetch`를 쓰고, `AbortSignal.any`·`AbortSignal.timeout`(Node 20.3+)으로 호출자 취소와 30초 제한을 합친다(`desktop/src/main/chat/knotApi.ts` — `S8`의 MCP 도구 실행이 재사용).
- 출처: https://www.electronjs.org/docs/latest/api/session , /api/cookies , /api/web-request , /api/net , /api/client-request , /api/command-line-switches , https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie , https://web.dev/articles/samesite-cookies-explained

### 2.6 커스텀 프로토콜 `app://` [확인]

```js
// ready 이전, 한 번만
protocol.registerSchemesAsPrivileged([{ scheme: 'app',
  privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } }])
// ready 이후
protocol.handle('app', (req) => {
  const { pathname } = new URL(req.url)
  const p = path.resolve(distDir, pathname.slice(1)); const rel = path.relative(distDir, p)
  if (!rel || rel.startsWith('..') || path.isAbsolute(rel)) return new Response('bad', { status: 400 })
  return net.fetch(pathToFileURL(p).toString())
})
```

- `standard`(호스트 있는 URL 구문·상대 경로), `secure`(`url::AddSecureScheme`), `supportFetchAPI`, `corsEnabled`, `stream`, `bypassCSP`, `allowServiceWorkers`, `codeCache`(standard 필요). 비표준 스킴은 `file:`처럼 동작하고 web storage가 꺼진다.
- `protocol.handle`(25+)은 `Response`를 반환. `register*Protocol`은 deprecated.
- `secure: true`가 `window.isSecureContext`를 보장하는지는 공식 문서에 없음(미확인). SPA 라우팅은 `app://knot/workspace/...` 요청을 모두 `index.html`로 돌리는 fallback을 핸들러에서 구현해야 한다[추론].
- 출처: https://www.electronjs.org/docs/latest/api/protocol , /api/structures/custom-scheme

### 2.7 OS 통합 API [확인]

**딥링크**

- `app.setAsDefaultProtocolClient('knot')`. macOS는 `Info.plist`의 `CFBundleURLTypes`에 있어야 하며 런타임 수정 불가(패키징 시 Forge `packagerConfig.protocols: [{name:'Knot', schemes:['knot']}]`, Linux maker `mimeType: ['x-scheme-handler/knot']`). Windows는 레지스트리.
- macOS: `app.on('open-url', (event, url) => …)` 초기 등록. Windows/Linux: `app.requestSingleInstanceLock()` 실패 시 `app.quit()`, 주 인스턴스가 `second-instance`의 `commandLine.pop()`에서 URL 획득.
- 개발 모드: `process.defaultApp`이면 `setAsDefaultProtocolClient('knot', process.execPath, [path.resolve(process.argv[1])])`. macOS·Linux는 패키징 전에는 동작하지 않는다.
- 출처: https://www.electronjs.org/docs/latest/tutorial/launch-app-from-url-in-another-app

**`safeStorage`**

- `isEncryptionAvailable()`, `encryptString()→Buffer`, `decryptString(Buffer)→string`. macOS Keychain(서명이 일관되지 않으면 프롬프트 반복), Windows DPAPI, Linux `getSelectedStorageBackend()`가 `basic_text`면 보호되지 않음(`gnome_libsecret`/`kwallet*`이어야 안전).
- 출처: https://www.electronjs.org/docs/latest/api/safe-storage

**기타**

| API | 핵심 | 서명 의존 |
| --- | --- | --- |
| `shell.openExternal(url)` | Promise, Windows 2081자 제한. 검증된 `https:`만 | — |
| `Tray` | macOS Template Image(`@2x`), Windows ICO + guid, Linux StatusNotifierItem. 참조 유지 | — |
| `Menu.setApplicationMenu` | 미설정 시 기본 메뉴, `null`이면 제거. 편집(복사·붙여넣기) 롤은 반드시 포함 | — |
| `globalShortcut` | ready 이후, 비포커스에도 동작 | — |
| `Notification` | main 전용. **macOS는 서명 필수(42+)**, Windows는 `app.setAppUserModelId()` + 시작 메뉴 바로가기 | 예 |
| `nativeTheme` | `shouldUseDarkColors`, `themeSource`, `'updated'` | — |
| `powerMonitor` | `suspend`/`resume`/`lock-screen`/`on-battery` | — |
| `app.setLoginItemSettings` | 자동 시작. macOS 서명·공증 필요 | 예 |
| `clipboard` | main 전용(44+), Promise API | — |
| `dialog` | Promise, 43부터 기본 Downloads | — |
| `BrowserWindow` 옵션 | `titleBarStyle: 'hidden'|'hiddenInset'`, `titleBarOverlay`(Win/Linux 색), `trafficLightPosition`, CSS `env(titlebar-area-*)`, `app-region: drag`, `vibrancy`(macOS), `backgroundMaterial: 'mica'|'acrylic'`(Windows), `show:false` + `ready-to-show`, `windowStatePersistence`(44 실험) | — |
| `crashReporter.start({submitURL, uploadToServer, rateLimit, compress})` | 다른 crashReporter API보다 먼저·ready 전에 호출. Crashpad, 덤프는 `app.getPath('crashDumps')` | — |
| `app.getPath` | `userData`, `sessionData`(쿠키·캐시·localStorage), `logs`, `crashDumps`, `temp`, `home`, `downloads` 등. `app.isPackaged` | — |

출처: https://www.electronjs.org/docs/latest/api/{shell,tray,menu,global-shortcut,notification,native-theme,power-monitor,app,clipboard,dialog,crash-reporter} , /tutorial/notifications , /tutorial/custom-title-bar , /tutorial/code-signing

### 2.8 개발 도구 [확인]

- DevTools: `win.webContents.openDevTools({mode})`, `webPreferences.devTools:false`면 차단. main 디버깅 `electron --inspect=5858 .`, VS Code.
- React DevTools: `session.defaultSession.loadExtension(path)`(영속 세션, 매 부팅 호출). 공식 문서가 `electron-devtools-installer`를 언급 → `installExtension(REACT_DEVELOPER_TOOLS)`.
- 로깅 env: `ELECTRON_ENABLE_LOGGING`, `ELECTRON_LOG_FILE`, `ELECTRON_ENABLE_STACK_DUMPING`, `ELECTRON_DEBUG_NOTIFICATIONS`(macOS). 스위치 `--enable-logging[=file]`, `--remote-debugging-port`.
- 스캐폴딩: 공식 권장은 **Electron Forge**(`npx create-electron-app@latest --template=webpack-typescript`, Vite 템플릿은 7.5.0부터 "experimental"). 기존 프로젝트는 `npx electron-forge import`. `@electron-forge/plugin-webpack`은 `mainConfig` + `renderer.config` + `entryPoints[{html, js, preload}]`, `nodeIntegration:false` 엔트리는 webpack `target:'web'`, `MAIN_WINDOW_WEBPACK_ENTRY`/`MAIN_WINDOW_PRELOAD_WEBPACK_ENTRY` 상수 주입, `devContentSecurityPolicy`.
- 출처: https://www.electronjs.org/docs/latest/tutorial/application-debugging , /tutorial/devtools-extension , /api/environment-variables , /tutorial/forge-overview , https://www.electronforge.io/config/plugins/webpack , https://www.electronforge.io/import-existing-project

### 2.9 확인하지 못한 항목·공식 문서에 없는 것

- `secure: true` 커스텀 스킴이 secure context(`isSecureContext`)로 판정되는지(Chromium 소스 미확인).
- `app://` 오리진에서 `https://api.*`로의 `fetch(credentials:'include')`에 Lax 쿠키가 붙는지 — 웹 표준상 cross-site라 차단으로 추론, 실측 없음(프로토타입 실험 필요).
- `cookies.get`이 `sameSite=strict; httpOnly` 쿠키를 `url` 필터 없이 반환하는지(이슈 #22345).
- main `net.fetch` 응답 body 스트리밍(SSE 대행) 가능 여부.
- electron-builder `protocols` 세부(Windows NSIS 레지스트리) 페이지 404.
- SameSite·`__Host-`·소스맵·SSE·`disable-site-isolation-trials`·"기존 webpack SPA를 Forge에 얹는 권장 구조"는 Electron 공식 문서에 없음.

## 3. 패키징·서명·배포·자동 업데이트·CI·테스트·관측 (요약, 2026-09-04)

상세 근거·출처·스크립트 원문은 부록 [`electron-desktop-app-packaging-research.md`](./electron-desktop-app-packaging-research.md)(이하 "부록 §n")에 있다. 여기에는 결정에 필요한 값만 남긴다.

### 3.1 버전 (npm `latest`, 2026-09-04) [문서]

| 패키지 | 안정 | 프리릴리즈 | 비고 |
| --- | --- | --- | --- |
| `electron` | 44.2.0 (정정 2026-09-06, `npm view electron version`) | 45.0.0-alpha.4 | Chromium 152 / Node 24.19 |
| `@electron-forge/*` | **7.11.2**(2026-05-20) | 8.0.0-alpha.10 (ESM, Node ≥22.12) | Forge 7: Node ≥16.4, pnpm은 v7.7.0부터 지원 |
| `electron-builder` | 26.15.3 | 26.16.0(`v26` 태그), 27.0.0-alpha.8(`next`, ESM) | 커뮤니티 유지보수 |
| `electron-updater` | 6.8.9 | 7.0.0-alpha.7 | electron-builder 전용 |
| `electron-vite` | 5.0.0 | 6.0.0-beta.1 | Forge `plugin-vite`는 experimental |
| `@electron/packager` | 20.3.0 | — | Forge 7.11.2는 `^18.3.5`, Forge 8은 20+ |
| `@electron/osx-sign` / `notarize` / `windows-sign` / `fuses` / `universal` / `rebuild` / `asar` | 2.7.0 / 3.1.1 / 2.0.6 / 2.1.3 / 3.0.6 / 4.2.0 / 4.3.0 | — | notarize 3.0에서 `altool` 제거 |
| `update-electron-app` | 3.3.0 | — | update.electronjs.org 클라이언트 |
| `electron-winstaller` / `electron-squirrel-startup` | 5.4.4 / 1.0.1 | — | Squirrel.Windows |
| `@playwright/test` | 1.63.0 (정정 2026-09-06, `npm view @playwright/test version`) | — | Electron 지원 experimental |
| `@sentry/electron` / `electron-log` / `vitest` | 7.18.0 / 5.4.4 / 5.0.0 | — | vitest 5는 Node ^22.12 |
| `pnpm` | 11.25.0 | 12.3.1(`latest-12`) | Knot은 pnpm 11 |
| GitHub Actions | `actions/checkout@v7`, `actions/setup-node@v7`, `actions/cache@v6`, `actions/upload-artifact@v7`, `softprops/action-gh-release@v3`, `pnpm/setup@v2`(pnpm 11+ 전용, `pnpm/action-setup@v6`은 pnpm ≤10) | | Knot 워크플로우는 `pnpm/action-setup@v4`·`setup-node@v4` 사용 중 |

### 3.2 빌드 도구 (부록 §1)

| 항목 | Electron Forge 7.11.2 | electron-builder 26.15.3 |
| --- | --- | --- |
| 지위 | Electron 프로젝트 소유, 공식 문서가 "We recommend using Electron Forge" | 커뮤니티(electron-userland) |
| 서명·공증 | `packagerConfig.osxSign`(`@electron/osx-sign`), `osxNotarize`(`@electron/notarize`), `windowsSign`(`@electron/windows-sign`) | `CSC_*`·`APPLE_*` 환경변수 규약, `mac.notarize: true` 자동 staple, `win.sign.type: signtool|hsm|pkcs11|azure` |
| 자동 업데이트 | 내장 `autoUpdater`(Squirrel.Mac/Windows, MSIX) + `update-electron-app`(update.electronjs.org 무료: **공개 저장소 + macOS 서명**) 또는 S3 정적 | `electron-updater`(NSIS·AppImage·deb·rpm, 차등 업데이트 blockmap, `stagingPercentage`, 채널). Windows는 **NSIS만**, 프라이빗 GitHub은 "very special cases" |
| Windows 설치기 | Squirrel `Setup.exe` + nupkg + `RELEASES`(`electron-squirrel-startup` 필요) | NSIS(oneClick/assisted/perMachine) |
| Linux | deb/rpm/snap/flatpak/zip | + AppImage(27은 정적 런타임)/pacman |
| asar | Forge 7은 `packagerConfig.asar` **기본 off** → 명시 `asar: true` + `plugin-auto-unpack-natives`. Forge 8/packager 19+는 기본 on | 기본 on |
| Fuses | `@electron-forge/plugin-fuses` | `electronFuses` 키(패키징 후·서명 전 적용) |
| 번들링 | `plugin-webpack`/`plugin-vite`(experimental) 또는 hooks(`generateAssets`·`prePackage`)로 외부 빌드 | 별도(electron-vite 등), `files`로 포함 |

**pnpm 호이스팅** [문서]: Electron 튜토리얼 "you must set `nodeLinker: hoisted` in pnpm", Forge CLI "set `node-linker=hoisted` in your project's `.npmrc`". pnpm 11은 설정을 **`pnpm-workspace.yaml`**에서 읽고(인증 관련만 `.npmrc`), 워크스페이스 루트 단위로 적용된다 → [추론] `frontend/`만 hoisted로 만들 수 없으므로 Electron 앱을 **별도 워크스페이스 루트**(예 `desktop/`)로 두거나 전체를 hoisted로 바꿔야 한다. electron-builder 문서에는 pnpm 호이스팅 문장이 없다.

**pnpm 11 `blockExoticSubdeps`** [확인, 2026-09-06 `A1` 실측]: `@electron-forge/cli@7.11.2` 설치가 pnpm 11.20.0에서 실패한다. `@electron-forge/shared-types` → `@electron/rebuild@3.7.2`가 `@electron/node-gyp`을 **git 저장소로** 참조하는데 pnpm 11의 `blockExoticSubdeps` 기본값(true)이 하위 의존성의 git 참조를 막기 때문이다. 오류: `[ERR_PNPM_EXOTIC_SUBDEP] Exotic dependency "@electron/node-gyp" (resolved via git-repository) is not allowed in subdependencies when blockExoticSubdeps is enabled`. 해소: `desktop/pnpm-workspace.yaml`에 `blockExoticSubdeps: false`. 이 설정도 워크스페이스 루트 단위라 `desktop/`을 별도 루트로 둔 결정(D7·Q4)과 맞물려 `frontend/`에는 영향이 없다.

**기존 webpack SPA 재사용** [추론, 부록 §1.6]: 원격 로드면 renderer 빌드가 없어 hooks에서 main·preload만 `tsc`/esbuild로 빌드하면 된다. 로컬 번들이면 `output.publicPath: 'auto'|'./'` + HashRouter 또는 `app://` 스킴 + `protocol.handle` fallback이 필요하다. `plugin-webpack`을 main/preload 전용으로 쓰는 방법은 미확인.

**`@electron/*` 요점**: packager 20은 darwin x64/arm64/**universal** 타깃(유니버설은 2배 크기, `mergeASARs`), Electron 44부터 32비트 없음. asar 안의 바이너리는 `exec/spawn` 불가 → `.node` 등은 `unpack`. `@electron/fuses`는 패키징 후·서명 전에 `flipFuses()`, Apple Silicon에서 즉시 서명하지 않으면 `resetAdHocDarwinSignature: true`.

### 3.3 코드 서명·공증 (부록 §2)

**macOS** [문서]

- Apple Developer Program **US$99/년**. 무료 계정은 Developer ID·공증 불가.
- 공증 요건: Developer ID Application 인증서, Hardened Runtime, 모든 실행 파일 서명, secure timestamp, `notarytool`(altool·Xcode 13 이하 거부, 2023-11-01). "usually takes less than an hour"(`@electron/notarize`는 "many minutes").
- entitlements: `com.apple.security.cs.allow-jit`가 V8에 필수. `allow-unsigned-executable-memory`·`disable-library-validation`은 네이티브 모듈 상황에 따라 선택(보안 약화). osx-sign 기본 plist는 `allow-jit` + 디바이스 권한들.
- Forge: `packagerConfig.osxSign: {}` + `osxNotarize: {appleId, appleIdPassword(앱 암호), teamId}` 또는 `appleApiKey/appleApiKeyId/appleApiIssuer`(CI 권장, 2FA 불필요). 환경변수 이름은 코드에서 `process.env`로 직접 전달(규약 아님).
- CI keychain 임포트: GitHub 공식 스크립트(`security create-keychain` → `import` → `set-key-partition-list`), secret은 base64 `.p12`(48 KB 제한 내). electron-builder는 `CSC_LINK`만 주면 내부 처리.
- Gatekeeper: **macOS Sequoia부터 Control-click 우회 제거** → 미서명·미공증 앱은 시스템 설정 › 개인정보 보호 및 보안에서 "그래도 열기"를 눌러야 한다. `safeStorage`·로그인 아이템·쿠키 암호화·`autoUpdater`는 서명·공증 앱에서만 정상.
- MAS 배포는 App Sandbox 필수, `crashReporter`·`autoUpdater` 비활성 → Developer ID 배포가 Knot에 맞다.

**Windows** [문서]

- 2023-06부터 OV 인증서 개인 키는 HSM/하드웨어 토큰 필수. Microsoft(2026-08-29): OV $150~300/년, EV $400+/년, **"EV certificates no longer bypass SmartScreen … removed in 2024"**(Electron 문서의 "EV 필요" 문장은 구식).
- **Azure Artifact Signing**(구 Trusted Signing): Basic **US$9.99/월**(월 5,000 서명), Premium $99.99/월, 초과 $0.005/건, 인증서 3일 유효, EV 아님. 대상: 조직은 미국·캐나다·EU·영국·호주·뉴질랜드·일본·**한국**·싱가포르 등(quickstart 2026-05-21 기준; code-signing-options 페이지는 미국·캐나다·EU·영국만 적어 불일치), **개인 개발자는 미국·캐나다만**. 조직 신원 검증 1~20 영업일. 종량제·EA 구독 필요(무료·체험 불가). Korea Central 엔드포인트 있음. SmartScreen 즉시 신뢰는 아니며 평판 축적 필요. Forge는 `@electron/windows-sign` + `.env.trustedsigning`(경로에 공백 금지), electron-builder는 `win.sign.type: "azure"`(Beta).
- SmartScreen: 미서명은 "Windows protected your PC" → "More info → Run anyway". 기업 정책·Smart App Control(Win11)은 차단. 서명해도 평판 전까지 경고 가능("several weeks and hundreds of clean installs").
- 설치 형식: Squirrel(Forge 기본, 내장 autoUpdater), NSIS(electron-builder 기본, electron-updater), MSI(자동 업데이트 없음, 엔터프라이즈용), MSIX/APPX(Store·App Installer, `maker-msix`는 experimental). Microsoft Store 등록비는 개인·기업 모두 면제, MSIX는 Microsoft가 재서명(인증서 불필요).
- Squirrel 앱은 `electron-squirrel-startup`으로 설치·업데이트 이벤트 처리, 설치 직후 `--squirrel-firstrun` 중에는 업데이트 확인을 10초 지연.

**Linux** [문서]: Forge·electron-builder 문서에 deb/rpm/AppImage 서명 절차 없음(도구 밖 `dpkg-sig` 등). Snap Store(`snapcraft register/upload/release`, `SNAPCRAFT_STORE_CREDENTIALS`), Flathub(PR 제출, `org.electronjs.Electron2.BaseApp` 25.08, `zypak-wrapper`).

### 3.4 자동 업데이트 (부록 §3)

| 방식 | 조건 | 요점 |
| --- | --- | --- |
| Electron 내장 `autoUpdater` | macOS 서명 필수, Windows는 Squirrel 또는 MSIX 설치본, **Linux 미지원** | 이벤트 `checking-for-update`·`update-available`·`update-downloaded`·`error`, `quitAndInstall()`. Squirrel.Windows는 `/RELEASES` 엔드포인트 |
| `update-electron-app` 3.3.0 + update.electronjs.org | 공개 GitHub 저장소 + GitHub Releases + macOS 서명(FAQ: 소스는 비공개여도 릴리즈 전용 공개 저장소 가능) | `updateInterval` 기본 10분(최소 5분), `notifyUser: true`면 다운로드 후 재시작 다이얼로그. 자산 명명: macOS `*-darwin-arm64.zip`, Windows Squirrel `*-win32-x64.exe`/`RELEASES`. 정적 스토리지(S3)도 가능 |
| `electron-updater` 6.8.9 | electron-builder, macOS dmg+zip, Windows NSIS만, Linux AppImage/deb/rpm | `latest*.yml`, GitHub/S3/R2/generic provider, 차등 업데이트, `stagingPercentage`, 채널(`-beta` 접미사, `allowPrerelease`), `verifyUpdateCodeSignature` |
| 자체 서버(Hazel/Nuts/ERS/Nucleus) | — | 모두 2년 이상 정체 → 장기 운영엔 정적 스토리지 권장 |

- 롤백 [추론]: 공식 절차 없음. Squirrel/내장 autoUpdater는 버전 비교 기반이라 **이전 빌드를 더 높은 버전 번호로 재게시**하는 것이 안전 경로. electron-updater는 `allowDowngrade` + 채널 파일 되돌리기 또는 `stagingPercentage: 0`.
- 권장 UX: 백그라운드 다운로드 → `update-downloaded`에서 비차단 알림 + "지금 재시작" → `quitAndInstall()`; 시작 직후에는 체크 지연.

### 3.5 CI/CD (부록 §4)

- 러너: `ubuntu-latest`=24.04, `windows-latest`=Server 2025, **`macos-latest`=macOS 26 arm64**, x64는 `macos-26-intel`. macOS는 arm64 러너에서 x64·universal 교차 패키징 가능(네이티브 모듈 없을 때).
- 요금: Linux $0.006/분, Windows $0.010/분, **macOS $0.062/분(≈10.33배)**. 공개 저장소는 표준 러너 무료. 캐시 10 GB/7일, secret 48 KB, Release 자산 2 GiB/1,000개.
- 캐시: pnpm store(`pnpm/setup@v2 cache: true`), Electron 바이너리 `~/Library/Caches/electron`·`~\AppData\Local\electron\Cache`·`~/.cache/electron`.
- 3-OS 매트릭스 예시(pnpm 11·Node 22·Forge, 부록 §4.3 — 동작 검증은 하지 않음): 태그 `desktop-v*.*.*` push → `permissions: contents: write` → checkout@v7 → pnpm/setup@v2(`runtime: node@22`) → cache@v6(electron) → `pnpm install --frozen-lockfile` → macOS keychain 임포트 → `electron-forge publish`(`GITHUB_TOKEN`, `APPLE_*`, `WINDOWS_CERTIFICATE_*`) → upload-artifact@v7.
- secrets 규약: electron-builder `CSC_LINK`/`CSC_KEY_PASSWORD`/`APPLE_ID`/`APPLE_APP_SPECIFIC_PASSWORD`/`APPLE_TEAM_ID`/`WIN_CSC_*`; Forge는 `osxNotarize`에 직접 전달, Windows `WINDOWS_CERTIFICATE_FILE`/`WINDOWS_CERTIFICATE_PASSWORD`/`WINDOWS_SIGNTOOL_PATH`/`WINDOWS_SIGN_WITH_PARAMS`; Azure `AZURE_TENANT_ID`/`AZURE_CLIENT_ID`/`AZURE_CLIENT_SECRET`.
- 공증 대기는 `submit --wait` 동기 → macOS 잡 `timeout-minutes` 60 권장.

### 3.6 테스트·관측 (부록 §5)

- Playwright Electron(experimental): `_electron.launch({args:['main.js']})` → `firstWindow()`, `evaluate()`(main에서 실행), 네이티브 다이얼로그는 `evaluate`로 `dialog` mock. **`nodeCliInspect` fuse를 끄면 실행 불가** → E2E는 미패키징(또는 fuse 미적용) 빌드로, 프로덕션 fuse는 `npx @electron/fuses read`로 별도 검증.
- Vitest: `vi.mock('electron', factory)`(호이스트, `vi.hoisted` 변수만 참조), `vi.importActual`, `vi.doMock`, `vi.stubGlobal`. webpack 프로젝트에서도 사용 가능(Knot 기존 사용).
- Sentry `@sentry/electron` 7.18: main(`/main`)·renderer(`/renderer`) 각각 `init`, renderer 이벤트는 main 경유, `SentryMinidump`(기본) vs `ElectronMinidump`, `replayIntegration`은 개인정보 마스킹 없이 금지, 소스맵 업로드 `sentry-cli`.
- Electron `crashReporter`: Crashpad, `submitURL`·`uploadToServer`·`rateLimit`(시간당 1회)·`extra`(키 39B/값 127B). 호환 서버: socorro, mini-breakpad-server, Sentry, BugSplat, Backtrace.
- `electron-log` 5.4: macOS `~/Library/Logs/{app}/main.log`, Windows `%APPDATA%\{app}\logs\main.log`, `maxSize` 1 MB 회전(`.old.log`), `electron-log/main|renderer|preload` 브리지.
- 성능: asar + Fuses 세트(`runAsNode: false`, `enableCookieEncryption: true`, `enableNodeOptionsEnvironmentVariable: false`, `enableNodeCliInspectArguments: false`, `enableEmbeddedAsarIntegrityValidation: true`, `onlyLoadAppFromAsar: true`, `grantFileProtocolExtraPrivileges: false`), 지연 `require`, main 블로킹 금지. V8 스냅샷(`electron/mksnapshot`)은 이 규모에서 불필요.

### 3.7 배포·운영 (부록 §6)

- 형식: macOS `dmg`(배포) + `zip`(Squirrel.Mac 업데이트), Windows Squirrel `Setup.exe`(Forge) 또는 NSIS(electron-builder), Linux AppImage/deb.
- 크기(Electron 44.1.1 zip): darwin-arm64 123 MB, darwin-x64 127 MB, win32-x64 150 MB, linux-x64 116 MB. 설치본은 대략 100~150 MB.
- 플랫폼 감지 다운로드 페이지: 공식 가이드 없음. `navigator.userAgentData`로 OS 판별, macOS arm64/x64는 브라우저에서 구분이 어려워 universal 하나 또는 두 링크.
- 라이선스: Electron MIT. 공식 zip에 `LICENSE`·`LICENSES.chromium.html` 포함. electron-builder는 win/linux에서 `LICENSE.electron.txt`로 개명, mac은 `.app` 밖이라 삭제. 동봉 의무를 명시한 Electron 문서 문장은 없음(이슈 #34236 미해결) → 보수적으로 동봉 + 앱 내 오픈소스 고지.
- 개인정보: Sentry replay·`crashReporter.extra`에 개인정보 금지, opt-in UI·고지 문서·법무 검토 필요.

### 3.8 확인하지 못한 항목 (부록 끝 15건·버전 확인 12건 중 결정에 영향 있는 것)

Forge `maker-squirrel`의 macOS 호스트 빌드 가능 여부(문서 상충), `plugin-webpack` main/preload 전용 구성, `@electron/notarize` 자동 staple 여부, `update-electron-app`의 draft/prerelease 처리, Azure Artifact Signing 한국 조직 대상 최종 확인(문서 불일치), Forge 8·electron-builder 27 정식 시점, Playwright 1.63 이후 Electron 지원 상태.

## 4. 데스크톱 앱 인증 패턴 (표준·GitHub·Spring Security·쿠키 규칙, 2026-09-04 조사)

표기: **[문서]** 출처에서 직접 확인(인용은 원문), **[추론]** Knot 상황에 적용한 해석.

### 4.1 전제 정정 (GitHub 2025~2026 변경) [문서]

| 과거 전제 | 현재 사실 | 출처 |
| --- | --- | --- |
| OAuth App은 콜백 URL 1개만 | github.com에서 **"You can enter up to 10 callback URLs."** (2026-08-14 변경) | https://github.blog/changelog/2026-08-14-multiple-redirect-uris-and-token-refresh-for-oauth-apps/ |
| 리프레시 토큰은 GitHub Apps만 | OAuth App도 expiring tokens 지원(access 8시간, refresh 6개월 미사용 시 만료, rotation). 신규 앱은 기본 on, 또는 `offline_access` scope | authorizing-oauth-apps.md |
| GitHub OAuth App은 PKCE 미지원 | 2025-07-14부터 S256 PKCE 지원·권장(강제 아님). device flow·installation token은 PKCE 미사용 | https://github.blog/changelog/2025-07-14-pkce-support-for-oauth-and-github-app-authentication/ |
| — | **2026-08-03 이전에 콜백 1개로 만든 앱은 wildcard matching이 켜져 있음** → "we recommend that you disable it" | creating-an-oauth-app.md |

### 4.2 표준 (RFC 8252·7636·9700·OAuth 2.1·8628) [문서]

**RFC 8252 OAuth 2.0 for Native Apps** (https://www.rfc-editor.org/rfc/rfc8252.txt)

- §3: 하이브리드 앱(웹 기술 + 네이티브 배포)도 native app으로 취급.
- §4: "perform the OAuth authorization request in an external user-agent (typically the browser) rather than an embedded user-agent (such as one implemented with web-views)."
- §6: "Public native app clients MUST implement the Proof Key for Code Exchange (PKCE)".
- §7 리다이렉트 URI 3종: (1) private-use scheme은 reverse-domain 기반 MUST("A scheme such as "myapp", however, would not meet this requirement"), 예 `com.example.app:/oauth2redirect/…`; (2) claimed https; (3) loopback `http://127.0.0.1:{port}/{path}`("MUST allow any port", `localhost`는 NOT RECOMMENDED).
- §8.1: 같은 스킴을 여러 앱이 등록할 수 있어 코드 수신 앱이 불확정, loopback도 같은 인터페이스의 다른 앱이 가로챌 수 있음 → PKCE 없는 요청은 SHOULD reject.
- §8.5: 앱에 정적으로 포함된 secret은 비밀이 아님 → public client.
- §8.9: `state`는 고엔트로피 난수, 불일치 응답 거부.
- §8.12: "native apps MUST NOT use embedded user-agents to perform authorization requests". 이유: 키 입력 기록·동의 자동 제출·세션 쿠키 복사. **"Even when used by trusted apps belonging to the same party as the authorization server, embedded user-agents violate the principle of least privilege"**.

**RFC 7636 PKCE**: verifier 43~128자, `S256 = BASE64URL(SHA256(verifier))`, S256 가능하면 MUST. 코드를 가로채도 verifier 없이는 교환 불가.

**RFC 9700 OAuth 2.0 Security BCP** (https://www.rfc-editor.org/rfc/rfc9700.txt): public client PKCE MUST; refresh token은 sender-constrained 또는 **rotation MUST**(§4.14.2: 재사용 감지 시 활성 토큰 폐기, 비활성 시 만료 SHOULD, 로그아웃 시 폐기 MAY); redirect URI는 정확 일치(loopback 포트만 예외).

**OAuth 2.1 draft-16** (2026-09): `code_challenge` REQUIRED, 마침표 없는 private-use 스킴은 SHOULD reject, per-request 커스터마이즈는 redirect URI가 아니라 `state`로, embedded UA는 WebAuthn도 비활성.

**RFC 8628 Device Authorization Grant**: "not intended to replace browser-based OAuth in native apps on capable devices" → 데스크톱에서는 fallback. `user_code` rate-limit·기기 소유 확인 권고, `slow_down` 시 interval +5초.

### 4.3 GitHub OAuth 특성 [문서]

| 항목 | 내용 |
| --- | --- |
| 권장 | "GitHub Apps are preferred over OAuth apps"(세밀 권한·단기 토큰). Knot 현행은 OAuth App(`read:user`) |
| 콜백 URL | 최대 10개. `redirect_uri` 생략 시 첫 번째. wildcard 토글(켜면 host·port 일치 + 하위 경로 허용, 보안 경고) |
| Loopback | 등록 `http://127.0.0.1/path` → 요청 `http://127.0.0.1:1234/path` 허용("does not need to match the port"). `localhost` 대신 `127.0.0.1`/`::1` 권장 |
| 커스텀 스킴 | 명시 서술 없음. `prompt` 설명의 "non-HTTP redirect URI" 문구가 간접 증거(미확인) |
| PKCE | `code_challenge_method=S256`만, `code_verifier`는 challenge 보냈으면 필수. `client_secret` 생략 가능 여부 미확인 |
| Device flow | 앱 설정에서 "Enable Device Flow" 필요. 모범사례: "Do not enable the device flow… unless you are using the app in a constrained environment (CLIs, IoT devices, or headless systems)" |
| 코드 수명 | 10분. 토큰 엔드포인트는 CORS preflight 미지원(브라우저 직접 호출 불가) |
| embedded webview 차단 | GitHub 문서에서 확인 못함(Google은 2021-09-30부터 `disallowed_useragent`로 차단) |

출처: https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/{authorizing-oauth-apps,creating-an-oauth-app,best-practices-for-creating-an-oauth-app}

### 4.4 데스크톱 로그인 패턴 4종 (백엔드가 OAuth 클라이언트)

| 패턴 | 흐름 | 장점 | 단점·위험 | 근거 |
| --- | --- | --- | --- | --- |
| **A. 시스템 브라우저 + 딥링크 회수** | 앱이 `shell.openExternal(API/oauth2/authorization/github?client=desktop&code_challenge=…)` → 백엔드가 GitHub 로그인 완료 → 일회용 코드를 `knot://auth/callback?code=…`로 302 → 앱이 코드 + verifier로 `POST /auth/device/token` → Bearer 디바이스 토큰 | 브라우저 세션 공유, GitHub 등록 변경 불필요, 패스키 사용 가능 | 스킴 충돌·탈취(§8.1) → 1회용·짧은 TTL·PKCE 바인딩·state 필수. macOS·Linux는 패키징 후에만 동작 | RFC 8252 §4·§7.1·§8.1, VS Code `vscode://vscode.github-authentication/did-authenticate?nonce=…`(소스 확인), Slack "Sign In to Slack to be taken to your web browser" |
| **B. 시스템 브라우저 + loopback 서버** | 앱이 `127.0.0.1:{임의 포트}`에 HTTP 리스너 → 백엔드가 `http://127.0.0.1:{port}/callback?code=…`로 302 → 앱이 코드 교환 후 포트 즉시 닫음 | 스킴 등록 불필요, 개발 모드에서도 동일 동작 | 같은 loopback의 다른 앱 탈취 가능(→ PKCE), 브라우저가 앱을 활성화하지 않아 "탭을 닫고 돌아가세요" 페이지 필요. Windows 방화벽 프롬프트는 미확인 | RFC 8252 §7.3·§8.3, Google/Microsoft 데스크톱 가이드(`127.0.0.1` 권장) |
| **C. 앱 내 BrowserWindow로 `https://knoted.kr` 로드 + 쿠키 재사용** | 웹과 동일한 302 체인이 Electron 세션 안에서 완료 → `__Host-` 쿠키가 first-party로 저장 | **백엔드 변경 0**, 웹과 동일 동작 | RFC 8252 §8.12(1st party여도 최소권한 위반, GitHub 관점에선 embedded UA), OAuth 2.1 §8.5.1(WebAuthn 비활성 → GitHub 패스키 영향 가능), 브라우저 세션 미공유. GitHub의 차단 정책은 미확인 | Electron `session` 문서, RFC 8252 §8.12 |
| **D. Device flow(백엔드 브로커)** | 앱이 `POST /auth/device/code` → `user_code`·`verification_uri` 표시 → 사용자가 웹에서 로그인 후 코드 입력 → 앱 폴링 | 딥링크·loopback 불가 환경 대응 | 표준·GitHub 모범사례상 capable device의 기본 경로 아님 | RFC 8628 §1·§5 |

권고 [추론]: **A(또는 B)를 정식 경로, C는 1단계 임시 경로, D는 fallback**. A와 B는 백엔드 코드가 동일하고 회수 URL만 다르므로 둘 다 지원할 수 있다.

### 4.5 Electron 구현 포인트 (공통) [문서]

- `app.setAsDefaultProtocolClient('kr.knoted.app' 또는 'knot')`; macOS는 `Info.plist` `CFBundleURLTypes` 필수(런타임 수정 불가), Windows 레지스트리.
- 개발 모드: `process.defaultApp`이면 `setAsDefaultProtocolClient(scheme, process.execPath, [path.resolve(process.argv[1])])`.
- macOS `app.on('open-url')`은 **`ready` 전에 등록**(늦으면 런치 URL 유실). Windows/Linux는 `requestSingleInstanceLock()` + `second-instance`의 `commandLine` 마지막 요소, 콜드 스타트는 `process.argv` 마지막 요소. "The order might change and additional arguments might be appended" → 스킴 접두로 찾는다.
- 다른 사용자가 두 번째 인스턴스를 띄우면 `argv`가 전달되지 않음.
- `shell.openExternal(url)`은 검증된 `https://api.*.knoted.kr` URL만.
- 출처: https://www.electronjs.org/docs/latest/tutorial/launch-app-from-url-in-another-app , /api/app , /api/shell

### 4.6 Spring Security 측 (Boot 4.1.x / Security 7.1.x) [문서]

버전: Spring Boot 4.1.1 → `spring-security-core 7.1.1`, `spring-core 7.0.9`, Java 17~26.

**쿠키 + Bearer 병행**

- `BearerTokenAuthenticationFilter`는 토큰이 없으면 체인을 계속 진행한다(소스: "Did not process request since did not find bearer token" → `filterChain.doFilter`). 따라서 기존 `JwtAuthenticationFilter`(쿠키)와 같은 체인에 `.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))`를 추가할 수 있다.
- HS256: `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()`.
- 토큰 위치 커스터마이즈: `DefaultBearerTokenResolver`, `HeaderBearerTokenResolver`, `oauth2.bearerTokenResolver(resolver)`(`setBearerTokenResolver`는 deprecated → `AuthenticationConverter`).
- 체인 분리: `@Order` + `http.securityMatcher("/api/**")`, `RequestHeaderRequestMatcher("Authorization")`(값 무관, 존재만).
- 출처: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/{jwt,bearer-tokens}.html , https://docs.spring.io/spring-security/reference/servlet/configuration/java.html

[추론] Knot 적용: 기존 쿠키 필터 유지 + 리소스 서버 JWT 병행(Bearer 먼저 인증되면 쿠키 필터는 이미 인증된 컨텍스트를 건너뜀). 같은 HS256 키를 쓰되 `aud`/`typ` 클레임으로 디바이스 토큰을 구분해 `OAuth2TokenValidator`로 검증. 또는 `BearerTokenResolver`를 커스텀해 헤더 우선·쿠키 폴백으로 필터 하나로 통합.

**CSRF**

- Spring: "If you are creating a service that is used only by non-browser clients, you likely want to disable CSRF protection". SameSite는 "defense in depth rather than the sole protection".
- `CsrfConfigurer#requireCsrfProtectionMatcher(RequestMatcher)`(기본 GET/HEAD/TRACE/OPTIONS 제외), `ignoringRequestMatchers(...)`.
- [추론] Bearer 요청은 브라우저가 자동으로 붙이는 자격증명이 아니므로 CSRF 면제 가능: `requireCsrfProtectionMatcher(and(DEFAULT_CSRF_MATCHER, not(RequestHeaderRequestMatcher("Authorization"))))`. 쿠키만 있는 요청은 헤더가 없어 CSRF가 그대로 적용된다. (`CsrfFilter.DEFAULT_CSRF_MATCHER`·`AndRequestMatcher`·`NegatedRequestMatcher`의 7.1.1 시그니처는 재확인 필요)
- 출처: https://docs.spring.io/spring-security/reference/features/exploits/csrf.html , /servlet/exploits/csrf.html

**CORS**

- "CORS must be processed before Spring Security, because the pre-flight request does not contain any cookies". `allowCredentials=true`이면 `*` 불가.
- `Origin: null`은 `file:`·`data:`·`blob:`·sandbox iframe 등에서 발생 → 허용 목록에 넣으면 모든 `file://` 페이지에 credentialed CORS를 여는 셈이므로 금지.
- [추론] 원격 로드면 CORS 변경 불필요. 로컬 번들 + Bearer면 `allowCredentials` 없이 `app://knot` 오리진만 추가(커스텀 스킴이 보내는 실제 `Origin` 값은 미확인).

**리프레시/디바이스 토큰**

- RFC 6749 §10.4(refresh token 기밀·클라이언트 바인딩·남용 탐지), RFC 9700 §4.14.2(rotation·family 폐기), RFC 7009(폐기 엔드포인트: `token`, `token_type_hint`, 즉시 무효화, refresh 폐기 시 같은 grant의 access도 폐기, 무효 토큰이어도 200).
- [추론] 데스크톱은 public client → (짧은 access JWT, 기존 HS256 키) + (DB 저장·해시된 opaque refresh token, rotation + 재사용 감지 시 family 폐기 + 비활성 만료). 테이블에 `deviceName, platform, lastUsedAt, createdAt, revokedAt`을 두면 GitHub/Slack/Notion식 기기 목록·원격 로그아웃 UX 제공 가능.

**`oauth2Login` 성공 후 분기**

- `successHandler(AuthenticationSuccessHandler)`(기본 `SavedRequestAwareAuthenticationSuccessHandler`), `OAuth2LoginConfigurer.AuthorizationEndpointConfig.authorizationRequestResolver(...)`, `authorizationRequestRepository(...)`(공식 구현은 `HttpSessionOAuth2AuthorizationRequestRepository`뿐, **`HttpCookieOAuth2AuthorizationRequestRepository`는 커뮤니티 패턴**).
- `DefaultOAuth2AuthorizationRequestResolver.setAuthorizationRequestCustomizer(builder -> builder.attributes(...)/additionalParameters(...))`, `OAuth2AuthorizationRequestCustomizers.withPkce()`(백엔드↔GitHub 구간 PKCE, verifier는 `code_verifier` attribute에 저장).
- Microsoft: "do not put URLs or other sensitive data directly in the state parameter. Instead, use a key or identifier"(open redirector 위협).
- [추론] `/oauth2/authorization/github?client=desktop&code_challenge=…&return=loopback:PORT` 진입 → 커스텀 resolver가 `attributes`에 보관(GitHub `state`는 Spring 기본 난수 유지) → 세션(또는 서명 쿠키)에 저장 → 커스텀 성공 핸들러가 `client=desktop`이면 쿠키를 발급하지 않고 일회용 코드 + 302(`knot://…` 또는 `http://127.0.0.1:PORT/…`). 성공 핸들러 시점에는 `OAuth2AuthorizationRequest`가 이미 제거되므로 resolver 단계에서 attributes를 별도 저장소에 복제해 둔다(구현 경험 기반 추론). ADR 314의 "고정 리다이렉트 3개" 결정은 이 변경으로 재논의된다.
- 출처: https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html , Javadoc `OAuth2LoginConfigurer.AuthorizationEndpointConfig`, `OAuth2AuthorizationRequest.Builder`, `OAuth2AuthorizationRequestCustomizers`

### 4.7 Chromium/Electron 쿠키 규칙 [문서]

- `__Host-`: Secure + secure page에서 설정 + Domain 없음 + Path=/ → 설정한 호스트에만 전송. `HttpOnly`는 `document.cookie` 접근만 막고 `fetch()`에는 자동 전송(MDN Set-Cookie).
- `SameSite=Lax`: same-site 요청과 "top-level navigation + safe method"인 cross-site 요청에만 전송. Chrome은 미지정을 Lax로 취급. **schemeful same-site**(scheme + eTLD+1)가 SameSite 판정에 적용(web.dev, MDN Glossary Site).
- [추론] `app://knot`·`file://`에서 `fetch('https://api.knoted.kr', {credentials:'include'})`는 top-level navigation이 아닌 cross-site 요청 → Lax 쿠키 미전송. **현행 쿠키 인증은 로컬 번들 방식에서 구조적으로 깨진다.** `SameSite=None; Secure`로 바꾸면 CSRF 표면 확대·CORS credentials 관리 부담. 원격 `https://knoted.kr` 로드(패턴 C)에서는 same-site라 웹과 동일.
- Electron `cookies.get` 필터 `httpOnly`·Cookie 구조체 `httpOnly`/`sameSite` 필드 존재, 제외 조항 없음 → main은 HttpOnly 값을 읽고 쓸 수 있다[추론]. HttpOnly는 renderer 격리 수단일 뿐 main 대비 보호가 아니다.
- `webRequest.onBeforeSendHeaders`로 `Cookie`/`Authorization` 주입 가능 → 디바이스 토큰을 main이 보관하고 `https://api.*.knoted.kr/*` 요청에만 Bearer를 붙이는 설계 가능. 위협 모델은 쿠키와 동일(XSS는 토큰을 못 읽지만 요청 위조 가능). **URL 필터를 API 오리진으로 엄격히 제한하지 않으면 토큰이 타 오리진으로 유출**.
- 3PCD: Chrome은 2025-04·2025-10 발표로 서드파티 쿠키 기본 차단을 하지 않음. Electron 문서에 3PC 설정 없음(이슈 #36922 "not planned"). Knot 쿠키는 top-level 로드의 first-party라 무관.
- 출처: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie , https://web.dev/articles/samesite-cookies-explained , https://web.dev/articles/schemeful-samesite , https://www.electronjs.org/docs/latest/api/{cookies,web-request} , https://privacysandbox.google.com/blog/privacy-sandbox-next-steps

### 4.8 토큰 저장·세션 운영 [문서]

- `safeStorage`: macOS Keychain(다른 사용자·다른 앱으로부터 보호), Windows DPAPI(**같은 사용자 계정의 다른 앱은 복호화 가능**), Linux `gnome_libsecret`/`kwallet*`(없으면 하드코딩 평문 키로 "unprotected"). `isEncryptionAvailable()`, `encryptStringAsync/decryptStringAsync`.
- keytar: 저장소 2022-12-15 archived(read-only), npm 7.9.0(2022-02). 사용 금지.
- [추론] refresh 토큰은 `safeStorage.encryptString` 후 `userData` 파일에, access JWT는 메모리에만. 로그아웃은 RFC 7009식 폐기 API 호출 → 로컬 파일 삭제 → `session.clearStorageData({storages:['cookies']})`.
- 기기 목록·원격 로그아웃 사례: GitHub Sessions "Revoke session", Slack "Sign out all other sessions", Notion "Log out of all devices"(현재 세션 유지), Google 기기별 "Sign out" + 마지막 통신 시각.
- 딥링크 탈취 완화: 코드 1회용·TTL 60~120초(RFC 6749 상한 10분)·재사용 시 연관 토큰 폐기, PKCE 유사 S256 바인딩, 앱 내 state/nonce 매칭(VS Code 방식), reverse-domain 스킴(`kr.knoted.app`) 또는 최소 마침표 포함, loopback 1차 + 딥링크 2차.

### 4.9 확인하지 못한 항목

GitHub embedded webview 차단 정책 유무, GitHub 콜백에 커스텀 스킴 등록 가능 여부, PKCE 시 `client_secret` 생략 가능 여부, Electron 기본 세션 3PC 차단 여부, `cookies.get`의 HttpOnly 반환 명시 문장, 커스텀 스킴 fetch의 `Origin` 값, loopback 리스너 Windows 방화벽 프롬프트, Slack/Notion/Linear의 브라우저→앱 복귀 세부, Spring `CsrfFilter.DEFAULT_CSRF_MATCHER`·`AndRequestMatcher`·`NegatedRequestMatcher` 7.1.1 시그니처.

## 5. 데스크톱 셸 대안 비교·원격 로드 사례·원격 MCP 서버 (2026-09-04 조사)

### 5.1 Electron vs Tauri 2 vs PWA

| 기준 | Electron 44.1.1 | Tauri 2.11.5 | PWA(설치형 웹앱) |
| --- | --- | --- | --- |
| 렌더러 | 번들 Chromium 152, 3 OS 동일 [확인] | Windows WebView2(Chromium), macOS/iOS WKWebView(OS 업데이트에 종속), Linux webkit2gtk(배포판별 파편화) [확인] | 사용자 브라우저 |
| main/백엔드 언어 | TS/JS(Forge `webpack-typescript` 템플릿이 main·preload·renderer 전부 TS) [확인] | Rust(`#[tauri::command]`, 플러그인·에러 타입 모두 Rust) [확인] | 없음 |
| 프레임워크 배포 크기 | zip 기준 darwin-arm64 ≈124 MiB, win32-x64 ≈158 MB, linux-x64 ≈123 MB [확인] | 최소 앱 <600 KB [확인] | 0 |
| 메모리 | 공식 수치 없음 | 공식 수치 없음 | 브라우저 공유 |
| 보안 모델 | sandbox·contextIsolation 기본, contextBridge, Fuses | Permissions + Scopes + Capabilities(`src-tauri/capabilities/`). 원격 URL은 `remote.urls` 명시 허용, Linux/Android는 iframe과 창 구분 불가 경고 [확인] | 브라우저 |
| 자동 업데이트 | `autoUpdater`(Squirrel), update.electronjs.org(공개 repo + 서명), electron-updater. Linux 공식 문서 없음 | `tauri-plugin-updater`(서명 필수, static JSON 또는 dynamic server) [확인] | 불필요 |
| 서명·공증 | `@electron/osx-sign`·`@electron/notarize`, Forge 통합 | env 기반 서명·공증 내장, 무료 Apple 계정은 공증 불가 [확인] | 불필요 |
| E2E | Playwright(experimental Electron 지원) [확인] | WebdriverIO `@wdio/tauri-service` 또는 Selenium + `tauri-driver`(macOS 불가). Playwright 언급 없음 [확인] | Playwright 그대로 |
| 트레이·글로벌 단축키·자동 시작·네이티브 메뉴 | 내장 API | 공식 플러그인 | 없음. "Run on OS Login"은 사용자가 `about://apps`에서 수동 opt-in, 개발자 제어 불가 [확인] |
| 모바일 | 없음 | iOS/Android 지원(플러그인 일부 미지원) | 모바일 브라우저 |
| 채택 | 공식 showcase에 VS Code, Slack, Discord, Notion, Figma, 1Password, Obsidian, Signal, Teams, Postman, GitHub Desktop [확인] | 공식 showcase 없음. Spacedrive, Yaak(README 확인) | — |

- Electron 공식 문서(why-electron): OS 웹뷰는 "limited by the browser version included in the oldest operating system version you need to support", 번들 Chromium은 보안 픽스를 OS 업그레이드 없이 배포. [확인] https://www.electronjs.org/docs/latest/why-electron
- Tauri 공식 문서(webview-versions): macOS WebKit은 OS 업데이트로만 갱신, 미지원 macOS는 WebKit 업데이트를 받지 못함. [확인] https://tauri.app/reference/webview-versions/
- Tauri "even without needing to be developed by Rust experts"라고 하나 커맨드·플러그인·에러 타입은 Rust 작성 필수. [확인] https://tauri.app/start/ , https://tauri.app/develop/calling-rust/
- 기타(한 줄): Neutralinojs(OS 웹뷰 + C++ 서버, ≈2MB), Wails(Go + 네이티브 웹뷰, v3 베타), Flutter desktop(웹뷰 아닌 네이티브 렌더링, Dart), .NET MAUI(C#, Linux 미지원), `@capacitor-community/electron`(unmaintained).

**PWA 세부** [확인]

- 설치 조건: HTTPS + manifest(`name`/`short_name`, `icons` 192·512, `start_url`, `display`), Chrome 112부터 메뉴 설치에는 fetch 핸들러 Service Worker 불필요(자동 프롬프트에는 필요). macOS Safari 17은 "Add to Dock"(manifest 없어도 가능).
- 지원: Badging(`navigator.setAppBadge`), Window Controls Overlay(`display_override`), File handlers(데스크톱 전용), `protocol_handlers`(`web+knot` 접두 필수), `launch_handler`.
- 한계: 트레이·글로벌 단축키·네이티브 메뉴 없음, 자동 시작 개발자 제어 불가.
- Cloudflare Workers `not_found_handling: "single-page-application"`은 실제 파일을 먼저 서빙하므로 `manifest.webmanifest`·`sw.js`를 `dist/`에 두면 된다.
- msw 충돌: 브라우저는 scope당 SW 1개. Knot은 `API_MOCKING=true`(dev)에서만 `/mockServiceWorker.js`를 등록하므로 프로덕션 전용 PWA SW는 충돌 없음. dev에서도 PWA SW를 켜려면 `importScripts('/mockServiceWorker.js')` 병합 + `worker.start({serviceWorker:{url}})`. https://mswjs.io/docs/recipes/merging-service-workers/

**결론 후보 검토**

- "웹과 동일한 Chromium 렌더링 보장 + TS만으로 main 작성 → Electron" 논리는 Electron·Tauri 양쪽 공식 문서로 성립한다.
- Tauri를 골라야 할 조건: 배포 크기·메모리가 최우선, Rust 담당자 확보, macOS/Linux WebKit 호환 QA 여력, 모바일까지 한 코드베이스 계획 — 네 가지가 동시에 충족될 때.
- PWA는 "웹 그대로 + 설치 아이콘"만 필요할 때 가장 싸다. Knot에 데스크톱 고유 기능(트레이·글로벌 단축키·딥링크·알림) 요구가 없다면 PWA가 먼저다.

### 5.2 원격 URL 로드형 데스크톱 앱 사례

| 앱 | 로드 방식 | 근거 |
| --- | --- | --- |
| Slack | 하이브리드: "we ship some of the assets as part of the app, but most of the assets and code are loaded remotely." 데스크톱은 slack.com을 가리키는 "guest pages"의 호스트. preload 원칙: 모듈 누출 금지, 파일 경로 API 신중, remote 객체 감사 | [확인] https://slack.engineering/building-hybrid-applications-with-electron/ , https://slack.engineering/growing-pains-migrating-slacks-desktop-app-to-browserview/ |
| Figma | 원격 웹앱 임베드(BrowserView 개발 계기). "same features and functionality as using Figma in a browser". 로컬 폰트는 FigmaAgent, 구버전 6개월 후 지원 종료 | [확인] https://www.figma.com/blog/introducing-browserview-for-electron/ , https://help.figma.com/hc/en-us/articles/5601429983767 |
| Linear | "the same Javascript/React application we build for the web, but with the Electron wrapper you get nicer notifications, dock badge… it's always on" | [확인] https://linear.app/changelog/2019-04-25-linear-desktop-app |
| Notion | 로드 방식 공식 미확인. 오프라인은 데스크톱·모바일 앱 전용(페이지 단위 다운로드) | [확인] https://www.notion.com/help/use-pages-offline |
| Discord | Electron + 자체 updater(델타). 로드 방식 미확인 | [확인] https://discord.com/blog/how-discord-seamlessly-upgraded-millions-of-users-to-64-bit-architecture |

원격 로드의 장점은 웹 배포로 즉시 업데이트·셸 릴리스 최소화(Slack 모델), 단점은 오프라인 불가와 "untrusted remote content" 취급(nodeIntegration off·sandbox·최소 contextBridge·네비게이션 제한·CSP). Electron Security 문서: "Displaying arbitrary content from untrusted sources poses a severe security risk that Electron is not intended to handle", "Do not directly expose Electron's APIs, especially IPC, to untrusted web content in your preload scripts". [확인] https://www.electronjs.org/docs/latest/tutorial/security

### 5.3 프론트 라이브러리·webpack target 특이사항

- React 19 + Electron 알려진 이슈: 발견되지 않음. React DevTools는 `ses.loadExtension()` 수동 로드.
- react-router 8.3.1(peer `react >=19.2.7`): 원격 로드면 `createBrowserRouter`(현행) 그대로(Cloudflare SPA fallback이 새로고침 처리). 로컬 번들이면 `createHashRouter`/`createMemoryRouter` 또는 `app://` 표준 스킴 + `protocol.handle` fallback 필요. electron-vite 문서: "only a hash-based router will work properly in production"(file 로드 기준). [확인] https://reactrouter.com/api/data-routers/createHashRouter , https://electron-vite.org/guide/troubleshooting
- Emotion 11.14: `createCache({ nonce })`로 CSP nonce 부여 가능. [확인] https://emotion.sh/docs/@emotion/cache
- webpack 5 `target`: `electron-main`, `electron-preload`, `electron-renderer`(Node·Electron 내장 모듈을 externals 처리). **sandbox renderer에는 `require`가 없으므로 renderer는 `target: 'web'` 유지**, preload는 별도 엔트리(`electron-preload` 또는 Forge `sandboxedPreload`). Forge 문서: `nodeIntegration: false`면 renderer target `web`. [확인] https://webpack.js.org/configuration/target/ , https://www.electronforge.io/config/plugins/webpack

### 5.4 MCP 서버 (스펙·Java 구현·원격 변형 — 데스크톱 로컬 MCP 서버 `S8`의 스펙 근거, 서버 호스팅 변형 `A13`의 검토 재료)

2026-09-09: 데스크톱 앱이 `127.0.0.1`에 띄우는 **로컬** MCP 서버가 탐색의 데스크톱 경로로 채택됐다(로드맵 `S8`, 검토 문서 7절 F안). 아래 스펙·보안 요구는 그 근거이고, Java 구현·원격 등록 항목은 서버 호스팅 변형(`A13`)에만 해당한다. 세 CLI의 등록 명령·스킬 경로는 §6.8.

**스펙(현재 2026-07-28)** [확인] https://modelcontextprotocol.io/specification/versioning

| 항목 | 내용 |
| --- | --- |
| 전송 | stdio, **Streamable HTTP**(단일 엔드포인트 POST, JSON 또는 요청 단위 SSE, Origin 검증 MUST). HTTP+SSE(2024-11-05)는 deprecated |
| 2026-07-28 변경 | `Mcp-Session-Id` 세션 제거, `initialize` 핸드셰이크 제거(매 요청 `_meta`에 protocolVersion), `server/discover` 필수, `subscriptions/listen`, `Mcp-Method`/`Mcp-Name` 헤더 필수, Sampling·Roots·Logging·DCR deprecated |
| 인증(HTTP, OPTIONAL) | OAuth 2.1. MCP 서버=리소스 서버. **RFC 9728 Protected Resource Metadata MUST**, AS 메타데이터(RFC 8414/OIDC) MUST, Client ID Metadata Documents SHOULD, PKCE, **RFC 8707 `resource` MUST**, `Authorization: Bearer`만. 흐름: 401 + `WWW-Authenticate: Bearer resource_metadata=…` → PRM → AS 메타데이터 → 클라이언트 등록 → 브라우저 인가 → 토큰 |
| 프리미티브 | Tools(model-controlled), Resources(application-controlled), Prompts(user-controlled) |
| 로컬 서버 보안 요구(2025-06-18 개정판 transports 원문, 2026-09-09 확인) | "Servers **MUST** validate the `Origin` header on all incoming connections to prevent DNS rebinding attacks", "When running locally, servers **SHOULD** bind only to localhost (127.0.0.1) rather than all network interfaces (0.0.0.0)", "Servers **SHOULD** implement proper authentication for all connections". 단일 MCP 엔드포인트(예 `/mcp`)에 클라이언트가 JSON-RPC를 POST하고 `Accept: application/json, text/event-stream`을 보낸다. 이 개정판은 `Mcp-Session-Id`·`initialize`를 쓰고, `MCP-Protocol-Version` 헤더가 없으면 서버가 `2025-03-26`으로 가정한다 → 로드맵 Q47·Q48의 근거 https://modelcontextprotocol.io/specification/2025-06-18/basic/transports |

**Java 구현** [확인]

| 항목 | 값 |
| --- | --- |
| Spring AI | 2.0.1(GA 2.0.0 2026-06-12). Spring Boot 4.0.x·4.1.x 지원, Java 25 호환 |
| 스타터 | `spring-ai-starter-mcp-server-webmvc`(권장), `-webflux`, `-server`(stdio). `spring.ai.mcp.server.protocol=STREAMABLE`(SSE는 2.0부터 deprecated) |
| 툴 등록 | `@McpTool`/`@McpToolParam`, `@McpResource`, `@McpPrompt`(annotation scanner 기본 on) |
| 보안 | HTTP 전송은 기본 "unauthenticated JSON-RPC endpoint". 커뮤니티 `org.springaicommunity:mcp-server-security`(`McpServerOAuth2Configurer.mcpServerOAuth2()`, `validateAudienceClaim(true)`), WebMVC·JWT 한정, WIP |
| MCP Java SDK | `io.modelcontextprotocol.sdk:mcp` 2.0.1(2026-08-19), Java 17+, Tier 2. **conformance는 2025-11-25 스펙 기준** → 2026-07-28(무세션·무initialize) 대응 미확인 |

**클라이언트 등록** [확인]

- Claude Code: `claude mcp add --transport http knot https://…/mcp` (`--header "Authorization: Bearer …"`, `--scope local|project|user`), `/mcp`에서 브라우저 로그인, `claude mcp login knot`. SSE 전송 deprecated. 로컬 `http://127.0.0.1:<port>/mcp`도 같은 명령이다(§6.8). https://code.claude.com/docs/en/mcp
- Claude Desktop/claude.ai: Settings > Connectors > Add custom connector(URL, 선택 OAuth Client ID/Secret) → 브라우저 OAuth. `claude_desktop_config.json`은 로컬 stdio 전용. **커넥터는 Anthropic 클라우드에서 서버로 접속**하므로 서버 공개 노출·IP 허용 전제. https://modelcontextprotocol.io/docs/develop/connect-remote-servers
- Cursor: `.cursor/mcp.json` `{"mcpServers":{"knot":{"url":"https://…/mcp"}}}`, Connect → 브라우저 OAuth. https://cursor.com/docs/mcp

**"제품이 구독을 제공하는 것이 아니다" 근거** [확인]

- MCP 아키텍처: 호스트(Claude Code·Claude Desktop)가 서버에 연결하며 모델 호출 주체는 사용자의 호스트 앱. https://modelcontextprotocol.io/docs/learn/architecture
- Anthropic: "Custom connectors allow you to connect Claude to arbitrary services that have not been verified by Anthropic." "These servers are not owned, operated, or endorsed by Anthropic." https://support.claude.com/en/articles/11175166 , https://platform.claude.com/docs/en/agents-and-tools/remote-mcp-servers
- Sampling(서버가 클라이언트 LLM을 빌려 쓰는 기능)이 2026-07-28에 deprecated → Knot 서버가 사용자 Claude로 모델 호출을 우회하는 설계는 스펙상으로도 권장되지 않음.

### 5.5 확인하지 못한 항목

- Electron·Tauri 빈 앱 설치 크기·메모리 공식 수치, Notion·Discord 로드 방식, `launch_handler` 출시 Chrome 버전, Anthropic 헬프센터 "Build custom connectors via remote MCP servers" 본문(404), MCP Java SDK·Spring AI의 2026-07-28 스펙 지원, Spring AI 2.0 `@Tool`+`ToolCallbackProvider` 유지 여부.

## 6. Claude API·Anthropic 정책 (백엔드 어댑터 설계 재료)

출처: Claude Code 번들 `claude-api` 스킬(모델 표 캐시 2026-06-24), 검토 문서 2.1절(2026-09-04 공식 문서 확인). 실제 구현 Issue에서 https://platform.claude.com/docs/en/about-claude/models/overview.md 와 https://platform.claude.com/docs/en/pricing.md 를 다시 조회해 확정한다.

### 6.1 정책 제약 (검토 문서 2.1·5.1절 요약)

| 항목 | 내용 | 기획 반영 |
| --- | --- | --- |
| 서드파티 제품의 claude.ai 로그인·구독 한도 제공 | Agent SDK 공식 문서가 "사전 승인 없이는 금지"로 명시 | 데스크톱 앱은 사용자 구독 크리덴셜(`~/.claude`, Keychain)을 **읽지도 요구하지도 않고** `claude` 바이너리를 실행하지도 않는다(2026-09-09, 로드맵 불변 계약 3번). 사용자가 자기 Claude Code에 Knot MCP 서버를 등록하는 것은 이 조항의 대상이 아니다(아래 "제3자 MCP 서버·스킬 등록" 행) |
| `claude setup-token` / `CLAUDE_CODE_OAUTH_TOKEN` | Claude Code CLI 전용, Agent SDK bare mode는 읽지 않음 | 앱 로그인 폴백으로 쓰지 않는다 |
| 변경 없는 Claude Code 바이너리를 제품이 실행 + 사용자 각자 로그인 | `legal-and-compliance` "Can customers offer Claude Code in their products?"가 조건부 허용(바이너리 미변경·내장 인증 유지·대납/중개 금지·사용자 각자 자격증명)이고 "unmodified Claude Code binary with their own Claude subscription"을 명시(검토 문서 2.1, 2026-09-07 확인) | 2026-09-08 초안(로드맵 `S6`, `claude -p` 자식 프로세스)의 근거였으나 사용자가 거부해 같은 날 폐기. Knot은 CLI 바이너리를 실행하지 않으므로 이 조항은 적용 대상이 아니다. `claude -p` 실측 표는 §6.8에서 제거했다(2026-09-09, git 이력에만 남는다) |
| Console API 키(서버 보관) | 허용 경로 | 백엔드 `LlmClient`에 Anthropic 어댑터 추가(B안)는 웹 채팅 UI의 서버 경로(로드맵 Q22)에 쓰인다. Workspace BYO 키(C안)는 후속 |
| 사용자가 공식 CLI에 제3자 MCP 서버·스킬을 등록 | 세 CLI(Claude Code·Codex CLI·Gemini CLI) 모두 문서화된 정식 기능(§6.8, 2026-09-09 확인). `legal-and-compliance`에 MCP 서버 연결을 제한하는 조항 없음(같은 날 재확인) | **탐색의 데스크톱 경로**(2026-09-09, 로드맵 `S8`·불변 계약 2·3번, 검토 문서 7절 F안). 데스크톱 앱이 `127.0.0.1`에 MCP 서버를 띄우고 사용자가 자기 CLI에 등록한다. Knot은 LLM을 호출하지 않고 자격증명·바이너리를 만지지 않는다. 해석 위험은 로드맵 R28 |
| 원격 MCP 서버(서버 호스팅) | 사용자가 자기 Claude 앱/Claude Code에서 서버를 연결하는 구조라 제품이 구독을 제공하는 것이 아님 | 서버 호스팅 변형은 개발자용 부가 기능 후보(로드맵 `A13`)로만 기록. 로컬 변형이 위 행 |

### 6.2 현재 모델·가격 (Anthropic 1st-party API 기준, 캐시 2026-06-24)

| 모델 | ID | 컨텍스트 | 입력 $/1M | 출력 $/1M | 비고 |
| --- | --- | --- | --- | --- | --- |
| Claude Opus 5 | `claude-opus-5` | 1M | 5.00 | 25.00 | 기본 권장. thinking 기본 on(adaptive) |
| Claude Sonnet 5 | `claude-sonnet-5` | 1M | 2.00 | 10.00 | 비용 절감 후보 |
| Claude Haiku 4.5 | `claude-haiku-4-5` | 200K | 1.00 | 5.00 | 분류·경량 작업 |
| Claude Opus 4.8 | `claude-opus-4-8` | 1M | 5.00 | 25.00 | Opus 5 폴백 후보 |
| Claude Fable 5.1 | `claude-fable-5-1` | 1M | 10.00 | 50.00 | 최상위. 30일 데이터 보존 필수, 강제 tool_choice 불가 |

- 모델 ID는 표의 문자열 그대로 사용하고 날짜 접미사를 붙이지 않는다.
- 캐시 읽기는 입력의 약 0.1배, 캐시 쓰기는 약 1.25배 비용. Batch API는 50% 할인(채팅에는 부적합).
- `GET /v1/models/{id}`로 `max_input_tokens`, `max_tokens`, `capabilities`를 런타임 조회할 수 있다.

### 6.3 요청 파라미터 (Opus 5 기준)

| 파라미터 | 값 | 주의 |
| --- | --- | --- |
| `thinking` | `{type: "adaptive"}` 또는 생략 | `budget_tokens`는 400 오류. `display: "summarized"`를 줘야 사고 요약이 보임(기본 `omitted`) |
| `output_config.effort` | `low`/`medium`/`high`/`xhigh`/`max` | 기본 `high`. 채팅 QA는 `low`~`medium`부터 측정 |
| `max_tokens` | 스트리밍 시 넉넉히(예 16,000) | 답변 길이 정책은 서버 프롬프트로 제어 |
| `system` | 문자열 또는 `TextBlockParam[]` | Knot `SearchContext.groundingPrompt`가 1:1 대응 |
| `messages` | 히스토리 + 사용자 턴 | 첫 메시지는 `user`. assistant prefill은 400 오류 |
| `cache_control` | `{type: "ephemeral"}` (최대 4개 브레이크포인트) | 근거 규칙(고정)은 캐시, 근거 문서(가변)는 브레이크포인트 뒤 |
| `temperature`/`top_p`/`top_k` | Opus 5에서 제거(400) | 기존 OpenAI 호환 설정 키 그대로 넘기지 않는다 |
| `stop_reason` | `end_turn`/`max_tokens`/`tool_use`/`refusal`/`pause_turn` | `refusal`은 HTTP 200. `stop_details.category` 확인 후 사용자에게 안내 |

### 6.4 스트리밍 이벤트 (SSE)

| 이벤트 | 의미 | Knot SSE 대응 |
| --- | --- | --- |
| `message_start` | usage 초기값 | 무시 또는 계측 |
| `content_block_start` | 블록 시작(`text`/`thinking`/`tool_use`) | `text`만 사용자에게 |
| `content_block_delta` | `text_delta`(본문), `thinking_delta`, `input_json_delta`(툴 인자) | `text_delta.text` → 기존 `chunk` 이벤트 |
| `content_block_stop` | 블록 종료 | — |
| `message_delta` | `stop_reason`, 누적 `usage.output_tokens` | `complete` 직전 계측 저장 |
| `message_stop` | 종료 | 기존 `complete(messageId)` 발행 |
| `error` | 스트림 중 오류(overloaded 등) | 기존 `error` 이벤트 |

Java SDK는 `client.messages().createStreaming(params)`가 `StreamResponse<RawMessageStreamEvent>`를 돌려주며, `event.contentBlockDelta()` → `delta().text()`로 `text_delta`만 추출한다. Knot의 pull형 `LlmStream` 인터페이스로 감싸면 `ChatStreamService`·SSE 계약은 바뀌지 않는다(검토 문서 7절 B안).

### 6.5 Tool use (서버 측 재검색 루프 옵션)

- 툴 정의: `name`, `description`, `input_schema`(JSON Schema). `strict: true`를 툴 정의 최상위에 두면 `additionalProperties: false` + `required` 스키마에 정확히 맞는 입력이 보장된다.
- 루프: 응답 `stop_reason == "tool_use"`이면 `tool_use` 블록의 `id`·`input`으로 서버가 `PublishedDocumentSearchService`를 실행하고, 같은 수의 `tool_result`를 **한 개의 user 메시지**에 담아 재요청한다. `end_turn`까지 반복하되 최대 반복 횟수(예 2회)를 서버가 강제한다.
- 병렬 호출: 한 assistant 메시지에 `tool_use`가 여러 개 올 수 있다. 전부 실행해 한 메시지로 돌려준다. 실패한 툴은 `is_error: true`로 돌려준다.
- 툴 입력은 항상 JSON 파서로 읽는다(문자열 매칭 금지).
- 검토 문서 5.6절: 툴 루프는 모델 왕복을 2회로 늘리므로 TTFT 5초 목표 대비 실측이 필요하다. 기본 경로는 현행 "사전 주입"을 유지하고 재검색 루프는 플래그로 켠다.

### 6.6 Java SDK

| 항목 | 값 |
| --- | --- |
| 좌표 | `com.anthropic:anthropic-java:2.34.0` (스킬 캐시 기준, 구현 시 최신 확인) |
| 클라이언트 | `AnthropicOkHttpClient.fromEnv()` 또는 `.builder().apiKey(...).build()` |
| 요청 | `com.anthropic.models.messages.MessageCreateParams.builder().model("claude-opus-5").maxTokens(...).systemOfTextBlockParams(...).addUserMessage(...)` |
| 스트리밍 | `client.messages().createStreaming(params)` → `StreamResponse<RawMessageStreamEvent>` (try-with-resources) |
| 사고·effort | `.thinking(ThinkingConfigAdaptive.builder().build())`, `.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())` |
| 캐시 | `TextBlockParam.builder().text(...).cacheControl(CacheControlEphemeral.builder().build())` |
| 오류 | `com.anthropic.errors.AnthropicServiceException`(`errorType()`), `RateLimitException`, `NotFoundException` 등. 재시도는 SDK 기본 `maxRetries=2` |
| 타임아웃 | Java `Duration`, 스트리밍 요청은 SDK가 자동 확장 |
| 미도입 대안 | 현재 백엔드는 JDK `HttpClient`만 사용. SDK 없이 `POST https://api.anthropic.com/v1/messages`(헤더 `x-api-key`, `anthropic-version: 2023-06-01`, `stream: true`)를 직접 호출하는 것도 가능. 도입 여부는 구현 Issue에서 결정(검토 문서 7절) |

### 6.7 비용 추정 공식 (설계 판단용)

1회 질문 = 입력(근거 규칙 약 1K + 근거 문서 최대 12,000자≈6~8K 토큰(정정 2026-09-07 `S1`: 10,000자 → 12,000자) + 히스토리) + 출력(답변 300~800 토큰).
- Opus 5, 캐시 없음, 입력 8K·출력 600: 8,000×5/1M + 600×25/1M ≈ $0.055/질문.
- 근거 규칙만 캐시(약 1K)하면 절감은 미미하고, 히스토리 캐시는 세션 단위 prefix가 안정적일 때만 효과가 있다.
- Sonnet 5로 바꾸면 같은 조건에서 ≈ $0.022/질문. 품질 재검증(gold set 30문항) 없이 모델을 바꾸지 않는다(검토 문서 5.6절).

### 6.8 CLI 코딩 에이전트의 MCP 서버·스킬 등록 방법 (2026-09-09 공식 문서 확인, 데스크톱 `S8`·`S9` 재료)

2026-09-08의 `claude -p` 실측 표(데스크톱 `S6` 재료)는 사용자가 서브프로세스 방식을 거부해 `S6`이 폐기되면서 지웠다(git 이력에만 남는다). 아래는 세 CLI가 **사용자 설정으로** 제3자 MCP 서버와 스킬을 붙이는 공식 방법이다. 설계 판단은 로드맵 Q47~Q51, 미확인은 §8 U28~U31. 전부 [문서] 확인이며 실측은 아직 없다.

**MCP 서버 등록(Streamable HTTP)** [문서]

| CLI | 등록 명령·설정 | 헤더 | 설정 파일·스코프 | 근거 |
| --- | --- | --- | --- | --- |
| Claude Code | `claude mcp add --transport http <name> <url>` (`-t`, stdio는 `claude mcp add [옵션] <name> -- <command> [args]`, `--env KEY=value`) | `--header "Authorization: Bearer <토큰>"` (`-H`) | `--scope local`(기본, `~/.claude.json`) · `project`(`.mcp.json`, 버전 관리 공유) · `user`(`~/.claude.json`, 전 프로젝트). 도구 이름은 `mcp__<server>__<tool>`. 상태는 `claude mcp list`(`✔ Connected`/`✘ Failed to connect`)·`/mcp`. 문서의 HTTP 예시는 전부 원격 URL이고 localhost 예시·금지 문구 둘 다 없다(U28) | https://code.claude.com/docs/en/mcp |
| Codex CLI | `~/.codex/config.toml`의 `[mcp_servers.<name>]`에 `url = "…"`(Streamable HTTP). 문서 원문 예시: `[mcp_servers.figma]` `url = "https://mcp.figma.com/mcp"` `bearer_token_env_var = "FIGMA_OAUTH_TOKEN"` `http_headers = { "X-Figma-Region" = "us-east-1" }`. CLI 명령은 stdio 형식(`codex mcp add context7 -- npx -y @upstash/context7-mcp`)만 문서에 있고 **HTTP 서버용 `codex mcp add --url` 형식은 문서에 없다**(원문 재확인 2026-09-09, U31) — HTTP 등록은 `config.toml` 편집 또는 ChatGPT·IDE 확장의 GUI | `bearer_token_env_var`(토큰을 담은 **env 변수 이름**), `http_headers = { … }`(고정 헤더), `env_http_headers`(env에서 읽는 헤더). Knot 스니펫은 `http_headers`를 쓴다(로드맵 Q50) | `~/.codex/config.toml`(사용자), `.codex/config.toml`(프로젝트). `startup_timeout_sec`·`tool_timeout_sec`·`enabled`. 목록은 `codex mcp list`, OAuth는 `codex mcp login` | https://learn.chatgpt.com/docs/extend/mcp?surface=cli (developers.openai.com/codex/mcp에서 308) |
| Gemini CLI | `gemini mcp add [옵션] <name> <commandOrUrl> [args…]`, `-t, --transport stdio\|sse\|http`(기본 stdio) | `-H, --header "Authorization: Bearer <토큰>"` | `-s, --scope user\|project`(기본 project). `~/.gemini/settings.json`·`.gemini/settings.json`의 `mcpServers.<name>` — HTTP는 `"httpUrl": "http://localhost:3000/mcp"` + `"headers": {…}`(문서 예시가 localhost), stdio는 `command`·`args`·`env`·`cwd`, SSE는 `url` | https://geminicli.com/docs/tools/mcp-server/ |

**스킬(Agent Skills 표준)** [문서]

| CLI | 위치(사용자 → 프로젝트) | 프런트매터 | 호출 | 근거 |
| --- | --- | --- | --- | --- |
| Claude Code | `~/.claude/skills/<name>/SKILL.md`, `.claude/skills/<name>/SKILL.md`, 플러그인 `<plugin>/skills/`. `~/.agents/skills`는 문서에 없다 | "Claude Code skills follow the Agent Skills open standard". 표준 필드 `name`·`description`·`license`·`compatibility`·`metadata`·`allowed-tools`, 나머지(`disable-model-invocation`·`context`·`hooks` 등)는 Claude Code 확장. 전부 선택이며 `description` 권장 | `description`(+`when_to_use`, 합쳐 1,536자까지)이 항상 컨텍스트에 있어 모델이 자동 호출, `/<name>`으로 수동 호출 | https://code.claude.com/docs/en/skills |
| Codex CLI | `$CWD/.agents/skills` → 상위 폴더 → `$REPO_ROOT/.agents/skills` → `$HOME/.agents/skills` → `/etc/codex/skills` → 내장 | "build on the open agent skills standard". `name`·`description` 필수, 추가 메타데이터는 `agents/openai.yaml` | `description` 일치 시 자동(`allow_implicit_invocation: false`로 끔), `/skills` 또는 `$<name>` 명시 | https://learn.chatgpt.com/docs/build-skills |
| Gemini CLI | 사용자 `~/.gemini/skills/` 또는 `~/.agents/skills/` 별칭, 워크스페이스 `.gemini/skills/` 또는 `.agents/skills/`(같은 계층에서는 `.agents/skills/` 우선), 확장·내장 | "Based on the Agent Skills open standard". 읽는 필드 목록은 문서에 없다(`SKILL.md` 본문·폴더 구조가 대화에 주입된다고만 적혀 있다) | 모델이 `activate_skill` 도구를 부르고 사용자 확인 프롬프트 뒤 주입 | https://geminicli.com/docs/cli/skills/ |

Codex CLI·Gemini CLI가 모두 `~/.agents/skills/`를 읽으므로 로드맵 Q50은 두 CLI의 설치 위치를 그 경로 하나로 잡았다. 표준 자체는 https://agentskills.io .

**실측(2026-09-09, 데스크톱 `S8` 구현)** [실측]

- `@modelcontextprotocol/sdk` 1.30.0(npm latest): `LATEST_PROTOCOL_VERSION = "2025-11-25"`, `DEFAULT_NEGOTIATED_PROTOCOL_VERSION = "2025-03-26"`. 로드맵 Q47이 적었던 "2026-07-28 무세션 개정판"은 SDK에 없다(Q47 정정). 서버 전송은 `server/streamableHttp.js`의 `StreamableHTTPServerTransport`(Node `IncomingMessage`/`ServerResponse`, 내부는 `@hono/node-server` 2.1.1로 Web 표준 전송을 감싼다)이며 `sessionIdGenerator: undefined`가 무상태 모드다. `allowedHosts`·`allowedOrigins`·`enableDnsRebindingProtection` 옵션은 deprecated이고 Origin은 "있고 목록 밖일 때만" 거부라 Q48의 "Origin 있으면 403"은 직접 검사해야 한다(`desktop/src/mcp/guard.ts`). 의존성에 `express`·`hono`·`cors`·`ajv`·`zod`가 있어 esbuild 번들이 1.3MB다. 무상태 GET(서버 발신 SSE)은 405.
- Electron 44.2.0 `utilityProcess`: `process.parentPort`는 `electron.d.ts`가 `Electron.ParentPort`로 선언하며 `/// <reference types="electron" />`로 타입을 끌어온다. `MessageEvent`는 `{data, ports: MessagePortMain[]}`이고 `MessagePortMain`은 `on("message")`·`postMessage`·`start()`. `fork(..., {serviceName, stdio: "pipe"})`의 stdout/stderr를 main 로그로 넘길 수 있다. 앱 시작 0.5초 뒤 HTTP 서버가 `127.0.0.1:47871`에 LISTEN(IPv4만), `MessagePort` 왕복 + 도구 실행 지연 3ms, SIGTERM 3초 뒤 포트 해제(로드맵 U29 해소).
- Claude Code 2.1.263: `claude mcp add --transport http knot http://127.0.0.1:47871/mcp --header "Authorization: Bearer <토큰>"`는 local 스코프로 `~/.claude.json`의 프로젝트 항목에 저장되며 출력에서 헤더 값을 `[REDACTED]`로 가린다. `claude mcp list`는 `knot: http://127.0.0.1:47871/mcp (HTTP) - ✔ Connected`. initialize `protocolVersion`은 2025-11-25, `Authorization` 그대로 전달, `Origin` 헤더 없음. `claude -p`에서 `--allowedTools mcp__knot__list_workspaces`로 도구 호출이 서버 → main → 결과까지 왕복했다(로드맵 U28 부분 해소). `claude -p` 뒤에 `--allowedTools`를 두면 그 뒤 프롬프트 인자를 목록으로 먹으므로 프롬프트는 stdin으로 준다. Codex CLI·Gemini CLI는 이 PC에 없어 미측정(U31).

**정책 문구** [문서, 2026-09-09 재확인]: `legal-and-compliance`(https://code.claude.com/docs/en/legal-and-compliance)에 MCP 서버 연결을 제한하는 조항은 없다. 금지는 claude.ai 로그인 제공·구독 자격증명으로 대리 요청·자격증명/세션 토큰 수집·저장·중개·바이너리 변경·대납/재판매뿐이며, F안(검토 문서 7절)은 이 중 어느 것도 하지 않는다.

## 7. 참고 링크 총목록

**Knot 저장소**: `docs/llm-electron-subscription-architecture-review.md`, `docs/llm-search-feature-spec.md`, `docs/llm-java-integration.md`, `docs/adr/{212,232,254,271,314,328}-*.md`, `docs/adr/README.md`, `docs/harness/issue-planning.md`, `.agents/skills/knot-issue-planning/references/risk-policy.md`, `.github/knot-conventions.yml`, `CONTRIBUTING.md`, `frontend/webpack.config.js`, `frontend/wrangler.jsonc`, `frontend/src/shared/api/httpClient/index.ts`, `backend/src/main/java/com/knot/backend/global/config/SecurityConfig.java`, `backend/src/main/resources/application.properties`

**CLI 코딩 에이전트(MCP·스킬, 2026-09-09)**: https://code.claude.com/docs/en/mcp , https://code.claude.com/docs/en/skills , https://learn.chatgpt.com/docs/extend/mcp?surface=cli , https://learn.chatgpt.com/docs/build-skills , https://geminicli.com/docs/tools/mcp-server/ , https://geminicli.com/docs/cli/skills/ , https://agentskills.io , https://modelcontextprotocol.io/specification/2025-06-18/basic/transports

**Anthropic**: https://code.claude.com/docs/en/agent-sdk (정책 조항), https://code.claude.com/docs/en/legal-and-compliance , https://code.claude.com/docs/en/authentication , https://code.claude.com/docs/en/mcp , https://platform.claude.com/docs/en/about-claude/models/overview.md , https://platform.claude.com/docs/en/pricing.md , https://platform.claude.com/docs/en/build-with-claude/streaming.md , https://platform.claude.com/docs/en/agents-and-tools/tool-use/overview.md , https://platform.claude.com/docs/en/build-with-claude/prompt-caching.md , https://platform.claude.com/docs/en/agents-and-tools/remote-mcp-servers , https://support.claude.com/en/articles/11175166

**Electron 공식**: https://releases.electronjs.org/ (+ /schedule), https://www.electronjs.org/docs/latest/tutorial/{security,fuses,asar-integrity,process-model,ipc,sandbox,esm,message-ports,installation,breaking-changes,electron-timelines,launch-app-from-url-in-another-app,notifications,code-signing,updates,performance,automated-testing,devtools-extension,application-debugging,forge-overview,why-electron,custom-title-bar} , https://www.electronjs.org/docs/latest/api/{session,cookies,web-request,net,client-request,protocol,app,shell,safe-storage,notification,tray,menu,global-shortcut,native-theme,power-monitor,clipboard,dialog,crash-reporter,auto-updater,context-bridge,ipc-main,utility-process,command-line-switches,environment-variables} , https://www.electronjs.org/apps

**Electron Forge / electron-builder / 도구**: https://www.electronforge.io/ (cli, config/plugins/{webpack,vite,fuses}, config/makers/*, config/publishers/github, guides/code-signing/*, advanced/auto-update, import-existing-project), https://www.electron.build/ (docs/mac, docs/win, docs/features/code-signing/*, docs/features/auto-update, docs/features/github-actions, docs/tutorials/adding-electron-fuses), https://github.com/electron/{forge,packager,asar,rebuild,universal,get,osx-sign,notarize,windows-sign,fuses,update-electron-app,update.electronjs.org,windows-installer,mksnapshot} , https://electron-vite.org/

**Apple / Microsoft / GitHub Actions**: https://developer.apple.com/programs/ , https://developer.apple.com/documentation/security/notarizing-macos-software-before-distribution , https://developer.apple.com/documentation/security/hardened-runtime , https://developer.apple.com/news/?id=saqachfa , https://learn.microsoft.com/en-us/azure/artifact-signing/{overview,faq,quickstart} , https://azure.microsoft.com/en-us/pricing/details/artifact-signing/ , https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/{code-signing-options,smartscreen-reputation} , https://docs.github.com/en/actions/use-cases-and-examples/deploying/installing-an-apple-certificate-on-macos-runners-for-xcode-development , https://docs.github.com/en/billing/managing-billing-for-your-products/about-billing-for-github-actions , https://github.com/actions/runner-images

**표준·인증**: https://www.rfc-editor.org/rfc/rfc8252 , https://datatracker.ietf.org/doc/html/rfc7636 , https://www.rfc-editor.org/rfc/rfc9700 , https://www.ietf.org/archive/id/draft-ietf-oauth-v2-1-16.txt , https://www.rfc-editor.org/rfc/rfc8628 , https://datatracker.ietf.org/doc/html/rfc7009 , https://www.rfc-editor.org/rfc/rfc6749 , https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/{authorizing-oauth-apps,creating-an-oauth-app,best-practices-for-creating-an-oauth-app} , https://github.blog/changelog/2026-08-14-multiple-redirect-uris-and-token-refresh-for-oauth-apps/ , https://github.blog/changelog/2025-07-14-pkce-support-for-oauth-and-github-app-authentication/ , https://developers.google.com/identity/protocols/oauth2/native-app , https://learn.microsoft.com/en-us/entra/identity-platform/reply-url

**Spring**: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/{jwt,bearer-tokens}.html , https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorization-grants.html , https://docs.spring.io/spring-security/reference/features/exploits/csrf.html , https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html , https://docs.spring.io/spring-boot/system-requirements.html , https://docs.spring.io/spring-ai/reference/api/mcp/{mcp-server-boot-starter-docs,mcp-security}.html

**쿠키·웹 표준**: https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie , https://developer.mozilla.org/en-US/docs/Glossary/Site , https://web.dev/articles/samesite-cookies-explained , https://web.dev/articles/schemeful-samesite , https://privacysandbox.google.com/blog/privacy-sandbox-next-steps

**대안·사례·MCP**: https://tauri.app/{start,concept/architecture,reference/webview-versions,security/capabilities,plugin/updater,distribute/sign/macos,develop/tests/webdriver}/ , https://web.dev/articles/install-criteria , https://developer.chrome.com/docs/capabilities , https://slack.engineering/building-hybrid-applications-with-electron/ , https://www.figma.com/blog/introducing-browserview-for-electron/ , https://linear.app/changelog/2019-04-25-linear-desktop-app , https://modelcontextprotocol.io/specification/versioning , https://modelcontextprotocol.io/specification/2026-07-28/basic/{transports,authorization} , https://modelcontextprotocol.io/docs/develop/connect-remote-servers , https://github.com/modelcontextprotocol/java-sdk , https://cursor.com/docs/mcp

**테스트·관측**: https://playwright.dev/docs/api/class-electron , https://vitest.dev/api/vi.html , https://docs.sentry.io/platforms/javascript/guides/electron/ , https://github.com/megahertz/electron-log , https://mswjs.io/docs/recipes/merging-service-workers/

## 8. 결정에 영향을 주는 미확인 항목 (통합)

| # | 항목 | 영향 받는 결정 | 해소 방법 |
| --- | --- | --- | --- |
| U1 | GitHub이 Electron 창(embedded user-agent) 로그인을 경고·차단하는지 | D4 1단계 | P0 스파이크 실측 |
| U2 | Notion OAuth 302 체인의 실제 도메인 | 네비게이션 허용 목록 | P0 실측 → 2026-09-08 부분 실측: `api.notion.com/v1/oauth/authorize` → 302 `app.notion.com/install-integration`(§1.1). 동의 이후 홉은 미측정 → 2026-09-08 23:21 동의 화면 앞 4홉(`app.notion.com` sessionSync 왕복) 통과, 로그인 IdP 팝업은 U27 → 2026-09-08 23:41 종단 통과로 해소 |
| U3 | 운영 API 오리진(`API_BASE_URL_PROD`) | prod 빌드 설정 | 팀 확인 |
| U4 | `app://`에서 `https://api.*`로의 credentialed fetch에 Lax 쿠키가 붙는지(표준상 차단) | D2(로컬 번들 재검토 시) | 프로토타입 |
| U5 | Electron `cookies.get`의 HttpOnly·`sameSite=strict` 쿠키 반환(이슈 #22345) | main 쿠키 접근(사용 안 함) | 필요 시 실측 |
| U6 | `secure: true` 커스텀 스킴의 secure context 판정 | 로컬 번들 | Chromium 소스 |
| U7 | GitHub 콜백에 커스텀 스킴 등록 가능 여부·PKCE 시 `client_secret` 생략 | 직접 GitHub 연동 시(현 설계는 백엔드 브로커라 무관) | — |
| U8 | Azure Artifact Signing 한국 조직 자격(문서 불일치), 개인 개발자 불가 | Windows 서명 | Azure 포털 신청 |
| U9 | Forge `maker-squirrel` macOS 호스트 빌드 | CI 매트릭스 | Windows 러너에서 빌드하면 무관 |
| U10 | `@electron/notarize` 자동 staple | 릴리스 절차 | `xcrun stapler validate`로 검증 |
| U11 | `update-electron-app`의 draft/prerelease 처리 | 릴리스 절차 | README 재확인 |
| U12 | Forge 8·electron-builder 27·Playwright 1.63 정식 시점 | 착수 시 버전 | 착수 시 재조회 |
| U13 | MCP Java SDK·Spring AI의 2026-07-28 스펙 대응 | P3 MCP 서버 | SDK 릴리스 노트 |
| U14 | Spring `CsrfFilter.DEFAULT_CSRF_MATCHER`·`AndRequestMatcher`·`NegatedRequestMatcher` 7.1.1 시그니처 | 2단계 CSRF 면제 | 구현 시 Javadoc |
| U15 | Cloudflare Workers 정적 자산의 `_headers` CSP 지원과 Emotion 인라인 스타일 충돌 | 웹 CSP Issue | 실험 |
| U25 | Gemini `batchEmbedContents` 요청당 최대 건수와 입력 2,048토큰 초과 시 동작 | `B5` 배치 크기(로드맵 Q37) | 부분 해소(2026-09-08): 1,300자 텍스트 32건 이상이면 429 `RESOURCE_EXHAUSTED`, 8·16건은 200 → 배치 16. 토큰 초과 동작은 미확인 |
| U26 | `gemini-embedding-001`이 REST 최상위 `outputDimensionality`·`taskType`으로 1,024차원 응답을 주는가(레퍼런스 deprecated 표시) | `B5` 차원 계약(로드맵 Q35) | 해소(2026-09-08): 최상위 필드로 1,024차원 응답, norm 0.6165(미정규화) |
| U24 | Electron main의 전역 `fetch`(undici)가 SSE 응답 본문을 끊김 없이 스트리밍하고 `AbortController`로 취소되는가 | 무효(2026-09-09 — `S3` 폐기, 데스크톱은 LLM을 호출하지 않는다) | 부분 해소 기록만 남긴다(2026-09-08 vitest Node 22.21 실측, `desktop/test/openAiCompatibleClient.test.ts` — `S8`에서 함께 제거). 서버 API 호출의 취소·30초 제한은 `knotApi.ts`가 같은 API로 처리하며 `S8`이 재사용 |
| U28 | 세 CLI(Claude Code·Codex CLI·Gemini CLI)가 `http://127.0.0.1:<port>/mcp`의 Streamable HTTP 서버에 실제로 붙는가 — 어느 스펙 개정판(2026-07-28 무세션 vs 2025-06-18 `initialize`·`Mcp-Session-Id`)으로 오는지, `Authorization` 헤더를 그대로 보내는지, `Origin` 헤더를 안 보내는지 | `S8` 전송·인증(로드맵 Q47·Q48) | 부분 해소(2026-09-09 실측, §6.8): Claude Code 2.1.263이 등록 뒤 ✔ Connected, initialize 2025-11-25, `Authorization` 전달, `Origin` 없음, `claude -p` 도구 호출 왕복. Codex CLI·Gemini CLI는 미설치라 미측정(U31). Gemini CLI 문서 예시는 `http://localhost:3000/mcp`다 |
| U29 | Electron 44 `utilityProcess`에서 `http.createServer`가 `127.0.0.1`에 바인딩되고 `MessagePort` 왕복이 도구 호출 지연(목표 50ms 미만, 서버 API 시간 제외)을 만족하는가, 앱 종료·크래시 시 포트가 풀리는가 | `S8`(로드맵 Q47) | 해소(2026-09-09 실측, §6.8): `127.0.0.1:47871`만 LISTEN, 왕복 3ms, SIGTERM 뒤 포트 해제. 크래시 시 해제는 미측정 |
| U30 | MCP 서버 `instructions`(2026-07-28 개정판에서는 `server/discover` 응답의 해당 필드)가 세 CLI에서 모델에 노출되는가 | `S9`(로드맵 Q50) | 미확인. Claude Code MCP 문서에 언급이 없다. 노출되지 않으면 스킬 파일만이 안내 경로다 |
| U31 | Codex CLI의 HTTP MCP 서버 등록 — `config.toml` `url`·`http_headers` 편집이 문서대로 동작하는가, `codex mcp add`에 HTTP용 옵션(`--url` 등)이 실제로 있는가(문서에는 stdio 형식만 있다) — 와 Gemini CLI `gemini mcp add --transport http`가 문서대로 동작하는가, `~/.agents/skills/knot/`을 두 CLI가 자동 발견하는가 | `S9`(로드맵 Q50) | 미확인(문서 확인만, §6.8). Codex 스니펫은 CLI 명령이 아니라 `config.toml` `http_headers` 편집으로 둔다. 설치본이 없어 미측정 |
| U27 | Notion 로그인 IdP 팝업이 자식 창에서 끝까지 끝나는가(IdP embedded UA 거부 여부, `*popupcallback` 뒤 opener 통지·창 닫힘) | 네비게이션 허용 목록·새 창 정책(로드맵 Q46) | 부분 실측(2026-09-08): 팝업 URL·검증 페이지 동작·IdP 3종 302 목적지는 §1.1. → 해소(2026-09-08 23:41): Microsoft 경로가 자식 창에서 끝까지 지나갔다(§1.1). Google·Apple은 미실측 |
