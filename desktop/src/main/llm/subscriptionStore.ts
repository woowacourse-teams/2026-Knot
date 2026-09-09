/**
 * 사용자 Claude 구독 자격증명 저장소 (기획서 6.5 "저장", 로드맵 Q61 b).
 *
 * access·refresh 토큰과 만료 시각을 `userData/subscription-auth.bin`에 `safeStorage`로 암호화해 둔다.
 * 서버 액세스 토큰(`auth.bin`)·기기 세션(`auth-session.bin`)과 파일을 나누는 이유는 로그아웃의 단위가
 * 다르기 때문이다 — Knot 로그아웃은 구독 로그인을 건드리지 않고, 구독 로그아웃은 Knot 세션을 건드리지 않는다.
 *
 * 값은 이 디렉터리(`src/main/llm/`) 밖으로 나가지 않으며 renderer·IPC 응답·로그에 싣지 않는다.
 */

import { createSecretStore } from "../secretStore";
import type { SecretStore } from "../secretStore";

export const SUBSCRIPTION_FILE_NAME = "subscription-auth.bin";

export interface SubscriptionCredentials {
  accessToken: string;
  refreshToken: string;
  /** 액세스 토큰 만료 시각(ISO 8601). 갱신 예약의 기준 */
  expiresAt: string;
}

export interface SubscriptionStore {
  /** 저장된 자격증명. 없거나 깨졌으면 null */
  read(): SubscriptionCredentials | null;
  write(credentials: SubscriptionCredentials): void;
  clear(): void;
}

export function createSubscriptionStore(
  store: SecretStore = createSecretStore(SUBSCRIPTION_FILE_NAME, "구독 토큰"),
): SubscriptionStore {
  return {
    read() {
      const raw = store.read();
      if (raw === null) return null;
      return parseCredentials(raw);
    },
    write(credentials) {
      store.write(JSON.stringify(credentials));
    },
    clear() {
      store.clear();
    },
  };
}

export function parseCredentials(raw: string): SubscriptionCredentials | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (typeof parsed !== "object" || parsed === null) return null;
  const { accessToken, refreshToken, expiresAt } = parsed as Record<string, unknown>;
  if (
    typeof accessToken !== "string" ||
    accessToken.length === 0 ||
    typeof refreshToken !== "string" ||
    refreshToken.length === 0 ||
    typeof expiresAt !== "string" ||
    Number.isNaN(Date.parse(expiresAt))
  ) {
    return null;
  }
  return { accessToken, refreshToken, expiresAt };
}
