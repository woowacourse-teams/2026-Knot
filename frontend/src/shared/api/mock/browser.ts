import { setupWorker } from "msw/browser";

import { handlers } from "./handlers";
import { devAuthHandlers, devNotionOAuthHandlers } from "./handlers/dev";

// 개발 서버 전용. src/index.tsx가 API_MOCKING 플래그 안에서 동적 import해요
// 먼저 맞는 핸들러가 이기므로, 개발 전용 핸들러(쿠키 로그인 판정 me·nickname,
// 같은 오리진으로 돌려보내는 Notion OAuth 시작)를 앞에 둬 기본 핸들러만 덮어요.
// vitest(server.ts)에는 넣지 않아요
export const mockWorker = setupWorker(
  ...devAuthHandlers,
  ...devNotionOAuthHandlers,
  ...handlers,
);
