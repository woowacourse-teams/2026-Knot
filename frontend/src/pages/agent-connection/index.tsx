import AgentConnectionCard from "@widgets/agent/AgentConnectionCard";

/**
 * CLI 에이전트 연결 화면 (`/agent-connection`, 데스크톱 전용)
 *
 * 데스크톱 앱이 띄운 로컬 MCP 서버의 상태를 보여 주고, 사용자의 CLI 코딩 에이전트
 * (Claude Code·Codex CLI·Gemini CLI)에 Knot을 등록하는 명령·설정과 Knot 스킬 설치 명령을
 * 복사하게 하며, 연결 토큰 재발급과 포트 변경을 맡는 화면이다(기획서 6.4, 로드맵 S9).
 *
 * 브라우저처럼 `window.knotDesktop.agent`가 없으면 데스크톱 앱에서 이어가라는 안내만 보여 준다.
 * 로고와 중앙 배치는 `CenteredLayout`이 담당하고, 이 페이지는 카드를 놓기만 한다.
 */
export default function AgentConnectionPage() {
  return <AgentConnectionCard />;
}
