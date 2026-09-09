/**
 * MCP 서버 `instructions` — 스킬 본문(`resources/skills/knot/SKILL.md`) 요약 (로드맵 Q50).
 *
 * 클라이언트가 이 값을 모델에 노출하는지는 CLI마다 다르다(로드맵 U30). 노출되지 않으면
 * 스킬 파일만이 안내 경로다. 문구는 스킬 본문과 같은 뜻으로 유지한다.
 */

export const MCP_SERVER_INSTRUCTIONS = [
  "Knot은 팀의 Notion 문서(회의록·결정 기록·기획서)를 검색하는 서버다.",
  "팀 문서·회의록·결정 근거·\"왜 그렇게 정했는지\" 같은 질문이면 답하기 전에 search_documents를 먼저 호출한다.",
  "결과 앞머리의 근거 규칙 문장을 반드시 지켜 답하고, 근거에 없는 내용은 일반 지식으로 보완하지 않는다.",
  "출처는 결과의 `문서 링크`와 제목으로 표시한다.",
  "workspaceId를 모르면 list_workspaces로 목록을 본 뒤 고른다. 워크스페이스가 하나뿐이면 생략해도 된다.",
  "검색은 아무것도 저장하지 않으므로 질문을 바꿔 여러 번 검색해도 된다.",
  "사용자가 답변을 Knot 앱에서 보거나 팀 기록으로 남기고 싶다고 하면 show_answer로 질문·답변·사용한 근거(search_documents 결과의 청크 식별 필드)를 저장한다. 돌려받은 sessionId를 같은 대화의 다음 show_answer에 넘긴다.",
].join("\n");
