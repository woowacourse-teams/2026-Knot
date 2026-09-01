import { setupServer } from "msw/node";

import { handlers } from "./handlers";

/** vitest 전용 진입점. `vitest.setup.ts`에서 전역으로 켜고, 테스트별 변형은 `mockServer.use(...)`로 덮어요 */
export const mockServer = setupServer(...handlers);
