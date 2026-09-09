import { describe, expect, it } from "vitest";
import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";

const ROOT = join(__dirname, "..");

/**
 * 계약 검사(`desktop/CLAUDE.md` 검증 절, 2026-09-09 `L1` 착수로 범위 조정).
 *
 * 옛 `s3Residue.test.ts`는 `src` 전체에서 LLM 호출·`src/main/llm`·preload `llm?:`의 부재를 검사했지만,
 * 트랙 L(기획서 6.5)은 그 셋을 의도적으로 만든다. 남기는 것은 (1) MCP 서버·에이전트 브리지는 여전히
 * LLM을 부르지 않는다, (2) CLI 바이너리 실행과 사용자 CLI 자격증명 접근은 `src` 전체에서 계속 금지다.
 */
describe("에이전트 경로 잔재", () => {
  it("src/main/chat에는 서버 API 클라이언트만 있다(프롬프트·LLM 호출은 src/main/llm)", () => {
    expect(readdirSync(join(ROOT, "src/main/chat"))).toEqual(["knotApi.ts"]);
  });

  it("preload 계약에 agent·llm API가 있고 폐기된 S3 모양(chat·UserLlmSettings)은 없다", () => {
    const api = readFileSync(join(ROOT, "src/shared/api.ts"), "utf8");
    expect(api).not.toMatch(/chat\?:|UserLlmSettings|ChatStreamEvent|knot:chat-/);
    expect(api).toMatch(/agent\?:/);
    expect(api).toMatch(/llm\?:/);
    expect(api).toContain("knot:agent-status");
    expect(api).toContain("knot:llm-status");
  });

  it("MCP 서버·에이전트 브리지는 LLM API를 부르지 않고 자격증명을 다루지 않는다(불변 계약 2번)", () => {
    const files = [...collect(join(ROOT, "src/mcp")), ...collect(join(ROOT, "src/main/agent"))];
    const forbidden =
      /api\.anthropic\.com|api\.openai\.com|generativelanguage\.googleapis|chat\/completions|\/v1\/messages|x-api-key|ANTHROPIC_API_KEY|OPENAI_API_KEY|GEMINI_API_KEY|sk-ant-|src\/main\/llm|subscription-auth/;
    for (const file of files) {
      expect(stripComments(readFileSync(file, "utf8")), file).not.toMatch(forbidden);
    }
  });

  it("소스 어디에도 CLI 바이너리 실행·사용자 CLI 자격증명 접근이 없다(기획서 2.2 비목표)", () => {
    // 사용자 CLI의 자격증명 파일(`~/.claude/.credentials.json`·`~/.claude.json`·Keychain)과 바이너리 실행만 금지한다.
    // `registration.ts`의 스킬 설치 스니펫(`~/.claude/skills`·`~/.codex/config.toml`)은 사용자가 복사해 쓰는 문자열이라 허용
    const forbidden =
      /child_process|execFile\(|spawn\(|CLAUDE_CODE_OAUTH_TOKEN|\.claude\.json|\.credentials\.json|Claude Code-credentials|~\/\.claude\/(?!skills)|~\/\.codex\/(?!config\.toml)|~\/\.gemini\/(?!settings\.json)|find-generic-password|keytar/;
    for (const file of collect(join(ROOT, "src"))) {
      expect(stripComments(readFileSync(file, "utf8")), file).not.toMatch(forbidden);
    }
  });
});

/** 주석의 "읽지 않는다" 문구는 허용한다. 실제 문자열·식별자만 본다 */
function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
}

function collect(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    return entry.isDirectory() ? collect(path) : entry.name.endsWith(".ts") ? [path] : [];
  });
}
