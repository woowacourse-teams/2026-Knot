/**
 * 빌드 시점에 esbuild `define`으로 치환되는 상수(`scripts/build.mjs`).
 *
 * 기획서 4.5: 환경은 빌드 시 상수로 고정하고 런타임 전환 UI를 두지 않는다.
 * 런타임 `process.env` 조회로 바꾸지 않는다 — Fuse로 `NODE_OPTIONS`를 막아도
 * 환경변수로 로드 오리진이 바뀌면 피싱 표면이 생긴다.
 */

/** `package.json`의 version */
declare const __KNOT_VERSION__: string;

/** 빌드 대상 환경 */
declare const __KNOT_ENV__: "prod" | "dev" | "local";

/** prod 빌드에서 주입되는 API 오리진(로드맵 Q3). dev·local은 null */
declare const __KNOT_API_ORIGIN__: string | null;
