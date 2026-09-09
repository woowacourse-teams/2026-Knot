# Knot 데스크톱 앱 개발 로드맵·TODO (실행 정본)

- 문서 상태: Active — 이 문서는 데스크톱 앱 작업의 **실행 정본(SSOT)**이다.
- 기준일: 2026-09-06
- 기준 커밋: `develop` `b1d4801` (`[BE] 채팅 답변 출처 조회 API 구현 (#351)`)
- 마지막 갱신: 2026-09-09 (**트랙 S `S10`(데스크톱 `show_answer` 도구) 구현·검증 완료로 `검증중`** — 사용자 지시(`남은 구현 해`). `S2` 턴 저장 API는 다른 세션이 같은 시각 구현 중이라 계약(기획서 6.4 `201 {userMessageId, messageId}`)만 가정. `S8` 미측정 중 패키징(arm64 asar) 실측 해소·로그인 셸 `list_workspaces` 종단 통과. SPA의 `onDeepLink` 구독자 부재는 U32. 앞선 갱신: **트랙 S `S8`(데스크톱 로컬 MCP 서버) 구현·검증 완료로 `검증중`** — 사용자 지시(`앱이 내부 클로드 토큰을 사용하도록 모든 구현을 다 해`, "내부 토큰"은 앱이 발급하는 연결 토큰 Q48로 해석). `utilityProcess` + `@modelcontextprotocol/sdk` 1.30.0 Streamable HTTP 서버, 연결 토큰·Origin/Host 검사, 도구 2개, `MessagePort` 위임, preload `agent` API, `S3` 코드 제거, 메뉴 임시 진입점, SKILL.md. vitest 98건, 이 PC의 Claude Code 2.1.263 등록·도구 호출 왕복 실측. Q47 정정(SDK 최신 프로토콜 2025-11-25·무상태), U28 부분 해소·U29 해소, R25 완화, GS 체크박스 3개 완료. 같은 날 다른 세션이 `S7`(backend)·`S9`(frontend)를 분담. 앞선 갱신: **탐색의 답변 생성을 사용자의 CLI 코딩 에이전트 + 데스크톱 로컬 MCP 서버 방식으로 확정** — 사용자 지시(2026-09-08 밤). 불변 계약 2·3번 개정, 트랙 S 재편(`S3`·`S4` 폐기, 서브프로세스 초안 `S6` 폐기, `S2`·`S5` 재정의, `S7`~`S10` 신설), Q22 정정·Q47~Q51 추가, Q26·Q27·Q43~Q45 무효, U23·U24 무효·U28~U31 추가, R17~R19 정정·R25~R28 추가. 2026-09-08 앞선 갱신: Notion OAuth 체인 종단 통과 실측 23:41(새 창 정책 개정 빌드로 재로그인해 IdP 팝업(Microsoft)·동의·콜백·`?result=connected` 복귀가 앱 창 안에서 차단 0건으로 끝남. G1 두 번째 체크박스 완료, U2·U27·R2 해소) 및 Notion 연결 "팝업 차단" 정정 — Notion 로그인 화면이 IdP 인증을 `window.open` 팝업으로 여는데 셸이 새 창을 전부 거부해 로그인이 불가했다. 새 창 정책을 허용 목록 한정 자식 창으로 개정(Q46), IdP 오리진 추가, U2 재측정·U27·R2·R16·R24 갱신. 같은 날 앞선 갱신: 트랙 S `S3` 착수(이후 폐기), Gemini 임베딩 배치 64 → 16·색인 429 재시도 신설(Q37·Q38 정정, Q42·R22 추가, U25 부분 해소, U26 해소))
- 현재 단계: **M0 `A1` 검증중** (아래 3절 G0 통과). 다음 게이트는 G1. 트랙 B는 `B1` 검증중(커밋·PR 승인 대기), `B5` 검증중(사용자 지시 2026-09-08 `임베딩 모델을 gemini 임베딩 1로 가자`, 구현·테스트 완료, 커밋·PR 승인 대기). 트랙 S는 `S1` 검증중(사용자 지시 2026-09-07 `로드맵 4.5절 s1구현해`, 구현·테스트 완료, 커밋·PR 승인 대기). `S3`은 구현·vitest까지 끝났으나 2026-09-08 사용자 지시로 `폐기`(데스크톱이 LLM을 호출하지 않는 방식으로 바뀜 — 코드 제거는 `S8` 범위). `S8`(데스크톱 로컬 MCP 서버)·`S7`(서버 Workspace 검색 API)은 2026-09-09 사용자 지시로 착수해 구현·검증이 끝나 `검증중(2026-09-09)`이다(커밋·PR 승인 대기). `S9`(스킬·연결 안내 화면)는 같은 날 다른 세션이 착수했다.

## 0. 이 문서의 지위

### 0.1 무엇의 정본인가

| 이 문서가 정본인 것 | 이 문서가 정본이 아닌 것 (정본 위치) |
| --- | --- |
| 작업 목록·ID·순서·의존 관계 | 사실(코드·외부 문서 값) → [지식 문서](./electron-desktop-app-knowledge.md), [패키징 부록](./electron-desktop-app-packaging-research.md) |
| 각 작업의 현재 상태와 완료 판정 | 설계·대안·근거 → [기술 기획서](./electron-desktop-app-tech-plan.md) |
| 게이트(다음 단계로 넘어갈 조건) | 적용 가능성 판정 → [검토 문서](./llm-electron-subscription-architecture-review.md) |
| 미결 결정의 소유자·차단 대상 | 확정된 결정 → `docs/adr/*.md` |
| 착수 전 반드시 지켜야 할 불변 계약 목록 | Issue 본문(`구현 기능 설명`·`TODO`·`메모`) → GitHub Issue |

**충돌 시 우선순위**: 확정 ADR(`Accepted`) > 이 문서(실행 상태·순서) > 기술 기획서(설계 초안) > 나머지 조사 문서.
설계 내용이 ADR로 확정되면 기획서를 갱신하고, 이 문서는 링크만 유지한다. **설계 본문을 이 문서로 복사하지 않는다.** 복사는 SSOT를 깨뜨린다.

### 0.2 문서 우선 원칙 (가장 중요)

**코드보다 문서가 먼저다. 코드와 문서가 어긋나면 문서가 맞다.**

- 코드를 먼저 고치지 않는다. 계획·계약·설계가 바뀌면 이 문서와 기획서를 먼저 고치고, 코드를 그 문서에 맞춘다.
- 구현 중 문서와 다른 사실을 발견하면 **문서를 먼저 정정하고 그대로 계속 진행한다.** 코드를 근거로 문서를 되돌리지 않는다. 정정 사실은 작업이 끝난 뒤 함께 보고하며, 정정 때문에 구현을 멈추거나 답을 기다리지 않는다.
- 문서가 실제와 다르다고 판단되면 코드를 따라가지 말고 어긋난 지점·영향·정정안을 문서에 반영한다. 관찰로 확인한 사실은 근거(경로:라인 또는 URL)와 함께 바로 정정하고, 사람만 답할 수 있는 판단은 5절 기본값으로 진행한다.
- 지식 문서처럼 관찰한 사실을 적은 문서는 **실제 값으로 정정하는 것**이 "문서를 먼저 고치는 것"이다. 정정 날짜와 근거(경로:라인 또는 URL)를 함께 남긴다.
- 문서에 없는 것을 구현해야 하면 **4절에 작업 행을 먼저 추가하고 이어서 구현한다.** 행 추가와 5절 기본값 추가는 사용자 승인 대상이 아니다.

### 0.3 갱신 규칙

- 상태 변경은 **그 변경을 만든 PR에서 같이 커밋**한다. 별도 "문서 갱신 PR"을 만들지 않는다.
- 작업 ID(`A1`, `B1` …)는 **영구 불변**이다. 폐기해도 행을 지우지 않고 상태를 `폐기`로 바꾸고 사유를 남긴다.
- Issue 번호·ADR 파일명은 실제로 생성된 뒤에만 채운다. `{ISSUE_NUMBER}` 같은 표시는 이 문서에 쓰지 않는다.
- 새 작업을 추가할 때는 반드시 선행·완료 판정·위험 신호를 함께 적는다. 셋 중 하나라도 못 쓰면 그 작업은 아직 작업이 아니라 미결 결정(5절)이다.
- 이 문서는 **Issue를 자동으로 만들지 않으며 원격 쓰기 권한이 아니다.** Issue 생성은 매번 사용자가 현재 요청에서 명시하고 판정기가 `pass`·`publish_ready=true`일 때만 한다.

### 0.4 상태 어휘

| 상태 | 뜻 |
| --- | --- |
| `대기` | 선행 작업이 안 끝나 시작할 수 없음. 미결 결정은 5절 기본값으로 처리하므로 이 상태의 사유가 되지 않는다 |
| `준비됨` | 선행이 모두 풀렸고 바로 구현에 착수할 수 있음 |
| `기획중` | 사용자가 요청한 Issue 기획(`/knot-issue-planning` → 인터뷰 → Grill) 진행 중. 구현 착수는 이 상태를 거치지 않는다 |
| `구현중` | Issue 확정, 구현 브랜치 존재 |
| `검증중` | 구현 완료, PR·리뷰·실측 대기 |
| `완료` | 완료 판정을 모두 충족하고 병합됨 |
| `보류` | 사람만 답할 수 있는 항목(비용·외부 계정·법적 판단)에 실제로 막힘. 미결 결정만으로는 이 상태가 되지 않는다(5절) |
| `폐기` | 하지 않기로 함(사유 명시) |

## 1. 한 줄 목표

> Knot 데스크톱 앱은 `https://knoted.kr` 웹 앱을 원격 로드하는 Electron 셸이다. 탐색(채팅)의 **답변은 사용자가 터미널에서 쓰는 CLI 코딩 에이전트(Claude Code·Codex CLI·Gemini CLI)가 만들고**, 데스크톱 앱은 그 에이전트가 붙는 **로컬 MCP 서버**를 띄워 문서 검색 도구를 제공하며, 서버는 검색·저장을 맡는다(2026-09-09 개정, 기획서 6.4). Knot은 LLM을 호출하지 않고, LLM 자격증명을 저장·중개하지 않으며, CLI 바이너리를 실행·변경하지 않는다. 웹 채팅 UI는 브라우저·셸 모두 서버 SSE 경로를 쓴다(Q22). 데스크톱이 더하는 것은 상시 실행·딥링크·알림·퀵 질문 창·자동 업데이트, 그리고 이 CLI 에이전트 연결이다.

트랙은 넷이며 **서로 독립**이다. 하나가 막혀도 다른 하나는 진행한다.

| 트랙 | 내용 | 왜 분리하나 |
| --- | --- | --- |
| **A. 데스크톱 셸** | Electron 셸·빌드·서명·배포·인증·딥링크 | 제품 요구가 확정돼야 시작 |
| **B. 채팅 모델 어댑터** | 백엔드 `LlmClient`에 Anthropic 어댑터 추가 | "채팅을 Claude로" 목적은 Electron과 무관하게 달성 가능(검토 문서 7절 권고). 트랙 S 이후 서버 LLM 경로는 웹 채팅 UI(브라우저·데스크톱 셸 공통, Q22)에 쓰인다 |
| **C. 인증 전환(`D11`)** | 인증 자격증명을 쿠키 → `Authorization: Bearer` JWT로 옮기고 웹·데스크톱이 각자의 저장소에 보관 | 웹·백엔드·셸을 동시에 건드리는 한 덩어리라 트랙 A 안에 두면 데스크톱 진행에 묶인다. 웹만으로도 배포·검증이 끝나며, 데스크톱 2단계 인증(`A6`·`A7`)의 선행이다 |
| **S. 탐색 CLI 에이전트 경유** | 서버를 검색·저장 전용으로 두고(청크 상위 8개 응답), 데스크톱 앱이 로컬 MCP 서버로 검색 도구를 제공하며, 사용자의 CLI 코딩 에이전트가 답변을 만든다 | 백엔드 검색·저장 API(`S1`·`S2`·`S7`)는 셸과 무관하게 먼저 끝나고, 데스크톱 쪽(`S8`~`S10`)은 `A1`·`C3` 위에 얹는다. 2026-09-07 신설, 2026-09-09 사용자 지시로 MCP 서버 방식 확정(개정 전 "데스크톱 main이 사용자 LLM 호출"은 폐기) |

## 2. 진행 현황 요약

| 마일스톤 | 트랙 | 게이트 | 작업 수 | 상태 |
| --- | --- | --- | --- | --- |
| M0 스파이크 | A | G0 → G1 | 2 | 검증중 (`A1`) |
| M1 MVP 배포 | A | G1 → G2 | 6 | 대기 |
| M2 데스크톱 통합 | A | G2 → G3 | 5 | 대기 |
| M3 선택 | A | G3 | 3 | 대기 |
| C 인증 전환(`D11`) | C | GC | 3 | 검증중 |
| B0 provider 분리 | B | 없음 | 1 | 검증중 |
| B1 Anthropic 어댑터 | B | GB | 1 | 검증중 |
| B2~B4 어댑터 후속 | B | GB | 3 | 대기 (B1) |
| B5 임베딩 Gemini 어댑터 | B | GB | 1 | 검증중 |
| S 탐색 CLI 에이전트 경유 | S | GS | 10 | 검증중 (`S1`), `S8`·`S7`·`S9`(웹 부분)·`S10`(데스크톱 부분) 검증중(2026-09-09), `S2` 구현중(다른 세션), `S5` 대기, `S3`·`S4`·`S6` 폐기 |

### 2.1 지금 착수 가능한 작업

다음에 무엇을 구현할지는 여기서 고른다. 위에서부터 처음 만족하는 규칙을 따른다.

0. **사용자가 작업을 지정했으면 그것을 한다.** 지정된 작업은 이 목록·게이트·미결 결정보다 우선하며, 착수 전에 되묻지 않는다(3절·5절).
1. 지정이 없고 상태가 `구현중` 또는 `검증중`인 작업이 있으면 **그것을 먼저 끝낸다.** 새 작업을 열지 않는다.
2. 없으면 아래 목록에서 순위가 높은 것을 고른다. 이 목록에는 상태가 `준비됨`이고 선행 작업이 풀린 작업만 올린다. 미결 결정은 5절 기본값으로 처리하므로 선택을 막지 않는다.
3. 목록이 비어 있을 때만 새 작업을 시작하지 않고, 무엇이 막고 있는지(3절 게이트 또는 5절)를 사람에게 보고한다.

**현재 규칙 1에 해당하는 작업이 있다.** `A1`(데스크톱 스파이크)·`W1`·`W3`(웹 선행)·`C1`~`C3`(트랙 C)·`B0`·`B1`·`B5`(트랙 B)·`S1`(트랙 S)이 `검증중`이다. 지정이 없으면 아래 목록보다 이들을 먼저 끝낸다. 단 `S8`·`S7`은 사용자 지시(2026-09-08 밤)로 아래 목록 순위 1·2이며 규칙 0에 따라 이들보다 먼저 착수한다. `S7`은 2026-09-09 사용자 지시(`남은 구현 구현해`)로 착수해 구현·검증이 끝나 `검증중`으로 목록에서 내려갔다(4.5절). `S8`도 같은 날 사용자 지시(`앱이 내부 클로드 토큰을 사용하도록 모든 구현을 다 해`)로 착수해 구현·검증이 끝나 `검증중`으로 내려갔다(4.5절 `S8` 실측). `S10`은 같은 날 사용자 지시(`남은 구현 해`)로 선행 `S2`가 끝나기 전에 착수했다 — `S2` 계약이 기획서 6.4에 고정돼 있어 데스크톱 쪽을 먼저 끝낼 수 있었고, 실제 저장 종단만 `S2` 병합 뒤로 남겼다(4.5절 `S10` 실측). 목록이 비었으므로 규칙 3에 따라 남은 것은 `검증중` 작업의 커밋·PR 승인과 `S2`다.

| 순위 | ID | 작업 | 왜 지금 가능한가 |
| --- | --- | --- | --- |
| — | — | `S2`는 `S1` 병합 뒤, `S9`는 `S8` 뒤 자동 선택 대상이 된다(`S10`은 2026-09-09 사용자 지시로 `S2` 전에 착수) | |

`W1`·`W3`은 2026-09-06 사용자 지시("웹 구현 시작")로 착수해 구현이 끝났고 커밋·PR 승인 대기(`검증중`), `B0`은 같은 날 사용자 지시("BE 개발 시작")로, `C1`~`C3`은 같은 날 사용자 지시("기존의 쿠키 방식을 jwt토큰으로 변경")로 착수해 구현·검증이 끝나 커밋·PR 승인 대기(`검증중`)라 이 목록에서 내려갔다. `B0`은 2026-09-07 커밋이 끝나 `검증중`이고, `B1`은 같은 날 사용자 지시("구현 ㄱㄱ" — `B0` 다음 순서)로 착수해 구현·검증이 끝나 커밋·PR 승인 대기(`검증중`)다. `B5`(임베딩 Gemini 어댑터)는 2026-09-08 사용자 지시("임베딩 모델을 gemini 임베딩 1로 가자. 토큰값을 env로 설정할 수 있게 해줘")로 문서를 먼저 고친 뒤 착수해 구현·검증이 끝나 커밋·PR 승인 대기(`검증중`)다.

트랙 A(`A1~A13`)는 2026-09-06 사용자 지시로 G0을 통과했다(3절). `A2` 이후는 각자의 선행 작업이 끝나야 자동 선택 대상이 된다.

트랙 S(`S1~S10`)는 2026-09-07 사용자 지시로 신설했다(불변 계약 1·2번 개정, 문서 먼저 개정). `S1`은 같은 날 사용자 지시(`로드맵 4.5절 s1구현해`)로 착수해 구현·검증이 끝나 커밋·PR 승인 대기(`검증중`)다. `S3`은 2026-09-08 사용자 지시로 착수해 구현·vitest까지 끝났으나, 같은 날 밤 사용자가 탐색의 답변 생성 방식을 **사용자의 CLI 코딩 에이전트 + 데스크톱 로컬 MCP 서버**로 확정하면서 `폐기`됐다(데스크톱이 LLM을 호출하지 않으므로). 이 지시는 불변 계약 2·3번의 개정 지시이며 문서를 먼저 개정했다(11절). 같은 날 앞서 문서를 "사용자 PC의 `claude` 바이너리를 자식 프로세스로 실행"하는 방식(`S6`)으로 고쳤다가 사용자가 거부해 `폐기`했다 — 서브프로세스 방식은 다시 제안하지 않는다(계약 3번). `S8`·`S7`은 이 지시로 `준비됨`, `S2`·`S5`는 재정의, `S9`·`S10`은 선행이 끝나면 자동 선택 대상이 된다. 2026-09-09 `S9`의 웹 부분은 사용자 지시(`남은 구현 구현해`)로 `S8`이 병합되기 전에 세션 분담으로 병행 착수해 구현·검증이 끝났다(`검증중`) — 계약(기획서 4.4 `agent` API)이 확정돼 있어 셸 없이도 스텁으로 검증할 수 있었고, 실제 셸 preload 종단만 남았다.

이 목록은 4절 표에서 파생된다. 둘이 어긋나면 4절이 정본이며 이 목록을 즉시 고친다. 작업 상태를 바꿀 때 이 목록도 같은 PR에서 갱신한다.

## 3. 게이트

게이트는 **사람이 명시적으로 통과 선언**해야 넘어간다. 통과 시 이 절의 체크박스를 채우고 날짜·근거를 적는다.

**사용자가 게이트 뒤의 작업 구현을 지시하면 그 지시가 통과 선언이다.** 착수 전에 게이트 통과 여부를 되묻지 않는다. 해당 체크박스에 `사용자 지시(날짜)`를 근거로 적고 구현을 시작한다. 체크박스 중 실제 비용·외부 계정이 필요한 항목은 그 항목이 실제로 필요해지는 작업(예: `A3` 서명·배포)에 도달했을 때만 알리고, 그때까지는 그 항목 없이 가능한 범위를 모두 구현한다.

### G0 — 제품 결정 게이트 (트랙 A 착수 전) — **통과 2026-09-06**

검토 문서는 데스크톱 앱을 "대규모 신규 개발"로 판정했다. 코드를 쓰기 전에 팀이 만들기로 합의해야 한다.

- [x] 팀이 데스크톱 앱을 만들기로 합의했다 (근거: 사용자 지시 `일랙트론 데스크탑 앱 구현 시작`, 2026-09-06)
- [x] 데스크톱이 주는 값(상시 실행·딥링크·알림·퀵 질문)이 실제 사용자 요구로 확인됐다 (근거: 사용자 지시(2026-09-06). 착수 판단을 사용자가 내렸다)
- [ ] Q1(macOS 서명 계정)과 Q2(Windows 서명)의 비용을 팀이 수용했다 → 5절 (`A3` 도달 시 확인. Q1·Q2 기본값으로 `A1`·`A2`는 미서명 로컬 빌드로 진행)
- [x] Q4(`desktop/` 위치·브랜치 area)가 정해졌다 → 5절 기본값 확정(2026-09-06): 루트 독립 패키지 `desktop/`, area `fe`

통과 근거는 사용자 지시다. 비용·계정이 필요한 항목(Q1·Q2)은 미체크로 남기며, `A3`에 도달할 때 알린다. 그때까지는 5절 기본값(미서명 로컬 빌드)으로 진행한다.

### G1 — 스파이크 결과 게이트 (M0 → M1, 현재 위치)

- [x] Electron 창에서 GitHub 로그인이 경고·차단 없이 **끝까지** 동작한다 (U1 해소) — 2026-09-07 종단 확인: `github.com/login` → `github.com/session`(비밀번호) → `sessions/two-factor/webauthn` → `sessions/two-factor/mobile`(2FA 승인) → `login/oauth/authorize` → `dev-api.knoted.kr/login/oauth2/code/github?code=…` → `dev.knoted.kr/`. 차단 0건이고 **2FA까지 앱 창 안에서 동작한다**. 단 이 경로는 GitHub 계정 직접 로그인이며 소셜 로그인(Google)은 미검증(U21)
- [x] Notion OAuth 302 체인의 실제 도메인을 기록했다 (U2 해소) → `desktop/src/shared/env.ts` 허용 목록에 반영 — 2026-09-08 `local` 실측(부분): SPA가 `api.notion.com/v1/oauth/authorize`로 이동(허용) → Notion이 `https://app.notion.com/install-integration?…`로 302 → 목록 밖이라 `will-redirect` 차단 후 외부 브라우저로 빠졌다(09-07 11:37·09-08 10:33 두 번, `~/Library/Logs/Knot/main.log`). `app.notion.com`을 목록에 추가했다. 동의 화면 이후 홉(Notion 로그인·콜백 `:8080` → `:3000/…?result=connected`)은 아직 앱 창 안에서 지나가지 못했으므로 추가 후 재측정해야 체크한다 → 2026-09-08 23:21 재측정: 동의 화면까지 4홉(`install-integration` → `api/v3/sessionSync` → `sessionSyncCallback?status=unauthenticated` → `install-integration&session_sync_attempted=1`, 전부 `app.notion.com`)이 차단 없이 지나갔다. 미로그인이라 Notion 로그인 화면이 떴고, IdP 버튼이 `window.open`으로 여는 팝업(`app.notion.com/verifyNoPopupBlockerHtmlAndRedirect?redirectUri=…microsoftpopupredirect…`)이 새 창 전면 거부에 막혀 세 번 외부 브라우저로 빠졌다(23:21:38·23:21:41·23:22:32). 팝업 검증 페이지는 `window.opener`가 없으면 스스로 닫히므로 외부 브라우저로는 통과할 수 없다. 새 창 정책을 Q46으로 개정하고 IdP 오리진을 추가했다(U27). 로그인·동의·콜백 복귀는 재측정 대기 → 2026-09-08 23:41 **종단 통과**: 개정 빌드로 재시도해 팝업이 자식 창으로 열리고(`새 창 허용 → 자식 창`) `microsoftpopupredirect` → 302 `login.microsoftonline.com/common/oauth2/v2.0/authorize` → `…/common/login`(로그인) → 302 `app.notion.com/microsoftpopupcallback?code=…`, 메인 창은 동의 뒤 `localhost:8080/api/v1/notion/oauth/callback?code=…&state=…` → 302 `localhost:3000/workspace/6/notion-connection?result=connected`. 차단 0건. 로컬 DB `content_source_connections` 행(workspace 6)과 `content_import_runs` COMPLETED 확인. `login.live.com`·`www.notion.so`는 미관측
- [ ] 채팅 SSE가 Electron renderer에서 웹과 동일하게 스트리밍된다 — 로그인이 선행이라 미측정
- [ ] 실패 시 결정: 2단계 인증(`A6`·`A7`)을 M1으로 당기고 M1 범위를 재작성했다

### G2 — 배포 게이트 (M1 → M2)

- [ ] macOS 서명·공증 통과, 설치본이 Gatekeeper 경고 없이 실행된다
- [ ] 자동 업데이트가 종단으로 동작한다(구버전 설치 → 새 Release → 앱이 감지·다운로드·재시작)
- [ ] 웹 배포 워크플로우·E2E가 무변경으로 통과한다 (G6)
- [ ] 보안 체크리스트 20항목(기획서 8절) 리뷰를 통과했다 (G5)

### G3 — 인증 2단계 게이트 (M2 내부, `A7` 착수 전)

- [ ] `A6`(백엔드 디바이스 토큰)의 ADR이 `Accepted`다
- [ ] refresh rotation·재사용 감지·폐기가 백엔드 테스트로 검증됐다
- [ ] 1단계 Bearer 경로(트랙 C)에 회귀가 없음을 확인했다 (2026-09-06 갱신: `D11` 이후 쿠키 경로가 없어졌다)

### GC — 인증 전환 배포 게이트 (트랙 C, `C1`~`C3`을 운영에 올리기 전)

- [ ] 백엔드·프론트가 **같은 릴리스로** 나갈 수 있게 준비됐다(호환 기간 없음, 기획서 5.1)
- [ ] 배포 순간 살아 있던 세션이 만료되어 전 사용자가 재로그인한다는 것을 팀이 알고 있다
- [ ] dev 환경에서 GitHub 로그인 → 온보딩 → 홈 → 채팅 SSE → 로그아웃이 종단으로 동작한다 — 2026-09-06 로컬 mock(dev 서버 302 + msw)으로 기존 회원·신규 가입 두 경로를 Playwright로 확인했다. **실제 dev 백엔드로는 미측정**

### GB — 모델 교체 게이트 (트랙 B, `llm.chat.provider=anthropic` 또는 `llm.embedding.provider=gemini`를 운영에 켜기 전)

- [ ] `docs/llm-search-benchmark-independent-30.json` gold set 30문항 재측정 + 사람 검수 통과
- [ ] TTFT 5초 실측 (ADR 271 조건)
- [ ] 비용 상한·일일 예산 경고가 동작한다
- [ ] (임베딩, `B5`) Gemini 키가 서버 env에만 있고 저장소·로그·응답에 없음을 확인했다. 첫 실호출에서 응답 차원 1,024와 정규화를 실측했다(U26 — 차원 1,024·미정규화는 2026-09-08 로컬 실호출로 해소, 서버 env 확인은 남음)
- [ ] (임베딩, `B5`) `gemini`를 켠 뒤 모든 Workspace의 Notion 동기화를 다시 실행해 `search_document_chunks`가 Gemini 임베딩으로 재색인됐다(Q39). 재색인 전에는 벡터 점수가 무의미하다

### GS — 탐색 경로 전환 게이트 (트랙 S, CLI 에이전트 경로를 운영에 켜기 전)

- [ ] 불변 계약 1·2·3번 개정 ADR이 `Accepted`다(`S1` Issue 번호로 생성)
- [ ] V14(`search_references` rank 1~8·`chunk_index`, `chat_messages.generated_by`)가 dev에 적용되고 기존 출처 조회가 회귀 없이 통과한다
- [ ] 기준 에이전트 하나(Claude Code + Knot 스킬)로 gold set 30문항을 `search_documents` 경유로 재측정하고 사람 검수를 통과한다(`S5`)
- [ ] 데스크톱 설정 화면에서 세 CLI의 등록 스니펫과 스킬 설치 안내를 복사할 수 있고, 등록한 CLI에서 질문 → `search_documents` → 답변이 종단으로 동작한다(`S8`·`S9`, U28)
- [x] MCP 서버가 `127.0.0.1`에만 바인딩되고, `Origin` 헤더가 있는 요청과 연결 토큰이 없는 요청을 거부함을 테스트로 확인했다(`S8`, Q47·Q48) — 2026-09-09 `desktop/test/mcpGuard.test.ts`·`mcpServer.test.ts`(공식 SDK 클라이언트 실접속) + `local` 셸 curl 실측
- [x] 서버 액세스 토큰·연결 토큰이 로그·크래시 리포트·IPC 인자 로깅·도구 결과·서버 요청 본문에 나타나지 않는다(`S8`) — 2026-09-09 코드 검토: 토큰은 main의 `bridgeConfig`·`tokenStore`만 읽고, `MessagePort` 메시지·도구 결과·IPC 응답(`preview`는 토큰을 가림)·로그(`knotApi.test.ts`·`agentBridgeCore.test.ts`가 토큰·질문 부재를 검사)에 싣지 않는다. 크래시 리포트는 미수집(Q5)
- [x] Knot 프로세스가 LLM 자격증명(`~/.claude`·`~/.codex`·`~/.gemini`·Keychain·`CLAUDE_CODE_OAUTH_TOKEN`·`ANTHROPIC_API_KEY`·`OPENAI_API_KEY`·`GEMINI_API_KEY`)을 읽거나 쓰지 않고 CLI 바이너리를 실행하지 않음을 코드 검토로 확인했다(불변 계약 3번) — 2026-09-09 `desktop/test/s3Residue.test.ts`가 `desktop/src` 전체에서 LLM API 호스트·API 키 환경변수·`CLAUDE_CODE_OAUTH_TOKEN`·`.claude.json`·`.credentials`·`child_process`·`spawn(`·`execFile(` 부재를 검사한다

사용자 지시(2026-09-07·2026-09-08)가 트랙 S 착수의 통과 선언이다. 이 게이트는 착수가 아니라 **운영에 켜는 시점**만 막는다.

## 4. 작업 목록

각 행이 작업 1건이다. `위험 신호` 열은 **Issue를 실제로 만들 때** 쓰는 분류 입력이며, 구현 착수의 선행 조건이 아니다. 인터뷰·Grill은 사용자가 Issue 기획을 요청했을 때만 거친다(10절).
"기획서 §" 열은 설계 근거 위치다. 이 표에 설계를 다시 쓰지 않는다.

**기획서 14절 `I1~I27`과의 대응** (기획서는 초안 번호를 유지하고, 실행은 아래 ID로 한다):

| 기획서 | 이 문서 | 기획서 | 이 문서 | 기획서 | 이 문서 |
| --- | --- | --- | --- | --- | --- |
| I1 | A1 | I6 | W2 (폐기 → C1) | I11 | A8 |
| I2 | A2 | I7 | B1 | I12 | A9 |
| I3 | A3 + A4 (업데이트 분리) | I8 | B0 | I13 | A10 |
| I4 | W1 | I9 | A6 | I14 | A11 |
| I5 | W3 | I10 | A7 | I15 · I16 · I17 | C1 · C2 · C3 |
| I18 · I19 · I20 · I21 · I22 · I23 · I24 · I25 · I26 · I27 | S1 · S2 · S3(폐기) · S4(폐기) · S5 · S6(폐기) · S7 · S8 · S9 · S10 | | | | |
| — | A5·A12·A13·B2·B3·B4·B5 (신규) | | | | |

### 4.1 트랙 A — 데스크톱 셸

| ID | 작업 | area | 마일스톤 | 선행 | 위험 신호 | 기획서 § | 상태 | Issue | ADR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A1 | 데스크톱 스파이크: Electron 셸에서 `knoted.kr` 원격 로드·OAuth 체인 검증 | fe | M0 | G0 (통과) | `shared`, `core-flow` | 7 P0, 4.5 | 검증중 | | |
| A2 | 셸 MVP: 창·메뉴·네비게이션 제한·보안 기본값·로그 | fe | M1 | A1 | `shared`, `security` | 4.2, 8, 9.1 | 대기 | | |
| A3 | 빌드·서명·공증·릴리스 파이프라인 | fe | M1 | A2 | `external`, `hard-to-reverse`, `shared` | 10 | 대기 | | |
| A4 | 자동 업데이트(GitHub Releases + `update-electron-app`) | fe | M1 | A3 | `external`, `hard-to-reverse` | 10.1, 10.3 | 대기 | | |
| A5 | 다운로드 안내 페이지(`/download`, OS 감지) + `<title>` 수정 | fe | M1 | A3 | 없음 | 9.3 | 대기 | | |
| A6 | 디바이스 토큰 인증 경로(코드 교환·refresh rotation·폐기·기기 목록) | be | M2 | A2, C1, G3 | `security`, `data`, `cross-boundary`, `core-flow` | 5.2, 13 | 대기 | | ADR 314 보완 |
| A7 | 시스템 브라우저 로그인·loopback·딥링크 콜백·토큰 갱신 | fe | M2 | A6, C3 | `security`, `cross-boundary` | 5.2 | 대기 | | A6와 동일 |
| A8 | `knot://` 딥링크: 초대·채팅 진입 | fe | M2 | A2 | `core-flow` | 4.3, 4.4 | 대기 | | 스킴 이름(Q7) |
| A9 | 트레이·글로벌 단축키 퀵 질문 창 | fe | M2 | A2 | `core-flow` | 7 P2 | 대기 | | |
| A10 | Notion 동기화 완료 알림(폴링) + Dock 배지 | fe | M2 | A2, A3 | 없음(판정기 결과 따름) | 7 P2 | 대기 | | |
| A11 | 기기 목록·원격 로그아웃 UI(웹 공용) | fe | M3 | A6 | `security` | 7 P3 | 대기 | | |
| A12 | 로컬 번들(`app://`) 셸 전환 검토 | fe | M3 | C1 | `security`, `shared` | 지식 §2.4 | 대기 | | 필요 시 |
| A13 | 원격 MCP 서버(개발자용, 서버 호스팅 변형) | be | M3 | S7 | `external`, `security` | 지식 §5.4 | 대기 | | 별도 기획. 데스크톱 로컬 MCP 서버(`S8`)와 도구 집합·결과 모양을 맞춘다 |

### 4.2 웹 선행 작업 (데스크톱과 무관하게 웹에도 필요)

G0 미통과 상태에서도 착수할 수 있다.

| ID | 작업 | area | 선행 | 위험 신호 | 기획서 § | 상태 | Issue |
| --- | --- | --- | --- | --- | --- | --- | --- |
| W1 | 웹 로그아웃 액션(`POST /api/v1/auth/logout` → `/login`) + `useDesktop` 감지 훅 | fe | — | `cross-boundary` | 9.3, 5.1 | 검증중 | |
| W2 | 로그아웃 응답 204 및 XHR 호환 | be | W1 | `cross-boundary` | 13 | 폐기 | |
| W3 | 웹 CSP 헤더(Cloudflare `_headers`) | fe | — | `security` | 8 #7, 9.4 | 검증중 | |

> W1은 현재 SPA에 로그아웃 호출 코드가 아예 없어서 생긴 누락 기능이다(지식 §1.2). 데스크톱과 무관하게 가치가 있다.
>
> W2는 `C1`에 흡수돼 `폐기`다(2026-09-06). `C1`이 `LogoutFilter`(302)를 걷어내고 로그아웃을 204 컨트롤러 엔드포인트로 바꾸므로, 같은 파일을 두 작업이 나눠 고칠 이유가 없다.

### 4.3 트랙 C — 인증 전환 (쿠키 → Bearer JWT, 기획서 `D11`·5.1)

셋은 **한 배포 단위**다. 따로 배포하면 인증이 끊긴다(기획서 5.1 "호환 기간을 두지 않는다"). 순서는 `C1` → `C2` → `C3`이고, 배포는 백엔드(`C1`)를 먼저 올린 뒤 프론트(`C2`)를 바로 잇는다.

| ID | 작업 | area | 선행 | 위험 신호 | 기획서 § | 상태 | Issue | ADR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| C1 | 백엔드 Bearer 전환: `JwtAuthenticationFilter`가 `Authorization` 헤더를 읽고, OAuth 성공 핸들러가 토큰을 프래그먼트로 넘기고, `/auth/nickname`이 본문으로 액세스 토큰을 돌려주고, CSRF·`/auth/csrf`·`AuthCookieManager`·쿠키 프로퍼티를 제거하고, 로그아웃이 204를 주고, 회원이 없는 토큰은 401을 준다(Q41) | be | — | `security`, `cross-boundary`, `core-flow` | 5.1, 13 | 검증중 | | 필요(314 보완) |
| C2 | 프론트 토큰 저장소·`Authorization` 헤더·프래그먼트 수신·CSRF 코드 제거(SSE fetch·mock·E2E 포함) | fe | C1 | `security`, `cross-boundary` | 5.1, 9.3 | 검증중 | | C1과 동일 |
| C3 | 데스크톱 토큰 저장: preload `auth.getToken/setToken/clearToken` + main `safeStorage` | fe | C2 | `security` | 4.4, 5.1 | 검증중 | | C1과 동일 |

완료 판정: `C1` 백엔드 테스트 전량 통과(쿠키 참조 0건) · `C2` vitest·tsc·ESLint 통과와 dev 서버 로그인 → 홈 → 로그아웃 종단 확인 · `C3` `desktop` vitest 통과와 셸에서 재시작 후 로그인 유지.

위험 신호: 셋 다 인증 계층 변경이라 Issue를 만들 때는 인터뷰·Grill 경로다(7절 계약 7번). 구현 착수는 사용자 지시로 이뤄졌으므로 Issue를 선행 조건으로 삼지 않는다.

### 4.4 트랙 B — 채팅 모델 어댑터

| ID | 작업 | area | 선행 | 위험 신호 | 기획서 § | 상태 | Issue |
| --- | --- | --- | --- | --- | --- | --- | --- |
| B0 | 채팅·임베딩 provider 설정 분리(`llm.chat.provider` / `llm.embedding.provider`) | be | — | `shared` | 6.2 | 검증중 | |
| B1 | 채팅 LLM Anthropic Messages API 어댑터 | be | B0 | `external`, `shared` | 6.2 | 검증중 | |
| B2 | 사용량 계측(입력·출력·캐시 토큰) | be | B1 | `data` | 6.2, 13 | 대기 | |
| B3 | Workspace BYO 키(C안) | be | B1 안정화 | `security`, `data`, `external` | 6.3 | 대기 | |
| B4 | 재검색 루프(`search_knowledge` 툴 정의, `stop_reason=tool_use` 시 서버가 재검색, 최대 2회) | be | B1, GB 실측 | `external`, `core-flow` | 6.2 | 대기 | |
| B5 | 임베딩 Gemini Embedding 어댑터(`llm.embedding.provider=gemini`, `gemini-embedding-001`, 키는 `GEMINI_API_KEY`) | be | B0 | `external`, `shared`, `data` | 6.2 | 검증중 | |

두 LLM 클라이언트가 `llmHttpClient` 빈과 `llm.provider` 키 하나를 공유하던 것이 **B0이 B1의 필수 선행**인 이유였다(지식 §1.4). `B0`이 이 공유를 `llm.chat.provider`/`llm.embedding.provider`와 `chatLlmHttpClient`/`embeddingLlmHttpClient`로 끊는다.

완료 판정: `B1` 백엔드 단위 테스트 전량 통과(2026-09-07: 91클래스 500건, 어댑터 테스트 19건 포함) · 기본값 `llm.chat.provider=fake`에서 기존 테스트·컨텍스트 무변경 통과 · 실제 Anthropic API 종단(TTFT·gold set)은 Q6(API 키)에 도달해 사람이 키를 넣은 뒤 GB 게이트에서 측정한다.

> `B5`는 2026-09-08 사용자 지시로 추가했다. 임베딩만 바꾸며 채팅 provider·검색 융합·V13 차원(1,024)은 건드리지 않는다(Q34~Q40). `openai-compatible`(LM Studio Qwen)·`fake` 분기는 남겨 두어 되돌리기는 설정 한 줄이다. 완료 판정: 백엔드 단위 테스트 전량 통과(어댑터·설정 테스트 포함) · 기본값 `llm.embedding.provider=fake`에서 기존 테스트·컨텍스트 무변경 통과 · `gemini`인데 키가 비면 기동 실패 · 실제 Gemini API 종단(1,024차원 응답·재색인·gold set)은 사람이 키를 넣은 뒤 GB 게이트에서 측정한다. 2026-09-08 실측: 단위 536건(어댑터 8·설정 4·빈 등록 3 포함)·통합 120건·수락 177건 통과. 같은 날 로컬 실호출: 배치 64는 첫 요청부터 429, 배치 16도 무료 티어 분당 한도에 걸려 재시도 5회로도 실행 #4가 실패(412초). 결제 계정 연결 뒤 실행 #5가 21페이지·611청크를 106초(수집 57초·색인 48초)에 COMPLETED·발행했고 `search_document_chunks` 611행이 Gemini 임베딩이다(Q37·Q42·U25·U26·R22). 단위 543건 통과. 통합 1건(`ContentImportWorkerPersistenceIntegrationTest`의 heartbeat 만료 판정)이 첫 실행에서 실패했으나 임베딩과 무관한 시간 의존 테스트이며 단독 재실행에서 통과했다.
>
> `B4`는 기획서 6.2 "재검색 루프(선택)"를 `B1`에서 떼어낸 것이다(2026-09-07). 왕복 2회로 TTFT가 늘어나므로 GB 실측 뒤에 켤지 정하며, `B1`은 `llm.anthropic.research-loop.enabled` 키를 만들지 않는다.
>
> 트랙 S(2026-09-07) 이후 서버 `LlmClient` 경로는 웹 채팅 UI(브라우저·데스크톱 셸 공통, Q22 기본값)에만 쓰인다. CLI 에이전트 경로(`S8`)는 서버 LLM을 부르지 않는다. `B2`~`B4`는 그 경로의 후속이며 우선순위는 트랙 S 뒤다.
>
> `A3`의 Q1·Q2, `B1`의 Q6은 **선행이 아니라 5절 기본값 항목**이다. 답이 없어도 착수하고, 실제 비용·계정이 필요해지는 지점에서만 알린다.

### 4.5 트랙 S — 탐색 CLI 에이전트 경유 (서버 검색 전용 + 데스크톱 로컬 MCP 서버, 기획서 6.4)

사용자 지시(2026-09-07): "서버에서 탐색 파이프라인을 융합/선별을 상위 3개가 아니라 8개로 늘리고, 이걸 응답하도록 해줘 … 사용자의 llm에게 요청하는 방식". 사용자 지시(2026-09-08 밤): CLI 코딩 에이전트가 컴퓨터의 프로세스에 접근할 수 있음을 이용해, **앱이 띄워 둔 MCP 서버**에 skill 안내대로 "문서 검색" 도구를 호출하고, MCP 서버가 IPC로 데스크톱 앱에 전달해 앱이 서버에서 문서 조각을 받아 돌려주며, 에이전트가 답변을 터미널에 표시하고 필요하면 앱 화면에도 같은 답을 보여 주는 방식. 앱은 자격증명을 저장·중개하지 않고 CLI 바이너리를 실행·변경하지 않으며 MCP 서버라는 공식 확장 지점으로만 붙는다("이 방식대로 데스크탑 앱을 구현할꺼야"). 설계는 기획서 6.4가 정본이며 이 표에 다시 쓰지 않는다.

| ID | 작업 | area | 선행 | 위험 신호 | 기획서 § | 상태 | Issue | ADR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | 서버 검색 API `POST /api/v1/conversations/{sessionId}/search`: 접근·스냅샷·진행 중 턴 검사, USER 저장, 하이브리드 검색을 **청크 단위 상위 8개**로 선별해 규칙 문장과 함께 응답, READY가 아니면 안내 문구를 저장하고 응답. `top-k=8`·`max-context-characters=12000`. V14(`search_references` rank 1~8·`chunk_index`·유일 키, `chat_messages.generated_by`). 2026-09-09 주: 데스크톱 쪽 소비자(`S3`)가 폐기돼 현재 이 엔드포인트의 소비자는 없다. 검색·선별·예산 로직과 V14는 `S7`·`S2`가 재사용하며, 엔드포인트는 되돌리기 쉬우므로 남긴다 | be | — | `core-flow`, `data`, `shared` | 6.4, 13 | 검증중 | | 필요(불변 계약 1·2 개정) |
| S2 | 서버 턴 저장 API `POST /api/v1/conversations/{sessionId}/turns`(재정의 2026-09-09): CLI 에이전트가 만든 질문·답변·근거(≤8)를 USER + ASSISTANT(`generated_by=CLIENT`) + `search_references`로 한 트랜잭션에 저장. 세션 소유자·Workspace JOIN 검증, rank ≤ 8, 진행 중 턴 검사(Q23) + 출처 조회 8건·`chunkIndex` 응답. 개정 전 `POST …/messages/assistant`(직전 USER 검증)는 만들지 않는다 | be | S1 | `data`, `security`, `core-flow` | 6.4, 13 | 대기 | | S1과 동일 |
| S3 | 데스크톱 preload `chat`·`llm` API + main의 서버 검색 호출·프롬프트 조립·사용자 LLM 스트리밍 클라이언트(openai-compatible·anthropic)·설정 저장 | fe | — | — | — | **폐기**(2026-09-08, 사용자 지시 — 데스크톱은 LLM을 호출하지 않고 LLM 자격증명을 저장하지 않는다). 구현·vitest 89건은 끝나 있었다. `src/main/llm/`·`src/main/chat/prompt`·`chatService`·preload `chat`/`llm`·`llm-settings.json`·`llm-key.bin`·관련 테스트 제거는 `S8` 범위. `src/main/chat/knotApi`(Bearer 서버 클라이언트)·`secretStore`는 `S8`이 재사용 | | |
| S4 | 웹 `streamChatMessageApi` 데스크톱 분기·LLM 설정 화면·설정 없음 안내 | fe | — | — | — | **폐기**(2026-09-08, `S3`과 같은 사유. 찾은 문서 `GET /messages/{id}/sources` 연동은 `S9`로 이관) | | |
| S5 | 기준 에이전트 하나(Claude Code + Knot 스킬)로 gold set 30문항을 `search_documents` 경유로 재측정 + 사람 검수 + 도구 지연(요청 → 결과) 계측(재정의 2026-09-09) | be, fe | S8, S9 | `core-flow` | 6.4 | 대기 | | |
| S6 | 데스크톱 main Claude Code provider(사용자 PC의 `claude` 바이너리를 `claude -p` 자식 프로세스로 실행) | fe | — | — | — | **폐기**(2026-09-08, 사용자 거부 — 서브프로세스 방식은 다시 제안하지 않는다. 계약 3번. 코드 미착수) | | |
| S7 | 서버 Workspace 검색 API `POST /api/v1/workspaces/{workspaceId}/search`: Workspace 멤버 검사·공개 스냅샷 검사, `S1`과 같은 하이브리드 검색·청크 단위 상위 8개·규칙 문장·예산(Q28·Q33). **저장·턴 검사 없음**. READY가 아니면 안내 문구만 응답(저장 없음) | be | S1 | `core-flow`, `shared` | 6.4, 13 | 검증중(2026-09-09 구현·검증 완료, 커밋·PR 승인 대기. 사용자 지시 `남은 구현 구현해`. 단위 9건·수락 9건·OpenAPI 계약 1건 추가, 백엔드 단위 전체 552건·통합 121건·수락 188건 통과. 구현은 기획서 13절 S7 행) | | S1과 동일 |
| S8 | 데스크톱 로컬 MCP 서버: Electron `utilityProcess`에서 Streamable HTTP 서버(`http://127.0.0.1:<port>/mcp`, Q47), 연결 토큰 발급·검사·Origin/Host 검사(Q48), 도구 `search_documents`·`list_workspaces`(Q49), `MessagePort` IPC로 main에 위임(main이 Bearer로 `S7`·워크스페이스 목록 호출), 설정 저장(`agent-bridge.json`), preload `agent` API(`getStatus`·`copyRegistration`·`rotateToken`·`setPort`), `S3` 코드 제거·웹 계약 사본 갱신. Knot은 LLM을 호출하지 않고 LLM 자격증명·CLI 바이너리를 다루지 않는다(계약 2·3번) | fe | A1, C3, S7 | `security`, `external`, `core-flow` | 4.4, 6.4 | 검증중(2026-09-09, 사용자 지시 `앱이 내부 클로드 토큰을 사용하도록 모든 구현을 다 해`. `S7`은 같은 브랜치에서 다른 세션이 함께 구현) | | S1과 동일(계약 2·3번 개정 포함) |
| S9 | Knot 스킬(`SKILL.md`, Agent Skills 표준 필드만, Q50) + 웹 연결 안내 화면(데스크톱 전용): MCP 서버 상태, 세 CLI(Claude Code·Codex CLI·Gemini CLI) 등록 명령·설정 스니펫 복사, 스킬 설치 안내, 토큰 재발급·포트 변경, Workspace 소유자 고지(R27). 찾은 문서 `GET /messages/{id}/sources` 연동(8건, 페이지로 묶기) | fe | S8 | `core-flow` | 6.4, 9.3 | 검증중(웹 부분, 2026-09-09 — 사용자 지시 `남은 구현 구현해`, 세션 간 분담으로 `S8`과 병행 착수. 연결 안내 화면 `/agent-connection`·GNB 프로필 메뉴 진입점·찾은 문서 서버 연동·웹 계약 사본 완료. `SKILL.md`는 `S8`이 만들었다(Q50). 세 CLI 스니펫 복사·Codex/Gemini 종단 실측(U31)과 실제 셸 preload 종단은 미수행) | | |
| S10 | (선택) 도구 `show_answer`: 에이전트의 답변·근거를 세션에 저장(`S2`)하고 앱 창을 앞으로 가져와 그 세션으로 이동(Q51). 웹은 기존 채팅 화면 그대로 | fe | S2, S8 | `data`, `core-flow` | 6.4 | 검증중(데스크톱 부분, 2026-09-09 — 사용자 지시 `남은 구현 해`. `S2`가 같은 시각 다른 세션에서 구현 중이라 기획서 6.4 턴 저장 계약을 가정하고 먼저 착수. 도구 `show_answer` 등록·입력 재검사·세션 생성·턴 저장·창 앞으로+`knot:deep-link` 전송·SKILL.md·instructions. 실제 저장 종단은 `S2` 병합 뒤, SPA 쪽 이동은 U32) | | S1과 동일 |

완료 판정: `S1`·`S2`·`S7` 백엔드 테스트 전량 통과(`S1` 2026-09-07: 단위 521건·통합 120건·수락 177건, 검색 API 수락 9건·OpenAPI 계약 1건·V14 업그레이드 1건 포함)(`S7` 2026-09-09: Workspace 검색 단위 6건·컨트롤러 3건·수락 9건·OpenAPI 계약 1건 추가, 단위 전체 552건·통합 121건·수락 188건 통과 — 수락 9건은 READY 8청크·같은 질문 연속 200·NO_RESULT·NEEDS_CLARIFICATION·409 미준비·비멤버 403·404·400·401이며 전 시나리오에서 `chat_sessions`·`chat_messages`·`search_references` 0건을 확인) + 검색·저장·출처 API 계약 테스트(다른 Workspace 청크 거부, rank 1~8, 진행 중 턴 409, Workspace 검색은 비멤버 403·DB 변경 0건) · `S8` `desktop` vitest(연결 토큰·Origin/Host 검사·도구 입력 검증·도구 결과 조립·IPC 왕복·설정 저장·`S3` 잔재 0건) + 이 PC의 Claude Code에 `claude mcp add --transport http`로 등록해 질문 → `search_documents` → 청크 8개 → 터미널 답변 종단 · `S9` vitest·tsc·ESLint 통과 + 세 CLI 스니펫 복사 실측 + Codex CLI·Gemini CLI 중 설치 가능한 것으로 종단(U31) · `S5` 재측정 결과를 `docs/llm-search-ab-test-report.md`에 추가 · `S10` 저장된 턴이 앱 채팅 화면에 보이고 출처 8건이 조회됨.

`S8`·`S7` 착수 가정(2026-09-09, 사용자 지시 `앱이 내부 클로드 토큰을 사용하도록 모든 구현을 다 해` — "내부 토큰"은 앱이 스스로 발급해 CLI 등록 스니펫에 넣는 **연결 토큰**(Q48)으로 해석했다. Claude Code의 구독 OAuth 자격증명(Keychain·`~/.claude`)을 앱이 읽는 방식은 계약 3번·검토 문서 5.1의 정책 금지 항목이라 구현하지 않았다): (1) `S9` 연결 안내 화면 전까지 등록 스니펫 복사·상태 확인 진입점은 앱 메뉴 `CLI 에이전트 연결`이다(되돌리기 쉬움). (2) `list_workspaces`의 `role`은 서버가 줄 때만 실린다(Q49 정정과 같다). (3) 도구 결과의 main 쪽 오류 코드: 토큰 없음 `UNAUTHENTICATED`("Knot 앱에 로그인하세요"), 서버 연결 실패·30초 타임아웃 `KNOT_API_UNREACHABLE`, HTTP 오류 본문 못 읽음 `UNKNOWN`, 응답 모양 불일치 `KNOT_API_MALFORMED`(이상 Q49와 같다), 동시 5개째 `AGENT_TOO_MANY_REQUESTS`("잠시 뒤 다시"), MCP 프로세스가 main의 답을 35초 안에 못 받음 `AGENT_BRIDGE_TIMEOUT`, 입력 계약 위반 `INVALID_TOOL_INPUT`, 속한 워크스페이스 없음 `NO_WORKSPACE`. (4) 동시성 상한 4는 main 브리지가 센다. (5) `resources/skills/knot/SKILL.md`(Q50)는 `AgentBridgeStatus.skillPath`가 가리키므로 `S8`에서 함께 만든다.

`S8` 2026-09-09 실측: `desktop` typecheck(main·preload) 통과, vitest 12파일 98건 통과(guard Origin/Host/토큰 · `agent-bridge.json` 0600·재발급·포트 · 등록 스니펫 3종+스킬 · 도구 입력 검증·결과 조립 · `MessagePort` 왕복·동시 4개 상한 · 공식 SDK 클라이언트로 실제 HTTP 접속·도구 호출·401/403/404 · `S3` 잔재 0건과 LLM API·자격증명 문자열 부재를 소스 전체 스캔으로 확인), `KNOT_DESKTOP_ENV=local` 빌드 통과(`dist/mcp/index.cjs` 1.3MB — SDK를 esbuild로 한 파일에 묶음). `local` 셸 스모크: 앱 시작 0.5초 뒤 `MCP 서버 기동 {port: 47871}`, `lsof`에 `127.0.0.1:47871`만 LISTEN, `userData/agent-bridge.json`(0600, 토큰 base64url 43자)·`userData/skills/knot/SKILL.md` 생성. curl: 토큰 없음 401, `Origin` 있음 403, `Host` 불일치 403, 토큰 있음 initialize 200(`protocolVersion` 2025-11-25, `instructions` 포함). 이 PC의 Claude Code 2.1.263에 `claude mcp add --transport http knot … --header "Authorization: Bearer …"`(local 스코프, `~/.claude.json`)로 등록 → `claude mcp list` ✔ Connected → `claude -p`로 `list_workspaces` 호출이 MCP 프로세스 → `MessagePort` → main → 도구 실행 → 터미널까지 왕복(main 로그 `도구 호출 {tool, workspaceId: null, status: error:UNAUTHENTICATED, latencyMs: 3}`, 셸에 저장된 액세스 토큰이 없어 `UNAUTHENTICATED: Knot 앱에 로그인하세요`가 답으로 돌아옴). 앱 종료(SIGTERM) 3초 뒤 포트 리스너 0개. **미측정**: 셸에 로그인한 상태의 `search_documents` → 청크 8개 → 터미널 답변(GitHub OAuth 로그인은 사용자만 할 수 있어 이 세션에서 못 함), `S9` 화면과 preload `agent` API의 실제 연동(로그인 뒤 `/agent-connection`), Codex CLI·Gemini CLI(미설치, U31), 패키징 빌드에서의 `utilityProcess` 엔트리 경로. → 2026-09-09 01:2x 추가 실측(`S10` 착수 중): (1) 패키징 해소 — `KNOT_DESKTOP_ENV=local pnpm package --arch=arm64`(셸 Node가 x64라 기본 타깃이 x64로 잡히고, 그 x64 `Knot.app`은 이 arm64 Mac에서 Rosetta 번역 단계(`UN` 상태)에 멈춰 뜨지 않았다 — `--arch=arm64` 필수) → `app.asar`에 `dist/mcp/index.cjs`·`resources/skills/knot/SKILL.md` 포함 확인, `Knot.app/Contents/MacOS/Knot --user-data-dir=<임시 디렉터리>`(같은 `userData`면 dev 인스턴스의 단일 인스턴스 락에 걸려 즉시 종료되고, `HOME` 덮어쓰기는 `userData` 경로에 영향이 없었다)로 띄우면 시작 0.3초 뒤 `MCP 서버 기동 {port: 47899}`(미리 둔 `agent-bridge.json`의 포트), `userData/skills/knot/SKILL.md` 복사, curl 401/403/initialize 200, SIGTERM 3초 뒤 포트 해제. (2) 사용자가 dev 셸(`pnpm start`, `local`)에 GitHub 로그인한 상태에서 이 PC의 Claude Code 세션이 `list_workspaces` 호출 → `{workspaces: [{id: 7, name: "test"}]}` 종단 통과(main 로그 `status: ok, latencyMs: 60`). `search_documents`는 로컬 백엔드가 500 `INTERNAL_SERVER_ERROR`를 돌려줘 그 코드·문구가 그대로 도구 `isError`로 중계됐다(데스크톱 경로는 정상, 원인은 로컬 백엔드 기동 설정 — dummy Anthropic 키로 기동된 인스턴스라 다른 세션이 진단 중). 청크 8개 종단은 여전히 미측정.

`S10` 2026-09-09 실측(데스크톱 부분): `desktop` typecheck(main·preload) 통과, vitest 12파일 113건 통과(`show_answer` 입력 재검사 11개 위반 케이스·세션 생성→턴 저장→`presentAnswer` 순서·`sessionId` 있으면 세션 생성 생략·저장 실패 시 창 미이동·오류 메시지에 질문·답변 부재 · `knotApi` 세션 생성·턴 저장 요청 모양·응답 검사·로그에 답변 부재 · 공식 SDK 클라이언트로 도구 3개 노출·`show_answer` 위임·`sources` 9개 SDK 거절), `KNOT_DESKTOP_ENV=local` 빌드 통과. 기존 `agentBridgeCore` 테스트의 5ms 대기가 병렬 실행에서 한 번 플레이크해 25ms로 올렸다. **미측정**: 실제 `show_answer` 종단(`S2`가 없어 저장이 404가 되고, 세션 생성만 성공하면 사용자 워크스페이스에 빈 세션이 남으므로 실행하지 않음), 앱 창 이동(SPA에 `onDeepLink` 구독자가 없다 — U32), Claude Code가 `show_answer`를 스킬 안내대로 사용자 요청 시에만 부르는지.

`S3` 2026-09-08 실측(폐기 전 기록): `desktop` typecheck(main·preload) 통과, vitest 11파일 89건 통과, `KNOT_DESKTOP_ENV=local` 빌드 통과. U24는 vitest 안에서 실제 `http.createServer` SSE 서버로 확인했으나 `S3` 폐기로 무효. 같은 날 `local` 셸 스모크에서 새 IPC 핸들러 등록에 예외 없음. 이 코드는 `S8`에서 제거하며, 서버 API 클라이언트(`knotApi`)와 `secretStore`만 남긴다.

위험 신호: `S1`·`S2`·`S7`·`S8`·`S10`은 데이터 경계(에이전트 생성 답변 저장, Workspace 문서를 사용자의 CLI 에이전트와 그 모델 제공자로 내보냄)·로컬 네트워크 표면(로컬 HTTP 서버)·계약 개정을 포함하므로 Issue를 만들 때는 인터뷰·Grill 경로다(7절 계약 7번). 구현 착수는 사용자 지시로 이뤄졌으므로 Issue를 선행 조건으로 삼지 않는다. 불변 계약 개정 ADR은 `S1` Issue 번호가 생기면 `Proposed`로 만든다.

## 5. 미결 결정과 기본값 (답이 없어도 진행한다)

각 항목에는 **기본값**이 있다. 사람이 답하지 않아도 기본값으로 구현을 진행하며, 착수 전에 되묻지 않는다. 기본값은 되돌리기 쉬운 쪽으로 잡았고, 사람이 나중에 다르게 정하면 그 시점에 문서와 코드를 함께 고친다.

| ID | 질문 | 차단 대상 | 기본값 (답이 없으면 이대로 진행) | 상태 |
| --- | --- | --- | --- | --- |
| Q1 | macOS 서명: Apple Developer Program(US$99/년)을 개설하는가, 소유 주체는 누구인가 | A3, A4, A10(알림) | 개설 없이 진행한다. 개발·스파이크는 미서명 로컬 빌드로 하고, `A3`에 도달했을 때만 계정 필요를 알린다 | 미결 (기본값 적용) |
| Q2 | Windows 서명: Azure Artifact Signing(조직 계정 필요, US$9.99/월) vs 미서명 출시 + 경고 안내 | A3 | 미서명 출시 + SmartScreen 경고 안내 문구 | 미결 (기본값 적용) |
| Q3 | 운영 API 오리진(`vars.API_BASE_URL_PROD`) 실제 값 | prod 빌드(A3) | 값을 하드코딩하지 않고 빌드 환경변수로 주입한다. 실제 값은 `A3` 배포 시점에 사람이 채운다 | 미결 (기본값 적용) |
| Q4 | `desktop/` 위치(루트 독립 패키지 vs `frontend/` 하위)와 브랜치 area | A1 | 루트 독립 패키지 `desktop/`, area는 `fe` 유지. `deploy-frontend-*.yml`이 `frontend/**`만 감시하므로 웹 배포에 회귀가 없다 | 기본값 확정 |
| Q5 | 크래시·에러 수집: Sentry vs 자체 vs 미수집, 개인정보 고지 문구 | A2 완료 판정 | 미수집. 로컬 로그 파일만 남긴다(기획서 9.1). 외부 전송을 추가할 때 개인정보 고지를 함께 만든다 | 기본값 확정 |
| Q6 | Anthropic Console API 키 발급 주체와 월 예산 상한 | B1 | 키는 서버 환경변수로 주입하고 저장소에 넣지 않는다. 예산 상한은 `B1`에 도달했을 때만 알린다 | 미결 (기본값 적용) |
| Q7 | 딥링크 스킴: `knot://` vs `kr.knoted.app://`(reverse-domain) | A8 | `knot://`. 충돌이 실측되면 그때 reverse-domain으로 바꾼다 | 기본값 확정 |
| Q8 | 오프라인 사용을 지원하지 않는다는 제약을 수용하는가 | — | 수용한다. 원격 로드 구조의 필연적 한계다(검토 문서 7절) | 기본값 확정 |
| Q9 | 웹의 로그아웃 진입점을 어디에 두는가 | W1 | GNB 우측 프로필 아바타를 눌러 여는 메뉴의 `로그아웃` 한 항목. 데스크톱 메뉴(`A2`)가 붙어도 같은 액션을 부르므로 진입점만 늘어난다 | 기본값 확정(2026-09-06) |
| Q10 | 로그아웃 요청이 실패하면 웹은 무엇을 하는가 | W1 | 성공·실패와 무관하게 캐시를 비우고 `/login`으로 보낸다. 서버는 응답을 만들기 전에 쿠키를 이미 만료시키므로(지식 §1.2), 화면만 로그인 상태로 남는 쪽이 더 나쁘다 | 기본값 확정(2026-09-06) |
| Q11 | `_headers`의 `connect-src`에 API 오리진을 어떻게 넣는가 | W3 | 저장소에 고정하지 않고 빌드 시 `API_BASE_URL`로 `_headers`를 생성한다(Q3와 같은 이유). 값이 비면 `connect-src 'self'`만 남긴다 | 기본값 확정(2026-09-06) |
| Q12 | provider 키를 나눈 뒤 기존 `LLM_PROVIDER`(=`llm.provider`)를 어떻게 하는가 | B0 | 지우지 않고 두 키의 fallback으로 남긴다(`llm.chat.provider=${LLM_CHAT_PROVIDER:${llm.provider}}`). 운영에 이미 들어간 환경변수를 그대로 두면 회귀가 없고, 나중에 지우는 쪽이 되돌리기 쉽다 | 기본값 확정(2026-09-06, `B0`) |
| Q13 | 앱 식별자: macOS 번들 ID·`productName`·userData 디렉터리 이름 | A1 | 번들 ID `kr.knoted.desktop`, `productName` `Knot`(userData `~/Library/Application Support/Knot`). 첫 릴리스 전이라 되돌리기 쉽다. 배포 후에는 사용자 데이터 경로가 바뀌므로 변경 비용이 커진다 | 기본값 확정(2026-09-06, `A1`) |
| Q14 | 로그인 성공 뒤 토큰을 클라이언트에 어떻게 넘기는가 | C1, C2 | 리다이렉트 URL의 **프래그먼트**(`#access_token=…&expires_in=…`). `#` 뒤는 서버로 전송되지 않아 액세스 로그·`Referer`에 남지 않고, 백엔드 변경이 성공 핸들러 한 곳으로 끝난다. 대안인 일회용 코드 교환(2단계 `device_code`와 같은 방식)은 코드 저장소·엔드포인트가 늘어 되돌리기 어렵다. 히스토리에 남는 것은 SPA가 `history.replaceState`로 지운다 | 기본값 확정(2026-09-06, `C1`) |
| Q15 | 웹은 액세스 토큰을 어디에 두는가 | C2 | `localStorage`의 `knot.accessToken`. 쿠키 시절과 같이 새로고침·탭·재시작을 넘겨 로그인이 유지된다. `sessionStorage`는 탭마다 다시 로그인해야 하고, 메모리 전용은 새로고침마다 끊겨 리프레시 토큰(2단계) 없이는 쓸 수 없다. XSS 노출은 셋 다 같다(스크립트가 읽을 수 있는 건 마찬가지) | 기본값 확정(2026-09-06, `C2`) |
| Q16 | 데스크톱은 액세스 토큰을 어디에 두는가 | C3 | main 프로세스의 `safeStorage.encryptString` → `userData/auth.bin`, preload로 읽기·쓰기만 노출. `isEncryptionAvailable()`이 false면 저장하지 않고 메모리로만 들고 있다가 앱 종료 시 잃는다. renderer의 `localStorage`를 쓰지 않는 이유는 데스크톱에서 그것이 평문 파일이기 때문이다 | 기본값 확정(2026-09-06, `C3`) |
| Q17 | 쿠키 경로를 얼마나 오래 함께 유지하는가 | C1, C2 | 유지하지 않는다. 백엔드를 먼저 배포하고 프론트를 바로 잇는다. 두 경로를 함께 두면 필터가 두 자격증명을 받아들이고 CSRF도 켜 둔 채 남겨야 해서, 전환의 목적(단일 경로)이 사라진다. 대가는 배포 순간 재로그인 1회 | 기본값 확정(2026-09-06, `C1`) |
| Q18 | CSRF 토큰 체계를 어떻게 하는가 | C1, C2 | 제거한다. CSRF는 브라우저가 자동으로 붙이는 자격증명을 노린 공격을 막는 장치이고, `D11` 이후 그런 자격증명이 없다. `GET /api/v1/auth/csrf`·`X-XSRF-TOKEN`·403 재시도 인터셉터를 함께 지운다 | 기본값 확정(2026-09-06, `C1`) |
| Q20 | Anthropic 호출 방식: 공식 SDK(`com.anthropic:anthropic-java`) vs JDK `HttpClient` 직접 호출 | B1 | JDK `HttpClient`로 `POST /v1/messages`를 직접 호출하고 SSE를 자체 파싱한다. 기존 `OpenAiCompatibleLlmClient`와 같은 구조라 의존성 추가 없이 `chat/infrastructure/anthropic/` 안에서 끝나고, 나중에 SDK로 바꿔도 그 패키지만 갈아 끼우면 된다. SDK는 OkHttp·Jackson 2.x(Spring Boot 4의 Jackson 3와 별도 트리)를 끌어와 되돌리기 비용이 더 크다 | 기본값 확정(2026-09-07, `B1`) |
| Q19 | 온보딩 토큰(`#onboarding_token`)은 어디에 두는가 | C2 | 액세스 토큰과 슬롯을 나눠 웹·데스크톱 모두 renderer의 `sessionStorage` `knot.onboardingToken`에 둔다. 같은 슬롯에 담으면 `/auth/me` 401이 온보딩 토큰까지 지우고, preload 계약(기획서 4.4)은 슬롯이 하나뿐이라 두 토큰을 함께 넣으면 계약을 바꿔야 한다. 수명 10분·1회용이라 탭을 닫을 때 사라지는 편이 쿠키 시절 동작에 가깝다 | 기본값 확정(2026-09-06, `C2`) |
| Q21 | 검색 API 경로: 세션 아래(`/conversations/{sessionId}/search`) vs Workspace 아래(`/workspaces/{id}/search`, 검토 문서 8절 예시) | S1 | 세션 아래. 서버가 세션 이력(직전 4개)으로 검색 질의를 조립하고 USER 메시지 저장을 같은 트랜잭션에 두는 현행 구조를 그대로 옮길 수 있다. Workspace 아래는 이력 없는 검색이라 후속 질문 정책(기능 기획서 7절)이 깨진다 | 미결 (기본값 적용) |
| Q22 | 웹 채팅 UI(브라우저·데스크톱 셸)의 탐색 경로 | S1, S8, S9 | 기존 서버 SSE 경로(`POST …/messages`, 서버 `LlmClient`)를 브라우저와 데스크톱 셸 모두에서 그대로 쓴다. 근거만 청크 8개로 맞춘다. 셸 전용 채팅 전송 경로(개정 전 `chat.ask` IPC)는 만들지 않는다(정정 2026-09-09, `S3` 폐기). CLI 에이전트 경로는 채팅 UI의 대체가 아니라 데스크톱이 더하는 진입점이다. 대안 — 데스크톱에서 채팅 입력을 숨기고 "터미널의 CLI 에이전트로 질문" 안내만 두는 것(서버 LLM 비용 0) — 은 웹 변경이 필요해 되돌리기 어려우므로 기본값으로 잡지 않았다. 사용자가 원하면 `S9`에서 켠다 | 미결 (기본값 적용) → 정정 2026-09-09 |
| Q23 | 세션당 동시성: 답변 없는 USER 메시지가 남아 있을 때 새 검색 요청을 어떻게 하는가 | S1, S2 | 409 `CHAT_TURN_IN_PROGRESS`. 데스크톱은 다음 질문 전에 저장 재시도, 서버는 일정 시간(`chat.turn-timeout`, 기본 PT5M) 지난 미완 턴을 진행 중으로 보지 않고 다음 질문을 받는다. 정정(2026-09-07, `S1`): "실패로 표시"는 컬럼을 추가하지 않는다(V14 범위 유지). 만료된 턴은 그대로 남고, 그 턴의 저장 시도는 `S2`가 마지막 메시지 검사(`CHAT_TURN_MISMATCH`)로 거절한다. 판정은 DB 기준(세션의 마지막 메시지가 USER이고 생성 시각이 timeout 안)이며, 검색 요청을 처리하는 동안은 SSE와 같은 `ActiveChatStreamRegistry`로 세션을 잠가 같은 세션의 동시 진입을 막는다(잠금 충돌도 409 `CHAT_TURN_IN_PROGRESS`). 브라우저 단독 SSE 경로에는 DB 턴 검사를 추가하지 않는다(Q22 무변경) | 기본값 확정(2026-09-07, `S1`) |
| Q24 | 저장 API의 근거 검증 범위 | S2 | 현행 Workspace JOIN(`INSERT…SELECT`)만 재사용한다. 서버가 돌려준 8개의 부분집합인지는 검증하지 않는다(후보 집합 보관이 필요해 되돌리기 어렵다) | 미결 (기본값 적용) |
| Q25 | 클라이언트가 만든 답변을 데이터에서 구분하는가 | S1, S2 | `chat_messages.generated_by`(`SERVER` 또는 `CLIENT`) 컬럼을 추가한다. 검토 문서 8절 4번(무결성 정책)의 최소 형태다 | 미결 (기본값 적용) |
| Q26 | 데스크톱의 첫 조각 타임아웃 | S3 | main 30초(현행 SSE 타임아웃과 같은 값). 이후 조각 간 간격은 제한하지 않는다 | 무효(2026-09-09, `S3` 폐기) |
| Q27 | 사용자 LLM 엔드포인트 허용 범위 | S3 | `https:` 전체 + `http://localhost`·`http://127.0.0.1`. 네비게이션 허용 목록과 별개 목록이다. 고정 목록(Anthropic·OpenAI·LM Studio 기본 포트)은 로컬 모델 포트가 제각각이라 되돌릴 일이 많다 | 무효(2026-09-09, `S3` 폐기 — 데스크톱은 LLM 엔드포인트를 갖지 않는다) |
| Q28 | 청크 8개 응답의 컨텍스트 상한 | S1 | 서버가 `max-context-characters=12000`에 맞춰 청크 본문을 잘라 응답한다(8 × 1,200자 + 헤더). 데스크톱은 자르지 않는다 | 미결 (기본값 적용) |
| Q29 | 같은 페이지의 청크가 8개 안에 여럿 들어가는 것을 허용하는가 | S1 | 허용한다. 사용자 지시가 "유사도가 높은 청크 8개"이며, 페이지당 상한은 그 위에 얹는 후속 결정이다 | 기본값 확정(2026-09-07, 사용자 지시) |
| Q30 | 검색 API에서 USER 메시지 저장과 검색의 순서 | S1 | 검색을 먼저 하고 USER 메시지는 검색 결과가 나온 뒤 한 트랜잭션에서 저장한다(READY면 USER만, 아니면 USER + 안내 ASSISTANT). 임베딩 provider 오류로 검색이 실패하면 아무것도 저장하지 않으므로 Q23의 timeout 동안 세션이 잠기지 않는다. 검색 질의는 저장 전 이력(직전 4개)과 현재 질문으로 조립하므로 결과는 같다. 외부 HTTP 호출을 트랜잭션 안에 두는 대안은 커넥션을 점유해 되돌리기 어렵다 | 기본값 확정(2026-09-07, `S1`) |
| Q31 | 검색 API가 검색 실패(임베딩·설정 오류)를 어떤 코드로 돌려주는가 | S1 | `SearchException`의 코드를 그대로 500으로 돌려준다(`SEARCH_PROVIDER_FAILED`·`SEARCH_CONFIGURATION_INVALID`). `SEARCH_IMPORT_NOT_READY`만 409 `CHAT_DOCUMENTS_NOT_READY`로 바꾼다. SSE 경로처럼 `LLM_STREAM_FAILED`로 뭉개지 않는 이유는 데스크톱이 코드·문구를 그대로 중계하고, 이 경로에는 LLM이 없기 때문이다 | 기본값 확정(2026-09-07, `S1`) |
| Q32 | V14 이전 `search_references` 행의 `chunk_index` 값 | S1 | 0으로 채운다. V13은 페이지 단위 저장이라 어느 청크였는지 남아 있지 않고, 유일 키 `(message_id, imported_page_id, chunk_index)`는 기존 행이 페이지당 1개라 충돌하지 않는다 | 기본값 확정(2026-09-07, `S1`) |
| Q33 | 검색 API 응답 `content`를 자르는 기준(Q28 구체화) | S1 | 현행 `SearchContext.groundingPrompt`와 같은 누적 예산을 쓴다 — 규칙 문장 + `[근거 문서 n]` 헤더 + 본문을 순서대로 더해 `max-context-characters`(12,000)를 넘는 부분을 자르고, 예산이 다한 뒤의 청크는 응답에서 뺀다. 데스크톱이 같은 형식으로 조립하면 프롬프트 길이가 서버 SSE 경로와 같아진다. 저장용 `references()`는 자르지 않는다(현행 유지) | 기본값 확정(2026-09-07, `S1`) |
| Q34 | 임베딩 provider를 무엇으로 바꾸는가(LM Studio Qwen `openai-compatible` 유지 vs Gemini Embedding vs 서버 내장 모델) | B5 | `llm.embedding.provider=gemini` 분기를 추가하고 `gemini-embedding-001`을 Gemini API `POST {llm.gemini.base-uri}/v1beta/models/{model}:batchEmbedContents`(`x-goog-api-key`)로 호출한다. 키는 `GEMINI_API_KEY` 환경변수로만 주입한다(`llm.gemini.api-key`). 호출은 Q20과 같이 JDK `HttpClient` 직접 호출이며 `search/infrastructure/gemini/` 안에서 끝난다. `openai-compatible`·`fake` 분기는 지우지 않아 되돌리기는 설정 한 줄이다 | 기본값 확정(2026-09-08, 사용자 지시 `임베딩 모델을 gemini 임베딩 1로 가자`) |
| Q35 | Gemini 응답 차원: 모델 기본 3,072 vs V13 계약 1,024 | B5 | 1,024를 유지한다. 요청에 `outputDimensionality=llm.embedding.dimensions`(1024)를 넣고, `gemini-embedding-001`은 3,072 미만 벡터를 정규화해 주지 않으므로 어댑터가 L2 정규화한 뒤 돌려준다. 3,072는 pgvector `vector` 타입 인덱스 상한(2,000차원)을 넘어 `halfvec` 전환 + 마이그레이션이 필요하고, 차원 변경은 전 Workspace 재색인을 동반해 되돌리기 어렵다 | 기본값 확정(2026-09-08, `B5`) |
| Q36 | Gemini `taskType`을 쓰는가 | B5 | 쓴다. 색인은 `RETRIEVAL_DOCUMENT`, 질의는 `RETRIEVAL_QUERY`. 이를 위해 `DocumentEmbeddingClient.embed(texts, task)`로 용도(`EmbeddingTask.DOCUMENT`/`QUERY`)를 넘기고 `fake`·`openai-compatible`은 무시한다. Gemini 문서가 RAG에 권장하는 조합이며, 안 쓰는 쪽으로 되돌리려면 어댑터에서 필드 하나만 빼면 된다 | 기본값 확정(2026-09-08, `B5`) |
| Q37 | Gemini 배치 호출 단위 | B5 | `batchEmbedContents` 한 번에 `llm.search.embedding-batch-size`(**16**)건. 처음 64로 시작했으나 2026-09-08 로컬 실측에서 1,300자 텍스트 32·64건 요청이 429 `RESOURCE_EXHAUSTED`로 거절돼(U25) 16으로 내렸다(8·16건은 200, 8건×4회 연속도 200 — 요청 하나의 크기 상한이지 분당 누적이 아니었다). 21페이지·28만 자는 실제 611청크·39회 호출이다. 다시 429가 나오면 값만 더 내린다. 질의는 1건짜리 배치다 | 기본값 확정(2026-09-08, `B5`) → 정정 2026-09-08(실측, 64 → 16) |
| Q38 | Gemini 오류를 어떤 검색 코드로 매핑하는가 | B5 | HTTP 401·403 → `SEARCH_CONFIGURATION_INVALID`, 그 외 비 2xx(400·429·5xx)·응답 건수/차원 불일치·본문 파싱 실패 → `SEARCH_PROVIDER_FAILED`. 429·503은 색인 경로에서만 Q42대로 재시도하고, 재시도가 다 실패하면 같은 코드다(색인은 import 실패로 남고 기존 스냅샷 유지). 질의 경로는 재시도 없이 Q31대로 500. `gemini`인데 키가 비면 기동 시점에 `SEARCH_CONFIGURATION_INVALID`로 실패한다(Anthropic 어댑터와 같은 fail-fast). 오류 본문은 `error.status`만 로그에 남긴다 | 기본값 확정(2026-09-08, `B5`) → 정정 2026-09-08(429·503 재시도는 Q42) |
| Q39 | 기존 Qwen·fake 임베딩으로 색인된 `search_document_chunks`를 어떻게 하는가 | B5 | 자동 변환·마이그레이션을 하지 않는다. 임베딩 공간이 달라 provider를 켠 뒤에는 Workspace 소유자가 Notion 동기화를 다시 실행해 재색인해야 하며, 그 전까지 벡터 검색 점수는 무의미하다(키워드 경로만 유효). 청크별 모델 식별자 컬럼은 되돌리기 어려워 넣지 않는다. 운영 전환 절차는 GB 게이트 체크박스로 둔다 | 기본값 확정(2026-09-08, `B5`) |
| Q40 | 어느 환경에서 `gemini`를 켜는가 | B5 | `application.properties` 기본값은 `fake` 유지(테스트·CI가 외부 호출 없이 통과). 로컬은 `application-local.properties`(gitignore 대상)에 `llm.embedding.provider=gemini`, dev·prod는 서버 env 파일에 `LLM_EMBEDDING_PROVIDER=gemini`·`GEMINI_API_KEY`를 사람이 넣는다(배포 워크플로우는 초대 키 2개만 주입한다 — `deploy-backend-dev.yml:244-245`) | 기본값 확정(2026-09-08, `B5`) |
| Q41 | 서명은 유효하지만 회원이 없는 액세스 토큰(탈퇴·DB 초기화)을 어떻게 처리하는가 | C1 | `JwtAuthenticationFilter`가 토큰 검증 뒤 `MemberService.existsById`로 회원 존재를 요청마다 확인하고, 없으면 인증하지 않아 기존 진입점이 401 `UNAUTHENTICATED`를 준다. 발급·클레임은 바꾸지 않는다. 확인 전에는 `/auth/me`가 200을 줘 `AuthGuard`가 통과시키고 이후 요청이 404·500으로 흩어져 로그아웃 진입점(GNB)에도 못 갔다(2026-09-08 로컬 DB 초기화 실측: `/auth/me` 200, `POST /workspaces` 500). `/auth/me`에서만 확인하는 대안은 첫 진입 뒤 요청이 여전히 500이라 택하지 않았다. `A6`가 서버 세션 조회를 붙이면 이 확인이 그 조회로 대체된다 | 기본값 확정(2026-09-08, 사용자 지시 `백엔드도 401 주도록 고쳐`) |
| Q42 | Gemini 429·503을 재시도하는가, 어떻게 | B5 | 색인(`EmbeddingTask.DOCUMENT`)에서만 HTTP 429·503을 재시도한다. 지연은 `llm.gemini.retry-initial-delay`(PT5S)에서 2배씩(5·10·20·40초, 상한 60초) 늘리며 `llm.gemini.retry-max-attempts`(6)회까지 보낸다 — 5·10·20·40·60초, 누적 135초. 처음 5회(누적 75초)로 두었으나 실측(실행 #4)에서 무료 티어 6주기 중 1주기가 75초 안에 회복하지 못해 import가 실패해 6회로 올렸다. 시도마다 WARN 로그(status·attempt·delay)를 남기고 본문은 `error.status`만 적는다. 질의(`QUERY`)는 재시도하지 않는다(사용자가 최대 75초를 기다리게 되므로 Q31대로 바로 500). 4xx(400·401·403 등)·IO 오류·차원 불일치는 재시도하지 않는다. 무료 티어 21페이지(611청크)는 재시도 포함 10분 안팎이며 heartbeat(30초)·stale(1시간) 안이다. 유료 티어에서는 같은 양이 48초(39회 호출, 429 없음)다. 되돌리려면 `retry-max-attempts=1` | 기본값 확정(2026-09-08, 로컬 실측 — 배치 16에서도 3번째 배치가 429) |
| Q43 | 데스크톱 사용자 LLM 요청의 파라미터와 URL 조립 | S3 | 사용자 모델이 무엇이든 400이 나지 않도록 **최소 필드만** 보낸다. Anthropic: `POST {baseUrl}/v1/messages`(`baseUrl`이 이미 `/v1`로 끝나면 `/messages`만 붙인다), `x-api-key`·`anthropic-version: 2023-06-01`, 본문 `{model, max_tokens: 4096, stream: true, system, messages}` — `temperature`·`thinking`·`output_config`는 보내지 않는다(서버 어댑터의 `output_config.effort`는 Opus 5 전용이라 사용자 모델에 강제하지 않는다). OpenAI 호환: `POST {baseUrl}/chat/completions`, `Authorization: Bearer`는 키가 있을 때만, 본문 `{model, messages: [{role: system}, …], stream: true}` — `max_tokens`·`temperature`는 보내지 않는다(일부 서비스가 `max_tokens`를 거부한다). `messages`는 세션 이력 전체를 `user`/`assistant`로 옮기고 같은 역할이 연속되면 한 turn으로 합친다(서버 Anthropic 매퍼와 같은 규칙). 마지막이 `user`가 아니면 현재 질문을 덧붙인다 | 무효(2026-09-09, `S3` 폐기) |
| Q44 | 사용자 LLM 설정이 없을 때 `llm.getSettings`가 무엇을 돌려주고, `setSettings`는 무엇을 검사하는가 | S3, S4 | 저장된 설정이 없으면 `{provider: "openai-compatible", baseUrl: "", model: "", hasApiKey: false}`를 돌려준다(반환형을 nullable로 바꾸지 않는다 — 기획서 4.4 계약 유지). `setSettings`는 provider가 두 값 중 하나, `baseUrl`이 Q27 허용 범위(`https:` 전체 + `http://localhost`·`http://127.0.0.1`), `model`이 1~200자일 때만 저장하고 아니면 reject한다. `apiKey`가 빈 문자열이거나 없으면 기존 키를 유지하고, 값이 있으면 `safeStorage`로 바꿔 쓴다. `clearApiKey`만 키를 지운다. 질문 시점에 `baseUrl`·`model`이 비었거나 `anthropic`인데 키가 없으면 `LLM_CONFIGURATION_INVALID`(문구에 설정 화면 안내). `openai-compatible`은 키 없이 호출한다(로컬 모델) | 무효(2026-09-09, `S3` 폐기 — preload `llm`은 제거된다) |
| Q45 | 데스크톱 main의 요청 수명 세부: 같은 세션 중복 요청, 빈 답변, 서버 호출 실패·타임아웃, 저장 실패 뒤 재시도 | S3 | 같은 세션에 진행 중 요청이 있으면 서버와 같은 코드 `CHAT_TURN_IN_PROGRESS`를 `error`로 보낸다(별도 코드를 만들지 않는다). LLM 스트림이 조각 0개로 끝나면 저장하지 않고 `LLM_STREAM_FAILED`. 서버 검색·이력·저장 호출은 각 30초(현행 SSE 타임아웃과 같은 값) 안에 응답 헤더가 와야 하며, 넘기거나 네트워크 오류면 `LLM_STREAM_FAILED`, 저장된 액세스 토큰이 없으면 `UNAUTHENTICATED`, HTTP 오류 본문을 읽지 못하면 웹 SSE 경로와 같은 `UNKNOWN`. 서버 HTTP 오류 본문의 `{code, message}`는 그대로 중계한다. 저장이 실패하면 답변·근거를 세션별로 메모리에 한 건 보관했다가 그 세션의 다음 질문 직전에 한 번 재시도하고(Q23), 재시도 결과와 무관하게 보관을 비운다(턴이 만료됐으면 `S2`가 `CHAT_TURN_MISMATCH`로 거절하고 서버는 다음 질문을 받는다). 취소는 보관하지 않는다(기획서 6.4 "저장 없음") | 무효(2026-09-09, `S3` 폐기). 서버 호출 30초·오류 본문 그대로 중계 규칙은 Q49로 옮겼다 |
| Q47 | 데스크톱 MCP 서버의 전송·바인딩·포트·프로세스 | S8 | MCP Streamable HTTP(스펙 2026-07-28, 단일 엔드포인트 `POST http://127.0.0.1:<port>/mcp`), `127.0.0.1`에만 바인딩(스펙 SHOULD). 기본 포트 `47871`, 충돌하면 기동 실패를 설정 화면에 표시하고 사용자가 `agent.setPort`로 바꾼다(임의 포트는 CLI 설정 파일의 URL이 매번 깨진다). 서버는 Electron `utilityProcess`(Node 환경, renderer 아님)에서 돌고 main과 `MessagePort`로 통신한다 — HTTP 서버가 죽어도 창은 살고, Fuse·sandbox 정책과 무관하다. 도구 실행(서버 API 호출)은 전부 main이 하며 MCP 프로세스는 서버 액세스 토큰을 모른다. 앱 시작 시 띄우고 종료 시 닫는다. 구현은 공식 TypeScript SDK(`@modelcontextprotocol/sdk`)의 Streamable HTTP 서버 전송을 쓰며(LLM SDK가 아니다, 기획서 9.2), 클라이언트가 구 개정판(initialize 세션)으로 오면 SDK의 하위 호환 절차를 따른다(U28). 대안 stdio 브리지(CLI가 `knot-mcp` 실행 파일을 자식 프로세스로 띄워 앱과 로컬 소켓으로 통신)는 별도 실행 파일 배포·PATH 등록이 필요해 미채택 | 기본값 확정(2026-09-09, `S8`) → 정정(2026-09-09 실측): `@modelcontextprotocol/sdk` 1.30.0의 `LATEST_PROTOCOL_VERSION`은 `2025-11-25`이며 "2026-07-28 무세션 개정판"은 SDK에 없다. 구현은 무상태(`sessionIdGenerator: undefined`)로 요청마다 `McpServer`+전송을 새로 만드는 SDK 예시 방식이며, initialize 세대 클라이언트(Claude Code 2.1.263)는 세션 ID 없이 그대로 붙었다. GET SSE 스트림은 405(무상태에서는 서버 발신 알림 없음) |
| Q48 | 로컬 MCP 서버의 인증·요청 검사 | S8 | 앱이 첫 실행 시 256-bit 난수 **연결 토큰**을 만들어 `userData/agent-bridge.json`(0600, `{port, token, issuedAt}`)에 두고, CLI 등록 스니펫에 `Authorization: Bearer <토큰>`으로 넣어 준다(세 CLI 모두 헤더 설정을 지원 — 지식 §6.8). 요청마다 대조하고 불일치·부재는 401. `Origin` 헤더가 **있으면** 403(스펙 MUST — CLI는 Origin을 보내지 않고, 보내는 것은 브라우저이므로 DNS 리바인딩 차단), `Host`가 `127.0.0.1:<port>`·`localhost:<port>`가 아니면 403. 이 토큰은 Knot 자체의 로컬 비밀이지 LLM 자격증명이 아니다(계약 3번). 재발급은 설정 화면(`agent.rotateToken`)에서 하고, 토큰은 renderer로 내려보내지 않고 main이 클립보드에 쓴다(`agent.copyRegistration`). MCP 스펙의 OAuth 2.1 인증은 로컬 단일 사용자에 과해 쓰지 않는다. `safeStorage`로 암호화하지 않는 이유는 같은 토큰이 CLI 설정 파일(`.mcp.json`·`config.toml`·`settings.json`)에 평문으로 있어 암호화의 이득이 없기 때문이다 | 기본값 확정(2026-09-09, `S8`) |
| Q49 | 도구 집합·입력·결과 모양·서버 호출 규칙 | S8, S7 | 도구는 셋. `list_workspaces()` → main이 `GET /api/v1/workspaces`(Bearer) → `[{id, name}]`(정정 2026-09-09: 현행 목록 응답 `backend/src/main/java/com/knot/backend/workspace/presentation/dto/response/WorkspaceListItemResponse.java:7-10`에 `role`이 없다. `S8`은 서버가 주는 `id`·`name`만 돌려주고, `role`은 서버가 노출하면 그때 더한다 — 목록 API 변경은 `S7` 범위 밖). `search_documents({query: 1~10,000자, workspaceId?: number})` → main이 `POST /api/v1/workspaces/{id}/search`(`S7`) → 결과 `content[0].text`는 서버가 준 `groundingRules` + `[근거 문서 n] 제목/문서 ID/문서 링크/청크 n/내용` × ≤8(현행 `SearchContext.groundingPrompt`와 같은 형식이라 에이전트가 근거 규칙을 그대로 읽는다), `structuredContent`에 `{status, chunks[]}` 원본. `workspaceId`가 없고 워크스페이스가 하나면 그것, 여럿이면 `isError: true`로 목록을 돌려주고 `list_workspaces`를 안내한다. READY가 아니면(`NO_RESULT`·`NEEDS_CLARIFICATION`) `fallbackAnswer`를 텍스트로 돌려준다. `show_answer`는 Q51(`S10`). 서버 호출은 각 30초 안에 응답 헤더, 저장된 액세스 토큰이 없으면 `isError`로 "Knot 앱에 로그인하세요", 서버 HTTP 오류는 `{code, message}`를 그대로 텍스트로. 네트워크 오류·타임아웃은 main이 만든 코드 `KNOT_API_UNREACHABLE`("Knot 서버에 연결하지 못했어요"), HTTP 오류 본문을 읽지 못하면 웹 SSE 경로와 같은 `UNKNOWN`, 응답 모양이 계약과 다르면 `KNOT_API_MALFORMED`로 `isError` 텍스트에 싣는다(추가 2026-09-09, `S8` — 개정 전 Q45의 `LLM_STREAM_FAILED`는 LLM이 없는 경로라 쓰지 않는다). 도구 결과에 서버 토큰·연결 토큰을 싣지 않는다. 동시 도구 호출은 4개까지, 넘으면 `isError`("잠시 뒤 다시"). main은 도구 호출마다 requestId·도구 이름·workspaceId·상태·지연 ms만 INFO 로그로 남기고 질문·본문은 남기지 않는다 | 기본값 확정(2026-09-09, `S8`) |
| Q50 | 스킬(사용 안내)과 CLI 등록 스니펫을 어떻게 제공하는가 | S9, S8 | 스킬은 `desktop/resources/skills/knot/SKILL.md` 하나이며 Agent Skills 표준 필드(`name`·`description`·`license`·`compatibility`·`metadata`·`allowed-tools`)만 쓴다 — 세 CLI가 모두 이 표준을 따른다(지식 §6.8). 본문은 "팀 문서·회의록·결정 근거 질문이면 `search_documents`를 먼저 호출하고, 결과 앞머리의 근거 규칙 문장을 지켜 답하며, 출처를 `문서 링크`로 표시"하는 안내와 `show_answer` 사용 조건. 설치 위치는 CLI별로 — Claude Code `~/.claude/skills/knot/`, Codex CLI·Gemini CLI `~/.agents/skills/knot/`(두 CLI 모두 이 경로를 읽는다). 앱은 사용자 홈에 파일을 자동으로 쓰지 않고 설정 화면이 복사 명령을 보여 준다(사용자 홈 디렉터리 변경은 되돌리기 어렵다). MCP 등록 스니펫 — Claude Code: `claude mcp add --transport http knot http://127.0.0.1:<port>/mcp --header "Authorization: Bearer <토큰>"`, Codex CLI: `~/.codex/config.toml`에 `[mcp_servers.knot]` `url = "http://127.0.0.1:<port>/mcp"` `http_headers = { Authorization = "Bearer <토큰>" }`, Gemini CLI: `gemini mcp add --transport http --scope user --header "Authorization: Bearer <토큰>" knot http://127.0.0.1:<port>/mcp`. MCP 서버 `instructions`에도 스킬 본문 요약을 넣는다(노출 여부는 U30) | 기본값 확정(2026-09-09, `S9`) |
| Q51 | (선택) `show_answer` 도구가 앱 화면에 답을 어떻게 보이는가 | S10 | 입력 `{workspaceId, question, answer, sources: [{importRunId, importedPageId, chunkIndex, score}] ≤8, sessionId?}`. `sessionId`가 없으면 main이 `POST /api/v1/workspaces/{id}/conversations`로 세션을 만들고(제목은 질문 앞 50자), `POST /api/v1/conversations/{sessionId}/turns`(`S2`)로 USER + ASSISTANT(`generated_by=CLIENT`) + 근거를 한 번에 저장한 뒤 `{sessionId, messageId}`를 돌려준다(에이전트가 같은 터미널 대화에서 다음 호출에 `sessionId`를 넘기면 같은 세션에 이어진다). 저장 뒤 main이 창을 앞으로 가져오고 preload `onDeepLink({type: "chat", workspaceId, sessionId})`로 SPA를 그 세션으로 보낸다(기존 계약 재사용, `A8` 딥링크와 같은 이벤트). 데스크톱 전용 화면은 만들지 않는다(기획서 2.2). 근거는 `search_documents`가 돌려준 값만 넘기도록 스킬이 안내하고, 서버는 Workspace JOIN으로만 검증한다(Q24) | 기본값 확정(2026-09-09, `S10`) → 착수 가정 추가(2026-09-09 구현): `answer` 상한 100,000자(계약에 없는 방어값, `MAX_ANSWER_LENGTH`), `sources` 각 항목은 `importRunId`·`importedPageId` 양의 정수·`chunkIndex` 0 이상·`score` 유한 수(0~1 클램프는 서버), `question`은 검색과 같은 1~10,000자. 세션 제목은 질문의 공백을 접은 앞 50자. 창이 없으면 새로 열고 `webContents.isLoading()`이면 `did-finish-load` 뒤에 `knot:deep-link`를 보낸다. 도구 결과 텍스트는 다음 호출에 넘길 `sessionId`를 안내하고 `structuredContent {workspaceId, sessionId, messageId, userMessageId}`. 저장 실패면 창을 건드리지 않는다 |
| Q46 | 새 창(`window.open`·`target=_blank`)을 어디까지 허용하는가 — Notion 로그인 화면이 IdP 인증을 팝업으로 열고, 팝업 검증 페이지가 `window.opener` 없이는 스스로 닫혀 외부 브라우저로는 통과할 수 없다 | A1 (U2·U27) | 새 창 URL의 오리진이 네비게이션 허용 목록 안이면 **자식 창으로 허용**하고, 밖이면 지금처럼 `deny` + 검증된 `https:`만 외부 브라우저. 자식 창은 `overrideBrowserWindowOptions.webPreferences`로 `sandbox`·`contextIsolation`·`nodeIntegration: false`·`webviewTag: false`를 다시 명시하고 preload를 주지 않는다(Electron은 보안 관련 webPreferences만 부모에서 상속하고 preload는 상속하지 않는다 — `DidCreateWindowDetails.options` 문서). 자식 창은 opener의 세션(`persist:knot`)을 그대로 쓰고(Chromium이 opener의 BrowserContext로 자식 WebContents를 만든다) `web-contents-created`로 같은 네비게이션·리다이렉트 정책을 받으므로 허용 목록 밖으로는 못 나간다. 기획서 8절 #14는 "새 창 생성 제한"이고 공식 항목도 disable **or limit**이므로 목록 한정 허용은 불변 계약 4번을 낮추지 않는다. 팝업이 지나가는 IdP 오리진(`login.microsoftonline.com`·`appleid.apple.com` 실측, `login.live.com` 미관측)은 허용 목록에 둔다. 되돌리려면 `resolveWindowOpen`을 항상 deny로 | 기본값 확정(2026-09-08, `A1` 앱 로그 + curl 302 실측) |

**규칙**: 미결이라는 이유로 멈추지 않는다. 기본값으로 구현하고 적용한 기본값을 PR 본문에 적는다. 다음 두 가지만 예외로 사람에게 알린다 — (1) 실제 비용 지출·외부 계정 개설이 그 작업에 **실제로 필요해진 시점**(Q1·Q2·Q6), (2) 개인정보·법적 판단이 필요한 시점. 알린 뒤에도 그 항목 없이 가능한 범위는 계속 구현한다. 표에 없는 결정이 생기면 되돌리기 쉬운 쪽을 기본값으로 잡아 행을 추가하고 진행한다.

## 6. 실측 대기 (코드로 확인해야 아는 것)

| ID | 확인할 것 | 어디서 해소 | 상태 |
| --- | --- | --- | --- |
| U1 | GitHub이 Electron 창(embedded UA) 로그인을 경고·차단하는가 | A1 | 해소(2026-09-07): 종단 성공. 비밀번호 로그인 + 2FA(webauthn 화면 → 모바일 승인)까지 앱 창 안에서 차단·경고 없이 끝나고 `dev.knoted.kr/`로 복귀했다. GitHub은 embedded UA를 막지 않는다 |
| U20 | GitHub 소셜 로그인(Google) 경유 도메인이 허용 목록 밖이라 로그인이 깨지는가 | A1 | 해소(2026-09-07): `github.com/login`에서 Google 버튼을 누르면 `github.com/sessions/social/google/initiate` → `accounts.google.com/o/oauth2/v2/auth`로 나가고, 후자가 목록 밖이라 외부 브라우저로 빠졌다. Google 인증만 다른 브라우저에서 끝나 GitHub 소셜 `state` 세션이 갈리고 콜백이 "We could not validate the response from your social login provider"로 실패한다. `accounts.google.com`을 목록에 추가 |
| U21 | `accounts.google.com`이 Electron embedded UA를 `disallowed_useragent`로 거부하는가 | A1 | 미확인(2026-09-07 재측정 시 Google 버튼을 쓰지 않아 이 홉을 지나지 않았다). Google은 임베디드 브라우저 OAuth를 정책으로 막는다. 거부되면 허용 목록 확장으로는 해결되지 않고 `A6`·`A7`(시스템 브라우저 로그인)이 유일한 경로가 된다 |
| U22 | 로컬 실 백엔드(`:8080`) GitHub OAuth 체인이 셸 안에서 끝까지 지나가는가 | A1 | 해소(2026-09-07): `local` API 오리진을 `:8080`으로 고친 뒤 4홉(`:8080/oauth2/authorization/github` → `github.com/login/oauth/authorize` → `:8080/login/oauth2/code/github?code=` → `:3000/#access_token=`)이 차단 0건으로 지나갔다. 고치기 전에는 1홉에서 `will-navigate` 차단 후 `shell.openExternal`도 `http:`라 거부해 버튼이 무반응이었다 |
| U2 | Notion OAuth 302 체인의 실제 도메인 | A1 | 부분 해소(2026-09-08): 1홉 `api.notion.com/v1/oauth/authorize`(SPA `window.location.assign`, `will-navigate` 허용) → 2홉 302 `app.notion.com/install-integration?response_type=code&client_id=…&redirect_uri=…&state=…&owner=user`이 목록 밖이라 `will-redirect`에서 차단됐다. 셸이 `shell.openExternal`로 외부 브라우저에 열어 동의를 거기서 마치면 백엔드 연결은 성공하지만(로컬 DB `content_source_connections` 행 생성 확인) `?result=connected` 복귀도 외부 브라우저로 가고 앱 창의 SPA는 `isRedirecting`이 풀리지 않아 연결 버튼이 무한 로딩이 된다. `app.notion.com`을 목록에 추가. 2026-09-08 23:21 재측정: 추가 뒤 동의 화면까지의 4홉(`install-integration` → `api/v3/sessionSync` → `sessionSyncCallback?status=unauthenticated` → `install-integration&session_sync_attempted=1`)이 모두 `app.notion.com`이라 차단 없이 지나갔다. 미로그인 상태라 Notion 로그인 화면이 떴고, 그 IdP 버튼이 여는 팝업이 새 창 전면 거부에 막혔다(U27·Q46). 해소(2026-09-08 23:41): 새 창 정책 개정 뒤 Microsoft 로그인 팝업 → 동의 → `:8080/api/v1/notion/oauth/callback` → `:3000/workspace/6/notion-connection?result=connected`까지 차단 0건으로 앱 창 안에서 끝났다. 체인의 도메인은 `api.notion.com`·`app.notion.com`·`login.microsoftonline.com`뿐이며 `www.notion.so`·`login.live.com`은 미관측 |
| U9 | Forge `maker-squirrel`을 macOS 호스트에서 빌드할 수 있는가(문서 상충) | A3 (Windows 러너 사용 시 무관) | 미확인 |
| U10 | `@electron/notarize`가 자동 staple 하는가 | A3 (`xcrun stapler validate`로 검증) | 미확인 |
| U11 | `update-electron-app`의 draft/prerelease 처리 | A4 | 미확인 |
| U14 | Spring `CsrfFilter.DEFAULT_CSRF_MATCHER` 7.1.1 시그니처 | A6 | 무효(2026-09-06): `C1`이 CSRF를 제거해 매처를 건드릴 일이 없어졌다 |
| U17 | `safeStorage.isEncryptionAvailable()`이 미서명 로컬 빌드(macOS)에서 true인가 | C3 | 해소(2026-09-07): true. `local` 빌드로 실 로그인한 뒤 `~/Library/Application Support/Knot/auth.bin`이 419바이트·`0600`으로 생겼고, 선두가 Chromium OSCrypt의 `v10` 프리픽스라 평문 JWT가 아니다(`grep eyJ` 0건). 메모리 대체 경로는 남겨 둔다 |
| U15 | Cloudflare `_headers` CSP와 Emotion 인라인 스타일 충돌 | W3 | 해소(2026-09-06). `style-src 'unsafe-inline'`을 넣으면 충돌 없음. `wrangler dev`로 `dist`를 띄워 실측 — CSP 위반 0건, Emotion `<style>` 2개 적용, jsDelivr Pretendard CSS·woff2 로드, API 요청은 CSP가 아닌 DNS 미해소로만 실패 |
| U16 | `will-navigate`가 서버 302 리다이렉트에서 발화하지 않는다는 전제(기획서 4.5 정정)를 실제 OAuth 체인으로 확인 | A1 | 해소(2026-09-06): GitHub OAuth 3홉 중 SPA가 시작한 1홉만 `will-navigate`, 서버 302인 나머지 2홉은 `will-redirect`로 들어왔다. `will-navigate`만 검사했다면 `github.com`으로 넘어가는 두 홉이 허용 목록 검사를 거치지 않았다 |
| U18 | `SearchContext.GROUNDING_INSTRUCTION`(약 330자)이 Opus 5 캐시 최소 프리픽스 512 토큰을 넘는가 | B1 → B2 | 미확인. 최소 프리픽스보다 짧으면 `cache_control`을 붙여도 조용히 캐시되지 않으므로 `B1`은 `system`을 문자열 하나로 보내고 캐시 표시를 넣지 않는다. `count_tokens`로 실측해 넘으면 `B2`에서 규칙 블록에 `cache_control`을 붙인다 |
| U23 | 청크 8개(최대 12,000자) + 규칙을 넣은 프롬프트가 LM Studio `qwen/qwen3.6-27b`의 컨텍스트·TTFT 5초에 들어오는가 | S5 | 무효(2026-09-09, `S5` 재정의 — 기준은 LM Studio가 아니라 Claude Code + `search_documents`) |
| U24 | Electron main(Node 22 `fetch`/undici)에서 OpenAI 호환·Anthropic SSE 스트리밍을 끊김 없이 읽고 `AbortController`로 취소되는가 | S3 | 무효(2026-09-09, `S3` 폐기 — 데스크톱은 LLM 스트림을 읽지 않는다). 2026-09-08 vitest 실측(실제 `http.createServer` SSE·`AbortController`)은 기록으로만 남긴다 |
| U25 | Gemini `batchEmbedContents` 요청당 최대 건수와 입력 2,048토큰 초과 시 동작(조용한 절단 vs 400) | B5 → GB | 부분 해소(2026-09-08): 로컬 키(무료 티어)로 1,300자 텍스트를 8·16건 보내면 200, 32·64건이면 429 `RESOURCE_EXHAUSTED`(본문 `details`에 `QuotaFailure` 없이 `Help`만). 8건×4회 연속 호출은 모두 200이라 분당 누적 한도가 아니라 요청 하나의 크기 상한이다. 로컬 Notion 동기화(21페이지·28만 자)가 배치 64에서 첫 배치부터 429로 실패해 Q37을 16으로 정정했다. 이어서 16건 배치를 연속으로 보내면 3번째(누적 32건·약 4만 자)가 429이고 56초 뒤 200으로 회복돼, 무료 티어는 **분당 약 32청크** 한도다(배치 16으로 내려도 재시도 없이는 3번째 배치에서 실패 — Q42). 재시도 5회(누적 75초)를 넣고 돌린 실행 #4는 6주기 중 1주기가 회복하지 못해 412초 만에 실패했다. 결제 계정을 연결(유료 티어)한 뒤에는 64건 배치도 200이고, 실행 #5가 611청크·39회 호출을 48초에 429 없이 끝냈다. 2,048토큰 초과 시 동작은 아직 미확인 |
| U26 | `gemini-embedding-001`이 REST 요청 최상위 `outputDimensionality`·`taskType`으로 실제 1,024차원 응답을 주는가(API 레퍼런스가 두 필드에 `EmbedContentConfig`로 옮기라는 deprecated 표시를 달았지만 가이드의 REST 예시는 최상위 필드를 쓴다) | B5 → GB | 해소(2026-09-08): 최상위 `outputDimensionality=1024`·`taskType=RETRIEVAL_DOCUMENT`로 보낸 `batchEmbedContents`가 `values` 1,024개를 돌려줬고, 그 벡터의 L2 norm은 0.6165로 정규화돼 있지 않았다(Q35의 어댑터 측 정규화가 필요함을 확인) |
| U28 | 세 CLI(Claude Code·Codex CLI·Gemini CLI)가 `http://127.0.0.1:<port>/mcp`의 Streamable HTTP 서버에 실제로 붙는가 — 어느 스펙 개정판(2026-07-28 무세션 vs 2025-11-25 initialize)으로 오는지, `Authorization` 헤더를 그대로 보내는지, `Origin` 헤더를 안 보내는지 | S8 (Q47·Q48) | 부분 해소(2026-09-09): Claude Code 2.1.263이 `claude mcp add --transport http … --header`로 등록한 뒤 `claude mcp list`에 ✔ Connected, initialize `protocolVersion` 2025-11-25(SDK 무상태 서버가 세션 ID 없이 응답), `Authorization` 헤더 그대로 전달, `Origin` 없음(있으면 403인 서버에 붙었으므로), `claude -p`로 `list_workspaces` 도구 호출·결과 왕복. Codex CLI·Gemini CLI는 미설치라 미측정(U31) |
| U29 | Electron 44 `utilityProcess`에서 `http.createServer`가 `127.0.0.1`에 바인딩되고 `MessagePort` 왕복이 도구 호출 지연(목표 50ms 미만, 서버 API 시간 제외)을 만족하는가, 앱 종료·크래시 시 포트가 풀리는가 | S8 (Q47) | 해소(2026-09-09): `local` 셸에서 앱 시작 0.5초 뒤 `utilityProcess`의 `http.createServer`가 `127.0.0.1:47871`에만 LISTEN(`lsof`, IPv4). `MessagePort` 왕복 + 도구 실행(서버 호출 없는 `UNAUTHENTICATED` 경로) 지연 3ms(main 로그 `latencyMs`). 앱 종료(SIGTERM) 3초 뒤 포트 리스너 0개. 크래시 시 해제는 미측정. 패키징(asar) 안 엔트리 경로는 2026-09-09 arm64 `Knot.app`으로 해소(4.5절 `S8` 추가 실측) |
| U30 | MCP 서버 `instructions`(2026-07-28 개정판에서는 `server/discover` 응답의 해당 필드)가 세 CLI에서 모델에 노출되는가 | S9 (Q50) | 미확인. Claude Code MCP 문서에 언급이 없다. 노출되지 않으면 스킬 파일만이 안내 경로다 |
| U31 | Codex CLI의 HTTP MCP 서버 등록(`config.toml` `url` 방식은 문서 확인, `codex mcp add` CLI 형식은 미확인)과 Gemini CLI `gemini mcp add --transport http`가 문서대로 동작하는가, `~/.agents/skills/knot/`을 두 CLI가 자동 발견하는가 | S9 (Q50) | 미확인(문서 확인만, 지식 §6.8). 설치본이 없어 미측정 |
| U32 | 웹 SPA가 preload `onDeepLink`(`knot:deep-link`)를 구독해 `{type: "chat", workspaceId, sessionId}`를 `/workspace/:workspaceId/chat/:sessionId`로 옮기는가 — `S10`이 저장 뒤 보내는 이벤트의 소비자 | S10, A8 (Q51) | 미확인(2026-09-09). `frontend/`에 구독자가 없다(테스트 스텁뿐). 구독 훅(chat·invite 라우팅 + 마운트 시 `getPendingDeepLink` 소비)은 frontend 담당 세션에 요청해 둠. 그 전까지 `show_answer`는 저장·창 앞으로만 되고 화면은 그대로다 |
| U27 | Notion 로그인 화면의 IdP 팝업 체인 — 팝업이 자식 창으로 열리면 IdP 인증·`app.notion.com/*popupcallback` 복귀·opener 통지·창 닫힘까지 앱 안에서 끝나는가 | A1 | 부분 해소(2026-09-08): 앱 로그로 팝업 URL이 `app.notion.com/verifyNoPopupBlockerHtmlAndRedirect?redirectUri=https://app.notion.com/microsoftpopupredirect?callbackType=popup&redirectToAuth=true&popupFlowId=…`임을 확인했고, 그 페이지 본문은 `window.opener`가 있을 때만 `redirectUri`로 `location.replace`하고 없으면 `window.close()`한다(curl 실측). `<idp>popupredirect` 세 종은 curl로 302 목적지를 확인했다 — microsoft → `login.microsoftonline.com/common/oauth2/v2.0/authorize`(redirect_uri `app.notion.com/microsoftpopupcallback`), google → `accounts.google.com/o/oauth2/v2/auth`(`googlepopupcallback`), apple → `appleid.apple.com/auth/authorize`(`response_mode=form_post`, `applepopupcallback`). Microsoft 개인 계정이 거치는 `login.live.com`은 미관측이라 가능성으로만 목록에 뒀다. → 해소(2026-09-08 23:41): 자식 창에서 `verifyNoPopupBlockerHtmlAndRedirect` → `microsoftpopupredirect` → `login.microsoftonline.com/common/oauth2/v2.0/authorize` → `…/common/login` → `app.notion.com/microsoftpopupcallback?code=…`가 차단·거부 없이 지나갔고(Microsoft는 embedded UA를 막지 않았다), 메인 창이 동의 뒤 콜백으로 복귀했다. Google·Apple 팝업은 미실측 |
| V1 | 착수 시점의 Electron·Forge·Playwright 최신 버전 재조회 | A1 착수 시 | 해소(2026-09-06): `electron@44.2.0`(2026-09-04 배포), `@electron-forge/cli@7.11.2`(latest, 8은 `8.0.0-alpha.10`), `@playwright/test@1.63.0`, `update-electron-app@3.3.0`, `electron-log@5.4.4`, `@electron/fuses@2.1.3`, `@electron/notarize@3.1.1`, `esbuild@0.28.2`. 근거: `npm view <pkg> version` |

전체 목록은 지식 문서 8절과 패키징 부록 끝의 "확인하지 못한 항목"에 있다. 여기에는 **작업을 막는 것만** 옮겨 적었다.

## 7. 불변 계약 (위반하면 작업을 멈춘다)

이 목록을 바꾸려면 이 문서와 관련 ADR을 함께 고쳐야 한다. PR 리뷰에서 이 절을 확인한다.

1. **탐색 계약은 기획서 6.4가 정본이다**(2026-09-07 개정, 사용자 지시). 서버는 검색 API·답변 저장 API·출처 조회의 요청/응답 모양, 문서 준비 게이트(`CHAT_DOCUMENTS_NOT_READY`), 세션당 진행 중 턴 1개(Q23), Workspace JOIN 검증, 근거 규칙 문장을 소유한다. 웹이 받는 이벤트 모양(`chunk {delta}`·`complete {messageId}`·`error {code, message}`)은 전송 경로(SSE·IPC)와 무관하게 바뀌지 않는다. 브라우저 단독 실행용 SSE 경로(Q22)는 어댑터 오류 코드 전달 예외(기획서 6.2)를 포함해 현행을 유지한다.
2. **Knot은 LLM을 호출하지 않는다(웹 채팅 UI의 서버 SSE 경로 제외). 답변은 사용자의 CLI 코딩 에이전트가 만들고, 데스크톱은 MCP 도구로 검색 결과만 제공하며, 저장은 서버만 한다**(2026-09-09 개정, 사용자 지시). 데스크톱 main·MCP 서버·renderer 어디에서도 LLM API를 부르지 않는다. 웹 채팅 UI는 브라우저·셸 모두 서버 SSE 경로(Q22)를 쓴다. 에이전트가 만든 답변·출처는 서버 검증(세션 소유자·Workspace JOIN·rank ≤ 8)을 거쳐 `generated_by=CLIENT`로만 저장된다. 서버 액세스 토큰·연결 토큰은 main 밖(renderer·MCP 서버 프로세스·도구 결과·로그·크래시 리포트)으로 나가지 않는다. 개정 전 문구("LLM 호출은 데스크톱 main만, 저장은 서버만 한다")는 11절 변경 이력에 남긴다.
3. **Knot은 어떤 LLM 자격증명도 저장·중개·요구하지 않고, CLI 바이너리를 실행·동봉·변경하지 않으며, 공식 확장 지점(MCP 서버·스킬)으로만 붙는다**(2026-09-09 개정, 사용자 지시). (a) claude.ai·OpenAI·Google 로그인 화면을 제공하지 않고 OAuth·세션 토큰·`CLAUDE_CODE_OAUTH_TOKEN`·`ANTHROPIC_API_KEY`·`OPENAI_API_KEY`·`GEMINI_API_KEY`·Keychain·`~/.claude`·`~/.codex`·`~/.gemini`를 읽거나 쓰거나 중개하지 않는다. (b) `claude`·`codex`·`gemini` 바이너리를 자식 프로세스로 실행하지 않는다(`claude -p` 서브프로세스·Agent SDK 내장 — 사용자가 반복 거부, `S6` 폐기. 다시 제안하지 않는다). (c) 연결은 사용자가 자기 CLI에 Knot MCP 서버와 스킬을 등록하는 것으로만 이뤄지며, 세 CLI 모두 제3자 MCP 서버 연결과 스킬이 문서화된 정식 기능이다(지식 §6.8). 근거는 `legal-and-compliance`의 금지 조항(로그인 제공·자격증명 중개·바이너리 변경)에 해당하는 행위가 없다는 것이며, 이 해석과 그 위험은 사용자 결정이다(R28). "사용자의 LLM"은 사용자가 터미널에서 쓰는 CLI 코딩 에이전트를 뜻한다. 개정 전 문구는 11절 변경 이력에 남긴다.
4. **Electron 보안 체크리스트 20항목 + 권장 Fuses**(기획서 8절)를 낮추지 않는다. `nodeIntegration`·`contextIsolation`·`sandbox`·네비게이션 허용 목록·IPC sender 검증은 협상 대상이 아니다.
5. **preload는 기획서 4.4 인터페이스만 노출한다.** `ipcRenderer` 원본 노출 금지.
6. **웹 사용자에게 회귀를 만들지 않는다.** 웹 배포 워크플로우·E2E 무변경 통과가 모든 M1 작업의 완료 조건이다.
7. **Issue를 만들 때는 공통 Issue 계약을 우회하지 않는다.** 사용자가 Issue 기획을 요청했고 고위험 신호가 있으면 인터뷰 → Grill → ADR. Issue 본문은 `구현 기능 설명`·`TODO`·`메모` 3섹션만. 계약 전문·ADR 전문·인터뷰 원문은 Issue에 넣지 않는다. 이 계약은 Issue 게시 경로에만 적용하며, **구현 착수는 Issue를 선행 조건으로 하지 않는다.**
8. **원격 쓰기는 요청마다 별도 승인**이다. Issue 생성·commit·push·PR·merge 권한은 각각 따로 판정한다.
9. **문서를 코드에 맞추지 않는다.** 0.2절 문서 우선 원칙을 어긴 PR은 병합하지 않는다. 코드가 문서와 다르면 코드가 틀린 것이다.
10. **인증 자격증명은 `Authorization: Bearer` 하나뿐이다**(2026-09-06 추가, 기획서 `D11`). 인증 쿠키를 다시 만들지 않고, 토큰을 쿼리스트링·로그·크래시 리포트에 싣지 않으며, 데스크톱에서는 renderer의 `localStorage`가 아니라 main의 `safeStorage`에 둔다.

## 8. 완료 정의 (기획서 2.1 목표와 1:1)

| ID | 목표 | 검증 방법 | 마일스톤 |
| --- | --- | --- | --- |
| G1 | 데스크톱에서 웹과 같은 기능을 쓸 수 있다 | 웹 E2E 시나리오를 Electron에서 통과 | M1 |
| G2 | 탐색 계약(기획서 6.4)을 서버가 소유한다 | 검색·저장·출처 API 계약 테스트 통과, 웹 채팅 이벤트 모양 무변경, 서버 SSE 경로 회귀 없음, 데스크톱이 LLM·LLM 자격증명을 다루지 않음(7절 1·2·3번, 2026-09-09 개정) | 상시 |
| G3 | macOS(arm64·x64)·Windows(x64) 서명 설치본 + 자동 업데이트 | 서명·공증 통과, 업데이트 종단 테스트 | M1 |
| G4 | `knot://` 딥링크가 콜드·웜 스타트 모두 동작 | 패키징 앱에서 실측 | M2 |
| G5 | 보안 체크리스트 20항목·Fuses 충족 | PR 리뷰 + `npx @electron/fuses read` | M1 |
| G6 | 웹 회귀 없음 | 웹 배포 워크플로우·E2E 무변경 통과 | 상시 |
| G7 | 앱 셸 릴리스 없이 웹 배포만으로 기능 반영 | 셸 릴리스 주기 ≥ 4주 | M1 이후 |

## 9. 리스크 추적

기획서 15절의 R1~R19를 상태만 여기서 추적한다. 내용은 기획서를 본다.

| # | 요지 | 소유 작업 | 상태 |
| --- | --- | --- | --- |
| R1 | GitHub이 Electron 창 로그인을 차단할 수 있음 | A1 (U1) | 해소(2026-09-07): 비밀번호 + 2FA 종단 성공 |
| R16 | 3자 IdP(Google·Apple)가 GitHub 로그인 체인에 끼어들어 허용 목록을 계속 넓히게 됨 | A1 (U20·U21), A7 (해소) | 미해소. 2026-09-07 `accounts.google.com` 추가로 Google만 뚫었다. Apple(`appleid.apple.com`)은 2026-09-08 Notion 로그인 팝업 실측(U27)을 근거로 목록에 추가했지만 GitHub 경로에서는 미검증이다. Notion 로그인 화면도 같은 IdP 3종(Google·Apple·Microsoft)을 팝업으로 열어 `login.microsoftonline.com`·`login.live.com`까지 목록이 넓어졌다. IdP가 embedded UA를 거부하면 목록 확장 자체가 막힌다. 근본 해소는 시스템 브라우저 로그인(`A7`) |
| R2 | Notion OAuth 도메인이 허용 목록과 다를 수 있음 | A1 (U2) | 부분 해소(2026-09-08): 실제로 달랐다 — 동의 화면이 `app.notion.com`에 있어 차단됐고, 목록에 추가했다. 같은 날 재측정에서 동의 화면 앞 4홉은 통과했고, 대신 로그인 IdP 팝업이 새 창 거부에 막혀 새 창 정책을 개정했다(Q46·U27). 해소(2026-09-08 23:41): 로그인(Microsoft 팝업)·동의·콜백 복귀까지 종단 통과, 새 도메인 없음. Google·Apple 팝업 경로만 미실측 |
| R3 | 운영 API 오리진이 저장소에 없음 | Q3 | 미해소. 2026-09-06 dev 값이 `dev-api.knoted.kr`로 확인되면서 `api.<env>.knoted.kr` 대칭 추정이 깨졌다 — 운영 값을 추측하지 않는다 |
| R4 | access 1시간·리프레시 없음 | A6 | 미해소 |
| R14 | `D11`으로 토큰이 JS에서 읽혀 XSS 노출면이 커짐(`HttpOnly` 격리 상실) | C2 (완화), A7 (해소) | 미해소. CSP(`W3`)·1시간 만료로 완화 |
| R15 | `D11`은 프론트·백엔드 동시 배포가 필요하고 호환 기간이 없음 | GC | 미해소. 배포 시 전 사용자 재로그인 1회 |
| R5 | macOS 서명·공증 계정 부재 | Q1 | 미해소 |
| R6 | Windows 서명 자격 불명확 | Q2 | 미해소 |
| R7 | 크래시 수집 서버·개인정보 | Q5 | 미해소 |
| R8 | CSP가 Emotion 인라인 스타일과 충돌 | W3 (U15) | 해소(2026-09-06, U15 실측) |
| R9 | Electron 8주 메이저 주기 유지보수 | A2 (Renovate 등록) | 미해소 |
| R10 | Governance area 확장 요구 | Q4 | 미해소 |
| R11 | 단일 인스턴스 전제(인메모리 레지스트리·인가 코드) | A6 | 현행과 동일 제약 |
| R12 | Anthropic 모델 교체 시 품질·TTFT 회귀 | GB | 미해소 |
| R13 | Tauri·PWA 재검토 조건 | — | 조건 미충족(재검토 불필요) |
| R17 | 에이전트가 만든 답변을 서버가 검증할 수 없어 피드백·품질 데이터의 신뢰가 떨어짐 | S2, S10 (Q24·Q25) | 미해소. `generated_by` 표시와 Workspace JOIN으로만 완화 |
| R18 | 서버 SSE(웹 채팅 UI)·CLI 에이전트(MCP) 두 탐색 경로를 함께 유지하는 비용 | Q22 | 미해소. 규칙 문장·선별·저장 검증을 서버 한 곳에 두어 중복을 줄인다 |
| R19 | 근거 3페이지 → 청크 8개, 답변 모델이 사용자의 CLI마다 달라 gold set 결과가 이전과 비교되지 않음 | S5 (GS) | 미해소. 기준 에이전트(Claude Code) 하나로 재측정 |
| R20 | 임베딩 공간 교체(Qwen → Gemini)로 기존 색인이 무효화되고, gold set·A/B 보고서가 Qwen 임베딩 기준이라 이전 결과와 비교되지 않음 | B5 (Q39), GB | 미해소. 재색인은 동기화 재실행으로, 품질은 GB에서 재측정 |
| R21 | 허용 목록 밖 홉이 나오면 셸은 외부 브라우저로 빼지만, 이동을 시작한 SPA는 이동 대기 상태(`useNotionConnect`의 `isRedirecting`)에 갇혀 버튼이 무한 로딩이 되고 복구 경로가 없음 | A1 (U2) 관측, FE 후속 | 미해소(2026-09-08 관측). 목록을 맞추면 지나가지만 새 도메인이 나올 때마다 같은 증상이 재발한다. 창 포커스 복귀·타임아웃으로 대기 상태를 푸는 것은 FE 후속 |
| R22 | 무료 티어 Gemini 키는 분당 약 32청크(≈4만 자)만 받아 큰 Workspace 동기화가 수 분 걸리고(21페이지 약 9분), 사용자는 그동안 카드 스피너만 본다. 일일 한도(RPD)에 걸리면 재시도로도 못 넘긴다 | B5 (Q42), GB | 부분 해소(2026-09-08): 로컬 키의 프로젝트에 결제 계정을 연결해 611청크가 48초에 색인됐다(실행 #5). 무료 티어에서는 Q42 재시도로도 실패할 수 있다(실행 #4). 운영 키는 유료 티어로 둔다(Java 연동 문서). 진행률·실패 사유 원인별 노출은 ADR 261의 재논의 조건이라 별도 결정 |
| R23 | 어떤 상태에서는 SPA가 셸 안에서 `auth.getToken`을 초당 수백 번 호출한다. 웹에서는 `localStorage` 동기 읽기라 드러나지 않지만 데스크톱에서는 호출마다 IPC + `safeStorage` 복호화 시도 + 로그 1줄이라 `main.log`가 2분에 5MB(약 6만 줄) 차서 회전되고 OAuth 체인 기록이 밀려난다 | C2 관측, FE 후속 | 미해소·재현 조건 미확인(2026-09-08 `S3` 스모크 중 관측: 이 세션 전부터 떠 있던 `local` 셸 인스턴스의 로그가 16:59~17:00 두 분에 61,680줄 전부 `저장된 토큰 없음`, 무슨 화면이었는지는 기록이 회전돼 알 수 없다). 같은 빌드를 새로 띄운 로그아웃 상태에서는 21초 동안 2회뿐이라 기본 동작은 아니다. 원인 지점은 SPA 쪽(토큰 없음 → 재요청 루프로 추정)이라 FE 후속이며, 셸 쪽 완화는 `tokenStore`의 "없음" 로그를 debug로 내리거나 첫 1회만 남기는 것 |
| R25 | 로컬 HTTP MCP 서버는 같은 PC의 다른 프로세스와 (DNS 리바인딩 시) 원격 웹 페이지가 사용자의 Workspace 문서를 검색할 수 있는 표면 | S8 (Q47·Q48) | 완화됨(2026-09-09, `S8` vitest·curl 실측): `127.0.0.1` 바인딩·`Origin` 있으면 403·`Host` 검사·연결 토큰 필수. 같은 사용자 계정의 프로세스가 `agent-bridge.json`을 읽으면 검색은 가능하다(액세스 토큰 `auth.bin`과 같은 위협 모델, 기획서 5.3) |
| R26 | 답변 품질·가용성이 사용자의 CLI 에이전트(설치·로그인·모델·구독 한도)에 달려 Knot이 관측·제어할 수 없고, CLI가 없는 팀원은 웹 채팅 UI(서버 SSE)만 쓴다 | S8, S9 | 미해소. 설정 화면의 서버 상태·마지막 도구 호출 시각(`agent.getStatus`)으로 연결 여부만 보여 준다. 서버 로그로는 원인을 알 수 없다(검토 문서 5.7) |
| R27 | Workspace 문서 본문(청크 8개)이 사용자의 CLI 에이전트를 거쳐 그 에이전트의 모델 제공자(Anthropic·OpenAI·Google)로 전송됨 | S7, S8 | 미해소·수용(사용자 결정). 개정 전 "사용자 LLM" 설계와 같은 범위다. Workspace 소유자가 승인한 문서만 색인되고(기능 기획서 4절), 전송 주체는 사용자 본인의 도구·계정이다. 소유자 고지 문구는 `S9` 안내 화면에 둔다 |
| R28 | MCP 서버 연결 경로의 정책 해석(제3자 MCP 서버 연결은 세 CLI의 문서화된 기능이고 Knot은 자격증명·바이너리를 만지지 않으므로 사전 승인 불요)은 공개 문서에 기댄 사용자 결정이며 각 사의 개별 확인은 없다 | S8, 계약 3번 | 미해소(사용자 결정 2026-09-08). Knot이 LLM 자격증명·바이너리를 다루지 않는 구조를 코드 검토(GS)로 유지하는 것이 유일한 완화. 사용량 한도 관련 공지(검토 문서 5.1)는 사용자 본인 도구의 통상 사용이라 직접 대상은 아니다 |
| R24 | 새 창을 허용 목록 한정 자식 창으로 열면서(Q46) 팝업 창이라는 표면이 생김 — 허용 오리진 페이지의 XSS가 팝업을 띄울 수 있고, 팝업은 주소 표시 없이 IdP 로그인 폼을 보여준다 | A1 (Q46) | 완화 적용(2026-09-08): 자식 창은 목록 안 오리진만 열리고 보안 webPreferences 동일·preload 없음·같은 네비게이션 정책이라 도달 범위가 메인 창과 같다. 메인 창도 주소 표시 없이 IdP 폼을 보여주므로 새 표면은 아니다. 근본 해소는 시스템 브라우저 로그인(`A7`) |

## 10. 작업 1건 실행 절차 (매번 반복)

0. **문서를 먼저 읽는다.** 이 문서 → 필요한 범위의 기획서 절 → 관련 ADR 순. 코드부터 열지 않는다.
1. 작업을 고른다. 사용자가 지정했으면 그것, 지정이 없으면 2.1절 순서를 따른다. **착수 전에 되묻지 않는다.**
2. 결정이 필요한 지점은 5절 `기본값` 열을 그대로 쓴다. 표에 없는 결정은 가장 되돌리기 쉬운 선택을 잡아 5절에 행을 추가하고 계속 진행한다.
3. **바로 구현한다.** Issue 기획(`/knot-issue-planning`)·인터뷰(`/knot-deep-interview`)·Grill(`/knot-grill-me`)은 사용자가 명시적으로 요청했을 때만 실행하며, 구현의 선행 조건이 아니다.
4. 이 문서의 해당 행 상태를 `구현중`으로 바꾼다. Issue를 만들었으면 번호도 적는다.
5. 구현 브랜치에서 작업한다. 확정 Issue가 있고 `adr.required=true`면 실제 Issue 번호로 `harness/materialize_adr.py`를 실행해 `Proposed` ADR을 같은 PR에 포함한다.
6. 구현 중 문서와 다른 사실을 발견하면 0.2절에 따라 문서를 먼저 정정하고 **그대로 계속 진행한다.**
7. PR 전에 7절 불변 계약과 (데스크톱이면) 기획서 8절 보안 체크리스트를 확인한다. 적용한 5절 기본값을 PR 본문에 적는다.
8. commit·push·PR·merge는 불변 계약 8번에 따라 그 시점에 사용자 승인을 받는다. **구현 자체는 승인 대상이 아니다.**
9. 병합 후 이 문서의 상태를 `완료`로 바꾸고, 실측으로 해소된 U·R 항목을 갱신한다. 게이트 조건을 채웠으면 3절 체크박스도 함께 채운다.

## 11. 변경 이력

| 날짜 | 변경 | 근거 |
| --- | --- | --- |
| 2026-09-06 | 최초 작성. 기획서 7·14·15절, 검토 문서 7·8절, 지식 문서 8절을 실행 단위로 재구성 | 4개 조사·기획 문서 |
| 2026-09-06 | 0.2절 문서 우선 원칙, 2.1절 착수 가능 목록, 불변 계약 9번 추가. `CLAUDE.md`에 진입 규칙 연결 | 사용자 지시(문서 우선 SSOT) |
| 2026-09-06 | 구현 착수에서 확인 질문 제거. 2.1절 사용자 지정 우선, 3절 지시=게이트 통과 선언, 5절 `기본값` 열 신설, 10절 절차 재작성. Issue 기획·인터뷰·Grill은 명시 요청 시에만 | 사용자 지시(질문 없이 바로 개발) |
| 2026-09-06 | G0 통과 기록(사용자 지시), `A1` 상태 `구현중`, 2.1절·2절 요약 갱신, V1 버전 재조회 결과 기입 | 사용자 지시(`일랙트론 데스크탑 앱 구현 시작`) |
| 2026-09-06 | Q13(앱 식별자) 행 추가 | `A1` 구현에서 새로 필요해진 결정 |
| 2026-09-06 | U16 추가(`will-redirect` 차단 전제 실측). 기획서 4.5·8 #13을 `will-redirect` 포함으로 정정 | `A1` 구현 중 발견한 설계 누락 |
| 2026-09-06 | dev API 오리진을 `api.dev.knoted.kr` → `dev-api.knoted.kr`로 정정(기획서 4.5, 지식 1.1). prod 오리진의 대칭성 추정 문구 삭제 | `A1` 셸에서 GitHub 로그인 버튼 클릭 시 네비게이션 로그로 실측 |
| 2026-09-06 | 웹 선행 작업 `W1`·`W3` 착수. 4.2절 상태 `구현중`, 2.1절에서 두 행 내림, 5절에 Q9~Q11 추가 | 사용자 지시(`웹 구현 시작`) |
| 2026-09-06 | `W1`·`W3` 구현 완료로 `검증중`. U15·R8 해소 기록. 기획서에 9.4 CSP 지시문 표 신설 | W3 실측(`wrangler dev` + Playwright) |
| 2026-09-06 | 트랙 B `B0` 착수. 2절·4.3절 상태 `구현중`, 2.1절 목록 비움, 5절에 Q12 추가, 4.3절 선행 사유를 구현 결과로 정정 | 사용자 지시(`BE 개발 시작`) |
| 2026-09-06 | `A1` 셸 구현(`desktop/`) 후 상태 `검증중`. U1 부분 해소·U16 해소, R1 완화·R3 보강, G1 체크박스에 실측 기록. Q13(앱 식별자) 행 추가(다른 세션의 Q9와 번호가 겹쳐 옮김) | `A1` 실행·패키징 실측 |
| 2026-09-06 | `C1`~`C3` 구현 완료로 `검증중`. 백엔드 `check` 전량 통과(main에 쿠키·CSRF 참조 0건), 프론트 vitest 291건·tsc 통과, 데스크톱 vitest 25건·typecheck 통과. GC 세 번째 항목에 로컬 mock 종단 확인 기록, U17 보강 | `C1`~`C3` 구현·검증 |
| 2026-09-06 | **인증을 쿠키 → Bearer JWT로 전환**. 트랙 C(`C1`~`C3`) 신설, `W2` 폐기(`C1` 흡수), 게이트 `GC` 추가, G3 문구 정정, Q14~Q19 추가, U14 무효·U17 추가, R14·R15 추가, 불변 계약 10번 추가. 설계는 기획서 `D11`·5.1 | 사용자 지시(`기존의 쿠키 방식을 jwt토큰으로 변경`) |
| 2026-09-07 | 트랙 B `B1` 착수 후 구현·검증 완료로 `검증중`(단위 테스트 500건 통과), `B0` 커밋 완료로 `검증중`. Q20(HTTP 방식) 기본값 확정, U18(캐시 최소 프리픽스) 추가, `B4`(재검색 루프) 행 분리, 불변 계약 1번·G2에 어댑터 오류 코드 전달 예외 명시. 기획서 6.2 설정·HTTP·파라미터·캐시·오류·계측·재검색 행 정정 | 사용자 지시(`구현 ㄱㄱ`, `B0` 다음 순서) |
| 2026-09-07 | `local` API 오리진을 같은 오리진(mock 전제) → `http://localhost:8080`(실 백엔드)으로 정정(기획서 4.5, 8 #1). U22 추가 후 해소, U17 해소 | 사용자 지시(`서버까지 띄워서 검사해야해. 목을 보지 않도록`) + `A1` 셸 실측(정정 전 로그인 버튼 무반응 → 정정 후 OAuth 4홉 종단 통과, `auth.bin` 암호화 확인) |
| 2026-09-07 | **탐색 경로를 서버 검색 전용 + 데스크톱 사용자 LLM 호출로 개정**. 1절 목표 문장, 불변 계약 1·2번 개정(개정 전 1번: "백엔드 채팅 계약 무변경, `chat/` diff 0", 2번: "클라이언트에서 LLM을 호출하지 않는다. 답변·출처를 클라이언트가 저장하지 않는다. 데스크톱 전용 채팅 경로를 만들지 않는다"), 3번 보강, G2 정정, 트랙 S(`S1`~`S5`)·게이트 GS 신설, Q21~Q29·U23~U24·R17~R19 추가, 2.1절에 `S1` 등재. 설계는 기획서 6.4 신설, 기능 기획서 1·3·6·8·11·12절 정정. 코드는 미착수 | 사용자 지시(`서버에서 탐색 파이프라인을 융합/선별을 상위 3개가 아니라 8개로 늘리고 … 사용자의 llm에게 요청하는 방식으로 동작하게 문서를 전면 수정해줘`) |
| 2026-09-07 | 트랙 S `S1` 착수 후 구현·검증 완료로 `검증중`(단위 521·통합 120·수락 177건 통과). 검색 API `POST /conversations/{sessionId}/search`, V14(`chunk_index`·rank 1~8·`generated_by`), `top-k=8`·`max-context-characters=12000`, `chat.turn-timeout`, `CHAT_TURN_IN_PROGRESS`. Q23 정정(컬럼 없이 timeout 판정·SSE 잠금 공유·SSE 경로 무변경), Q30(검색 후 저장)·Q31(검색 오류 코드 그대로)·Q32(`chunk_index` 백필 0)·Q33(본문 자르기 예산) 추가. 기획서 6.4 흐름·오류 표·V14 표 정정, 지식 문서 §1.4·Java 연동 문서 정정. ADR은 `S1` Issue 번호가 없어 아직 만들지 않음 | 사용자 지시(`로드맵 4.5절 s1구현해. 기획서 6.4 기준으로 서버 검색 api와 v14까지`) |
| 2026-09-08 | 트랙 B `B5`(임베딩 Gemini 어댑터) 착수 후 구현·검증 완료로 `검증중`(단위 536·통합 120·수락 177건 통과, 실제 Gemini 종단 미측정). Q34~Q40(provider·차원 1,024 유지·`taskType`·배치·오류 매핑·재색인·환경) 기본값 확정, U25·U26·R20 추가, GB 게이트를 임베딩 교체까지 포함하도록 확장. 기획서 6.2 활성화 행 정정·임베딩 행 신설, 13절 변경 목록 추가. 지식 문서 §1.4·§8, Java 연동 문서, 기능 기획서 모델 절 정정 | 사용자 지시(`임베딩 모델을 gemini 임베딩 1로 가자. 토큰값을 env로 설정할 수 있게 해줘`) |
| 2026-09-08 | `C1` 보강: 회원이 없는 액세스 토큰을 401로 처리(Q41 추가, C1 행 문구 보강). 기획서 5.1 계약에 `회원 확인` 행, 13절에 변경 행 추가 | 사용자 지시(`auth.bin 지우고 백엔드도 401 주도록 고쳐`) + 로컬 DB 초기화 후 stale 토큰 실측(`/auth/me` 200, `POST /workspaces` 500, 로그아웃 진입점 도달 불가) |
| 2026-09-08 | Notion 연결 무한 로딩 진단·정정. U2 부분 해소(동의 화면 `app.notion.com` 실측), R2 부분 해소, R21 추가(차단 시 SPA 복구 부재), G1 두 번째 체크박스에 실측 기록. 기획서 4.5 허용 목록에 `app.notion.com` 추가·Notion 체인 항목 신설·15절 R2 정정·R21 추가, 지식 문서 §1.1 Notion OAuth 체인 행·§8 U2 정정. 코드는 `desktop/src/shared/env.ts` 허용 목록과 `env.test.ts` | 사용자 지시(`고쳐줘, 문서부터 순서대로`) + `~/Library/Logs/Knot/main.log` 09-07·09-08 `will-redirect` 차단 기록, 로컬 DB 연결 행 |
| 2026-09-08 | 홈 Notion 동기화 실패(`Notion 문서를 가져오지 못했습니다`) 진단·정정. 원인은 Notion이 아니라 Gemini `batchEmbedContents` 429(무료 티어). 배치 64 → 16(Q37 정정), 색인 배치 429·503 지수 백오프 재시도 신설(Q42, `GeminiEmbeddingClient`·`llm.gemini.retry-*`, 기본 6회), Q38 정정, U25 부분 해소·U26 해소, R22 추가. 기획서 6.2·13절, 지식 문서 §1.4·§8, Java 연동 문서 정정. 단위 543건 통과. 무료 티어 실행 #4는 재시도로도 실패(412초), 결제 계정 연결 뒤 실행 #5 완료(106초·611청크·발행). 실패 사유 원인별 노출은 ADR 261 재논의 조건이라 미착수 | 사용자 지시(`1번부터 진행해줘`) + 로컬 DB(`content_import_runs` 21/21 FAILED·`search_document_chunks` 0건)·Gemini 직접 호출 실측 |
| 2026-09-08 | 트랙 S `S3` 착수 후 구현·검증 완료로 `검증중`(typecheck·vitest 89건·local 빌드 통과, U24 부분 해소, 셸 종단은 `S2`·`S4`·로컬 모델 뒤). 스모크 중 R23(특정 상태의 SPA가 `auth.getToken`을 폭주 호출해 로그 회전) 관측·추가. 데스크톱 preload `chat.ask`·`llm.getSettings/setSettings/clearApiKey`, main의 검색 API 호출·이력 조회·프롬프트 조립·사용자 LLM 스트리밍 클라이언트(openai-compatible·anthropic, `fetch` 직접 호출)·오류 매핑·첫 조각 30초·세션당 1요청·설정 저장(`llm-settings.json`·`llm-key.bin`). Q43(요청 파라미터·URL 조립)·Q44(설정 없음·검증)·Q45(요청 수명 세부) 추가. 기획서 6.4 데스크톱 main 표·4.4 규칙 보강, `desktop/CLAUDE.md`의 개정 전 계약 문구("클라이언트에서 LLM을 호출하지 않는다") 정정 | 사용자 지시(`로드맵 4.5절 S3 구현해. 기획서 6.4 기준으로 데스크톱 main의 사용자 LLM 호출까지`) |
| 2026-09-08 | Notion 연결 "팝업 차단" 진단·정정. 앞선 정정(`app.notion.com` 추가) 뒤 동의 화면까지는 통과했으나, 미로그인 사용자의 Notion 로그인 화면이 IdP 인증을 `window.open` 팝업으로 열고 셸이 새 창을 전부 거부해 외부 브라우저로 빼면 팝업 검증 페이지가 `window.opener` 부재로 스스로 닫혀 "팝업이 차단됨"이 됐다. 새 창 정책을 허용 목록 한정 자식 창으로 개정(Q46), U2 재측정 기록·U27 추가(IdP 세 종 302 목적지 curl 실측), R2·R16 갱신, R24 추가, G1 두 번째 체크박스 갱신. 기획서 4.5 새 창 규칙·허용 목록(`login.microsoftonline.com`·`appleid.apple.com`·`login.live.com`)·Notion 로그인 팝업 항목·8절 #14·15절 R2·R24, 지식 문서 §1.1·§2.3 #14·§8 U2·U27, `desktop/README.md`. 코드는 `navigation.ts` `resolveWindowOpen`·`env.ts` IdP 오리진·테스트 | 사용자 지시(`팝업이 막혔다고 로그인 안돼. 수정해`) + `~/Library/Logs/Knot/main.log` 2026-09-08 23:21 `새 창 요청 거부` 3건, `app.notion.com/verifyNoPopupBlockerHtmlAndRedirect`·`*popupredirect` curl 실측 |
| 2026-09-09 | **탐색의 답변 생성을 사용자의 CLI 코딩 에이전트 + 데스크톱 로컬 MCP 서버 방식으로 확정**(사용자 지시 2026-09-08 밤). 2026-09-08 낮에 "사용자 PC의 `claude` 바이너리를 `claude -p` 자식 프로세스로 실행"하는 초안(`S6`·Q47~Q50·계약 3번 개정·지식 §6.8 실측)을 문서에 넣었다가 사용자가 거부해 폐기하고 이 방식으로 다시 썼다. 불변 계약 2번 개정(개정 전: "LLM 호출은 데스크톱 main만, 저장은 서버만 한다. renderer는 LLM을 직접 호출하지 않고 preload `chat` API만 쓴다 … 사용자 LLM 키·엔드포인트·프롬프트 본문은 main 밖으로 나가지 않는다")·3번 개정(개정 전: "사용자 개인 Claude 구독으로 모델을 호출하지 않는다. 검토 문서 8절 전제 조건이 모두 충족되기 전까지 금지. '사용자의 LLM'은 로컬 모델 또는 사용자 본인 API 키를 뜻하며 claude.ai 로그인·구독 OAuth·세션 토큰 중개는 포함하지 않는다"), 1절 목표·트랙 B/S 설명·2절 요약·2.1절 순위·GS 게이트·G2·4절 대응표(I24~I27)·A13·4.4절 주석 정정, 4.5절 재편(`S3`·`S4`·`S6` 폐기, `S2`·`S5` 재정의, `S7`~`S10` 신설), Q22 정정·Q26·Q27·Q43~Q45 무효·Q47~Q51 추가, U23·U24 무효·U28~U31 추가, R17~R19 정정·R25~R28 추가. 기획서 1·2.2·3(D3·D9)·4.1~4.4·6.1·6.4·7·9.1~9.3·11~16절, 검토 문서 머리·1·5.1·6·7·8절, 지식 문서 §5.4·§6.1·§6.8·§7·§8, 기능 기획서 1·6·8·11절, Java 연동 문서, `desktop/CLAUDE.md`·`README.md` 정정. 코드는 미착수(`S3` 코드는 남아 있으며 `S8`에서 제거). 세 CLI의 MCP·스킬 등록 방식과 MCP 스펙 보안 요구는 공식 문서로 확인(지식 §6.8) | 사용자 지시(`이 방식대로 데스크탑 앱을 구현할꺼야. 이거랑 충돌될 수 있는 모든 문서를 다 찾아서 수정해줘`) + 앞선 지시(서브프로세스 방식 거부 — "다른 방법이라고") |
| 2026-09-08 | Notion OAuth 체인 종단 통과 실측(23:41). 새 창 정책 개정 빌드로 재시도해 Microsoft 로그인 팝업(자식 창)·동의·`:8080` 콜백·`?result=connected` 복귀가 차단 0건으로 끝났고 로컬 DB에 연결 행(workspace 6)·동기화 COMPLETED가 생겼다. G1 두 번째 체크박스 완료, U2·U27·R2 해소. 기획서 4.5 팝업 항목·15절 R2, 지식 §1.1·§8 U2·U27 정정 | `~/Library/Logs/Knot/main.log` 2026-09-08 23:40:35~23:41:09, 로컬 DB `content_source_connections`·`content_import_runs` |
| 2026-09-09 | 트랙 S `S7`(서버 Workspace 검색 API) 착수·구현·검증 완료로 `검증중`. Q49 정정(`list_workspaces`는 `role` 없이 `{id, name}` — 현행 목록 응답에 `role`이 없음) 및 데스크톱 서버 호출 오류 코드 기본값 추가(`KNOT_API_UNREACHABLE`·`UNKNOWN`·`KNOT_API_MALFORMED`). 기획서 13절·Java 연동 문서·지식 문서에 `S7` 구현 사실 반영. 같은 시각 다른 세션이 `S8`(desktop)·`S9`(frontend)를 분담해 진행 중 | 사용자 지시(`남은 구현 구현해`), 백엔드 테스트 실측 |
| 2026-09-09 | 트랙 S `S8`(데스크톱 로컬 MCP 서버) 착수·구현·검증 완료로 `검증중`. `src/mcp/`(guard·portRequester·server·index — `utilityProcess` 엔트리, `@modelcontextprotocol/sdk` 1.30.0 무상태 Streamable HTTP), `src/main/agent/`(bridgeConfig `agent-bridge.json` 0600·bridgeCore `MessagePort` 왕복·동시 4개·toolExecutor·registration 스니펫 3종+스킬·instructions·bridge Electron 접착), `knotApi` 개편(`GET /workspaces`·`POST /workspaces/{id}/search`, 오류 코드 Q49), preload `agent` API 4종·IPC 채널 4개, 메뉴 `CLI 에이전트 연결`(임시 진입점 — 연결 안내 화면 열기·스니펫 복사·상태·토큰 재발급), `resources/skills/knot/SKILL.md`(Q50), 빌드 스크립트 mcp 엔트리 추가, `S3` 코드·테스트·`llm-settings.json`·`llm-key.bin` 제거. 4.5절 `S8` 실측·착수 가정, Q47 정정(SDK 최신 프로토콜 2025-11-25), U28 부분 해소·U29 해소, R25 완화, GS 체크박스 3개. 기획서 4.4·9.1·9.2, 지식 §6.8·§8, `desktop/CLAUDE.md`·README 갱신. "내부 클로드 토큰"을 Claude Code의 구독 OAuth 자격증명으로 읽는 방식은 계약 3번·검토 문서 정책 판정에 걸려 구현하지 않았다. 같은 시각 `S7`(backend)·`S9`(frontend)는 다른 두 세션이 분담 | 사용자 지시(`앱이 내부 클로드 토큰을 사용하도록 모든 구현을 다 해`) + `local` 셸·Claude Code 2.1.263 실측 |
| 2026-09-09 | 트랙 S `S9`의 웹 부분 착수·구현·검증 완료로 `검증중`(다른 세션의 `S8`·`S7`과 분담해 병행). 웹 계약 사본 `frontend/src/shared/types/desktop.ts`에 `agent` API·`AgentBridgeStatus`·`AgentRegistrationTarget` 추가, 데스크톱 전용 라우트 `/agent-connection`(CenteredLayout·AuthGuard, `knotDesktop.agent` 없으면 데스크톱 안내만) + `AgentConnectionCard` 위젯(상태 표·세 CLI/스킬 복사·`preview`·`복사됨`·포트 변경·토큰 재발급 2단계 확인·소유자 고지 R27), GNB 프로필 메뉴에 데스크톱 전용 `CLI 에이전트 연결` 항목, 상태 쿼리 `useDesktopAgentStatusQuery`(10초 폴링, 재기동 뒤 1초 후 재조회 — 셸 실측: `setPort`·`rotateToken`은 재기동 결과 확정 전에 응답). 찾은 문서를 `GET /messages/{id}/sources`로 연동(DTO·fetch·쿼리·mock, 청크 ≤8을 페이지로 묶어 최고 점수 순, 위젯 mock 제거). frontend tsc·ESLint·Prettier 통과, vitest 50파일 324건 통과(연결 카드 12·찾은 문서 5·유틸 단위·프로필 메뉴 2 포함), Playwright E2E `agentConnectionPage.test.ts` 7건 통과(mock dev 서버 별도 기동). 홈 E2E 7건 실패는 Dock 개편 이전부터의 기존 문제. 실제 셸 preload 종단은 사용자 로그인이 필요해 미수행 | 사용자 지시(`남은 구현 구현해`) + 세션 간 분담 합의(2026-knot-20·6f·98) |
| 2026-09-09 | 트랙 S `S10`(데스크톱 `show_answer` 도구) 착수·구현·검증 완료로 `검증중`(데스크톱 부분). 선행 `S2`가 같은 시각 다른 세션에서 구현 중이라 기획서 6.4 턴 저장 계약(`201 {userMessageId, messageId}`)을 가정하고 먼저 착수. `agentProtocol`(도구 3개·상한), `knotApi`(`createConversation`·`saveTurn`), `toolExecutor`(`parseShowAnswerInput`·`sessionTitleFrom`·`presentAnswer`), `mcp/server`(`show_answer` 등록·zod), `index.ts`(`presentAnswer`: 창 앞으로 + `knot:deep-link`), `instructions`·SKILL.md(사용 조건·`allowed-tools`). Q51 착수 가정 추가, U29 패키징 해소, U32(SPA 딥링크 구독자 부재) 추가. `S8` 추가 실측: arm64 패키징 asar 기동·로그인 셸 `list_workspaces` 종단 통과·`search_documents`는 로컬 백엔드 500 중계. 기획서 9.1, `desktop/CLAUDE.md`·README 갱신 | 사용자 지시(`남은 구현 해`) + vitest 113건·arm64 `Knot.app` 실측 + Claude Code 세션의 도구 호출 |
