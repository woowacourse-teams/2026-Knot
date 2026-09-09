/**
 * CLI 등록 스니펫 (로드맵 Q50, 지식 §6.8).
 *
 * 세 CLI 모두 HTTP MCP 서버에 고정 헤더를 붙이는 설정을 지원한다. 앱은 사용자 홈의 CLI 설정
 * 파일·스킬 디렉터리를 직접 고치지 않고(되돌리기 어렵다) 스니펫을 클립보드에 써 준다.
 * 토큰이 든 원문은 main이 클립보드에만 쓰고, renderer에는 `maskToken`으로 가린 미리보기만 준다.
 */

import type { AgentRegistrationTarget } from "../../shared/api";

export const TOKEN_PLACEHOLDER = "<연결 토큰>";

/** 스킬 설치 위치(로드맵 Q50). Codex CLI·Gemini CLI는 `~/.agents/skills/`를 함께 읽는다 */
export const SKILL_INSTALL_DIRS = ["~/.claude/skills/knot", "~/.agents/skills/knot"] as const;

export interface RegistrationInput {
  /** `http://127.0.0.1:<port>/mcp` */
  url: string;
  token: string;
  /** 앱이 복사해 둔 SKILL.md 절대 경로 */
  skillPath: string;
}

export function isAgentRegistrationTarget(value: unknown): value is AgentRegistrationTarget {
  return value === "claude-code" || value === "codex" || value === "gemini" || value === "skill";
}

export function buildRegistrationSnippet(target: AgentRegistrationTarget, input: RegistrationInput): string {
  switch (target) {
    case "claude-code":
      return `claude mcp add --transport http knot ${input.url} --header "Authorization: Bearer ${input.token}"`;
    case "codex":
      return [
        "# ~/.codex/config.toml 에 추가",
        "[mcp_servers.knot]",
        `url = "${input.url}"`,
        `http_headers = { Authorization = "Bearer ${input.token}" }`,
      ].join("\n");
    case "gemini":
      return `gemini mcp add --transport http --scope user --header "Authorization: Bearer ${input.token}" knot ${input.url}`;
    case "skill": {
      const dirs = SKILL_INSTALL_DIRS.join(" ");
      const copies = SKILL_INSTALL_DIRS.map((dir) => `cp "${input.skillPath}" ${dir}/SKILL.md`).join(" && ");
      return `mkdir -p ${dirs} && ${copies}`;
    }
  }
}

/** 미리보기용. 토큰을 자리표시자로 바꾼다. 토큰이 없는 스니펫은 그대로다 */
export function maskToken(snippet: string, token: string): string {
  return token.length === 0 ? snippet : snippet.split(token).join(TOKEN_PLACEHOLDER);
}
