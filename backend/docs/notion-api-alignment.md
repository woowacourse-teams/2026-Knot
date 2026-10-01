# 운영 Notion API 후보와 백엔드 정합성

확인일: 2026-10-01
상태: 검토 메모. API 초안 승인이나 구현 완료 기록이 아니다.

이 문서는 운영 Team Notion에서 감지된 API 변경 후보를 백엔드의 현재 계약과 구분한다.
제품 범위와 충돌 처리에는 [현재 V2 MVP 기준](../../docs/product/current-v2-mvp.md)과
[Notion 정합성 워크플로](../../docs/harness/notion-alignment.md)를 함께 적용한다.

## 근거와 확인 범위

- 2026-10-01 11:37 KST에 다시 확인했다. `knot_notion_sources`는 741행,
  `knot_notion_pending`은 37행이었다. `Knot Notion Change Queue`의 예약 실행은 9/30
  16:23:56·16:25:17에 성공했고 16:14:32에 한 번 실패했다. 이 수치와 실행은 관측 시점 기준이다.
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
- Refresh source row 739와 pending candidate의 `snapshot_text`·`candidate_text`는 9/30
  16:16 KST 캡처에서 같은 204·수명·회전 규칙을 담는다. 반면 이번에 직접 연 Notion
  `/api/v1/auth/refresh` 화면은 Response 예제 본문이 로드되지 않고 처리 규칙도 비어 있어
  더 짧게 보였다. 캡처와 현재 화면을 같은 승인 상태로 취급하지 말고 원문을 다시 맞춘다.
- 저장소의 GitHub Issue #7, #9, #10, #11은 OAuth 계정 식별과 기존 로그인 구현을 다루며
  모두 종료됐다. 저장소 Issue 검색에서는 인증 refresh 작업을 찾지 못했다. Issue #10은
  OAuth 사용자 계정 식별·회원가입 구현으로 닫혔고 refresh lifecycle 승인을 뜻하지 않는다.

## 2026-09-30 변경 후보

| 주제 | 변경 후보의 내용 | 구현 계약으로 쓰기 전 확인할 점 |
| --- | --- | --- |
| 녹음 종료 | `/recordings/{recordingId}/end`는 세션을 `ENDED`로 전이하고 업로드 확인이나 STT 접수는 수행하지 않음 | 인증·시작자 권한, 상태 전이, 중복 호출, 동시 종료 결과 |
| 최종 오디오 | upload URL 발급 뒤 클라이언트가 최종 파일을 PUT하고, 별도 `/audio-upload-complete`가 저장 객체의 존재·크기·Content-Type을 확인한 뒤 STT를 접수 | object storage 제공자, 크기·시간 제한, 소유권 검증, 완료 통지 멱등성, 실패 시 재시도 |
| 처리 상태 | `sessionStatus`, `audioUploadStatus`, `transcriptionStatus`, `documentGenerationStatus`, `documentGenerationJobId`, `failureStage`를 분리 | 허용값·상태 전이·Job 식별 기준·공개 가능한 안정 오류 코드 |
| 전사 구간 | 원문 구간을 `startMillis`, `endMillis`, `speakerNumber`, `text` 배열로 제공하고 데이터가 없으면 `segments: []` | STT provider가 보장하는 timestamp·speaker, 저장 형식, 기존 `Transcript.content TEXT`와의 관계 |
| 탐색 명칭 | 사용자-facing 용어와 API 영역에서 `exploration` 대신 `search` 사용 | 현재 공개 경로 및 FE 호출 계약과의 호환성 |
| 인증·로그인 | OAuth callback, 닉네임 설정, refresh, logout 변경 후보가 대기 중이며 cookie·수명·가입 흐름을 함께 다룸 | 각 후보 상태와 현재 코드의 부분 구현은 아래 인증 섹션 참고 |

후보 세부 필드의 예시와 상태는 확인 편의를 위한 것이다. 이 메모의 표만으로 request/response
schema, DB 상태, 비동기 실행 정책을 확정하거나 코드를 변경하지 않는다.

### 인증·로그인: 2026-09-30 후보

#### 도메인 기준

- GitHub OAuth로 가입·로그인하고 GitHub 계정 하나는 Member 하나와 연결한다.
- 로그인 뒤 닉네임 입력을 완료해야 가입이 끝난다. 닉네임은 20자 이하이며 한글·영문·`(`,
  `)`, `-`만 허용하고 공백은 금지한다. 서버도 같은 기준으로 검증한다.
- GitHub 프로필 이미지를 기본값으로 사용하고 Member는 여러 Workspace에 참여할 수 있다.
- 도메인 규칙 페이지에는 탈퇴 계정의 재로그인 제한이 없다. `/oauth2/authorization/github`
  pending candidate는 탈퇴 처리된 Member를 다시 로그인시키지 않는다고 제안하지만, 이를 현재
  도메인 합의로 승격하지 않는다.
- 현재 열린 `아직 정하지 않은 것` 페이지는 OAuth 세션·토큰 갱신·만료, 가입 중단 후 재진입을
  미정으로 둔다. 앞서 제공된 발췌와 닉네임 중복·변경 정책의 포함 여부가 다르므로 출처를
  맞추기 전까지 미정으로 유지한다.

#### 로그인 API candidate가 제안하는 흐름

아래는 `knot_notion_pending` 캡처의 후보 내용이다. 네 endpoint 모두 대기 중이며 승인된 API
spec으로 간주하지 않는다. API root의 공통 문구는 access 인증에
`__Host-KNOT_ACCESS_TOKEN`을 쓰고 변경 요청에 `X-XSRF-TOKEN`을 요구하며 오류 응답을
`code`, `message`, `fieldErrors`로 구성한다.

| 후보 endpoint | 캡처에서 제안한 내용 |
| --- | --- |
| `GET /oauth2/authorization/github` | 인증 헤더 없이 브라우저 이동, GitHub `302`와 `state` 검증. 기존 활성 Member에는 access·refresh cookie 발급. 신규 사용자는 10분 `KNOT_NICKNAME_TOKEN`만 받고 닉네임 입력으로 이동. GitHub access token은 browser storage에 노출하지 않음. 탈퇴 Member 재로그인 제한도 이 후보에만 있음 |
| `POST /api/v1/auth/nickname` | JSON `{ "nickname": "octocat" }`, 온보딩 cookie와 `X-XSRF-TOKEN` 필요. 공백 입력·20자 초과, invalid body, 만료/부정확 token, 중복 완료 오류를 제안. 성공은 204로 access·refresh cookie를 발급하고 온보딩 cookie를 만료. 후보 유효성 설명은 공백·길이만 적어 도메인의 허용 문자 규칙보다 좁음 |
| `POST /api/v1/auth/refresh` | body·path·query 없음, `__Host-KNOT_REFRESH_TOKEN`과 `X-XSRF-TOKEN` 필요. 204와 새 access·refresh cookie. access 1시간, refresh는 마지막 발급/갱신 뒤 미사용 7일 또는 최초 OAuth 로그인 뒤 30일 중 먼저 도달한 시각에 만료 |
| `POST /api/v1/auth/logout` | 선택 access·refresh cookie와 필수 `X-XSRF-TOKEN`, 204. access·refresh·온보딩 cookie를 만료하고 유효 refresh가 있으면 현재 로그인 family만 폐기. 다른 기기는 유지. Stateless access JWT는 폐기되지 않아 최대 1시간 더 유효할 수 있음. 반복 호출도 204 제안 |

공통 cookie 후보는 access·refresh에 `Secure`, `HttpOnly`, `Path=/`, `SameSite=Lax`, `Domain`
생략을 사용한다. Refresh 성공마다 token을 교체하고 이전 token 재사용 시 해당 family를
폐기한다. 오류 후보는 refresh의 `401 UNAUTHENTICATED`/`403 CSRF_INVALID`; logout의 CSRF
실패 `403`이다. Candidate text는 저장·만료 정리 방식과 동시 refresh 충돌을 정의하지 않는다.

Notion API root의 refresh row는 `구현=NO`로 표시한다. `/nickname`, OAuth callback, logout
candidate의 `NO`/`YES` 값은 Notion metadata일 뿐 실제 code state를 확정하지 않는다.

#### 현재 저장소 구현과 Issue 상태

- [`AuthController`](../src/main/java/com/knot/backend/auth/presentation/AuthController.java)는
  `/me`, `/csrf`, `/nickname`만 제공하며 `POST /refresh` mapping은 없다.
- [`JwtProperties`](../src/main/resources/application.properties)는 access token 만료를
  `PT1H`, nickname token을 `PT10M`로 설정한다. 현재 cookie manager는 access·nickname cookie만 발급한다.
- [`SecurityConfig`](../src/main/java/com/knot/backend/global/config/SecurityConfig.java)는
  `POST /api/v1/auth/logout`을 설정한다.
  [`JwtLogoutHandler`](../src/main/java/com/knot/backend/auth/presentation/handler/JwtLogoutHandler.java)는
  access·nickname cookie를 만료시킬 뿐 서버측 refresh family 저장·폐기는 하지 않는다.
- 현재 OAuth callback은 신규/기존 회원에 따라 nickname token 또는 access token을 발급한다.
  닉네임 설정 성공 시 `Member`와 `OAuthIdentity`는 저장하지만 refresh cookie는 발급하지 않는다.
- 닉네임의 도메인 기준은 허용 문자·공백 금지·20자 최대다. 현재 `CompleteNicknameRequest`와
  `AuthenticatedMember`는 빈 값·공백만 입력한 값·20자 초과를 막지만, 내부 공백과 허용 문자
  목록은 이 validation 경로에서 검사하지 않는다.
- 따라서 refresh 발급·저장·회전, replay 탐지, family 폐기는 현재 코드에서 확인되지 않는다.
  구현 완료 상태로 기록하지 않는다.
- Issue #7/#9/#10/#11은 기존 OAuth 로그인·계정 식별·인증 기반 작업으로 종료됐으며,
  refresh candidate를 승인·추적하는 열린 Issue는 검색에서 확인되지 않았다. 신규 Issue는
  이 요청 범위에서 만들지 않는다.

저장·동시성 선택과 남은 질문은 [Persona draft](../.persona/decisions/draft/refresh-token-session-lifecycle.md)에
제안 상태로 둔다. 도메인 정책으로 승격하려면 팀 확인이 필요하다.

## 남은 제품·기술 충돌

- 최신 도메인 규칙은 Document의 `DRAFT`, `DRAFTING`, `ARCHIVED` 상태가 없다고 기록하지만,
  기존 API 상세 초안과 2026-09-22 ERD에는 DRAFT 계열 상태가 남아 있다.
- 도메인 규칙의 생성 실패 자동 재시도와 ERD의 OWNER 수동 재시도 설명이 다르다.
- API 후보는 녹음 후 최종 오디오 파일 한 개를 올리는 흐름이고, ERD에는 chunk upload 구조가
  남아 있다.
- Workspace에서 OWNER가 나갈 때의 권한 이전·삭제 및 마지막 멤버 이탈 soft-delete 규칙을
  기존 API와 ERD에 반영할지 정합해야 한다.
- 동시 녹음 범위, 생성 시점의 Document 확인 대상, 원문 segment 저장 형식도 서로 연관된
  제품 결정으로 남아 있다.

각 충돌은 현재 문서 사이의 불일치다. 최신 날짜, `최종`이라는 제목, API 후보의 시연 우선
표시만으로 대체 관계를 추정하지 않는다. 어느 문서가 팀의 현재 합의인지 확인한 뒤 제품 기준,
ERD, API 계약과 구현을 같은 방향으로 갱신한다.

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
