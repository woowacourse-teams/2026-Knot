/**
 * `safeStorage`로 암호화한 비밀값 파일 저장소.
 *
 * 액세스 토큰(`auth.bin`, 기획서 5.1)과 사용자 LLM 키(`llm-key.bin`, 기획서 6.4)가
 * 같은 규칙을 쓴다. `safeStorage`는 macOS Keychain·Windows DPAPI가 관리하는 키로
 * 암호화하고, 그 결과를 `userData` 아래 파일에 둔다.
 *
 * `isEncryptionAvailable()`이 false면(Linux `basic_text` 등) 파일에 쓰지 않고 메모리로만
 * 들고 있다가 앱이 꺼질 때 잃는다. 평문으로 디스크에 남기는 것보다 매 실행 입력이 낫다.
 *
 * **값은 로그·크래시 리포트에 절대 쓰지 않는다**(기획서 4.4).
 */

import { app, safeStorage } from "electron";
import { readFileSync, rmSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { logger } from "./logging";

export interface SecretStore {
  /** 저장된 값. 없거나 복호화에 실패하면 null */
  read(): string | null;
  write(value: string): void;
  clear(): void;
}

export function isEncryptionAvailable(): boolean {
  return safeStorage.isEncryptionAvailable();
}

/**
 * @param fileName `userData` 아래 파일 이름
 * @param label 로그에 쓰는 값의 이름("토큰"·"LLM 키"). 값 자체는 로그에 남기지 않는다
 */
export function createSecretStore(fileName: string, label: string): SecretStore {
  /** 암호화가 불가능한 환경에서 쓰는 대체 저장소. 앱이 꺼지면 사라진다 */
  let memoryValue: string | null = null;

  const filePath = (): string => join(app.getPath("userData"), fileName);

  return {
    read() {
      if (!isEncryptionAvailable()) return memoryValue;

      try {
        return safeStorage.decryptString(readFileSync(filePath()));
      } catch (error) {
        // 파일이 없는 것은 정상 상태다. 복호화 실패(키 변경·손상)도 같게 다룬다
        logger.info(`[knot] 저장된 ${label} 없음`, { reason: errorName(error) });
        return null;
      }
    },

    write(value) {
      if (!isEncryptionAvailable()) {
        memoryValue = value;
        logger.warn(`[knot] 암호화를 쓸 수 없어 ${label}을(를) 메모리에만 둔다`);
        return;
      }

      writeFileSync(filePath(), safeStorage.encryptString(value), { mode: 0o600 });
    },

    clear() {
      memoryValue = null;
      rmSync(filePath(), { force: true });
    },
  };
}

/** 로그에는 오류의 종류만 남긴다. 메시지에 경로·값이 섞일 수 있다 */
function errorName(error: unknown): string {
  return error instanceof Error ? error.name : "Unknown";
}
