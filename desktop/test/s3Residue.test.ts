import { describe, expect, it } from "vitest";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const ROOT = join(__dirname, "..");

/** 로드맵 S8 완료 판정: 폐기된 S3(데스크톱 사용자 LLM 클라이언트) 잔재 0건 */
describe("S3 잔재", () => {
  it("src/main/llm과 chat/prompt·chatService가 없다", () => {
    expect(existsSync(join(ROOT, "src/main/llm"))).toBe(false);
    expect(readdirSync(join(ROOT, "src/main/chat"))).toEqual(["knotApi.ts"]);
  });

  it("preload 계약에 chat·llm API가 없고 agent API가 있다", () => {
    const api = readFileSync(join(ROOT, "src/shared/api.ts"), "utf8");
    expect(api).not.toMatch(/chat\?:|llm\?:|UserLlmSettings|ChatStreamEvent|knot:chat-|knot:llm-/);
    expect(api).toMatch(/agent\?:/);
    expect(api).toContain("knot:agent-status");
  });

  it("소스 어디에도 LLM API 호출·LLM 자격증명 접근이 없다(불변 계약 2·3번)", () => {
    const files = collect(join(ROOT, "src"));
    const forbidden = /api\.anthropic\.com|api\.openai\.com|generativelanguage\.googleapis|chat\/completions|\/v1\/messages|x-api-key|ANTHROPIC_API_KEY|OPENAI_API_KEY|GEMINI_API_KEY|CLAUDE_CODE_OAUTH_TOKEN|\.claude\.json|\.credentials|Claude Code-credentials|child_process|execFile\(|spawn\(/;
    for (const file of files) {
      const source = readFileSync(file, "utf8");
      // 주석의 "읽지 않는다" 문구는 허용한다. 실제 문자열·식별자만 본다
      const code = source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
      expect(code, file).not.toMatch(forbidden);
    }
  });
});

function collect(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? collect(path) : entry.name.endsWith(".ts") ? [path] : [];
  });
}
