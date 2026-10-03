import "@testing-library/jest-dom/vitest";
import { afterAll, afterEach, beforeAll, vi } from "vitest";

import { mockServer } from "@api/mock/server";
import { resetSentChatMessages } from "@api/mock/store/chatMessage";

import { installFakeMedia } from "./vitest.media";

// 핸들러 없는 요청은 테스트 실패로 드러내고, 테스트 사이 핸들러 누수는 resetHandlers로 막아요
beforeAll(() => mockServer.listen({ onUnhandledRequest: "error" }));
// jsdom에는 마이크·녹음 API가 없어 가짜를 깔아 둬요
beforeAll(() => installFakeMedia());
afterEach(() => {
  mockServer.resetHandlers();
  // 핸들러가 기억하는 값은 resetHandlers로 되돌아가지 않아 따로 비웁니다
  resetSentChatMessages();
  vi.restoreAllMocks();
});
afterAll(() => mockServer.close());
