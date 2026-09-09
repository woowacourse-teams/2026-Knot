/**
 * main·preload·mcp 번들 빌드.
 *
 * renderer 빌드는 없다(원격 로드). Forge `plugin-webpack`/`plugin-vite`는
 * renderer 엔트리를 전제하므로 쓰지 않고 esbuild를 hooks에서 직접 돌린다
 * (기획서 9.1).
 *
 * 환경변수:
 * - `KNOT_DESKTOP_ENV`  prod | dev | local (기본 dev)
 * - `KNOT_API_ORIGIN`   prod 빌드에서 필수(로드맵 Q3). dev·local은 생략 가능
 */

import { build } from "esbuild";
import { readFileSync, rmSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
// Forge의 패키징 산출물 디렉터리는 out/이며 packager가 자기 outDir을 자동으로
// 제외한다. 번들을 out/에 두면 앱에 포함되지 않으므로 dist/에 낸다.
const OUT_DIR = path.join(ROOT, "dist");

const ENV_NAMES = ["prod", "dev", "local"];

const envName = process.env.KNOT_DESKTOP_ENV ?? "dev";
if (!ENV_NAMES.includes(envName)) {
  throw new Error(`KNOT_DESKTOP_ENV는 ${ENV_NAMES.join(" | ")} 중 하나여야 한다: ${envName}`);
}

const apiOrigin = process.env.KNOT_API_ORIGIN ?? null;
if (envName === "prod" && apiOrigin === null) {
  throw new Error("prod 빌드에는 KNOT_API_ORIGIN이 필요하다(로드맵 Q3).");
}

const packageJson = JSON.parse(readFileSync(path.join(ROOT, "package.json"), "utf8"));

const define = {
  __KNOT_VERSION__: JSON.stringify(packageJson.version),
  __KNOT_ENV__: JSON.stringify(envName),
  __KNOT_API_ORIGIN__: JSON.stringify(apiOrigin),
};

const shared = {
  bundle: true,
  platform: "node",
  // Electron 44는 Node 24를 번들한다(지식 §2.1)
  target: "node24",
  format: "cjs",
  external: ["electron"],
  sourcemap: true,
  define,
  logLevel: "info",
};

rmSync(path.join(OUT_DIR, "main"), { recursive: true, force: true });
rmSync(path.join(OUT_DIR, "preload"), { recursive: true, force: true });
rmSync(path.join(OUT_DIR, "mcp"), { recursive: true, force: true });

await Promise.all([
  build({
    ...shared,
    entryPoints: [path.join(ROOT, "src/main/index.ts")],
    outfile: path.join(OUT_DIR, "main/index.cjs"),
  }),
  build({
    ...shared,
    entryPoints: [path.join(ROOT, "src/preload/index.ts")],
    outfile: path.join(OUT_DIR, "preload/index.cjs"),
  }),
  // utilityProcess 엔트리(로드맵 Q47). @modelcontextprotocol/sdk까지 한 파일로 묶어
  // asar 안에서 모듈 해석에 기대지 않는다
  build({
    ...shared,
    entryPoints: [path.join(ROOT, "src/mcp/index.ts")],
    outfile: path.join(OUT_DIR, "mcp/index.cjs"),
  }),
]);

console.log(`[knot-desktop] 빌드 완료 — env=${envName} apiOrigin=${apiOrigin ?? "(기본값)"}`);
