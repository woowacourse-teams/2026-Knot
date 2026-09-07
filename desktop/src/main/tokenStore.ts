/**
 * 액세스 토큰 저장소 (기획서 5.1, 로드맵 Q16).
 *
 * `safeStorage`는 macOS Keychain·Windows DPAPI가 관리하는 키로 암호화한다.
 * 그 결과를 `userData/auth.bin`에 두고, renderer에는 preload 뒤의 세 함수만 연다.
 * renderer의 `localStorage`를 쓰지 않는 이유는 데스크톱에서 그것이 평문 파일이기 때문이다.
 *
 * `isEncryptionAvailable()`이 false면(Linux `basic_text` 등) 파일에 쓰지 않고 메모리로만
 * 들고 있다가 앱이 꺼질 때 잃는다. 평문으로 디스크에 남기는 것보다 매 실행 로그인이 낫다.
 *
 * **토큰 값은 로그·크래시 리포트에 절대 쓰지 않는다**(기획서 4.4).
 */

import { app, safeStorage } from "electron";
import { readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { logger } from "./logging";

const TOKEN_FILE_NAME = "auth.bin";

/** 암호화가 불가능한 환경에서 쓰는 대체 저장소. 앱이 꺼지면 사라진다 */
let memoryToken: string | null = null;

function tokenFilePath(): string {
  return join(app.getPath("userData"), TOKEN_FILE_NAME);
}

export function isEncryptionAvailable(): boolean {
  return safeStorage.isEncryptionAvailable();
}

export function readToken(): string | null {
  if (!isEncryptionAvailable()) return memoryToken;

  try {
    return safeStorage.decryptString(readFileSync(tokenFilePath()));
  } catch (error) {
    // 파일이 없는 것은 로그인하지 않은 정상 상태다. 복호화 실패(키 변경·손상)도 같게 다룬다
    logger.info("[knot] 저장된 토큰 없음", { reason: errorName(error) });
    return null;
  }
}

export function writeToken(token: string): void {
  if (!isEncryptionAvailable()) {
    memoryToken = token;
    logger.warn("[knot] 암호화를 쓸 수 없어 토큰을 메모리에만 둔다");
    return;
  }

  writeFileSync(tokenFilePath(), safeStorage.encryptString(token), { mode: 0o600 });
}

export function clearToken(): void {
  memoryToken = null;
  rmSync(tokenFilePath(), { force: true });
}

/** 로그에는 오류의 종류만 남긴다. 메시지에 경로·토큰이 섞일 수 있다 */
function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
