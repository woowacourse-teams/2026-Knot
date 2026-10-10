import { http, HttpResponse } from "msw";

import { currentRecordingResponse } from "@api/mock/responses/recording";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요. 일시정지·없음(204)·에러는 테스트에서 덮어요
export const currentRecordingHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId/recordings/current", () =>
    HttpResponse.json(currentRecordingResponse),
  ),
];
