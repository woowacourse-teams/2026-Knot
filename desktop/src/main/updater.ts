/**
 * 자동 업데이트 접착층 (기획서 10.2·10.3, 로드맵 `A4`).
 *
 * `update-electron-app`이 update.electronjs.org에서 GitHub Releases의 새 버전을 찾아 백그라운드로
 * 내려받고, 다 받으면 재시작 다이얼로그를 띄운다(`notifyUser: true`). 켤지 말지는 `updatePolicy`가
 * 정한다. 서명이 없는 macOS 빌드는 `autoUpdater`가 거부하므로(지식 §3.4) 서명·공증(`A3`)이 선행이다.
 */

import { app, autoUpdater } from "electron";
import { UpdateSourceType, updateElectronApp } from "update-electron-app";
import type { KnotEnvName } from "../shared/env";
import { logger } from "./logging";
import { resolveUpdatePolicy } from "./updatePolicy";
import type { UpdatePolicy } from "./updatePolicy";

let activePolicy: UpdatePolicy = { enabled: false, reason: "아직 초기화하지 않았다" };

export function initAutoUpdate(envName: KnotEnvName): UpdatePolicy {
  activePolicy = resolveUpdatePolicy({ isPackaged: app.isPackaged, platform: process.platform, envName });
  if (!activePolicy.enabled) {
    logger.info("[knot] 자동 업데이트 꺼짐", { reason: activePolicy.reason });
    return activePolicy;
  }

  updateElectronApp({
    updateSource: { type: UpdateSourceType.ElectronPublicUpdateService, repo: activePolicy.repo },
    updateInterval: activePolicy.updateInterval,
    logger,
    notifyUser: true,
  });
  logger.info("[knot] 자동 업데이트 켜짐", { repo: activePolicy.repo, updateInterval: activePolicy.updateInterval });
  return activePolicy;
}

export function currentUpdatePolicy(): UpdatePolicy {
  return activePolicy;
}

/** 메뉴 "업데이트 확인". 켜져 있을 때만 서버에 묻고 true를 돌려준다 */
export function checkForUpdatesNow(): boolean {
  if (!activePolicy.enabled) return false;
  autoUpdater.checkForUpdates();
  logger.info("[knot] 업데이트 확인 요청");
  return true;
}
