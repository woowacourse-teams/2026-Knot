import "@testing-library/jest-dom/vitest";
import { afterAll, afterEach, beforeAll } from "vitest";

import { mockServer } from "@api/mock/server";

// 핸들러 없는 요청은 테스트 실패로 드러나요. 워커별 setupFiles라 파일 사이 핸들러 누수는 resetHandlers로 막아요
beforeAll(() => mockServer.listen({ onUnhandledRequest: "error" }));
afterEach(() => mockServer.resetHandlers());
afterAll(() => mockServer.close());
