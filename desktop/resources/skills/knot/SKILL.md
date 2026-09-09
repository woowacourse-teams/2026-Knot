---
name: knot
description: Knot 워크스페이스에 동기화된 팀 문서(회의록·결정 기록·기획서)를 검색해 근거 기반으로 답한다. 팀 문서, 회의록, 어떤 결정을 왜 했는지, 프로젝트 진행 상황·규칙처럼 팀 내부 문서에 답이 있을 질문이면 답하기 전에 search_documents를 먼저 호출한다.
license: Proprietary
compatibility: Knot 데스크톱 앱이 실행 중이고 knot MCP 서버(http://127.0.0.1:47871/mcp, Streamable HTTP)가 이 CLI에 등록돼 있어야 한다.
metadata:
  author: Knot
  version: "0.1.0"
allowed-tools: mcp__knot__search_documents mcp__knot__list_workspaces mcp__knot__show_answer
---

# Knot 팀 문서 검색

Knot은 팀의 Notion 문서를 동기화해 검색해 주는 서버다. 이 스킬은 Knot 데스크톱 앱이 띄운 로컬 MCP
서버(`knot`)의 세 도구를 쓰는 법이다. 모델 호출·로그인·과금은 이 CLI와 사용자 사이의 일이며, Knot은
검색 결과를 주고(`search_documents`), 사용자가 원할 때만 답변을 앱에 저장해 보여 준다(`show_answer`).

## 언제 검색하는가

다음 질문이면 **답하기 전에** `search_documents`를 먼저 호출한다.

- 팀 문서·회의록·기획서·결정 기록의 내용
- "왜 그렇게 정했는지", "언제 결정됐는지", "누가 담당인지"처럼 근거가 문서에 있을 질문
- 프로젝트 진행 상황·규칙·용어처럼 팀 내부 맥락이 필요한 질문

일반 지식·코드 작성처럼 팀 문서와 무관한 질문에는 부르지 않는다.

## 도구

### `search_documents({ query, workspaceId? })`

- `query`: 사용자의 질문을 그대로 넣는다(1~10,000자). 후속 질문이면 앞 대화의 맥락을 한 문장으로 합쳐 넣는다.
- `workspaceId`: 워크스페이스가 하나뿐이면 생략한다. 여럿이면 `list_workspaces`로 목록을 본 뒤 고른다.
  생략했는데 여럿이면 도구가 목록과 함께 오류를 돌려주니 그때 고르면 된다.
- 아무것도 저장하지 않으므로 질문을 바꿔 여러 번 검색해도 된다.

결과 텍스트는 **근거 규칙 문장**으로 시작하고 `[근거 문서 n]` 블록(제목·문서 ID·문서 링크·청크·내용)이
최대 8개 이어진다. `structuredContent.chunks`에 같은 내용이 원본 필드로 있다.

### `list_workspaces()`

로그인한 사용자가 속한 워크스페이스 목록(`[id] 이름`)이다. `workspaceId`를 고를 때만 쓴다.

### `show_answer({ workspaceId, question, answer, sources, sessionId? })`

터미널에 표시한 답변을 Knot 앱의 대화에 저장하고, 앱 창을 앞으로 가져와 그 대화를 연다. 앱 화면에는
질문·답변과 "찾은 문서"(근거)가 보인다.

- **사용자가 요청했을 때만** 부른다 — "Knot 앱에서 보여줘", "앱에 저장해줘", "팀 기록으로 남겨줘"처럼.
  답할 때마다 자동으로 부르지 않는다.
- `workspaceId`: `search_documents`에 쓴 값. `question`: 사용자 질문 원문. `answer`: 터미널에 표시한 답변 전문.
- `sources`: 답에 **실제로 사용한** 근거만, `search_documents` 결과 `structuredContent.chunks`의
  `importRunId`·`importedPageId`·`chunkIndex`·`score`를 그대로 넣는다(최대 8개, 배열 순서가 순위). 검색
  결과에 없는 값을 만들어 넣지 않는다.
- `sessionId`: 같은 터미널 대화에서 앞선 `show_answer`가 돌려준 값을 넘기면 그 대화에 이어서 저장된다.
  처음이면 생략한다(앱이 질문 앞 50자를 제목으로 새 대화를 만든다).
- 저장이 실패해도(`isError`) 터미널 답변은 이미 표시돼 있으니 오류 문구만 전한다.

## 답하는 법

1. 결과 앞머리의 근거 규칙 문장을 **반드시** 지킨다 — 근거 문서에 실제로 적힌 내용만 답하고, 없는
   사실·날짜·이유를 일반 지식으로 보완하거나 추측하지 않는다. 문서끼리 내용이 다르면 충돌을 명시한다.
2. 답에 사용한 문서는 제목과 `문서 링크`를 함께 표시한다.
3. 결과가 `NO_RESULT`(관련 문서 없음)나 `NEEDS_CLARIFICATION`(질문 범위가 넓음)이면 돌려받은 안내
   문구를 그대로 전하거나, 사용자에게 무엇을 찾는지 구체화해 달라고 묻는다.
4. 도구가 `UNAUTHENTICATED`를 돌려주면 "Knot 앱에 로그인하세요"라고 안내한다. `CHAT_DOCUMENTS_NOT_READY`면
   워크스페이스 문서 동기화가 아직 끝나지 않은 것이다.

## 연결이 안 될 때

- Knot 데스크톱 앱이 실행 중인지 확인한다(MCP 서버는 앱이 켜져 있을 때만 뜬다).
- 앱 메뉴 **CLI 에이전트 연결**에서 등록 명령을 다시 복사해 실행한다. 연결 토큰을 재발급했다면 기존 등록은
  무효이므로 다시 등록해야 한다.
