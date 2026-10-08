import "@testing-library/jest-dom/vitest";
import { afterAll, afterEach, beforeAll, vi } from "vitest";

import { mockServer } from "@api/mock/server";
import { resetRecordingMockState } from "@api/mock/state/recording";

import { installFakeMedia } from "./vitest.media";

// 핸들러 없는 요청은 테스트 실패로 드러내고, 테스트 사이 핸들러 누수는 resetHandlers로 막아요
beforeAll(() => mockServer.listen({ onUnhandledRequest: "error" }));
// jsdom에는 마이크·녹음 API가 없어 가짜를 깔아 둬요
beforeAll(() => installFakeMedia());
afterEach(() => {
  mockServer.resetHandlers();
  // 요청으로 바뀐 mock 상태가 다음 테스트의 응답을 바꾸지 않게 지워요
  resetRecordingMockState();
  vi.restoreAllMocks();
});
afterAll(() => mockServer.close());
