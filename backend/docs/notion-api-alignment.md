# 운영 Notion API 후보와 백엔드 정합성

현재 작업의 관련 원문·DB 속성은 [Notion 근거 탐색 기준](notion-context.md)에 따라 찾는다.
아래 내용은 2026-10-01의 과거 검토 메모다. 현재 구현 상태를 이 메모에서 추정하지 않는다.

## 현재 동기화 방식 · 2026-10-08

1. 운영 n8n 수집 성공과 본문·DB 속성의 revision을 확인하고 ignored 원문 캐시를 갱신한다.
2. 변경된 API와 관련 도메인·기획만 찾아 실제 내용을 읽는다. 내부 메타데이터는 제외하고
   메서드·경로·권한·요청/응답·오류·상태 전이 등 관련 규칙과 DB 속성 값을 추출한다.
3. 최신 API 우선 규칙과 기존 결정을 적용한 뒤 Controller·DTO·Security·Migration·Issue·ADR와 대조한다.
4. 검토한 현재 규칙·출처·미확정 사항을 해당 주제 문서나 `docs/implement-plan/`의 관련 계획에 반영한다.
   `implementation-plan-coach`도 같은 [탐색 기준](notion-context.md)을 따른다.
5. queue는 [정합성 워크플로](../../docs/harness/notion-alignment.md#queue-완료-조건)의 비교·반영 조건을
   만족한 뒤 처리한다. 캐시 수집이나 DB의 구현 표시만으로 완료 처리하지 않는다.

## 2026-10-09 문서 확인 API 재수집 대조

02:42:53 KST 시작한 n8n 실행 `561506`이 성공했다. 문서 관련 API 7개의 본문
revision은 `2026-10-08T17:06:00Z`인데 같은 실행 Search 출력의 속성 revision은
9/30 또는 10/6~10/7로 남아 있다. `revision_mismatch`이므로 최신 정합 완료로
판정하지 않고 후보를 유지한다. 기존 캐시 이력은 보존했다.

5개 API는 이전 본문과 같고 캡처 시각·수집 상태만 달라졌다. 실제 본문 차이는
[확인 현황 조회](https://www.notion.so/3ebb43517522802da344e8f8eedd6849)와
[내 확인 처리](https://www.notion.so/3ebb4351752280fd8377e25d15fa3380)의 표 행이
추가 수집된 것이다. 이를 새 제품 결정으로 취급하지 않는다.

- 조회 표의 대상·집계·nullable 필드와 cursor/size는 기존
  [#497](https://github.com/woowacourse-teams/2026-Knot/issues/497) 계약과 같다.
  원문에서 기본 50·최대 100과 정렬은 여전히 제안이다. 현재 구현 계약은 Issue와
  `DocumentConfirmationService`·`DocumentController`에 따른다.
- 확인 표의 비대상 `409 CONFIRMATION_NOT_REQUIRED`, 최초 시각 유지, 확인·집계·보관의
  트랜잭션은 [#498](https://github.com/woowacourse-teams/2026-Knot/issues/498) 및
  `DocumentConfirmationCommandService`의 현재 코드와 대조했다.
- 두 원문의 “API/controller 없음” 문장은 현재 코드와 다르다. GET/PUT Controller와
  서비스가 존재하고 두 Issue는 조회 시 CLOSED였다. 배포·테스트 통과까지 확인한 것은 아니다.
- 목록·상세의 내 확인 3상태는 [ADR #496](../../docs/adr/496-document-my-confirmation-state.md)의
  기존 결정에 따른다. 확인 현황 API의 Boolean과 구분하며 이번 수집으로 변경하지 않는다.

본문과 속성 revision이 일치하는 수집 근거를 확보한 뒤 queue 완료 여부를 다시 판정해야 한다.
이번 대조에서는 원문·기능 코드·진행 중인 #524 계획과 후보 상태를 변경하지 않았다.

아래 과거 관측은 이력으로 보존한다. 현재 API와 구현 상태를 재검토한 결과가 자동으로 갱신된 것은 아니다.

## 2026-10-01 검토 이력

확인일: 2026-10-01
상태: 검토 메모. API 초안 승인이나 구현 완료 기록이 아니다.

이 문서는 운영 Team Notion에서 감지된 API 변경 후보를 백엔드의 현재 계약과 구분한다.
제품 범위와 충돌 처리에는 [현재 V2 MVP 기준](../../docs/product/current-v2-mvp.md)과
[Notion 정합성 워크플로](../../docs/harness/notion-alignment.md)를 함께 적용한다.

## 근거와 확인 범위

- 2026-10-01 14:32 KST n8n Data Table UI에서 741개 source와 37개 pending row를 읽었다.
  마지막 capture는 9/30 16:16 KST이고 Data Table의 마지막 `updatedAt`은 9/30 16:25:18 KST다.
  `Knot Notion Change Queue` 편집기에 `Publish`가 보여 수집 workflow는 게시되지 않은 상태였고,
  새 capture는 확인되지 않았다. 별도 `Knot Notion Queue Completion`은 게시된 5분 예약 workflow다.
  이 대조 전 queue 후보는 모두 `pending`이었다. 10/1 15:00 KST 재확인에서는 전체 37개 중
  14개가 `processed`, 23개가 `pending`, `verified`는 0개였다. 인증 API 네 후보는 아직 pending이며,
  최신 전체 상태와 row별 사유는 하네스 checkpoint에서 확인한다.
- 10/1 15:24 KST에 미게시 capture workflow를 수동 실행했지만, `Get many child blocks`가 429
  rate limit 오류로 실패했다. 두 `Upsert row(s)` node에는 도달하지 않아 Data Table은 갱신되지
  않았다. 따라서 9/30 16:16 KST capture가 여전히 최신 snapshot이다.
- 2026-09-28 API 상세 snapshot에는 operation 41개가 있었고, Notion의 구현 표시는
  `YES` 10개, `NO` 31개였다. 이는 당시 Notion 값이며 현재 코드 검증 결과가 아니다.
- 백엔드 Java 소스의 Controller mapping을 검색했을 때 `auth`, `workspace`, `invitation`
  영역은 확인했지만 Recording 및 Document controller mapping은 찾지 못했다. 실제 노출
  여부를 확정할 때는 코드뿐 아니라 실행 환경의 OpenAPI도 다시 확인한다.
- 이전 OpenAPI 관측은 개발 서버 기준 2026-09-28, 26 operations다. 이 시점 뒤의 runtime
  계약은 아직 이 문서에서 확인하지 않았다.
- `(신)도메인`의 `도메인 규칙 및 UseCase` page ID는
  `3e3b4351-7522-80cd-9f06-edc2ccdff1d8`, source row 161,
  `last_edited_time=2026-09-30T06:53:00Z`, `snapshot_hash=eb25bb84`,
  `captured_at=2026-09-30T07:16:02.692Z`다. 큰 snapshot은 Data Table 셀 대신 로그인된
  Notion 페이지를 열어 Member·로그인 규칙을 읽었다. 연결된 `로그인 · 온보딩 (v2)`는
  `개발 중`이며 닉네임 입력 중 검증·오류 문구·퍼널 배치 등 화면 규칙을 담는다.
- 10/1 14:21 KST 직접 연 도메인 본문은 GitHub OAuth, Member 1:1, nickname 입력 후 가입 완료,
  nickname 형식, 기본 GitHub 이미지와 여러 Workspace 참여를 적는다. Access·refresh lifetime은
  도메인 본문에 없으며 같은 도메인의 `아직 정하지 않은 것`은 OAuth session/token 갱신·만료와
  가입 중단 후 재진입을 미정으로 적는다. 9/30 변경 로그에는 로그인 규칙 수정 항목이 없다.
  10/1 열린 #427–#430은 사용자가 확인한 구현 방향을 나눠 기록하지만 Notion 페이지 간 표기와
  팀 승인은 별개다.
- 같은 도메인의 `아직 정하지 않은 것` child page는 n8n row 191, ID
  `3e3b4351-7522-8051-bc18-c34052dbbd4f`, `last_edited_time=2026-09-23T05:26:00Z`,
  `snapshot_hash=84999f04`다. 현재 열린 페이지에는 OAuth 세션·토큰 갱신·만료와 가입 중단
  후 재진입이 미정으로 적혀 있다. 이 스레드에서 앞서 제공한 발췌에는 중복 요청 멱등성과
  닉네임 중복·변경 정책도 미정으로 있었으나, 현재 열린 페이지에서는 확인되지 않아 차이로
  남긴다.
- 로그인 관련 pending API 행은 OAuth callback (`4bfb4351-7522-822e-a25c-81a034b85b8d`,
  `changed`, source edit `05:18Z`, hash `ce85e1bc`), 닉네임 설정
  (`3ebb4351-7522-802f-b8a1-e43c6234d470`, `new`, `05:41Z`, `c520072e`), logout
  (`482b4351-7522-835e-a4fc-81e24c8fd2a9`, `changed`, `05:15Z`, `9701d3bf`), refresh
  (`3ebb4351-7522-80a5-ba6a-f3743703998b`, `new`, `04:58Z`, `4d806547`)다. 네 행 모두
  `detected_at=2026-09-30T07:16:02.692Z`, 현재 `status=pending`, `claim_id`와
  `lease_until`은 비어 있다. API root와 로그인 database도 별도 `changed` 후보로 남아 있다.
- Refresh source row 739와 pending candidate는 9/30 16:16 KST capture다. 10/1 15:22 KST에
  직접 확인한 live `/api/v1/auth/refresh` page는 204, access 1시간, idle 7일·absolute 30일,
  회전, 재사용과 logout 때 현재 family 폐기를 적는다. 삭제·정지 시 모든 family 폐기와 token
  기록 보존·정리 규칙도 추가돼 있다. 9/30 candidate와 비교하면 candidate에는 `403 CSRF_INVALID`
  설명과 access·refresh cookie 예시가 있지만 live page의 403 행은 비어 있고 refresh cookie 예시만
  보인다. 반대로 삭제·정지 폐기와 정리 규칙은 candidate에 없다. API page의 구현 속성은 `NO`다.
  #430은 핵심 7일·30일·회전·family 재사용 범위를 적지만 open이며 checklist가 미완료다. 이
  원문·snapshot 차이와 도메인 미정 page를 정리하기 전까지 candidate는 `pending`으로 둔다.
- GitHub #427–#430은 모두 10/1 관측 시점에 열려 있다. #427은 기존 Member OAuth login과
  신규 nickname token 발급, #428은 현재 session logout, #429는 nickname 검증·가입 완료,
  #430은 refresh endpoint의 만료·회전·reuse 처리를 기록한다. #427과 #430은 사용자 확인을
  본문에 명시하지만 Issue open 상태이므로 팀 리뷰 승인이나 구현 완료는 아니다.

## 2026-09-30 변경 후보

| 주제 | 변경 후보의 내용 | 구현 계약으로 쓰기 전 확인할 점 |
| --- | --- | --- |
| 녹음 종료 | `/recordings/{recordingId}/end`는 세션을 `ENDED`로 전이하고 업로드 확인이나 STT 접수는 수행하지 않음 | 인증·시작자 권한, 상태 전이, 중복 호출, 동시 종료 결과 |
| 최종 오디오 | upload URL 발급 뒤 클라이언트가 최종 파일을 PUT하고, 별도 `/audio-upload-complete`가 저장 객체의 존재·크기·Content-Type을 확인한 뒤 STT를 접수 | object storage 제공자, 크기·시간 제한, 소유권 검증, 완료 통지 멱등성, 실패 시 재시도 |
| 처리 상태 | `sessionStatus`, `audioUploadStatus`, `transcriptionStatus`, `documentGenerationStatus`, `documentGenerationJobId`, `failureStage`를 분리 | 허용값·상태 전이·Job 식별 기준·공개 가능한 안정 오류 코드 |
| 전사 구간 | 원문 구간을 `startMillis`, `endMillis`, `speakerNumber`, `text` 배열로 제공하고 데이터가 없으면 `segments: []` | STT provider가 보장하는 timestamp·speaker, 저장 형식, 기존 `Transcript.content TEXT`와의 관계 |
| 탐색 명칭 | 사용자-facing 용어와 API 영역에서 `exploration` 대신 `search` 사용 | 현재 공개 경로 및 FE 호출 계약과의 호환성 |
| 인증·로그인 | OAuth callback, 닉네임 설정, refresh, logout 변경 후보가 대기 중이며 cookie·수명·가입 흐름을 함께 다룸 | 각 후보 상태와 현재 코드의 부분 구현은 아래 인증 섹션 참고 |

### API root 피드백과 endpoint 제안 요약

9/30 `/ (신)API 명세서` candidate에는 API 공통 인증·오류 규칙과 아래 피드백 반영 메모가 있다.
이는 개인 명세의 제안 상태이며 endpoint 구현 또는 FE 합의를 뜻하지 않는다.

| Endpoint 또는 주제 | candidate에 추가·정리된 내용 | 저장소 대조 상태 |
| --- | --- | --- |
| `GET /workspaces/{workspaceId}/documents` | `myConfirmationState`를 `PENDING`, `CONFIRMED`, `NOT_REQUIRED`로 구분하고 `myConfirmation` query로 필터링. 주제별 전체 개수와 cursor 기반 문서 목록을 제공하며 전체 비페이지 조회는 추가하지 않는 제안 | 문서 confirmation 범위와 페이지 크기 근거는 제품·FE 검토 후보. 구현 완료로 보지 않음 |
| `GET /recordings/current`, `GET /recordings/{recordingId}` | session, audio upload, transcription, document generation 상태를 분리하고 `documentGenerationJobId`, `failureStage`, `failureReason` 제안 | heartbeat/종료·업로드 API와 상태 전이 연동을 검토 대상으로 기록. 코드 구현 아님 |
| `GET /recordings/{recordingId}/transcript`, `GET /documents/{documentId}/transcript` | `transcriptId`, 전체 원문과 공통 `segments` 구조, `startMillis`, `endMillis`, `speakerNumber`, `text`를 제안 | 현 STT 저장 포맷과 성공 시 실제 timestamp 보장은 미정 |
| `GET /document-generation-jobs` | `QUEUED`, `RUNNING`, `FAILED` 작업을 cursor 기반으로 복원·조회하는 제안 | 목록·재시도와 녹음 응답의 job ID 연결은 구현 전 확인 |
| 탐색 이름 | 사용자-facing 용어 및 API 영역에서 `exploration` 대신 `search`로 통일하는 팀 메모 | 기존 endpoint와 계약 전환 범위는 검색 도메인에서 별도 확인; `frontend/`는 이번 작업에서 수정하지 않음 |

9/30 API root의 feedback note는 확인 상태, 홈의 pending 전용 필터, 로그인 연장, 문서 job ID,
failure reason 코드, 전사 segment, 문서 목록 페이지 크기, JSON 예시를 다룬다. 후보 endpoint 일부는
이 피드백을 반영한 상세를 담지만, `처리 완료` 문구나 candidate 변경 상태만으로 팀 승인이나 코드
구현을 추론하지 않는다. 별도 API 페이지가 제목만 수집된 경우에는 본문이 비어 있는지 실제로
수집되지 않은 것인지 확인될 때까지 기능 계약을 보완하지 않는다.

후보 세부 필드의 예시와 상태는 확인 편의를 위한 것이다. 이 메모의 표만으로 request/response
schema, DB 상태, 비동기 실행 정책을 확정하거나 코드를 변경하지 않는다.

### 인증·로그인: 2026-09-30 후보와 10/1 Issue 대조

#### 도메인 기준

- GitHub OAuth로 가입·로그인하고 GitHub 계정 하나는 Member 하나와 연결한다.
- 로그인 뒤 닉네임 입력을 완료해야 가입이 끝난다. 닉네임은 20자 이하이며 한글·영문·`(`,
  `)`, `-`만 허용하고 공백은 금지한다. 서버도 같은 기준으로 검증한다.
- GitHub 프로필 이미지를 기본값으로 사용하고 Member는 여러 Workspace에 참여할 수 있다.
- 도메인 규칙 페이지에는 탈퇴 계정의 재로그인 제한이 없다. `/oauth2/authorization/github`
  pending candidate는 탈퇴 처리된 Member를 다시 로그인시키지 않는다고 제안하지만, 이를 현재
  도메인 합의로 승격하지 않는다.
- 현재 열린 `아직 정하지 않은 것` 페이지는 OAuth 세션·토큰 갱신·만료, 가입 중단 후 재진입을
  미정으로 둔다. 앞서 제공된 발췌와 닉네임 중복·변경 정책의 포함 여부가 다르므로 그 두
  미정 항목은 source discrepancy로 남긴다. 사용자는 10/1 Issue #427–#430 본문에서 login,
  logout, nickname completion, refresh 방향을 확인했다. 이는 현재 task의 사용자 지시로 반영하되,
  Notion 문서 간 충돌이 정합됐거나 팀 리뷰가 끝났다고 표현하지 않는다.

#### 로그인 API candidate가 제안하는 흐름

아래는 `knot_notion_pending` 캡처에 기록된 후보 내용이다. 대조 시작 시 네 endpoint가 모두
`pending`이었고, 아래 Issue는 구체적인 작업 계약을 기록하지만 승인된 API spec으로 단정하지 않는다.
API root의 공통 문구는 access 인증에
`__Host-KNOT_ACCESS_TOKEN`을 쓰고 변경 요청에 `X-XSRF-TOKEN`을 요구하며 오류 응답을
`code`, `message`, `fieldErrors`로 구성한다.

| 후보 endpoint | 캡처에서 제안한 내용 |
| --- | --- |
| `GET /oauth2/authorization/github` | 인증 헤더 없이 브라우저 이동, GitHub `302`와 `state` 검증. 기존 활성 Member에는 access·refresh cookie 발급. 신규 사용자는 10분 `KNOT_NICKNAME_TOKEN`만 받고 닉네임 입력으로 이동. GitHub access token은 browser storage에 노출하지 않음. 탈퇴 Member 재로그인 제한도 이 후보에만 있음 |
| `POST /api/v1/auth/nickname` | JSON `{ "nickname": "octocat" }`, 온보딩 cookie와 `X-XSRF-TOKEN` 필요. 공백 입력·20자 초과, invalid body, 만료/부정확 token, 중복 완료 오류를 제안. 성공은 204로 access·refresh cookie를 발급하고 온보딩 cookie를 만료. 후보 유효성 설명은 공백·길이만 적어 도메인의 허용 문자 규칙보다 좁음 |
| `POST /api/v1/auth/refresh` | body·path·query 없음, `__Host-KNOT_REFRESH_TOKEN`과 `X-XSRF-TOKEN` 필요. 204와 새 access·refresh cookie. access 1시간, refresh는 마지막 발급/갱신 뒤 미사용 7일 또는 최초 OAuth 로그인 뒤 30일 중 먼저 도달한 시각에 만료 |
| `POST /api/v1/auth/logout` | 선택 access·refresh cookie와 필수 `X-XSRF-TOKEN`, 204. access·refresh·온보딩 cookie를 만료하고 유효 refresh가 있으면 현재 로그인 family만 폐기. 다른 기기는 유지. Stateless access JWT는 폐기되지 않아 최대 1시간 더 유효할 수 있음. 반복 호출도 204 제안 |

공통 API candidate는 access·refresh cookie의 `Secure`, `Path=/`, `Domain` 생략을 적고,
OAuth·nickname 후보는 HttpOnly도 적는다. Refresh API 본문은 요청마다 refresh를 교체하고,
동시 요청은 하나만 성공하며 이미 사용된 token 재제출은 해당 family 재사용으로 처리한다고
설명한다. 재사용 기록은 family의 절대 만료까지 보관하고 만료 후 7일 뒤 정리할 수 있다고
한다. 오류 후보는 refresh의 `401 UNAUTHENTICATED`/`403 CSRF_INVALID`; logout의 CSRF 실패
`403`이다. `SameSite=Lax` 등 개별 cookie 속성은 해당 endpoint 원문에 명시됐는지 확인해
사용하며 다른 후보 페이지에서 가져와 일괄 규칙으로 만들지 않는다.

Notion API root의 refresh row는 `구현=NO`로 표시한다. `/nickname`, OAuth callback, logout
candidate의 `NO`/`YES` 값은 Notion metadata일 뿐 실제 code state를 확정하지 않는다.

#### 현재 checkout 구현과 Issue 상태

- 현재 worktree의 [`AuthController`](../src/main/java/com/knot/backend/auth/presentation/AuthController.java)는
  `/me`, `/csrf`, `/nickname`을 제공하며 `POST /refresh` mapping은 없다.
- 기존 활성 Member OAuth login은 `AuthService`에서 access·refresh token을 만들고 refresh hash와
  수명을 갖는 `AuthSession`을 저장한다. OAuth success handler가 refresh와 access cookie를 발급한다.
  migration `V15__create_auth_sessions.sql`이 이 저장 구조를 만든다.
- [`JwtProperties`](../src/main/resources/application.properties)는 access token 만료를
  `PT1H`, nickname token을 `PT10M`로 설정한다. `AuthCookieManager`에는 refresh cookie 발급이 있지만,
  닉네임 가입 완료 경로에서는 아직 access cookie만 발급한다.
- [`SecurityConfig`](../src/main/java/com/knot/backend/global/config/SecurityConfig.java)는
  `POST /api/v1/auth/logout`을 설정한다.
  [`JwtLogoutHandler`](../src/main/java/com/knot/backend/auth/presentation/handler/JwtLogoutHandler.java)는
  access·nickname cookie를 만료시킬 뿐 서버측 refresh family 저장·폐기는 하지 않는다.
- 신규 OAuth 사용자에게는 nickname token만 발급한다. Nickname completion은 `Member`와
  `OAuthIdentity`를 저장하고 access token을 발급하지만 현재 worktree에서는 refresh session을
  만들거나 refresh cookie를 발급하지 않는다.
- Nickname validation은 현재 `CompleteNicknameRequest`의 `@NotBlank`/`@Size(max=20)` 및
  `AuthenticatedMember`의 blank/length 검사다. 내부 공백과 허용 문자는 아직 검사하지 않아
  #429의 사용자가 확인한 규칙보다 구현 범위가 좁다.
- `AuthSession` 생성·저장과 기존 Member login의 refresh 발급은 보이지만 refresh endpoint의
  hash 조회·회전, replay 처리, session revoke는 아직 없다. `JwtLogoutHandler`도 access와
  nickname cookie만 만료한다. 따라서 session lifecycle의 일부 기반만 관찰됐으며 Issue 완료나
  refresh/logout 구현 완료로 기록하지 않는다.
- #427–#430은 열려 있다. #427은 existing login에서 access 1시간, refresh idle 7일·absolute
  30일과 신규 사용자의 10분 nickname token을 정한다. #428은 현재 family만 폐기하는 logout,
  #429는 domain nickname 문자 규칙과 가입 완료 시 session/cookie 발급,
  #430은 refresh rotation/reuse 처리의 별도 구현 범위를 둔다. OAuth candidate의 탈퇴 Member
  재로그인 제한은 이 Issue 묶음에서 확인되지 않아 미정으로 남긴다.

저장·동시성 선택과 남은 질문은 [Persona draft](../.persona/decisions/draft/refresh-token-session-lifecycle.md)에
제안 상태로 둔다. 도메인 정책으로 승격하려면 팀 확인이 필요하다.

## 남은 제품·기술 충돌

- 최신 도메인 규칙은 Document의 `DRAFT`, `DRAFTING`, `ARCHIVED` 상태가 없다고 기록하지만,
  기존 API 상세 초안과 2026-09-22 ERD에는 DRAFT 계열 상태가 남아 있다.
- 도메인 규칙의 생성 실패 자동 재시도와 ERD의 OWNER 수동 재시도 설명이 다르다.
- API 후보는 녹음 후 최종 오디오 파일 한 개를 올리는 흐름이고, ERD에는 chunk upload 구조가
  남아 있다.
- Workspace 생성 상한은 별도 `[BE] ... 생성 3개 제한` Issue #396이 2026-09-30
  `not planned`로 닫히며 무제한 생성으로 정리됐다. 9/30 Notion의 생성·참여 후보와 일치한다.
  Workspace 삭제·탈퇴의 현재 담당자 계약은 open Issue #410과 #386에 있으며, 9/30 Notion/API
  capture 및 이전 API·ERD에는 완전히 반영되지 않았다.
- 동시 녹음 범위, 생성 시점의 Document 확인 대상, 원문 segment 저장 형식도 서로 연관된
  제품 결정으로 남아 있다.

각 충돌은 현재 문서 사이의 불일치다. 최신 날짜, `최종`이라는 제목, API 후보의 시연 우선
표시만으로 대체 관계를 추정하지 않는다. 어느 문서가 팀의 현재 합의인지 확인한 뒤 제품 기준,
ERD, API 계약과 구현을 같은 방향으로 갱신한다.

### 2026-10-01 GitHub Issue 대조

- [#396](https://github.com/woowacourse-teams/2026-Knot/issues/396)은 `not planned`로
  닫혔다. 종료 의견은 직접 생성 상한을 없애고 기존 무제한 생성 동작을 유지한다고 기록한다.
- [#410](https://github.com/woowacourse-teams/2026-Knot/issues/410)은 open 상태다. OWNER의
  Workspace soft-delete와 진행·일시정지 녹음 폐기, 이미 저장된 오디오·전사·문서 보존을 다룬다.
- [#386](https://github.com/woowacourse-teams/2026-Knot/issues/386)은 open 상태다. 10/1
  담당자 결정은 일반 탈퇴·OWNER 승계 후 탈퇴 시 본인의 진행·일시정지 녹음만 폐기하고 다른
  멤버 세션과 저장된 자료는 보존한다고 적는다. 상태 전달 경로와 연결 복구는 이 Issue의 작업 범위다.
- [#384](https://github.com/woowacourse-teams/2026-Knot/issues/384)와
  [#385](https://github.com/woowacourse-teams/2026-Knot/issues/385)은 open 상태로 30초
  heartbeat, 2분 무응답 만료, 일시정지를 제외한 누적 2시간 상한을 다룬다. 후보와 Issue는
  기획·구현 대조 대상이며 완료된 구현은 아니다.
- 9/30 `/api/v1/workspaces/{workspaceId}/recordings/{recordingId}/events` 후보의 SSE는
  승인 여부가 확인되지 않았다. #386은 HTTP 경로와 상태 전달 계약을 정하라고만 한다.
  다른 미확정 API는 사용자가 제품 결정을 내리기 전까지 제안 상태로 둔다.

## 구현 및 문서 갱신 규칙

1. 구현 전에 운영 Notion의 페이지 분류, 원문 상태, 변경 시각과 pending revision을 다시
   확인한다. 오래된 `자바의 Notion` 복사본은 운영 근거로 쓰지 않는다.
2. 해당 범위의 Controller, request/response DTO, validation, Security, persistence,
   migration, 테스트와 개발 OpenAPI를 조사해 현재 동작을 기록한다.
3. 초안이 승인되기 전에는 API spec, Persona Harness backlog와 Issue를 계약 변경으로
   갱신하거나 endpoint 구현을 시작하지 않는다. 독립적으로 이미 확정된 작업만 진행한다.
4. 사용자 판단이 필요한 충돌은 Notion 문장, 저장소 현재 내용, 구현 상태, 영향과 선택지를
   한 묶음으로 제시한다. 팀 승인이 필요한 결정은 사용자 개인 판단과 구분한다.
5. 합의가 확인되면 제품 기준, API 명세, 도메인 규칙·ERD, backend harness와 연결된 Issue를
   함께 갱신한다. 기존 기록은 삭제하지 않고 저장소의 `history` 규칙에 따라 보존한다.

`frontend/` 변경은 별도의 범위와 요청이 있기 전까지 수행하지 않는다.
