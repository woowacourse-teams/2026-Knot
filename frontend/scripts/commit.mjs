#!/usr/bin/env node
/**
 * pnpm commit — 기존 `/commit` 스킬을 `claude -p`로 비대화 실행하는 스크립트.
 *
 * - 호출 방식(토큰·allowedTools·stream-json 진행 출력·실패 분류)은 scripts/review.mjs 와 동일하다.
 * - 토큰은 `frontend/.env.local`(gitignored)의 CLAUDE_CODE_OAUTH_TOKEN 또는 셸 환경변수에서 읽는다.
 * - 토큰 값은 어떤 출력·로그에도 남기지 않는다.
 * - 비대화 세션은 사용자 승인을 받을 수 없으므로 claude 는 커밋 제안(파일 묶음·메시지)만 구조화
 *   출력으로 돌려주고, 승인 요청과 `git add`·`git commit`·`git push` 는 이 스크립트가 터미널에서
 *   사용자에게 직접 승인을 받은 뒤 수행한다. claude 세션에서는 git 쓰기 도구를 차단한다.
 * - 커밋 메시지에는 Co-Authored-By 등 공동 작성자 서명을 넣지 않고, 이슈 번호 트레일러를 보장한다.
 */
import { spawn, spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { createInterface } from "node:readline";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseEnv } from "node:util";

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const ENV_FILE = resolve(FRONTEND_DIR, ".env.local");
const TOKEN_KEY = "CLAUDE_CODE_OAUTH_TOKEN";
const REMOTE = "origin";
const PROTECTED_BRANCHES = ["main", "master", "develop", "dev"];
const SUBJECT_LIMIT = 50;

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
  CANCELLED: 8,
  PUSH_FAILED: 9,
};

/**
 * 스킬 1단계(변경사항 확인)에 필요한 읽기 명령만 허용한다.
 * 비대화 모드는 권한 프롬프트에 답할 수 없어 사전에 허용하며, 그 외 도구 호출은 자동 거부된다.
 */
const ALLOWED_TOOLS = [
  "Bash(git status:*)",
  "Bash(git diff:*)",
  "Bash(git log:*)",
  "Bash(git branch:*)",
  "Bash(git show:*)",
  "Read",
  "Glob",
  "Grep",
];

/** 저장소를 바꾸는 동작은 스크립트가 승인 후 직접 수행하므로 claude 세션에서는 명시적으로 차단한다. */
const DISALLOWED_TOOLS = [
  "Bash(git add:*)",
  "Bash(git commit:*)",
  "Bash(git push:*)",
  "Bash(git reset:*)",
  "Bash(git restore:*)",
  "Bash(git checkout:*)",
  "Bash(git stash:*)",
  "Bash(git rm:*)",
  "Bash(git mv:*)",
  "Write",
  "Edit",
  "MultiEdit",
  "NotebookEdit",
];

/** claude 가 돌려줄 커밋 제안의 구조. `--json-schema` 로 검증한다. */
const PROPOSAL_SCHEMA = {
  type: "object",
  properties: {
    commits: {
      type: "array",
      minItems: 1,
      items: {
        type: "object",
        properties: {
          files: {
            type: "array",
            minItems: 1,
            items: { type: "string" },
            description:
              "이 커밋에 넣을 파일 경로. 스크립트가 알려준 변경 파일 목록의 경로를 그대로 쓴다.",
          },
          message: {
            type: "string",
            description:
              "커밋 메시지 전문. 첫 줄 제목, 필요 시 빈 줄 뒤 본문, 마지막에 빈 줄 뒤 이슈 번호 트레일러.",
          },
        },
        required: ["files", "message"],
        additionalProperties: false,
      },
    },
    note: {
      type: "string",
      description:
        "사용자에게 알릴 사항(제외한 파일과 이유, 맥락이 섞인 변경 등). 없으면 빈 문자열.",
    },
  },
  required: ["commits"],
  additionalProperties: false,
};

const APPROVE_WORDS = new Set([
  "y",
  "yes",
  "ok",
  "네",
  "예",
  "ㅇ",
  "ㅇㅇ",
  "ㄱ",
  "ㄱㄱ",
  "ㄱㄱㄱ",
  "진행",
  "좋아",
  "좋아요",
]);
const CANCEL_WORDS = new Set([
  "",
  "n",
  "no",
  "ㄴ",
  "ㄴㄴ",
  "아니",
  "아니오",
  "아니요",
  "취소",
]);

const HELP = `사용법: pnpm commit [옵션]

기존 /commit 스킬을 claude -p 로 비대화 실행하여 커밋 제안(파일 묶음·메시지)을 받고,
터미널에서 승인하면 스크립트가 직접 git add → git commit → git push 를 수행합니다.

승인 프롬프트에서는 y(진행) / n(취소) 외에 수정 요청을 입력하면 같은 claude 세션에 전달해
제안을 다시 받습니다. 승인 전에는 저장소를 바꾸지 않습니다.

옵션:
  --issue <번호>   이슈 번호를 직접 지정 (기본: 브랜치명에서 추출). 모든 커밋의 트레일러로 들어갑니다.
  --no-push        커밋만 하고 푸시하지 않음
  --dry-run        claude 를 실행하지 않고 변경 파일·이슈·실행 인자만 출력
  -h, --help       도움말

종료 코드:
  0  완료
  1  기타 실패
  2  ${TOKEN_KEY} 미설정
  3  claude CLI 없음
  4  인증 실패 (토큰이 잘못됐거나 만료)
  5  구독 사용량 한도 초과
  6  커밋할 변경 없음
  7  git 실행 실패
  8  사용자가 취소함
  9  푸시 실패 (커밋은 로컬에 남아 있음)
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

function indent(text, prefix = "  ") {
  return String(text)
    .split("\n")
    .map((line) => `${prefix}${line}`)
    .join("\n");
}

// ---------- 인자 ----------

function parseIssueNumber(raw, origin) {
  if (!/^\d+$/.test(raw ?? "") || Number(raw) < 1) {
    process.stderr.write(
      `${origin} 값은 1 이상의 정수여야 합니다: ${raw ?? "(없음)"}\n\n${HELP}`,
    );
    process.exit(EXIT.UNKNOWN);
  }
  return Number(raw);
}

function parseArgs(argv) {
  const options = { issue: null, push: true, dryRun: false };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--issue") {
      options.issue = parseIssueNumber(argv[i + 1], "--issue");
      i += 1;
    } else if (arg.startsWith("--issue=")) {
      options.issue = parseIssueNumber(arg.slice("--issue=".length), "--issue");
    } else if (arg === "--no-push") options.push = false;
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

// ---------- git ----------

/**
 * `git status --porcelain` 은 하위 디렉터리에서도 저장소 루트 기준 경로를 찍지만 `git add` 는
 * 현재 디렉터리 기준으로 해석하므로, 모든 git 명령은 저장소 루트에서 실행해 경로 기준을 통일한다.
 */
let repoRoot = FRONTEND_DIR;

function git(args, { allowFailure = false, input } = {}) {
  const result = spawnSync("git", args, {
    cwd: repoRoot,
    encoding: "utf8",
    input,
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

/**
 * 워킹 트리의 변경 파일을 저장소 루트 기준 경로로 모은다.
 * 미추적 디렉터리는 파일 단위로 펼치고(`--untracked-files=all`), 이름 변경은 원래 경로를 함께 보관한다.
 */
function listChanges() {
  const raw = spawnSync(
    "git",
    ["status", "--porcelain=v1", "-z", "--untracked-files=all"],
    { cwd: repoRoot, encoding: "utf8" },
  );
  if (raw.status !== 0) {
    fail(EXIT.GIT, "git status 실행에 실패했습니다.", raw.stderr?.trim());
  }

  const fields = (raw.stdout ?? "").split("\0");
  const changes = [];
  for (let i = 0; i < fields.length; i += 1) {
    const entry = fields[i];
    if (!entry) continue;
    const xy = entry.slice(0, 2);
    const path = entry.slice(3);
    const change = { xy, path, from: null };
    if (xy[0] === "R" || xy[0] === "C") {
      i += 1;
      change.from = fields[i] ?? null;
    }
    changes.push(change);
  }
  return changes;
}

function isConflict(xy) {
  return xy.includes("U") || xy === "AA" || xy === "DD";
}

function isStaged(xy) {
  return xy[0] !== " " && xy[0] !== "?";
}

function formatChange(change) {
  return change.from
    ? `${change.xy} ${change.from} -> ${change.path}`
    : `${change.xy} ${change.path}`;
}

/** 스킬은 이슈 번호를 트레일러에 무조건 넣는다. 우선순위: --issue 옵션 → 브랜치명. 못 찾으면 경고만 한다. */
function resolveIssueNumber({ explicit, branch }) {
  if (explicit) return { number: explicit, source: "--issue 옵션" };
  const fromBranch = branch.match(/#(\d+)/);
  if (fromBranch) return { number: Number(fromBranch[1]), source: "브랜치명" };
  return null;
}

// ---------- 정책 프롬프트 ----------

function buildPolicy({ branch, prefix, changes, staged, issue }) {
  const issueLine = issue
    ? `- 이슈 번호: \`#${issue.number}\` (출처: ${issue.source}). 스크립트가 이미 확정했으므로 다시 추출하거나 묻지 않습니다. 모든 커밋 메시지의 마지막 줄은 빈 줄 뒤 \`#${issue.number}\` 트레일러 한 줄입니다.`
    : "- 이슈 번호: 브랜치명에서 찾지 못했습니다. 트레일러를 넣지 않으며, 커밋 메시지나 브랜치명에서 추측해 넣지도 않습니다.";

  const stagedLine = staged.length
    ? `- 이미 스테이징된 파일 ${staged.length}개(${staged.map((c) => c.path).join(", ")})는 포함 여부를 사용자에게 묻지 않고 다른 변경과 동일하게 커밋 후보로 다룹니다. 스크립트가 승인 후 파일 단위로 다시 add 합니다.`
    : "- 이미 스테이징된 파일은 없습니다.";

  return [
    "## 비대화 실행 정책 (pnpm commit)",
    "",
    "이 세션은 `pnpm commit` 스크립트가 `claude -p` 비대화 모드로 실행했습니다. 사용자에게 질문하거나 승인을 요청할 수 없으므로, /commit 스킬에서 사용자 승인이 필요한 단계와 실제 git 쓰기 동작은 아래 고정 정책으로 대체합니다. 아래 정책은 스킬 본문의 지시보다 우선합니다.",
    "",
    "- 역할 분담: 이 세션은 **커밋 제안만** 만듭니다. 승인 요청과 `git add`·`git commit`·`git push` 는 스크립트가 터미널에서 사용자 승인을 받은 뒤 직접 수행합니다. 따라서 저장소를 바꾸는 git 명령(`add`, `commit`, `push`, `reset`, `restore`, `checkout`, `stash` 등)을 실행하지 않고, 승인을 기다리지도 않습니다.",
    `- 현재 브랜치: \`${branch}\``,
    `- 커밋 후보: 스크립트가 확인한 변경 파일 ${changes.length}개. 경로는 저장소 루트 기준이며(현재 디렉터리 접두사: \`${prefix || "(없음)"}\`), \`git status\` 출력과 같은 형식입니다.`,
    ...changes.map((c) => `    ${formatChange(c)}`),
    "  이 목록에 없는 경로는 제안에 넣지 않습니다. `git status`, `git diff`, `git diff --staged`, `git log -n 5 --oneline` 으로 실제 변경 내용과 기존 커밋 컨벤션을 확인해 변경 의도를 파악합니다.",
    stagedLine,
    issueLine,
    "- 커밋 분리: 변경을 맥락별로 최대한 작게 나눠 여러 커밋으로 제안합니다. 분리 여부를 사용자에게 묻지 않고 스스로 판단합니다. 한 파일은 하나의 커밋에만 넣고, `files` 에는 위 후보 목록의 경로를 그대로 씁니다. 커밋에서 제외할 파일이 있으면 어떤 커밋에도 넣지 않고 `note` 에 이유를 적습니다. 커밋은 배열 순서대로 만들어지므로 의존 관계(예: 유틸 추가 → 사용)를 고려해 정렬합니다.",
    `- 커밋 메시지: 첫 줄은 \`<type>: <제목>\` (${SUBJECT_LIMIT}자 이내, 한글 명령형). 변경 이유·맥락이 필요할 때만 빈 줄 뒤 본문(각 줄 72자 이내)을 씁니다. \`Co-Authored-By\` 등 공동 작성자 서명이나 Claude·생성 도구 관련 문구는 절대 넣지 않습니다.`,
    "- 코드를 수정하지 않고 파일을 만들거나 쓰지 않습니다.",
    "- 결과 반환: 스킬의 '사용자 확인 요청 형식' 대신 구조화 출력(`commits[].files`, `commits[].message`, `note`)으로 제안을 반환합니다. 사용자에게 알릴 사항(제외한 파일과 이유, 맥락이 섞인 변경 등)은 `note` 에 짧게 적고, 없으면 빈 문자열로 둡니다.",
    "- 수정 요청: 이후 스크립트가 사용자의 수정 요청이나 검증 오류를 전달하면 반영해 같은 형식으로 **전체 제안을 다시** 반환합니다.",
  ].join("\n");
}

// ---------- claude 실행 ----------

function summarizeToolUse(block) {
  const input = block.input ?? {};
  switch (block.name) {
    case "Bash":
      return (input.command ?? "").split("\n")[0].slice(0, 100);
    case "Read":
      return input.file_path ?? "";
    case "Grep":
    case "Glob":
      return input.pattern ?? "";
    default:
      return "";
  }
}

function runClaude({ token, policy, prompt, resumeSessionId }) {
  const args = [
    "-p",
    prompt,
    "--output-format",
    "stream-json",
    "--verbose",
    "--append-system-prompt",
    policy,
    "--json-schema",
    JSON.stringify(PROPOSAL_SCHEMA),
    ...(resumeSessionId ? ["--resume", resumeSessionId] : []),
    "--allowedTools",
    ...ALLOWED_TOOLS,
    "--disallowedTools",
    ...DISALLOWED_TOOLS,
  ];

  return new Promise((resolvePromise) => {
    const child = spawn("claude", args, {
      cwd: FRONTEND_DIR,
      env: { ...process.env, [TOKEN_KEY]: token },
      stdio: ["ignore", "pipe", "pipe"],
    });

    let result = null;
    let sessionId = resumeSessionId ?? null;
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

      if (event.session_id) sessionId = event.session_id;

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
            info(indent(block.text.trim()));
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
        sessionId,
        rateLimitHit,
        stderrTail,
        exitCode: null,
        spawnError: error,
      });
    });
    child.on("close", (exitCode) => {
      resolvePromise({
        result,
        sessionId,
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

function reportDenials(result) {
  if (!result?.permission_denials?.length) return;
  warn(
    `권한이 거부된 도구 호출 ${result.permission_denials.length}건이 있었습니다. scripts/commit.mjs 의 ALLOWED_TOOLS 를 확인하세요.`,
  );
  for (const denial of result.permission_denials)
    warn(
      `  ${denial.tool_name}: ${JSON.stringify(denial.tool_input ?? {}).slice(0, 120)}`,
    );
}

// ---------- 제안 검증 ----------

const SIGNATURE_LINE =
  /^\s*(Co-Authored-By|Claude-Session|Generated-By|Signed-off-by)\s*:/i;

/** 공동 작성자 서명을 제거하고 이슈 트레일러를 보장한다. 변경 사항은 problems·notes 로 돌려준다. */
function normalizeMessage(raw, issue) {
  const notes = [];
  const lines = String(raw ?? "")
    .replace(/\r\n/g, "\n")
    .split("\n");
  const kept = lines.filter((line) => !SIGNATURE_LINE.test(line));
  if (kept.length !== lines.length) {
    notes.push("공동 작성자·생성 도구 서명 줄을 제거했습니다.");
  }

  let text = kept.join("\n").trim();
  if (!text)
    return { message: "", notes, problems: ["커밋 메시지가 비어 있습니다."] };

  if (issue) {
    const trailer = `#${issue.number}`;
    const lastLine = text.split("\n").at(-1).trim();
    const mentionsIssue = new RegExp(`(^|\\s)${trailer}(\\s|$)`).test(lastLine);
    if (!mentionsIssue) {
      text = `${text}\n\n${trailer}`;
      notes.push(`이슈 트레일러 ${trailer} 를 추가했습니다.`);
    }
  }

  const subject = text.split("\n")[0];
  if ([...subject].length > SUBJECT_LIMIT) {
    notes.push(
      `제목이 ${SUBJECT_LIMIT}자를 넘습니다 (${[...subject].length}자).`,
    );
  }

  return { message: text, notes, problems: [] };
}

/**
 * claude 의 경로를 스크립트가 확인한 변경 목록에 맞춘다.
 * 저장소 루트 기준 경로를 우선하고, 현재 디렉터리 기준(접두사 없음)으로 왔으면 접두사를 붙여 다시 찾는다.
 */
function normalizePath(raw, changeByPath, prefix) {
  const path = String(raw ?? "")
    .trim()
    .replace(/^\.\//, "")
    .replace(/\/+$/, "");
  if (changeByPath.has(path)) return changeByPath.get(path);
  if (prefix && changeByPath.has(prefix + path))
    return changeByPath.get(prefix + path);
  return null;
}

function validateProposal({ proposal, changes, prefix, issue }) {
  const changeByPath = new Map();
  for (const change of changes) {
    changeByPath.set(change.path, change);
    if (change.from) changeByPath.set(change.from, change);
  }

  const problems = [];
  const seen = new Map();
  const commits = (proposal?.commits ?? []).map((commit, index) => {
    const label = `커밋 ${index + 1}`;
    const entries = [];
    for (const raw of commit.files ?? []) {
      const change = normalizePath(raw, changeByPath, prefix);
      if (!change) {
        problems.push(`${label}: 변경 목록에 없는 경로입니다: ${raw}`);
        continue;
      }
      if (seen.has(change.path)) {
        problems.push(
          `${label}: ${change.path} 는 이미 ${seen.get(change.path)} 에 포함됐습니다.`,
        );
        continue;
      }
      if (entries.includes(change)) continue;
      seen.set(change.path, label);
      entries.push(change);
    }
    if (entries.length === 0)
      problems.push(`${label}: 커밋할 파일이 없습니다.`);

    const normalized = normalizeMessage(commit.message, issue);
    for (const problem of normalized.problems)
      problems.push(`${label}: ${problem}`);

    return {
      files: entries,
      message: normalized.message,
      notes: normalized.notes,
    };
  });

  const leftover = changes.filter((change) => !seen.has(change.path));
  return { commits, leftover, problems };
}

// ---------- 승인 ----------

/**
 * 표준 입력을 줄 단위 큐로 읽는다. 파이프로 여러 줄이 한 번에 들어와도 질문 순서대로 소비하고,
 * 입력이 닫히면(EOF·Ctrl+D) null 을 돌려줘 취소로 처리한다.
 */
const stdinQueue = { rl: null, lines: [], waiters: [], closed: false };

function ensureReadline() {
  if (stdinQueue.rl) return stdinQueue.rl;
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  rl.on("line", (line) => {
    const waiter = stdinQueue.waiters.shift();
    if (waiter) waiter(line);
    else stdinQueue.lines.push(line);
  });
  rl.on("close", () => {
    stdinQueue.closed = true;
    for (const waiter of stdinQueue.waiters.splice(0)) waiter(null);
  });
  rl.on("SIGINT", () => {
    info("\n취소했습니다.");
    process.exit(EXIT.CANCELLED);
  });
  stdinQueue.rl = rl;
  return rl;
}

function ask(question) {
  const rl = ensureReadline();
  const prompt = scrub(question);
  if (stdinQueue.lines.length) {
    const line = stdinQueue.lines.shift();
    process.stdout.write(`${prompt}${line}\n`);
    return Promise.resolve(line);
  }
  if (stdinQueue.closed) {
    process.stdout.write(`${prompt}\n`);
    return Promise.resolve(null);
  }
  rl.setPrompt(prompt);
  rl.prompt();
  return new Promise((resolvePromise) => {
    stdinQueue.waiters.push((line) => {
      if (line === null) process.stdout.write("\n");
      else if (!process.stdin.isTTY) process.stdout.write(`${line}\n`);
      resolvePromise(line);
    });
  });
}

function closeStdin() {
  stdinQueue.rl?.close();
}

function printProposal({
  commits,
  leftover,
  problems,
  note,
  branch,
  push,
  upstream,
}) {
  info("\n" + "=".repeat(60));
  info(`## 커밋 제안 (${commits.length}개)`);
  commits.forEach((commit, index) => {
    info(`\n### 커밋 ${index + 1}/${commits.length}`);
    info("대상 파일:");
    for (const change of commit.files) info(`  ${formatChange(change)}`);
    info("메시지:");
    info(indent(commit.message || "(비어 있음)"));
    for (const noteLine of commit.notes) info(`  ※ ${noteLine}`);
  });

  if (leftover.length) {
    info(
      `\n## 커밋에 포함되지 않는 변경 (${leftover.length}개, 워킹 트리에 그대로 남음)`,
    );
    for (const change of leftover) info(`  ${formatChange(change)}`);
  }

  if (note?.trim()) {
    info("\n## 비고");
    info(indent(note.trim()));
  }

  info("\n## 푸시 대상 브랜치");
  if (!push) info(`  ${branch} (--no-push: 푸시하지 않음)`);
  else if (upstream) info(`  ${branch} → ${upstream}`);
  else info(`  ${branch} → ${REMOTE}/${branch} (upstream 없음, -u 로 설정)`);

  if (problems.length) {
    info("\n## 검증 오류 (해결 전에는 승인할 수 없음)");
    for (const problem of problems) info(`  ✗ ${problem}`);
  }
  info("=".repeat(60));
}

// ---------- 실행 ----------

function commitPathspec(commit) {
  const paths = [];
  for (const change of commit.files) {
    paths.push(change.path);
    if (change.from) paths.push(change.from);
  }
  return paths;
}

/** 제안된 파일만 add 한 뒤 `--only` 로 그 경로만 커밋해, 제안에 없는 스테이징 내용은 건드리지 않는다. */
function executeCommits(commits) {
  const created = [];
  for (const [index, commit] of commits.entries()) {
    const paths = commitPathspec(commit);
    const added = git(["add", "--", ...paths], { allowFailure: true });
    if (!added.ok) {
      fail(
        EXIT.GIT,
        `커밋 ${index + 1}/${commits.length} 의 git add 에 실패했습니다.${created.length ? ` 앞선 커밋 ${created.length}개는 이미 만들어졌습니다.` : ""}`,
        added.stderr,
      );
    }
    const committed = git(
      [
        "commit",
        "--quiet",
        "--only",
        "--cleanup=whitespace",
        "-F",
        "-",
        "--",
        ...paths,
      ],
      { allowFailure: true, input: `${commit.message}\n` },
    );
    if (!committed.ok) {
      fail(
        EXIT.GIT,
        `커밋 ${index + 1}/${commits.length} 의 git commit 에 실패했습니다.${created.length ? ` 앞선 커밋 ${created.length}개는 이미 만들어졌습니다.` : ""}`,
        committed.stderr || committed.stdout,
      );
    }
    const sha = git(["rev-parse", "--short", "HEAD"]).stdout;
    const subject = commit.message.split("\n")[0];
    created.push({ sha, subject });
    info(`  ✓ ${sha}  ${subject}`);
  }
  return created;
}

function pushBranch({ branch, upstream }) {
  const args = upstream
    ? ["push", REMOTE, branch]
    : ["push", "-u", REMOTE, branch];
  info(`\n푸시: git ${args.join(" ")}`);
  const pushed = git(args, { allowFailure: true });
  if (pushed.stderr) info(indent(pushed.stderr));
  if (pushed.stdout) info(indent(pushed.stdout));
  if (!pushed.ok) {
    fail(
      EXIT.PUSH_FAILED,
      "푸시에 실패했습니다. 커밋은 로컬에 남아 있으며 --force 는 사용하지 않습니다.",
      "원격 상태를 확인한 뒤 직접 푸시하세요. (예: git pull --rebase 후 git push)",
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
  const prefix = git(["rev-parse", "--show-prefix"]).stdout;
  repoRoot = git(["rev-parse", "--show-toplevel"]).stdout;

  const branch = git(["rev-parse", "--abbrev-ref", "HEAD"]).stdout;
  if (branch === "HEAD") {
    fail(EXIT.GIT, "detached HEAD 상태입니다. 브랜치에서 실행하세요.");
  }

  const changes = listChanges();
  const conflicts = changes.filter((c) => isConflict(c.xy));
  if (conflicts.length) {
    fail(
      EXIT.GIT,
      `충돌 상태인 파일 ${conflicts.length}개가 있습니다. 충돌을 해결한 뒤 실행하세요.`,
      conflicts.map(formatChange).join("\n"),
    );
  }
  if (changes.length === 0) {
    fail(EXIT.NO_TARGET, "커밋할 변경이 없습니다. 워킹 트리가 깨끗합니다.");
  }

  const staged = changes.filter((c) => isStaged(c.xy));
  const issue = resolveIssueNumber({ explicit: options.issue, branch });
  const upstream = git(
    ["rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}"],
    { allowFailure: true },
  );
  const upstreamName = upstream.ok ? upstream.stdout : null;

  info(
    `현재 브랜치: ${branch}${PROTECTED_BRANCHES.includes(branch) ? " (보호 브랜치 — 푸시 전 재확인)" : ""}`,
  );
  if (issue) info(`이슈 번호: #${issue.number} (출처: ${issue.source})`);
  else
    warn(
      "브랜치명에서 이슈 번호를 찾지 못했습니다. 트레일러 없이 진행하며, 필요하면 --issue <번호> 로 지정하세요.",
    );
  info(`변경 파일: ${changes.length}개`);
  for (const change of changes) info(`  ${formatChange(change)}`);
  if (staged.length) {
    info(
      `이미 스테이징된 파일 ${staged.length}개는 다른 변경과 함께 커밋 후보로 다루며, 승인 후 파일 단위로 다시 add 합니다. (부분 스테이징은 유지되지 않습니다)`,
    );
  }

  const policy = buildPolicy({ branch, prefix, changes, staged, issue });

  if (options.dryRun) {
    info(
      "\n[dry-run] claude 를 실행하지 않습니다. 아래는 주입할 정책입니다.\n",
    );
    info(policy);
    info(`\n[dry-run] allowedTools: ${ALLOWED_TOOLS.join(", ")}`);
    info(`[dry-run] disallowedTools: ${DISALLOWED_TOOLS.join(", ")}`);
    info(
      `[dry-run] 푸시: ${options.push ? `${REMOTE}/${branch}${upstreamName ? "" : " (-u)"}` : "안 함 (--no-push)"}`,
    );
    process.exit(EXIT.OK);
  }

  info("\n커밋 제안을 생성합니다. 승인 전에는 저장소를 바꾸지 않습니다.\n");

  let prompt = "/commit";
  let sessionId = null;
  let approved = null;

  while (!approved) {
    const outcome = await runClaude({
      token,
      policy,
      prompt,
      resumeSessionId: sessionId,
    });
    const failure = classifyFailure(outcome);
    if (failure) fail(failure.code, failure.message, failure.hint);

    const { result } = outcome;
    sessionId = outcome.sessionId;
    reportDenials(result);

    const proposal = result?.structured_output;
    if (!proposal || !Array.isArray(proposal.commits)) {
      fail(
        EXIT.UNKNOWN,
        "claude 가 커밋 제안을 구조화 출력으로 돌려주지 않았습니다.",
        `마지막 응답: ${(result?.result ?? "").trim().slice(0, 500)}`,
      );
    }

    const validated = validateProposal({ proposal, changes, prefix, issue });
    printProposal({
      ...validated,
      note: proposal.note,
      branch,
      push: options.push,
      upstream: upstreamName,
    });

    const question = validated.problems.length
      ? "\n검증 오류가 있습니다. 수정 요청을 입력하거나(비우면 오류 내용만 전달) n 으로 취소하세요.\n> "
      : `\n위 내용으로 커밋${options.push ? " 및 푸시" : ""}를 진행할까요? [y=진행 / n=취소 / 그 외 입력=수정 요청]\n> `;
    const answer = await ask(question);
    const normalized = (answer ?? "").trim().toLowerCase();

    if (
      answer === null ||
      (!validated.problems.length && CANCEL_WORDS.has(normalized))
    ) {
      info("취소했습니다. 커밋은 만들지 않았습니다.");
      process.exit(EXIT.CANCELLED);
    }
    if (validated.problems.length && normalized === "n") {
      info("취소했습니다. 커밋은 만들지 않았습니다.");
      process.exit(EXIT.CANCELLED);
    }
    if (!validated.problems.length && APPROVE_WORDS.has(normalized)) {
      approved = validated;
      break;
    }

    const feedback = [];
    if (
      answer.trim() &&
      !(validated.problems.length && APPROVE_WORDS.has(normalized))
    )
      feedback.push(`사용자 수정 요청:\n${answer.trim()}`);
    if (validated.problems.length)
      feedback.push(
        `스크립트 검증 오류(반드시 해결):\n${validated.problems.map((p) => `- ${p}`).join("\n")}`,
      );
    feedback.push("반영해서 전체 커밋 제안을 같은 형식으로 다시 반환하세요.");
    prompt = feedback.join("\n\n");
    info("\n수정 요청을 전달해 제안을 다시 받습니다.\n");
  }

  info("\n커밋을 만듭니다.");
  const created = executeCommits(approved.commits);

  if (options.push) {
    let doPush = true;
    if (PROTECTED_BRANCHES.includes(branch)) {
      const confirm = await ask(
        `\n${branch} 은(는) 보호 브랜치로 추정됩니다. 정말 ${REMOTE}/${branch} 에 푸시할까요? [y/N]\n> `,
      );
      doPush = APPROVE_WORDS.has((confirm ?? "").trim().toLowerCase());
      if (!doPush) info("푸시를 건너뜁니다. 커밋은 로컬에 남아 있습니다.");
    }
    if (doPush) pushBranch({ branch, upstream: upstreamName });
  }
  closeStdin();

  const remaining = listChanges();
  info("\n" + "=".repeat(60));
  info(
    `커밋 ${created.length}개 생성${options.push ? " 및 푸시 완료" : " 완료 (푸시 안 함)"}`,
  );
  for (const c of created) info(`  ${c.sha}  ${c.subject}`);
  if (remaining.length) info(`워킹 트리에 남은 변경: ${remaining.length}개`);
  info("=".repeat(60));
  process.exit(EXIT.OK);
}

main().catch((error) => {
  fail(
    EXIT.UNKNOWN,
    `예상하지 못한 오류가 발생했습니다: ${error?.message ?? error}`,
  );
});
