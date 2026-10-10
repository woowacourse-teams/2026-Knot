#!/usr/bin/env node
/**
 * create-pr-content 스킬의 PR 자산 수집 스크립트.
 *
 * - 현재 브랜치의 PR 번호와 봇이 남긴 Storybook 미리보기 주소를 확인한다. (develop 이면 PR 이 없으므로 건너뛴다)
 * - 변경 파일마다 PR `Files changed` 탭의 diff 링크를 만든다. 앵커는 저장소 기준 경로의 sha256 이다.
 * - 변경 파일이 속한 컴포넌트의 스토리를 변경 전(develop 상시 Storybook)과 변경 후(브랜치 Storybook)로 캡처한다.
 *   브랜치 미리보기가 HEAD 기준이 아니면 로컬 Storybook 을 띄워 변경 후를 찍는다.
 * - PR 이 있으면 찍은 사진을 Aside(`aside repl`)로 PR 새 댓글 입력창에 올려 GitHub 주소를 받는다.
 *   주소만 읽고 입력창을 비우므로 댓글은 등록되지 않는다. `--upload-only` 는 캡처 없이 아직 올리지 않은
 *   사진과 `<out>/diagrams/*.svg` 를 올린다.
 * - 결과는 `<out>/manifest.json` 에 쓰고 같은 내용을 표준 출력으로 내보낸다.
 */
import { spawn, spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { createRequire } from "node:module";
import { createServer } from "node:net";
import { basename, dirname, extname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const FRONTEND_DIR = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../../../..",
);
const BASE_BRANCH = "develop";
/** develop 머지 때마다 갱신되는 상시 Storybook. 변경 전 캡처에 쓴다. */
const DEFAULT_BASE_STORYBOOK = "https://knot-storybook-5f8.pages.dev";
const DEFAULT_VIEWPORT = { width: 1280, height: 800 };
const STORYBOOK_START_TIMEOUT_MS = 240_000;
/** 캡처 영역 바깥 여백(CSS px). */
const CLIP_PADDING = 16;
/** Aside REPL 출력에서 업로드 결과 줄을 찾는 표식. */
const UPLOAD_MARKER = "KNOT_UPLOAD_RESULT ";
/** 파일을 올리면 GitHub 가 댓글 입력창에 적어 주는 주소. */
const ATTACHMENT_URL_PATTERN =
  /https:\/\/github\.com\/user-attachments\/assets\/[0-9a-f-]+/;
const MIME_TYPES = { ".png": "image/png", ".svg": "image/svg+xml" };

const HELP = `사용법: node .claude/skills/create-pr-content/scripts/pr-assets.mjs --out <디렉터리> [옵션]

옵션:
  --out <디렉터리>         결과(manifest.json, before/, after/)를 저장할 위치 (필수)
  --base <ref>             비교 기준 (기본: ${BASE_BRANCH})
  --head-storybook <url>   변경 후 캡처에 쓸 Storybook 주소 (기본: HEAD 기준 PR 미리보기, 없으면 로컬 Storybook)
  --base-storybook <url>   변경 전 캡처에 쓸 Storybook 주소 (기본: ${DEFAULT_BASE_STORYBOOK})
  --viewport <너비x높이>   캡처 화면 크기 (기본: ${DEFAULT_VIEWPORT.width}x${DEFAULT_VIEWPORT.height})
  --no-capture             링크만 만들고 캡처하지 않음
  --no-upload              찍은 사진을 GitHub 에 올리지 않음
  --upload-only            캡처 없이 <out>/manifest.json 의 사진과 <out>/diagrams/*.svg 중 아직 올리지 않은 것만 올림
  -h, --help               도움말
`;

// ---------- 인자 ----------

function parseArgs(argv) {
  const options = {
    out: null,
    base: BASE_BRANCH,
    headStorybook: null,
    baseStorybook: DEFAULT_BASE_STORYBOOK,
    viewport: DEFAULT_VIEWPORT,
    capture: true,
    upload: true,
    uploadOnly: false,
  };
  const takeValue = (i, name) => {
    const value = argv[i + 1];
    if (!value || value.startsWith("--"))
      exitWithHelp(`${name} 값이 없습니다.`);
    return value;
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--out") options.out = resolve(takeValue(i++, arg));
    else if (arg === "--base") options.base = takeValue(i++, arg);
    else if (arg === "--head-storybook")
      options.headStorybook = trimSlash(takeValue(i++, arg));
    else if (arg === "--base-storybook")
      options.baseStorybook = trimSlash(takeValue(i++, arg));
    else if (arg === "--viewport")
      options.viewport = parseViewport(takeValue(i++, arg));
    else if (arg === "--no-capture") options.capture = false;
    else if (arg === "--no-upload") options.upload = false;
    else if (arg === "--upload-only") options.uploadOnly = true;
    else if (arg === "-h" || arg === "--help") {
      process.stdout.write(HELP);
      process.exit(0);
    } else exitWithHelp(`알 수 없는 옵션입니다: ${arg}`);
  }
  if (!options.out) exitWithHelp("--out 은 필수입니다.");
  return options;
}

function parseViewport(raw) {
  const match = raw.match(/^(\d+)x(\d+)$/);
  if (!match) exitWithHelp(`--viewport 형식이 잘못됐습니다: ${raw}`);
  return { width: Number(match[1]), height: Number(match[2]) };
}

function exitWithHelp(message) {
  process.stderr.write(`${message}\n\n${HELP}`);
  process.exit(1);
}

function trimSlash(url) {
  return url.replace(/\/+$/, "");
}

// ---------- git · PR ----------

function run(command, args, { allowFailure = false } = {}) {
  const result = spawnSync(command, args, {
    cwd: FRONTEND_DIR,
    encoding: "utf8",
  });
  if ((result.error || result.status !== 0) && !allowFailure) {
    throw new Error(
      `${command} ${args.join(" ")} 실패: ${(result.stderr || result.error?.message || "").trim()}`,
    );
  }
  return {
    ok: !result.error && result.status === 0,
    stdout: (result.stdout ?? "").trim(),
  };
}

/** 저장소 루트 기준 경로로 변경 파일을 모은다. 삭제된 파일도 diff 앵커가 있으므로 포함한다. */
function listChangedFiles(base) {
  const output = run("git", [
    "diff",
    "--name-status",
    "--no-renames",
    `${base}...HEAD`,
  ]).stdout;
  if (!output) return [];
  return output.split("\n").map((line) => {
    const [status, path] = line.split("\t");
    return { path, deleted: status === "D" };
  });
}

/** PR 본문 끝의 봇 블록(storybook-preview)에서 미리보기 주소와 기준 커밋을 읽는다. */
function parseStorybookBlock(body) {
  const block = body.match(
    /<!-- storybook-preview:start -->([\s\S]*?)<!-- storybook-preview:end -->/,
  );
  const match = block?.[1].match(/(https?:\/\/\S+)\s+\(`([0-9a-f]+)`/);
  return match ? { url: trimSlash(match[1]), sha: match[2] } : null;
}

function findPullRequest() {
  const result = run("gh", ["pr", "view", "--json", "number,url,body"], {
    allowFailure: true,
  });
  if (!result.ok) return null;
  const pr = JSON.parse(result.stdout);
  return {
    number: pr.number,
    url: pr.url,
    storybook: parseStorybookBlock(pr.body ?? ""),
  };
}

function diffLink(prUrl, path) {
  return `${prUrl}/files#diff-${createHash("sha256").update(path).digest("hex")}`;
}

// ---------- Storybook ----------

async function fetchStoryIndex(storybookUrl) {
  try {
    const response = await fetch(`${storybookUrl}/index.json`, {
      signal: AbortSignal.timeout(15_000),
    });
    if (!response.ok) return null;
    const { entries } = await response.json();
    return Object.values(entries).filter((entry) => entry.type === "story");
  } catch {
    return null;
  }
}

function findFreePort() {
  return new Promise((resolvePort, reject) => {
    const server = createServer();
    server.once("error", reject);
    server.listen(0, () => {
      const { port } = server.address();
      server.close(() => resolvePort(port));
    });
  });
}

/** 로컬 Storybook 을 빈 포트에 띄운다. 사용자가 이미 띄운 6006 을 건드리지 않는다. */
async function startLocalStorybook() {
  const major = Number(process.versions.node.split(".")[0]);
  if (major < 22) {
    throw new Error(
      `로컬 Storybook 은 Node 22 이상이 필요합니다. (현재 ${process.versions.node}, nvm use 22 후 다시 실행)`,
    );
  }
  const port = await findFreePort();
  const child = spawn(
    resolve(FRONTEND_DIR, "node_modules/.bin/storybook"),
    ["dev", "-p", String(port), "--exact-port", "--ci", "--no-open", "--quiet"],
    { cwd: FRONTEND_DIR, detached: true, stdio: ["ignore", "ignore", "pipe"] },
  );
  let stderrTail = "";
  child.stderr.on("data", (chunk) => {
    stderrTail = (stderrTail + chunk.toString()).slice(-2000);
  });
  const stop = () => {
    try {
      process.kill(-child.pid, "SIGTERM");
    } catch {
      // 이미 종료됨
    }
  };

  const url = `http://localhost:${port}`;
  const deadline = Date.now() + STORYBOOK_START_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (child.exitCode !== null)
      throw new Error(`로컬 Storybook 이 시작하지 못했습니다.\n${stderrTail}`);
    if (await fetchStoryIndex(url)) return { url, stop };
    await new Promise((r) => setTimeout(r, 1_000));
  }
  stop();
  throw new Error(
    `로컬 Storybook 이 ${STORYBOOK_START_TIMEOUT_MS / 1000}초 안에 뜨지 않았습니다.\n${stderrTail}`,
  );
}

/** 스토리 폴더 안의 파일이 바뀐 스토리만 고른다. */
function selectStories(stories, changedFiles) {
  const changed = changedFiles
    .filter((file) => !file.deleted)
    .map((file) => file.path);
  return stories.filter((story) => {
    const storyDir = `frontend/${dirname(story.importPath.replace(/^\.\//, ""))}/`;
    return changed.some((path) => path.startsWith(storyDir));
  });
}

// ---------- 캡처 ----------

async function launchBrowser() {
  const require = createRequire(resolve(FRONTEND_DIR, "package.json"));
  const { chromium } = require("@playwright/test");
  try {
    return await chromium.launch();
  } catch {
    // Playwright 전용 브라우저를 설치하지 않았으면 설치된 Chrome 을 쓴다.
    return chromium.launch({ channel: "chrome" });
  }
}

/**
 * 스토리가 실제로 그린 영역(포털 포함)의 합집합을 구한다. 고정 위치 요소도 잡히도록 화면 좌표를 쓴다.
 * 투명한 레이아웃 래퍼까지 넣으면 여백이 커지므로 배경·테두리·그림자·대체 요소·텍스트만 센다.
 */
function measureRenderedArea(padding) {
  const REPLACED = new Set([
    "IMG",
    "SVG",
    "CANVAS",
    "VIDEO",
    "INPUT",
    "TEXTAREA",
    "SELECT",
  ]);
  const isHidden = (style) =>
    style.display === "none" ||
    style.visibility === "hidden" ||
    Number(style.opacity) === 0;
  const isTransparent = (color) =>
    color === "transparent" || /rgba\(.*,\s*0\)$/.test(color);
  const paints = (element, style) =>
    REPLACED.has(element.tagName.toUpperCase()) ||
    !isTransparent(style.backgroundColor) ||
    style.backgroundImage !== "none" ||
    style.boxShadow !== "none" ||
    ["Top", "Right", "Bottom", "Left"].some(
      (side) =>
        parseFloat(style[`border${side}Width`]) > 0 &&
        !isTransparent(style[`border${side}Color`]),
    );

  const rects = [];
  const push = (rect) => {
    if (rect.width >= 1 && rect.height >= 1) rects.push(rect);
  };
  for (const element of document.body.querySelectorAll("*")) {
    if (element.closest("#storybook-docs, script, style, link, meta, noscript"))
      continue;
    if (element.id === "storybook-root") continue;
    const style = getComputedStyle(element);
    if (isHidden(style)) continue;
    if (paints(element, style)) push(element.getBoundingClientRect());
    for (const node of element.childNodes) {
      if (node.nodeType !== Node.TEXT_NODE || !node.textContent.trim())
        continue;
      const range = document.createRange();
      range.selectNodeContents(node);
      push(range.getBoundingClientRect());
    }
  }
  if (rects.length === 0) return null;
  const left = Math.max(0, Math.min(...rects.map((r) => r.left)) - padding);
  const top = Math.max(0, Math.min(...rects.map((r) => r.top)) - padding);
  const right = Math.min(
    window.innerWidth,
    Math.max(...rects.map((r) => r.right)) + padding,
  );
  const bottom = Math.min(
    window.innerHeight,
    Math.max(...rects.map((r) => r.bottom)) + padding,
  );
  return { x: left, y: top, width: right - left, height: bottom - top };
}

async function captureStory(page, storybookUrl, storyId, path) {
  await page.goto(
    `${storybookUrl}/iframe.html?id=${encodeURIComponent(storyId)}&viewMode=story`,
  );
  await page.waitForSelector("body.sb-show-main, body.sb-show-errordisplay", {
    timeout: 30_000,
  });
  if (await page.$("body.sb-show-errordisplay"))
    return { error: "스토리 렌더링 오류" };
  await page
    .waitForLoadState("networkidle", { timeout: 10_000 })
    .catch(() => {});
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);

  const clip = await page.evaluate(measureRenderedArea, CLIP_PADDING);
  const buffer = await page.screenshot({
    path,
    clip: clip ?? undefined,
    animations: "disabled",
  });
  const size = clip ?? page.viewportSize();
  return {
    buffer,
    size: { width: Math.round(size.width), height: Math.round(size.height) },
  };
}

async function captureStories({
  selected,
  headUrl,
  baseUrl,
  baseIds,
  out,
  viewport,
  warnings,
}) {
  for (const sub of ["before", "after"]) {
    rmSync(resolve(out, sub), { recursive: true, force: true });
    mkdirSync(resolve(out, sub), { recursive: true });
  }
  const browser = await launchBrowser();
  try {
    const context = await browser.newContext({
      viewport,
      deviceScaleFactor: 2,
      reducedMotion: "reduce",
    });
    const page = await context.newPage();
    const results = [];
    for (const story of selected) {
      // 글쓰기 검사기가 `--` 를 금지하므로 PR 본문에 들어갈 파일 이름에서는 `__` 로 바꾼다.
      const fileName = `${story.id.replace(/--/g, "__")}.png`;
      const after = await captureStory(
        page,
        headUrl,
        story.id,
        resolve(out, "after", fileName),
      );
      if (after.error) {
        warnings.push(`${story.id}: 변경 후 ${after.error}`);
        continue;
      }
      let before = null;
      if (baseIds.has(story.id)) {
        before = await captureStory(
          page,
          baseUrl,
          story.id,
          resolve(out, "before", fileName),
        );
        if (before.error) {
          warnings.push(`${story.id}: 변경 전 ${before.error}`);
          before = null;
        }
      }
      results.push({
        story,
        after: {
          file: resolve(out, "after", fileName),
          placeholder: `{{after/${fileName}}}`,
          size: after.size,
          url: null,
        },
        before: before && {
          file: resolve(out, "before", fileName),
          placeholder: `{{before/${fileName}}}`,
          size: before.size,
          url: null,
        },
        // 바이트가 같으면 이 스토리에서는 화면 변화가 없다는 뜻이다.
        identical: Boolean(before && before.buffer.equals(after.buffer)),
      });
    }
    return results;
  } finally {
    await browser.close();
  }
}

// ---------- GitHub 업로드 ----------

/*
 * PR 본문에 쓰는 GitHub 사진 주소는 공개 API 로 받을 수 없고, 로그인한 브라우저에서 댓글 입력창에
 * 파일을 넣어야 받는다. 아래 세 함수는 Aside REPL 안에서 실행되므로 REPL 전역(openTab, closeTab,
 * sleep, Buffer)을 쓴다. REPL 은 줄마다 따로 평가하므로 함수 본문을 한 줄로 접어 넣으며, 이 때문에
 * 함수 안에는 줄 주석을 쓰지 않는다. 줄 사이 상태는 globalThis.knotUpload 에 둔다.
 */

/** PR 페이지를 새 탭으로 열고, 댓글 입력창이 있고 비어 있는지 확인한다. */
async function openUploadTab({ prUrl, marker }) {
  const tab = await openTab(prUrl);
  const field = tab.locator("#new_comment_field");
  const input = tab.locator("#fc-new_comment_field");
  globalThis.knotUpload = { tab, field, input, ready: false };
  if ((await input.count()) === 0) {
    console.log(
      marker +
        JSON.stringify({
          fatal:
            "PR 댓글 입력창이 없습니다. Aside 에서 GitHub 에 로그인했는지 확인해 주세요.",
        }),
    );
    return;
  }
  if ((await field.inputValue()).trim()) {
    console.log(
      marker +
        JSON.stringify({
          fatal: "PR 댓글 입력창에 작성 중인 글이 있어 올리지 않았습니다.",
        }),
    );
    return;
  }
  globalThis.knotUpload.ready = true;
}

/** 파일 하나를 댓글 입력창에 넣고, GitHub 가 적어 준 주소를 읽은 뒤 입력창을 비운다. */
async function uploadOneFile({ key, name, mimeType, base64, pattern, marker }) {
  const { field, input, ready } = globalThis.knotUpload ?? {};
  if (!ready) return;
  let url = null;
  try {
    await input.setInputFiles([
      { name, mimeType, buffer: Buffer.from(base64, "base64") },
    ]);
    for (let i = 0; i < 60 && !url; i += 1) {
      url = (await field.inputValue()).match(new RegExp(pattern))?.[0] ?? null;
      if (!url) await sleep(500);
    }
    console.log(marker + JSON.stringify({ key, url }));
  } catch (error) {
    console.log(
      marker + JSON.stringify({ key, url: null, error: error.message }),
    );
  } finally {
    await field.fill("");
  }
}

/** 입력창을 비우고 탭을 닫는다. 작성 중인 글이 있던 입력창은 건드리지 않는다. */
async function closeUploadTab() {
  const { tab, field, ready } = globalThis.knotUpload ?? {};
  if (!tab) return;
  if (ready) await field.fill("");
  await closeTab(tab);
}

/** 함수를 인자와 함께 바로 호출하는 한 줄짜리 REPL 코드로 만든다. */
function toReplLine(fn, arg) {
  const source = fn.toString().replace(/\s*\n\s*/g, " ");
  return `await (${source})(${JSON.stringify(arg)});`;
}

function stripAnsi(text) {
  // eslint-disable-next-line no-control-regex
  return text.replace(/\x1b\[[0-9;]*m/g, "");
}

/**
 * `aside repl` 에 코드를 한 줄씩 넣는다. REPL 은 표준 입력이 닫히면 실행 중인 줄만 끝내고 종료하므로,
 * 줄마다 끝났다는 표시(`[ok | 12ms]`, `[error | 12ms]`)를 본 뒤 다음 줄을 보내고 마지막에 `exit` 를 보낸다.
 */
function runReplLines(lines, timeoutMs) {
  return new Promise((resolveRun) => {
    const child = spawn("aside", ["repl"], { cwd: FRONTEND_DIR });
    let output = "";
    let sent = 0;
    let finished = 0;
    const sendNext = () => {
      if (sent < lines.length) child.stdin.write(`${lines[sent++]}\n`);
      else child.stdin.end("exit\n");
    };
    const onData = (chunk) => {
      output += chunk.toString();
      const done = (stripAnsi(output).match(/\[(ok|error) \| \d+ms\]/g) ?? [])
        .length;
      while (finished < done) {
        finished += 1;
        sendNext();
      }
    };
    const timer = setTimeout(() => child.kill("SIGTERM"), timeoutMs);
    const finish = () => {
      clearTimeout(timer);
      resolveRun(stripAnsi(output));
    };
    child.stdout.on("data", onData);
    child.stderr.on("data", onData);
    child.stdin.on("error", () => {});
    child.on("error", finish);
    child.on("close", finish);
    sendNext();
  });
}

/** REPL 출력에서 표식 뒤의 JSON 만 읽는다. 표식이 코드째 다시 찍힌 줄은 JSON 이 아니므로 건너뛴다. */
function parseUploadReports(output) {
  return output.split("\n").flatMap((line) => {
    const index = line.indexOf(UPLOAD_MARKER);
    const json = index === -1 ? "" : line.slice(index + UPLOAD_MARKER.length);
    if (!json.startsWith("{")) return [];
    try {
      return [JSON.parse(json)];
    } catch {
      return [];
    }
  });
}

/**
 * 파일들을 PR 새 댓글 입력창에 올려 `key → GitHub 주소` 를 돌려준다. 댓글은 등록하지 않는다.
 * 파일은 base64 로 표준 입력에 실어 보내므로 명령줄 길이 제한을 받지 않는다.
 */
async function uploadToGitHub(prUrl, uploads, warnings) {
  const urls = new Map();
  if (uploads.length === 0) return urls;
  if (!run("aside", ["--version"], { allowFailure: true }).ok) {
    warnings.push(
      "aside CLI 가 없어 사진을 GitHub 에 올리지 않았습니다. 본문에 {{...}} 표시를 넣고 직접 올려 주세요.",
    );
    return urls;
  }

  const lines = [
    toReplLine(openUploadTab, { prUrl, marker: UPLOAD_MARKER }),
    ...uploads.map((upload) =>
      toReplLine(uploadOneFile, {
        key: upload.key,
        name: basename(upload.file),
        mimeType:
          MIME_TYPES[extname(upload.file)] ?? "application/octet-stream",
        base64: readFileSync(upload.file).toString("base64"),
        pattern: ATTACHMENT_URL_PATTERN.source,
        marker: UPLOAD_MARKER,
      }),
    ),
    toReplLine(closeUploadTab, {}),
  ];
  const output = await runReplLines(lines, 60_000 + uploads.length * 40_000);
  const reports = parseUploadReports(output);

  const fatal = reports.find((report) => report.fatal);
  if (fatal) {
    warnings.push(`사진을 GitHub 에 올리지 않았습니다: ${fatal.fatal}`);
    return urls;
  }
  if (reports.length === 0) {
    warnings.push(
      `Aside REPL 결과를 읽지 못해 사진을 올리지 못했습니다. Aside 가 켜져 있는지 확인해 주세요.\n${output.trim().slice(-500)}`,
    );
    return urls;
  }
  for (const upload of uploads) {
    const report = reports.find((item) => item.key === upload.key);
    if (report?.url) urls.set(upload.key, report.url);
    else
      warnings.push(
        `${upload.key}: GitHub 에 올리지 못했습니다.${report?.error ? ` (${report.error})` : ""}`,
      );
  }
  return urls;
}

function fileHash(path) {
  return createHash("sha256").update(readFileSync(path)).digest("hex");
}

/** `{{after/a.png}}` → `after/a.png` */
function placeholderKey(placeholder) {
  return placeholder.slice(2, -2);
}

/**
 * 아직 올리지 않았거나 올린 뒤 내용이 바뀐 사진만 올리고, 각 항목의 `url`·`hash` 를 채운다.
 * 항목은 `{ file, placeholder, url, hash }` 모양이며 제자리에서 고친다.
 */
async function uploadAssets(prUrl, assets, warnings) {
  const pending = assets.filter(
    (asset) => !asset.url || asset.hash !== fileHash(asset.file),
  );
  const urls = await uploadToGitHub(
    prUrl,
    pending.map((asset) => ({
      key: placeholderKey(asset.placeholder),
      file: asset.file,
    })),
    warnings,
  );
  for (const asset of pending) {
    asset.url = urls.get(placeholderKey(asset.placeholder)) ?? null;
    asset.hash = asset.url ? fileHash(asset.file) : null;
  }
  return pending.length;
}

function listDiagrams(out, previous = []) {
  const dir = resolve(out, "diagrams");
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .filter((name) => name.endsWith(".svg"))
    .sort()
    .map((name) => {
      const file = resolve(dir, name);
      const known = previous.find((diagram) => diagram.file === file);
      return {
        file,
        placeholder: `{{diagrams/${name}}}`,
        url: known?.url ?? null,
        hash: known?.hash ?? null,
      };
    });
}

function storyAssets(stories) {
  return stories
    .flatMap((story) => [story.after, story.before])
    .filter(Boolean);
}

/** `--upload-only`: 이미 만든 manifest 를 읽어 남은 사진과 다이어그램만 올린다. */
async function uploadOnly(options) {
  const manifestPath = resolve(options.out, "manifest.json");
  if (!existsSync(manifestPath))
    throw new Error(
      `${manifestPath} 가 없습니다. 먼저 --upload-only 없이 실행해 주세요.`,
    );
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const pr = findPullRequest();
  if (!pr)
    throw new Error(
      "현재 브랜치의 PR 을 찾지 못했습니다. PR 을 연 뒤 다시 실행해 주세요.",
    );

  const warnings = [];
  const diagrams = listDiagrams(options.out, manifest.diagrams);
  const attempted = await uploadAssets(
    pr.url,
    [...storyAssets(manifest.stories ?? []), ...diagrams],
    warnings,
  );
  manifest.diagrams = diagrams;
  manifest.upload = { method: "aside", prUrl: pr.url, attempted, warnings };
  return manifest;
}

// ---------- main ----------

function writeManifest(out, manifest) {
  writeFileSync(
    resolve(out, "manifest.json"),
    `${JSON.stringify(manifest, null, 2)}\n`,
  );
  process.stdout.write(`${JSON.stringify(manifest, null, 2)}\n`);
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.uploadOnly) {
    writeManifest(options.out, await uploadOnly(options));
    return;
  }
  mkdirSync(options.out, { recursive: true });
  const warnings = [];

  const branch = run("git", ["rev-parse", "--abbrev-ref", "HEAD"]).stdout;
  const headSha = run("git", ["rev-parse", "HEAD"]).stdout;
  const changedFiles = listChangedFiles(options.base);

  // develop 에서 작업했다면 PR 이 없으므로 PR 링크를 만들지 않는다.
  const pr = branch === BASE_BRANCH ? null : findPullRequest();
  if (branch !== BASE_BRANCH && !pr)
    warnings.push(
      `${branch} 브랜치의 PR 을 찾지 못해 파일 링크를 만들지 않았습니다.`,
    );

  const files = changedFiles.map((file) => ({
    path: file.path,
    deleted: file.deleted,
    link: pr ? diffLink(pr.url, file.path) : null,
  }));

  let stories = [];
  let headSource = null;
  let stopLocal = null;
  try {
    if (options.capture) {
      let headUrl = options.headStorybook;
      if (headUrl) headSource = "지정 주소";
      else if (pr?.storybook && headSha.startsWith(pr.storybook.sha)) {
        headUrl = pr.storybook.url;
        headSource = `PR 미리보기 (${pr.storybook.sha})`;
      } else {
        if (pr?.storybook)
          warnings.push(
            `PR 미리보기가 ${pr.storybook.sha} 기준이라 HEAD 와 달라 로컬 Storybook 으로 캡처했습니다.`,
          );
        const local = await startLocalStorybook();
        headUrl = local.url;
        stopLocal = local.stop;
        headSource = "로컬 Storybook";
      }

      const headStories = await fetchStoryIndex(headUrl);
      if (!headStories)
        throw new Error(`${headUrl}/index.json 을 읽지 못했습니다.`);
      const baseStories = await fetchStoryIndex(options.baseStorybook);
      if (!baseStories)
        warnings.push(
          `${options.baseStorybook} 에 접근하지 못해 변경 전 사진을 찍지 않았습니다.`,
        );

      const selected = selectStories(headStories, changedFiles);
      if (selected.length === 0)
        warnings.push("변경 파일이 속한 스토리가 없어 캡처하지 않았습니다.");

      const captured = await captureStories({
        selected,
        headUrl,
        baseUrl: options.baseStorybook,
        baseIds: new Set((baseStories ?? []).map((story) => story.id)),
        out: options.out,
        viewport: options.viewport,
        warnings,
      });
      stories = captured.map(({ story, after, before, identical }) => ({
        id: story.id,
        title: story.title,
        name: story.name,
        importPath: story.importPath,
        link: pr?.storybook
          ? `${pr.storybook.url}/?path=/story/${story.id}`
          : null,
        after,
        before,
        identical,
      }));
    }
  } finally {
    stopLocal?.();
  }

  let upload = null;
  if (options.upload && stories.length > 0) {
    const uploadWarnings = [];
    let attempted = 0;
    if (pr)
      attempted = await uploadAssets(
        pr.url,
        storyAssets(stories),
        uploadWarnings,
      );
    else
      uploadWarnings.push(
        branch === BASE_BRANCH
          ? "develop 브랜치라 PR 이 없어 사진을 GitHub 에 올리지 않았습니다."
          : "PR 을 찾지 못해 사진을 GitHub 에 올리지 않았습니다. PR 을 연 뒤 --upload-only 로 올려 주세요.",
      );
    upload = {
      method: "aside",
      prUrl: pr?.url ?? null,
      attempted,
      warnings: uploadWarnings,
    };
  }

  const manifest = {
    branch,
    base: options.base,
    pr: pr && {
      number: pr.number,
      url: pr.url,
      storybookUrl: pr.storybook?.url ?? null,
    },
    capture: options.capture
      ? {
          headSource,
          baseStorybook: options.baseStorybook,
          deviceScaleFactor: 2,
        }
      : null,
    files,
    stories,
    diagrams: [],
    upload,
    warnings,
  };
  writeManifest(options.out, manifest);
}

main().catch((error) => {
  process.stderr.write(`[실패] ${error?.message ?? error}\n`);
  process.exit(1);
});
