/**
 * 자동 업데이트를 켤지 정하는 순수 판정 (기획서 10.2 "업데이트", 로드맵 `A4`·Q53).
 *
 * `update-electron-app` + GitHub Releases(update.electronjs.org)를 쓴다. 조건:
 * - 패키징된 빌드여야 한다(미패키징 빌드는 `update-electron-app`이 스스로 건너뛴다)
 * - macOS·Windows만. Linux는 내장 `autoUpdater`가 지원하지 않는다(지식 §3.4)
 * - `prod` 빌드만. dev·local 빌드는 같은 저장소의 최신 릴리스로 덮어써지면 안 된다(Q53)
 */

import type { KnotEnvName } from "../shared/env";

/** update.electronjs.org가 릴리스를 읽는 공개 저장소(`owner/repo`) */
export const UPDATE_REPO = "woowacourse-teams/2026-Knot";
/** 체크 주기. `update-electron-app` 기본값이며 최소 5분이다 */
export const UPDATE_INTERVAL = "10 minutes";

const UPDATABLE_PLATFORMS = new Set<string>(["darwin", "win32"]);

export interface UpdatePolicyInput {
  isPackaged: boolean;
  platform: string;
  envName: KnotEnvName;
}

export type UpdatePolicy =
  | { enabled: true; repo: string; updateInterval: string }
  | { enabled: false; reason: string };

export function resolveUpdatePolicy(input: UpdatePolicyInput): UpdatePolicy {
  if (!input.isPackaged) {
    return { enabled: false, reason: "미패키징 빌드에서는 자동 업데이트를 하지 않는다" };
  }
  if (!UPDATABLE_PLATFORMS.has(input.platform)) {
    return { enabled: false, reason: `${input.platform}은(는) 내장 autoUpdater가 지원하지 않는다` };
  }
  if (input.envName !== "prod") {
    return { enabled: false, reason: `${input.envName} 빌드는 자동 업데이트 대상이 아니다(prod만)` };
  }
  return { enabled: true, repo: UPDATE_REPO, updateInterval: UPDATE_INTERVAL };
}
