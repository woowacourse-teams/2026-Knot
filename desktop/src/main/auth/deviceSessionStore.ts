/**
 * 기기 세션(리프레시 토큰) 저장소 (기획서 5.2 "토큰 저장", 로드맵 Q16과 같은 규칙).
 *
 * 액세스 토큰은 1단계와 같은 `auth.bin`(`tokenStore`)에 그대로 두고, 리프레시 토큰과 세션 정보만
 * `userData/auth-session.bin`에 `safeStorage`로 암호화해 둔다. 두 파일을 나누는 이유는 SPA가 읽는
 * `auth.getToken`의 저장 형식(문자열 하나)을 바꾸지 않기 위해서다 — 1단계 로그인(프래그먼트)으로
 * 들어온 셸도 그대로 동작한다. 값은 로그에 남기지 않는다.
 */

import { createSecretStore } from "../secretStore";
import type { SecretStore } from "../secretStore";

export const DEVICE_SESSION_FILE_NAME = "auth-session.bin";

export interface DeviceSession {
  refreshToken: string;
  sessionId: string;
  deviceName: string;
  /** 액세스 토큰 만료 시각(ISO 8601). 갱신 예약의 기준 */
  accessTokenExpiresAt: string;
}

export interface DeviceSessionStore {
  /** 저장된 세션. 없거나 깨졌으면 null */
  read(): DeviceSession | null;
  write(session: DeviceSession): void;
  clear(): void;
}

export function createDeviceSessionStore(
  store: SecretStore = createSecretStore(DEVICE_SESSION_FILE_NAME, "기기 세션"),
): DeviceSessionStore {
  return {
    read() {
      const raw = store.read();
      if (raw === null) return null;
      return parseSession(raw);
    },
    write(session) {
      store.write(JSON.stringify(session));
    },
    clear() {
      store.clear();
    },
  };
}

export function parseSession(raw: string): DeviceSession | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (typeof parsed !== "object" || parsed === null) return null;
  const { refreshToken, sessionId, deviceName, accessTokenExpiresAt } = parsed as Record<string, unknown>;
  if (
    typeof refreshToken !== "string" ||
    refreshToken.length === 0 ||
    typeof sessionId !== "string" ||
    typeof deviceName !== "string" ||
    typeof accessTokenExpiresAt !== "string"
  ) {
    return null;
  }
  return { refreshToken, sessionId, deviceName, accessTokenExpiresAt };
}
