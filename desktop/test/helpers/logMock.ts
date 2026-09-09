import { vi } from "vitest";

/** `electron-log/main` 대체. 로그 호출을 기록만 한다 */
export const logMock = {
  info: vi.fn(),
  warn: vi.fn(),
  error: vi.fn(),
  transports: { file: { getFile: () => ({ path: "" }) }, console: {} },
};
