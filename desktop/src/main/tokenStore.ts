/**
 * 액세스 토큰 저장소 (기획서 5.1, 로드맵 Q16).
 *
 * `userData/auth.bin`에 `safeStorage`로 암호화해 두고, renderer에는 preload 뒤의 세 함수만
 * 연다. renderer의 `localStorage`를 쓰지 않는 이유는 데스크톱에서 그것이 평문 파일이기
 * 때문이다. 저장 규칙은 `secretStore`에 있다.
 */

import { createSecretStore } from "./secretStore";

export { isEncryptionAvailable } from "./secretStore";

const store = createSecretStore("auth.bin", "토큰");

export function readToken(): string | null {
  return store.read();
}

export function writeToken(token: string): void {
  store.write(token);
}

export function clearToken(): void {
  store.clear();
}
