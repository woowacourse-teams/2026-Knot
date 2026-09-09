import { describe, expect, it } from "vitest";
import {
  TOKEN_PLACEHOLDER,
  buildRegistrationSnippet,
  isAgentRegistrationTarget,
  maskToken,
} from "../src/main/agent/registration";

const input = {
  url: "http://127.0.0.1:47871/mcp",
  token: "dG9rZW4tZm9yLXRlc3Rz",
  skillPath: "/Users/me/Library/Application Support/Knot/skills/knot/SKILL.md",
};

describe("CLI 등록 스니펫 (로드맵 Q50)", () => {
  it("Claude Code: claude mcp add --transport http + --header", () => {
    expect(buildRegistrationSnippet("claude-code", input)).toBe(
      'claude mcp add --transport http knot http://127.0.0.1:47871/mcp --header "Authorization: Bearer dG9rZW4tZm9yLXRlc3Rz"',
    );
  });

  it("Codex CLI: config.toml의 [mcp_servers.knot] + url + http_headers", () => {
    const snippet = buildRegistrationSnippet("codex", input);

    expect(snippet).toContain("[mcp_servers.knot]");
    expect(snippet).toContain('url = "http://127.0.0.1:47871/mcp"');
    expect(snippet).toContain('http_headers = { Authorization = "Bearer dG9rZW4tZm9yLXRlc3Rz" }');
  });

  it("Gemini CLI: gemini mcp add --transport http --scope user --header", () => {
    expect(buildRegistrationSnippet("gemini", input)).toBe(
      'gemini mcp add --transport http --scope user --header "Authorization: Bearer dG9rZW4tZm9yLXRlc3Rz" knot http://127.0.0.1:47871/mcp',
    );
  });

  it("스킬 설치: ~/.claude/skills/knot·~/.agents/skills/knot 두 곳에 cp, 토큰 없음", () => {
    const snippet = buildRegistrationSnippet("skill", input);

    expect(snippet).toContain("mkdir -p ~/.claude/skills/knot ~/.agents/skills/knot");
    expect(snippet).toContain(`cp "${input.skillPath}" ~/.claude/skills/knot/SKILL.md`);
    expect(snippet).toContain(`cp "${input.skillPath}" ~/.agents/skills/knot/SKILL.md`);
    expect(snippet).not.toContain(input.token);
  });

  it("미리보기는 토큰을 자리표시자로 가린다", () => {
    for (const target of ["claude-code", "codex", "gemini"] as const) {
      const preview = maskToken(buildRegistrationSnippet(target, input), input.token);
      expect(preview, target).not.toContain(input.token);
      expect(preview, target).toContain(TOKEN_PLACEHOLDER);
    }
    const skill = buildRegistrationSnippet("skill", input);
    expect(maskToken(skill, input.token)).toBe(skill);
  });

  it("등록 대상은 네 값뿐이다", () => {
    expect(isAgentRegistrationTarget("claude-code")).toBe(true);
    expect(isAgentRegistrationTarget("skill")).toBe(true);
    expect(isAgentRegistrationTarget("cursor")).toBe(false);
    expect(isAgentRegistrationTarget(undefined)).toBe(false);
  });
});
