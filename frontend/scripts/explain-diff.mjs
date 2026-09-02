#!/usr/bin/env node
/**
 * pnpm explain-diff — 기존 `/explain-diff-html` 스킬을 `claude -p`로 비대화 실행하는 스크립트.
 *
 * - 호출 방식(토큰·allowedTools·stream-json 진행 출력·실패 분류)은 scripts/review.mjs 와 동일하다.
 * - 토큰은 `frontend/.env.local`(gitignored)의 CLAUDE_CODE_OAUTH_TOKEN 또는 셸 환경변수에서 읽는다.
 * - 토큰 값은 어떤 출력·로그에도 남기지 않는다.
 * - 결과 HTML 은 /tmp/<YYYY-MM-DD>-explanation-<브랜치>.html 에 저장하고 기본 브라우저로 연다.
 *   gist·Artifact 게시는 하지 않는다. 공유용 링크가 필요하면 pnpm pr-content 를 사용한다.
 */
import { spawn, spawnSync } from "node:child_process";
import { existsSync, readFileSync, rmSync, statSync } from "node:fs";
import { createInterface } from "node:readline";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseEnv } from "node:util";

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const ENV_FILE = resolve(FRONTEND_DIR, ".env.local");
const TOKEN_KEY = "CLAUDE_CODE_OAUTH_TOKEN";
const TMP_DIR = "/tmp";
const BASE_BRANCH = "develop";
const FILE_LIMIT_NOTICE = 20;

/** 종료 코드: 실행 실패 원인만 구분한다. */
const EXIT = {
  OK: 0,
  UNKNOWN: 1,
  TOKEN_MISSING: 2,
  CLI_MISSING: 3,
  AUTH_FAILED: 4,
  RATE_LIMITED: 5,
  NO_TARGET: 6,
  GIT: 7,
};

/**
 * 스킬에는 allowed-tools frontmatter 가 없으므로 스크립트가 정한다.
 * 비대화 모드는 권한 프롬프트에 답할 수 없어 사전에 허용한다. 코드 수정은 정책으로 금지한다.
 */
const ALLOWED_TOOLS = [
  "Agent",
  "Task",
  "Bash(git diff:*)",
  "Bash(git log:*)",
  "Bash(git show:*)",
  "Bash(git branch:*)",
  "Bash(git status:*)",
  "Bash(git merge-base:*)",
  "Bash(git ls-files:*)",
  "Read",
  "Write",
  "Glob",
  "Grep",
  "Skill",
];

const HELP = `사용법: pnpm explain-diff [옵션]

기존 /explain-diff-html 스킬을 claude -p 로 비대화 실행하여 변경 설명 HTML(배경·직관·코드·퀴즈)을
${TMP_DIR}/<YYYY-MM-DD>-explanation-<브랜치>.html 에 저장하고 기본 브라우저로 엽니다.
설명 대상은 기본적으로 ${BASE_BRANCH}...HEAD 의 커밋된 변경입니다.

옵션:
  --include-working-tree   커밋되지 않은 워킹 트리 변경(스테이징·미스테이징·미추적)도 설명 대상에 포함
  --no-open                완료 후 브라우저로 열지 않고 경로만 출력
  --dry-run                claude 를 실행하지 않고 비교 기준·대상 파일·실행 인자만 출력
  -h, --help               도움말

종료 코드:
  0  작성 완료
  1  기타 실패
  2  ${TOKEN_KEY} 미설정
  3  claude CLI 없음
  4  인증 실패 (토큰이 잘못됐거나 만료)
  5  구독 사용량 한도 초과
  6  설명 대상 없음
  7  git 기준(${BASE_BRANCH}) 확인 실패
`;

// ---------- 출력 유틸 ----------

let secret = "";

function scrub(text) {
  if (!secret) return text;
  return String(text).split(secret).join("[REDACTED]");
}

function info(message) {
  process.stdout.write(`${scrub(message)}\n`);
}

function warn(message) {
  process.stderr.write(`[경고] ${scrub(message)}\n`);
}

function fail(code, message, hint) {
  process.stderr.write(`\n[실패] ${scrub(message)}\n`);
  if (hint) process.stderr.write(`${scrub(hint)}\n`);
  process.exit(code);
}

// ---------- 인자 ----------

function parseArgs(argv) {
  const options = { includeWorkingTree: false, open: true, dryRun: false };
  for (const arg of argv) {
    if (arg === "--include-working-tree") options.includeWorkingTree = true;
    else if (arg === "--no-open") options.open = false;
    else if (arg === "--dry-run") options.dryRun = true;
    else if (arg === "-h" || arg === "--help") {
      process.stdout.write(HELP);
      process.exit(EXIT.OK);
    } else {
      process.stderr.write(`알 수 없는 옵션입니다: ${arg}\n\n${HELP}`);
      process.exit(EXIT.UNKNOWN);
    }
  }
  return options;
}

// ---------- 사전 검사 ----------

function checkClaudeCli() {
  const result = spawnSync("claude", ["--version"], { encoding: "utf8" });
  if (result.error || result.status !== 0) {
    fail(
      EXIT.CLI_MISSING,
      "claude CLI 를 찾을 수 없습니다.",
      "Claude Code 를 설치한 뒤 다시 실행하세요: https://docs.claude.com/ko/docs/claude-code/setup",
    );
  }
  return result.stdout.trim();
}

function loadToken() {
  const fromShell = process.env[TOKEN_KEY]?.trim();
  if (fromShell) return { token: fromShell, source: "셸 환경변수" };

  if (existsSync(ENV_FILE)) {
    let parsed = {};
    try {
      parsed = parseEnv(readFileSync(ENV_FILE, "utf8"));
    } catch {
      fail(EXIT.TOKEN_MISSING, `${ENV_FILE} 을 읽지 못했습니다.`);
    }
    const fromFile = parsed[TOKEN_KEY]?.trim();
    if (fromFile) return { token: fromFile, source: "frontend/.env.local" };
  }

  fail(
    EXIT.TOKEN_MISSING,
    `${TOKEN_KEY} 이 설정되어 있지 않습니다.`,
    [
      "구독 소유자에게 전달받은 토큰을 frontend/.env.local 에 아래 형식으로 저장하세요. (이 파일은 gitignored 입니다)",
      "",
      `  ${TOKEN_KEY}=<전달받은 토큰>`,
      "",
    ].join("\n"),
  );
}

function git(args, { allowFailure = false } = {}) {
  const result = spawnSync("git", args, {
    cwd: FRONTEND_DIR,
    encoding: "utf8",
  });
  if (result.status !== 0 && !allowFailure) {
    fail(
      EXIT.GIT,
      `git ${args.join(" ")} 실행에 실패했습니다.`,
      result.stderr?.trim(),
    );
  }
  return {
    ok: result.status === 0,
    stdout: (result.stdout ?? "").trim(),
    stderr: (result.stderr ?? "").trim(),
  };
}

function refExists(ref) {
  return git(["rev-parse", "--verify", "--quiet", `${ref}^{commit}`], {
    allowFailure: true,
  }).ok;
}

/** develop 이 없거나 뒤처지면 origin/develop 을 fetch 해 기준으로 쓴다. */
function resolveBaseRef() {
  const fetched = git(["fetch", "--quiet", "origin", BASE_BRANCH], {
    allowFailure: true,
  });
  if (!fetched.ok)
    warn(
      `origin/${BASE_BRANCH} fetch 에 실패해 로컬 기준으로 진행합니다. (${fetched.stderr || "네트워크 확인"})`,
    );

  const hasLocal = refExists(BASE_BRANCH);
  const hasRemote = refExists(`origin/${BASE_BRANCH}`);

  if (!hasLocal && !hasRemote) {
    fail(
      EXIT.GIT,
      `${BASE_BRANCH} 브랜치를 로컬과 origin 어디에서도 찾을 수 없습니다.`,
    );
  }
  if (!hasLocal) {
    info(
      `로컬 ${BASE_BRANCH} 가 없어 origin/${BASE_BRANCH} 를 기준으로 사용합니다.`,
    );
    return `origin/${BASE_BRANCH}`;
  }
  if (!hasRemote) return BASE_BRANCH;

  const localSha = git(["rev-parse", BASE_BRANCH]).stdout;
  const remoteSha = git(["rev-parse", `origin/${BASE_BRANCH}`]).stdout;
  if (localSha === remoteSha) return BASE_BRANCH;

  const localIsBehind = git(
    ["merge-base", "--is-ancestor", BASE_BRANCH, `origin/${BASE_BRANCH}`],
    { allowFailure: true },
  ).ok;
  if (localIsBehind) {
    info(
      `로컬 ${BASE_BRANCH} 가 origin 보다 뒤처져 있어 origin/${BASE_BRANCH} 를 기준으로 사용합니다.`,
    );
    return `origin/${BASE_BRANCH}`;
  }
  return BASE_BRANCH;
}

/** 설명 가치가 없는 생성물은 제외한다. 삭제된 파일은 변경의 일부이므로 포함한다. */
function isExplainTarget(filePath) {
  if (/(^|\/)pnpm-lock\.yaml$/.test(filePath)) return false;
  if (/(^|\/)(dist|node_modules)\//.test(filePath)) return false;
  return true;
}

function listTargets(diffArgs) {
  const output = git(["diff", "--name-status", "-M", ...diffArgs]).stdout;
  if (!output) return [];
  return output
    .split("\n")
    .map((line) => line.split("\t"))
    .filter(([, ...paths]) => isExplainTarget(paths.at(-1)))
    .map(([status, ...paths]) => ({ status: status[0], path: paths.at(-1) }));
}

function listUntracked() {
  const output = git([
    "ls-files",
    "--others",
    "--exclude-standard",
    "--full-name",
    ":/",
  ]).stdout;
  return output ? output.split("\n").filter(isExplainTarget) : [];
}

/** 파일명 규칙: 브랜치명의 `/`, `#`, 공백을 `-` 로 치환. (pnpm pr-content 와 동일) */
function branchSlug(branch) {
  return branch
    .replace(/[/#\\\s]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

/** 스킬의 `YYYY-MM-DD-` 접두 규칙에 맞는 로컬 날짜. */
function todayLocal() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

// ---------- 정책 프롬프트 ----------

function buildPolicy({
  branch,
  baseRef,
  mergeBase,
  diffSpec,
  includeWorkingTree,
  targets,
  untracked,
  htmlPath,
}) {
  const scope = includeWorkingTree
    ? [
        `- 설명 대상: 워킹 트리 포함. 모든 diff 는 \`git diff ${mergeBase}\` (인자 없이 워킹 트리와 비교) 를 사용합니다. \`${mergeBase}\` 는 \`${baseRef}\` 와 HEAD 의 merge-base 입니다.`,
        untracked.length
          ? `- 미추적 파일 ${untracked.length}개(${untracked.join(", ")})는 diff 에 나오지 않으므로 신규 파일로 취급해 전체 내용을 읽고 설명에 포함합니다.`
          : "- 미추적 파일은 없습니다.",
      ]
    : [
        `- 설명 대상: \`${diffSpec}\` (3점 diff). 커밋되지 않은 워킹 트리 변경은 사용자가 제외하기로 했으므로 다루지 않고, 포함 여부를 묻지 않습니다.`,
      ];

  return [
    "## 비대화 실행 정책 (pnpm explain-diff)",
    "",
    "이 세션은 `pnpm explain-diff` 스크립트가 `claude -p` 비대화 모드로 실행했습니다. 사용자에게 질문하거나 확인을 요청할 수 없으므로 아래 고정 정책을 따르고, 판단이 필요한 사항은 묻지 않고 합리적으로 정해 진행합니다. 아래 정책은 /explain-diff-html 스킬 본문의 파일 경로·형식 지시보다 우선합니다.",
    "",
    `- 현재 브랜치: \`${branch}\``,
    `- 기준 브랜치: \`${baseRef}\` — 스크립트가 이미 \`git fetch origin ${BASE_BRANCH}\` 을 수행하고 기준을 확정했습니다. 다시 fetch 하거나 기준 변경 여부를 묻지 않습니다.`,
    ...scope,
    `- 변경 파일: ${targets.length}개 (${targets.join(", ")}). Background 섹션을 위해 변경 파일 주변의 기존 코드도 넓게 읽습니다.`,
    `- 저장 경로: 반드시 \`${htmlPath}\` 에 저장합니다. 날짜는 스크립트가 확정했으므로 \`date\` 를 실행하지 않고, 다른 파일명이나 경로를 만들지 않으며 레포 안에는 아무것도 쓰지 않습니다.`,
    '- HTML 형식: 브라우저에서 파일을 직접 여는 독립 페이지입니다. `<!DOCTYPE html>`, `<html lang="ko">`, `<head>`(meta charset·viewport, `<title>`, `<style>`), `<body>` 를 갖춘 완전한 문서로 작성합니다. 색은 CSS 변수로 정의해 `:root` 에 라이트 기본값을 두고 `prefers-color-scheme: dark` 에서 재정의합니다. 외부 스크립트·스타일시트·이미지·폰트 없이 자체 완결이어야 하며, 퀴즈 피드백은 alert 대신 인라인으로 표시합니다. 코드 블록은 `<pre>` 를 사용합니다.',
    "- 본문 언어: 한국어 높임말로 작성합니다. 코드 식별자와 기술 용어는 원문을 유지합니다.",
    "- 게시·열기는 하지 않습니다. Artifact 게시, `open`, `code` 실행을 시도하지 않습니다. 저장이 끝나면 스크립트가 브라우저로 엽니다.",
    "- 코드를 수정하지 않습니다.",
    "- 마지막 응답은 아래 형식으로만 작성합니다. `비고` 줄은 알려야 할 사항이 있을 때만 씁니다.",
    `  \`설명 HTML: ${htmlPath}\``,
    "  `비고: <설명에서 다루지 못한 범위 등>`",
  ].join("\n");
}

// ---------- claude 실행 ----------

function summarizeToolUse(block) {
  const input = block.input ?? {};
  switch (block.name) {
    case "Agent":
    case "Task":
      return input.description ?? "";
    case "Skill":
      return `/${input.skill ?? ""} ${(input.args ?? "").split("\n")[0].slice(0, 80)}`.trim();
    case "Bash":
      return (input.command ?? "").split("\n")[0].slice(0, 100);
    case "Read":
    case "Write":
      return input.file_path ?? "";
    case "Grep":
    case "Glob":
      return input.pattern ?? "";
    default:
      return "";
  }
}

function runClaude({ token, prompt, policy }) {
  const args = [
    "-p",
    prompt,
    "--output-format",
    "stream-json",
    "--verbose",
    "--add-dir",
    TMP_DIR,
    "--append-system-prompt",
    policy,
    "--allowedTools",
    ...ALLOWED_TOOLS,
  ];

  return new Promise((resolvePromise) => {
    const child = spawn("claude", args, {
      cwd: FRONTEND_DIR,
      env: { ...process.env, [TOKEN_KEY]: token },
      stdio: ["ignore", "pipe", "pipe"],
    });

    let result = null;
    let rateLimitHit = false;
    let stderrTail = "";

    child.stderr.on("data", (chunk) => {
      stderrTail = (stderrTail + chunk.toString()).slice(-4000);
    });

    createInterface({ input: child.stdout }).on("line", (line) => {
      let event;
      try {
        event = JSON.parse(line);
      } catch {
        return;
      }

      if (event.type === "system" && event.subtype === "init") {
        info(`claude 세션 시작 (model: ${event.model ?? "?"})`);
        return;
      }
      if (event.type === "rate_limit_event") {
        const status = event.rate_limit_info?.status;
        if (status && status !== "allowed") {
          rateLimitHit = true;
          warn(`구독 사용량 상태: ${status}`);
        }
        return;
      }
      if (event.type === "assistant" && !event.parent_tool_use_id) {
        for (const block of event.message?.content ?? []) {
          if (block.type === "text" && block.text?.trim()) {
            info(
              block.text
                .trim()
                .split("\n")
                .map((l) => `  ${l}`)
                .join("\n"),
            );
          } else if (block.type === "tool_use") {
            info(`  ▸ ${block.name} ${summarizeToolUse(block)}`.trimEnd());
          }
        }
        return;
      }
      if (event.type === "result") result = event;
    });

    child.on("error", (error) => {
      resolvePromise({
        result,
        rateLimitHit,
        stderrTail,
        exitCode: null,
        spawnError: error,
      });
    });
    child.on("close", (exitCode) => {
      resolvePromise({
        result,
        rateLimitHit,
        stderrTail,
        exitCode,
        spawnError: null,
      });
    });
  });
}

function classifyFailure({
  result,
  rateLimitHit,
  stderrTail,
  exitCode,
  spawnError,
}) {
  if (spawnError) {
    return {
      code: EXIT.CLI_MISSING,
      message: `claude 실행에 실패했습니다: ${spawnError.message}`,
    };
  }

  if (result && !result.is_error && exitCode === 0) return null;

  const text = `${result?.result ?? ""}\n${stderrTail}`;
  const status = result?.api_error_status;

  if (
    status === 401 ||
    status === 403 ||
    /authenticat|OAuth .*invalid|not logged in|login/i.test(text)
  ) {
    return {
      code: EXIT.AUTH_FAILED,
      message: "인증에 실패했습니다. 토큰이 잘못됐거나 만료됐을 수 있습니다.",
      hint: "구독 소유자에게 토큰을 다시 받아 frontend/.env.local 의 CLAUDE_CODE_OAUTH_TOKEN 을 갱신하세요.",
    };
  }
  if (
    status === 429 ||
    rateLimitHit ||
    /rate.?limit|usage limit|hit your limit|limit reached/i.test(text)
  ) {
    return {
      code: EXIT.RATE_LIMITED,
      message: "구독 사용량 한도에 도달했습니다.",
      hint: "한도가 초기화된 뒤 다시 실행하거나 구독 소유자에게 상태를 확인하세요.",
    };
  }
  if (result?.is_error || exitCode !== 0) {
    return {
      code: EXIT.UNKNOWN,
      message: `claude 가 비정상 종료했습니다. (exit ${exitCode ?? "?"}, ${result?.terminal_reason ?? "이유 불명"})`,
      hint:
        (result?.result || stderrTail || "").trim().slice(-1500) || undefined,
    };
  }
  return null;
}

// ---------- 결과 열기 ----------

function openerCommand() {
  if (process.platform === "darwin") return "open";
  if (process.platform === "linux") return "xdg-open";
  return null;
}

/** 기본 브라우저로 HTML 을 연다. 실패해도 결과에는 영향이 없다. */
function openInBrowser(htmlPath) {
  const command = openerCommand();
  if (!command) {
    warn(
      `이 플랫폼에서는 자동으로 열 수 없습니다. 브라우저에서 직접 여세요: ${htmlPath}`,
    );
    return;
  }
  const result = spawnSync(command, [htmlPath], { stdio: "ignore" });
  if (result.error || result.status !== 0) {
    warn(
      `\`${command}\` 로 열지 못했습니다. 브라우저에서 직접 여세요: ${htmlPath}`,
    );
  }
}

// ---------- main ----------

async function main() {
  const options = parseArgs(process.argv.slice(2));

  const claudeVersion = checkClaudeCli();
  const { token, source } = loadToken();
  secret = token;
  info(`claude ${claudeVersion} / 토큰 출처: ${source}`);

  if (!git(["rev-parse", "--is-inside-work-tree"], { allowFailure: true }).ok) {
    fail(EXIT.GIT, "git 저장소 안에서 실행해야 합니다.");
  }

  const branch = git(["rev-parse", "--abbrev-ref", "HEAD"]).stdout;
  if (branch === BASE_BRANCH) {
    fail(
      EXIT.NO_TARGET,
      `현재 브랜치가 ${BASE_BRANCH} 입니다. 피처 브랜치에서 실행하세요.`,
    );
  }

  const baseRef = resolveBaseRef();
  const mergeBase = git(["merge-base", baseRef, "HEAD"], {
    allowFailure: true,
  });
  if (!mergeBase.ok) {
    fail(
      EXIT.GIT,
      `${baseRef} 와 HEAD 의 공통 조상을 찾을 수 없습니다.`,
      mergeBase.stderr,
    );
  }

  const diffSpec = `${baseRef}...HEAD`;
  const targets = options.includeWorkingTree
    ? listTargets([mergeBase.stdout])
    : listTargets([diffSpec]);
  const untracked = options.includeWorkingTree ? listUntracked() : [];
  const totalTargets = targets.length + untracked.length;
  const scopeLabel = options.includeWorkingTree
    ? `${mergeBase.stdout.slice(0, 7)} (${baseRef} merge-base) 대비 워킹 트리`
    : diffSpec;

  if (!options.includeWorkingTree) {
    const dirty = git(["status", "--porcelain"]).stdout;
    if (dirty) {
      const count = dirty.split("\n").length;
      info(
        `커밋되지 않은 변경 ${count}건은 설명에서 제외합니다. 포함하려면 --include-working-tree 를 사용하세요.`,
      );
    }
  }

  if (totalTargets === 0) {
    fail(
      EXIT.NO_TARGET,
      `설명 대상이 없습니다. (${scopeLabel} 에 변경 파일 없음)`,
      options.includeWorkingTree
        ? undefined
        : "커밋되지 않은 변경을 설명하려면 --include-working-tree 를 사용하세요.",
    );
  }

  info(`설명 대상: ${scopeLabel}`);
  info(`변경 파일: ${totalTargets}개`);
  for (const t of targets) info(`  ${t.status}  ${t.path}`);
  for (const p of untracked) info(`  ?  ${p}`);
  if (totalTargets > FILE_LIMIT_NOTICE) {
    warn(
      `변경 파일이 ${FILE_LIMIT_NOTICE}개를 초과합니다(${totalTargets}개). 토큰 소모가 크고 시간이 오래 걸릴 수 있지만 그대로 진행합니다.`,
    );
  }

  const htmlPath = `${TMP_DIR}/${todayLocal()}-explanation-${branchSlug(branch)}.html`;
  const prompt = `/explain-diff-html 대상: ${scopeLabel} (현재 브랜치 ${branch})`;
  const policy = buildPolicy({
    branch,
    baseRef,
    mergeBase: mergeBase.stdout,
    diffSpec,
    includeWorkingTree: options.includeWorkingTree,
    targets: [...targets.map((t) => t.path), ...untracked],
    untracked,
    htmlPath,
  });

  if (options.dryRun) {
    info(
      "\n[dry-run] claude 를 실행하지 않습니다. 아래는 주입할 정책입니다.\n",
    );
    info(policy);
    info(`\n[dry-run] prompt: ${prompt}`);
    info(`[dry-run] allowedTools: ${ALLOWED_TOOLS.join(", ")}`);
    info(`[dry-run] 저장 예정 경로: ${htmlPath}`);
    info(
      `[dry-run] 완료 후: ${options.open ? `${openerCommand() ?? "(자동 열기 불가)"} 로 브라우저에서 열기` : "열지 않음 (--no-open)"}`,
    );
    process.exit(EXIT.OK);
  }

  // Write 도구는 읽지 않은 기존 파일을 덮어쓰지 못하므로, 같은 날 같은 브랜치의 이전 결과는 미리 지운다.
  if (existsSync(htmlPath)) {
    rmSync(htmlPath, { force: true });
    info(`이전 결과 ${htmlPath} 를 지우고 새로 만듭니다.`);
  }
  info(`\n변경 설명 작성을 시작합니다. 결과는 ${htmlPath} 에 저장됩니다.\n`);

  const outcome = await runClaude({ token, prompt, policy });
  const failure = classifyFailure(outcome);
  if (failure) fail(failure.code, failure.message, failure.hint);

  const { result } = outcome;
  if (result?.permission_denials?.length) {
    warn(
      `권한이 거부된 도구 호출 ${result.permission_denials.length}건이 있었습니다. scripts/explain-diff.mjs 의 ALLOWED_TOOLS 를 확인하세요.`,
    );
    for (const denial of result.permission_denials)
      warn(
        `  ${denial.tool_name}: ${JSON.stringify(denial.tool_input ?? {}).slice(0, 120)}`,
      );
  }

  if (!existsSync(htmlPath) || statSync(htmlPath).size === 0) {
    fail(
      EXIT.UNKNOWN,
      `작업이 끝났지만 ${htmlPath} 가 생성되지 않았습니다.`,
      `마지막 응답: ${(result?.result ?? "").trim().slice(0, 500)}`,
    );
  }
  const html = readFileSync(htmlPath, "utf8");
  if (!/<title>/i.test(html) || !/<\/html>\s*$/i.test(html)) {
    warn(
      "HTML 에 <title> 이 없거나 </html> 로 끝나지 않습니다. 작성이 중간에 끊겼을 수 있으니 내용을 확인하세요.",
    );
  }

  const minutes = result?.duration_ms
    ? (result.duration_ms / 60000).toFixed(1)
    : "?";
  info("\n" + "=".repeat(60));
  info((result?.result ?? "").trim());
  info(`소요 시간: ${minutes}분 / 턴: ${result?.num_turns ?? "?"}`);
  info("=".repeat(60));

  info(`\n변경 설명 HTML: ${htmlPath} (${(html.length / 1024).toFixed(0)}KB)`);
  if (options.open) openInBrowser(htmlPath);
  process.exit(EXIT.OK);
}

main().catch((error) => {
  fail(
    EXIT.UNKNOWN,
    `예상하지 못한 오류가 발생했습니다: ${error?.message ?? error}`,
  );
});
