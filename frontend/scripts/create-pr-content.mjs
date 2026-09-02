#!/usr/bin/env node
/**
 * pnpm pr-content — 기존 `/create-pr-content` 스킬을 `claude -p`로 비대화 실행하는 스크립트.
 *
 * - 호출 방식(토큰·allowedTools·stream-json 진행 출력·실패 분류)은 scripts/review.mjs 와 동일하다.
 * - 토큰은 `frontend/.env.local`(gitignored)의 CLAUDE_CODE_OAUTH_TOKEN 또는 셸 환경변수에서 읽는다.
 * - 토큰 값은 어떤 출력·로그에도 남기지 않는다.
 * - 비대화 모드에는 Artifact 도구가 없으므로 변경 설명 페이지 게시(스킬 3-2)는 claude 가 아니라
 *   스크립트가 수행한다. HTML 을 비공개 gist 로 올리고, gist 원본을 text/html 로 서빙하는
 *   githack 뷰어 URL 로 PR 문서의 링크를 교체한다.
 */
import { spawn, spawnSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { createInterface } from "node:readline";
import { basename, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseEnv } from "node:util";

const FRONTEND_DIR = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const ENV_FILE = resolve(FRONTEND_DIR, ".env.local");
const TOKEN_KEY = "CLAUDE_CODE_OAUTH_TOKEN";
const TMP_DIR = "/tmp";
const PR_DIR = `${TMP_DIR}/knot-pr`;
const BASE_BRANCH = "develop";
/** gist 원본을 text/html 로 그대로 서빙하는 뷰어. 실행마다 새 비공개 gist 를 만들므로 영구 캐시 도메인을 쓴다. */
const GIST_VIEWER_ORIGIN = "https://gistcdn.githack.com";

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
  ISSUE_MISSING: 8,
  GIST_FAILED: 9,
};

/** 스킬 frontmatter의 allowed-tools와 동일. 비대화 모드는 권한 프롬프트에 답할 수 없어 사전에 허용한다. */
const ALLOWED_TOOLS = [
  "Bash(git diff:*)",
  "Bash(git log:*)",
  "Bash(git branch:*)",
  "Bash(git status:*)",
  "Bash(gh issue view:*)",
  "Bash(gh api:*)",
  "Bash(mkdir:*)",
  "Bash(code:*)",
  "Bash(date:*)",
  "Read",
  "Write",
  "Glob",
  "Grep",
  "Skill",
  "Artifact",
];

const HELP = `사용법: pnpm pr-content [옵션]

기존 /create-pr-content 스킬을 claude -p 로 비대화 실행하여 PR 본문 md를 ${PR_DIR} 에 저장합니다.
변경 설명 HTML은 ${TMP_DIR}/<YYYY-MM-DD>-explanation-<브랜치>.html 에 생성한 뒤 비공개 gist 로 올리고,
PR 문서의 링크를 ${GIST_VIEWER_ORIGIN} 뷰어 URL 로 교체합니다.
(비대화 모드에는 Artifact 도구가 없어 Artifact 대신 gist 를 씁니다. 링크를 아는 사람만 열 수 있고, 실행마다 새 gist 가 만들어집니다.)

옵션:
  --issue <번호>   이슈 번호를 직접 지정 (기본: 브랜치명 → 커밋 메시지 순으로 자동 추출)
  --dry-run        claude 를 실행하지 않고 기준·이슈·저장 경로·실행 인자만 출력
  -h, --help       도움말

종료 코드:
  0  작성 완료
  1  기타 실패
  2  ${TOKEN_KEY} 미설정
  3  claude CLI 없음
  4  인증 실패 (토큰이 잘못됐거나 만료)
  5  구독 사용량 한도 초과
  6  PR 대상 커밋 없음
  7  git 기준(develop) 확인 실패
  8  이슈 번호 확인 실패 (--issue 로 지정)
  9  변경 설명 페이지 gist 게시 실패 (PR 문서는 작성됐지만 링크에 로컬 경로가 남아 있음)
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
  const options = { issue: null, dryRun: false };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--issue") {
      options.issue = parseIssueNumber(argv[i + 1], "--issue");
      i += 1;
    } else if (arg.startsWith("--issue=")) {
      options.issue = parseIssueNumber(arg.slice("--issue=".length), "--issue");
    } else if (arg === "--dry-run") options.dryRun = true;
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

/** gh 가 없거나 인증되지 않으면 스킬 2단계(이슈 조회)를 생략하도록 정책에 반영한다. */
function checkGh() {
  const version = spawnSync("gh", ["--version"], { encoding: "utf8" });
  if (version.error || version.status !== 0) {
    return { available: false, reason: "gh CLI 없음" };
  }
  const auth = spawnSync("gh", ["auth", "status"], { encoding: "utf8" });
  if (auth.status !== 0) {
    return { available: false, reason: "gh 인증 안 됨 (gh auth login 필요)" };
  }
  return { available: true, reason: "" };
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

function listCommits(baseRef) {
  const output = git(["log", "--format=%h%x09%s", `${baseRef}..HEAD`]).stdout;
  if (!output) return [];
  return output.split("\n").map((line) => {
    const [sha, ...subject] = line.split("\t");
    return { sha, subject: subject.join("\t") };
  });
}

/**
 * 스킬 1단계는 이슈 번호를 못 찾으면 사용자에게 묻지만 비대화 모드에서는 불가능하므로
 * 스크립트가 미리 확정한다. 우선순위: --issue 옵션 → 브랜치명 → 커밋 메시지(단일 후보일 때만).
 */
function resolveIssueNumber({ explicit, branch, baseRef }) {
  if (explicit) return { number: explicit, source: "--issue 옵션" };

  const fromBranch = branch.match(/#(\d+)/);
  if (fromBranch) return { number: Number(fromBranch[1]), source: "브랜치명" };

  const messages = git(["log", "--format=%B", `${baseRef}..HEAD`]).stdout;
  const candidates = [
    ...new Set([...messages.matchAll(/#(\d+)/g)].map((m) => Number(m[1]))),
  ];
  if (candidates.length === 1) {
    return { number: candidates[0], source: "커밋 메시지" };
  }
  if (candidates.length > 1) {
    fail(
      EXIT.ISSUE_MISSING,
      `커밋 메시지에서 이슈 번호 후보가 여러 개 발견됐습니다: ${candidates.map((n) => `#${n}`).join(", ")}`,
      "--issue <번호> 로 PR 대상 이슈를 지정하세요.",
    );
  }
  fail(
    EXIT.ISSUE_MISSING,
    `브랜치명(${branch})과 커밋 메시지에서 이슈 번호를 찾지 못했습니다.`,
    "--issue <번호> 로 PR 대상 이슈를 지정하세요.",
  );
}

/** 스킬 4단계의 파일명 규칙: 브랜치명의 `/`, `#`, 공백을 `-` 로 치환. */
function branchSlug(branch) {
  return branch
    .replace(/[/#\\\s]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}

/** 스킬 3-1 의 `date +%F` 와 동일한 로컬 날짜. */
function todayLocal() {
  const now = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

// ---------- 정책 프롬프트 ----------

function buildPolicy({
  branch,
  baseRef,
  commitCount,
  issue,
  gh,
  htmlPath,
  prPath,
}) {
  const issueLookup = gh.available
    ? "- 이슈 조회: `gh` 인증이 확인됐습니다. 스킬 2단계대로 해당 이슈와 상위 이슈를 조회합니다. 조회가 실패하면 스킬의 지시대로 생략하고 마지막 응답의 `비고` 에 명시합니다."
    : `- 이슈 조회: \`gh\` 를 사용할 수 없습니다(${gh.reason}). 스킬 2단계 이슈 조회를 시도하지 않고 생략하며, 마지막 응답의 \`비고\` 에 "이슈 내용을 반영하지 못했음" 을 명시합니다.`;

  return [
    "## 비대화 실행 정책 (pnpm pr-content)",
    "",
    "이 세션은 `pnpm pr-content` 스크립트가 `claude -p` 비대화 모드로 실행했습니다. 사용자에게 질문하거나 확인을 요청할 수 없으므로, /create-pr-content 스킬에서 사용자 확인이 필요한 단계는 아래 고정 정책으로 대체합니다. 아래 정책은 스킬 본문의 '작업을 멈추고 사용자에게 물어봄' 지시보다 우선합니다.",
    "",
    `- 현재 브랜치: \`${branch}\``,
    `- 기준 브랜치: \`${baseRef}\` — 스크립트가 이미 \`git fetch origin ${BASE_BRANCH}\` 을 수행하고 기준을 확정했습니다. 스킬 1단계의 \`${BASE_BRANCH}..HEAD\` 와 \`${BASE_BRANCH}...HEAD\` 는 각각 \`${baseRef}..HEAD\` 와 \`${baseRef}...HEAD\` 로 읽습니다. 다시 fetch 하지 않습니다.`,
    `- 비교 대상 커밋: ${commitCount}개. 커밋되지 않은 워킹 트리 변경은 PR 대상이 아니므로 다루지 않습니다.`,
    `- 이슈 번호: \`#${issue.number}\` (출처: ${issue.source}). 스크립트가 이미 확정했으므로 다시 추출하거나 사용자에게 묻지 않습니다.`,
    issueLookup,
    `- 변경 설명 HTML 경로: 반드시 \`${htmlPath}\` 에 저장합니다. 날짜는 스크립트가 확정했으므로 \`date\` 를 실행하지 않습니다.`,
    '- 변경 설명 HTML 형식: 이 HTML 은 Artifact 가 아니라 gist 뷰어에서 독립 페이지로 열립니다. 스킬 3-1 인자 중 "<!DOCTYPE>, <html>, <head>, <body> 태그를 쓰지 않는다" 는 조건은 적용하지 않고, `<!DOCTYPE html>`, `<html lang="ko">`, `<head>`(meta charset·viewport, `<title>`, `<style>`), `<body>` 를 갖춘 완전한 문서로 작성합니다. 나머지 조건(CSS 변수 기반 라이트·다크 테마, 외부 스크립트·이미지 없는 자체 완결, 인라인 퀴즈 피드백, 한국어 높임말)은 그대로 지킵니다.',
    `- 변경 설명 페이지 게시(스킬 3-2)는 claude 가 수행하지 않습니다. 비대화 세션에는 Artifact 도구가 없으며, 작업이 끝나면 스크립트가 HTML 을 gist 로 게시합니다. PR 문서의 변경 설명 페이지 링크 URL 자리에는 정확히 \`${htmlPath}\` 문자열만 적습니다. 스크립트가 이 문자열을 게시 URL 로 교체하므로, 게시 여부나 로컬 경로에 관한 참고 사항·비고는 쓰지 않습니다.`,
    `- PR 문서 저장 경로: 반드시 \`${prPath}\` 에 저장합니다. 다른 파일명이나 경로를 만들지 않습니다.`,
    `- 저장 후 \`code ${prPath}\` 로 VS Code 를 엽니다. 실패해도 결과에는 영향이 없으므로 무시하고 진행합니다.`,
    "- 코드를 수정하지 않습니다. 확인이 필요한 사항은 PR 문서의 `### 참고 사항` 에 남기고 진행합니다.",
    "- 마지막 응답은 스킬 6단계의 공유 안내 대신 아래 형식으로만 작성합니다. `비고` 줄은 이슈 조회를 생략·실패했거나 알려야 할 사항이 있을 때만 씁니다.",
    `  \`PR 문서: ${prPath}\``,
    `  \`설명 HTML: ${htmlPath}\``,
    "  `비고: <이슈 미반영 사유 등>`",
  ].join("\n");
}

// ---------- claude 실행 ----------

function summarizeToolUse(block) {
  const input = block.input ?? {};
  switch (block.name) {
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

function runClaude({ token, policy }) {
  const args = [
    "-p",
    "/create-pr-content",
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

// ---------- gist 게시 ----------

/** HTML 을 비공개 gist 로 올리고 뷰어 URL 을 만든다. 실패해도 예외를 던지지 않는다. */
function publishExplanationGist({ htmlPath, issueNumber, branch }) {
  const created = spawnSync(
    "gh",
    [
      "gist",
      "create",
      "--desc",
      `Knot PR 변경 설명: #${issueNumber} (${branch})`,
      htmlPath,
    ],
    { encoding: "utf8" },
  );
  if (created.error || created.status !== 0) {
    return {
      ok: false,
      reason:
        (created.stderr || created.error?.message || "").trim() ||
        "gh gist create 실패",
    };
  }

  const gistUrl = created.stdout.trim().split("\n").pop() ?? "";
  const match = gistUrl.match(
    /^https:\/\/gist\.github\.com\/(?:([^/]+)\/)?([0-9a-f]+)$/,
  );
  if (!match) {
    return { ok: false, reason: `gist URL 을 해석하지 못했습니다: ${gistUrl}` };
  }

  let [, owner, id] = match;
  if (!owner) {
    const login = spawnSync("gh", ["api", "user", "--jq", ".login"], {
      encoding: "utf8",
    });
    owner = (login.stdout ?? "").trim();
    if (login.status !== 0 || !owner) {
      return {
        ok: false,
        reason: `gist 소유자 계정을 확인하지 못했습니다. (gist 는 생성됨: ${gistUrl})`,
      };
    }
  }

  return {
    ok: true,
    gistUrl,
    viewerUrl: `${GIST_VIEWER_ORIGIN}/${owner}/${id}/raw/${basename(htmlPath)}`,
  };
}

/** 뷰어가 gist 를 text/html 로 응답하는지 확인한다. 문제가 없으면 null, 있으면 사유를 돌려준다. */
async function checkViewerUrl(url) {
  try {
    const response = await fetch(url, { signal: AbortSignal.timeout(10_000) });
    const type = response.headers.get("content-type") ?? "";
    if (response.ok && type.includes("text/html")) return null;
    return `HTTP ${response.status} (${type || "content-type 없음"})`;
  } catch (error) {
    return error?.message ?? String(error);
  }
}

/** PR 문서 안의 로컬 HTML 경로를 게시 URL 로 바꾸고 교체한 개수를 돌려준다. */
function replaceExplanationLink(prPath, htmlPath, url) {
  const before = readFileSync(prPath, "utf8");
  const parts = before.split(htmlPath);
  if (parts.length === 1) return 0;
  writeFileSync(prPath, parts.join(url));
  return parts.length - 1;
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
  const commits = listCommits(baseRef);
  if (commits.length === 0) {
    fail(
      EXIT.NO_TARGET,
      `PR 대상 커밋이 없습니다. (${baseRef}..HEAD 에 커밋 없음)`,
      "변경을 커밋한 뒤 다시 실행하세요. 커밋되지 않은 변경은 PR 대상이 아닙니다.",
    );
  }

  const issue = resolveIssueNumber({
    explicit: options.issue,
    branch,
    baseRef,
  });

  const gh = checkGh();
  if (!gh.available) {
    warn(
      `${gh.reason}. 이슈·상위 이슈 조회를 생략하고 diff 만으로 작성합니다.`,
    );
  }

  const dirty = git(["status", "--porcelain"]).stdout;
  if (dirty) {
    info(
      `커밋되지 않은 변경 ${dirty.split("\n").length}건은 PR 대상이 아니므로 반영하지 않습니다.`,
    );
  }

  if (spawnSync("code", ["--version"], { encoding: "utf8" }).error) {
    warn(
      "VS Code 의 `code` 명령을 찾을 수 없어 PR 문서가 자동으로 열리지 않습니다. 완료 후 경로를 직접 여세요.",
    );
  }

  const slug = branchSlug(branch);
  const prPath = `${PR_DIR}/${slug}-pr.md`;
  const htmlPath = `${TMP_DIR}/${todayLocal()}-explanation-${slug}.html`;

  info(`비교 기준: ${baseRef}...HEAD (커밋 ${commits.length}개)`);
  for (const c of commits) info(`  ${c.sha}  ${c.subject}`);
  info(`이슈 번호: #${issue.number} (출처: ${issue.source})`);

  const policy = buildPolicy({
    branch,
    baseRef,
    commitCount: commits.length,
    issue,
    gh,
    htmlPath,
    prPath,
  });

  if (options.dryRun) {
    info(
      "\n[dry-run] claude 를 실행하지 않습니다. 아래는 주입할 정책입니다.\n",
    );
    info(policy);
    info(`\n[dry-run] allowedTools: ${ALLOWED_TOOLS.join(", ")}`);
    info(`[dry-run] PR 문서 저장 예정 경로: ${prPath}`);
    info(`[dry-run] 설명 HTML 저장 예정 경로: ${htmlPath}`);
    info(
      `[dry-run] 설명 HTML 게시 예정: 비공개 gist → ${GIST_VIEWER_ORIGIN}/<계정>/<gist id>/raw/${basename(htmlPath)}`,
    );
    process.exit(EXIT.OK);
  }

  mkdirSync(PR_DIR, { recursive: true });
  info(`\nPR 본문 작성을 시작합니다. 결과는 ${prPath} 에 저장됩니다.\n`);

  const outcome = await runClaude({ token, policy });
  const failure = classifyFailure(outcome);
  if (failure) fail(failure.code, failure.message, failure.hint);

  const { result } = outcome;
  if (result?.permission_denials?.length) {
    warn(
      `권한이 거부된 도구 호출 ${result.permission_denials.length}건이 있었습니다. scripts/create-pr-content.mjs 의 ALLOWED_TOOLS 를 확인하세요.`,
    );
    for (const denial of result.permission_denials)
      warn(
        `  ${denial.tool_name}: ${JSON.stringify(denial.tool_input ?? {}).slice(0, 120)}`,
      );
  }

  if (!existsSync(prPath)) {
    fail(
      EXIT.UNKNOWN,
      `작업이 끝났지만 ${prPath} 가 생성되지 않았습니다.`,
      `마지막 응답: ${(result?.result ?? "").trim().slice(0, 500)}`,
    );
  }
  let gist = {
    ok: false,
    reason: `변경 설명 HTML ${htmlPath} 이 생성되지 않았습니다.`,
  };
  if (existsSync(htmlPath)) {
    gist = gh.available
      ? publishExplanationGist({ htmlPath, issueNumber: issue.number, branch })
      : { ok: false, reason: gh.reason };
  }

  let replaced = 0;
  if (gist.ok) {
    const problem = await checkViewerUrl(gist.viewerUrl);
    if (problem) {
      warn(
        `뷰어 URL 응답을 확인하지 못했습니다 (${problem}). 브라우저에서 직접 열어 확인하세요: ${gist.viewerUrl}`,
      );
    }
    replaced = replaceExplanationLink(prPath, htmlPath, gist.viewerUrl);
    if (replaced === 0) {
      warn(
        `PR 문서에서 ${htmlPath} 를 찾지 못해 링크를 교체하지 못했습니다. 아래 게시 URL 을 직접 넣으세요.`,
      );
    }
  }

  const minutes = result?.duration_ms
    ? (result.duration_ms / 60000).toFixed(1)
    : "?";
  info("\n" + "=".repeat(60));
  info((result?.result ?? "").trim());
  info(`소요 시간: ${minutes}분 / 턴: ${result?.num_turns ?? "?"}`);
  info("=".repeat(60));

  if (!gist.ok) {
    fail(
      EXIT.GIST_FAILED,
      `변경 설명 페이지를 gist 로 게시하지 못했습니다: ${gist.reason}`,
      `PR 문서 ${prPath} 의 링크에는 로컬 경로 ${htmlPath} 가 남아 있습니다. HTML 을 직접 게시한 뒤 링크를 교체하세요.`,
    );
  }
  info(
    [
      "",
      `변경 설명 페이지: ${gist.viewerUrl}`,
      `비공개 gist: ${gist.gistUrl} (링크를 아는 사람만 열 수 있습니다)`,
      `PR 문서의 링크 ${replaced}곳을 게시 URL 로 교체했습니다.`,
    ].join("\n"),
  );
  process.exit(EXIT.OK);
}

main().catch((error) => {
  fail(
    EXIT.UNKNOWN,
    `예상하지 못한 오류가 발생했습니다: ${error?.message ?? error}`,
  );
});
