import { http, HttpResponse } from "msw";

import { recordingStartResponse } from "@api/mock/responses/recording";

export const workspaceRecordingsHandlers = [
  // 새 세션은 201이에요. 같은 requestId 재시도는 실제 서버가 200으로 기존 세션을 돌려줘요
  http.post("*/api/v1/workspaces/:workspaceId/recordings", () =>
    HttpResponse.json(recordingStartResponse, { status: 201 }),
  ),
];
