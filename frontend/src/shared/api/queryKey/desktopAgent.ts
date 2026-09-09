/**
 * 데스크톱 셸 CLI 에이전트 연결 쿼리 키
 *
 * - `status` 셸이 띄운 로컬 MCP 서버의 상태(preload `agent.getStatus`). 서버 API가 아니라 셸에서 읽어요
 */
export const desktopAgentKeys = {
  all: ["desktopAgent"] as const,

  status: () => [...desktopAgentKeys.all, "status"] as const,
};
