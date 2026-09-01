import { setupWorker } from "msw/browser";

import { handlers } from "./handlers";

// 개발 서버 전용. src/index.tsx가 API_MOCKING 플래그 안에서 동적 import해요
export const mockWorker = setupWorker(...handlers);
