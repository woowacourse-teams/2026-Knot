/**
 * 파일 로그.
 *
 * A1 스파이크의 산출물은 "GitHub 로그인·Notion OAuth 체인이 Electron 창에서
 * 어떻게 흘렀는가"의 기록이다(로드맵 U1·U2). 네비게이션·리다이렉트 이벤트를
 * 여기로 모은다.
 *
 * 로그 파일 위치: macOS `~/Library/Logs/Knot/main.log`
 */

import log from "electron-log/main";
import type { KnotEnvironment } from "../shared/env";

/**
 * `log.initialize()`를 호출하지 않는다.
 *
 * initialize()는 renderer가 부르는 ipcMain 채널을 연다. 우리 renderer는 원격
 * 웹사이트이므로(기획서 4.5) 사이트 XSS에 로그 파일 쓰기 능력을 주게 된다.
 * 보안 체크리스트 20 "신뢰할 수 없는 콘텐츠에 API 비노출"에 걸린다.
 */
export function initLogging(env: KnotEnvironment, appVersion: string): void {
  log.transports.file.level = "info";
  log.transports.console.level = env.name === "prod" ? "warn" : "debug";
  log.transports.file.maxSize = 5 * 1024 * 1024;

  log.info("[knot] 앱 시작", {
    version: appVersion,
    env: env.name,
    webOrigin: env.webOrigin,
    apiOrigin: env.apiOrigin,
    allowlist: env.navigationAllowlist,
  });
}

export function logFilePath(): string {
  return log.transports.file.getFile().path;
}

export const logger = log;
