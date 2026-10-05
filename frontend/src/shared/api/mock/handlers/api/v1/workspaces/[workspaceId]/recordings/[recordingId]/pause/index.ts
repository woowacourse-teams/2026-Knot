import { http, HttpResponse } from "msw";

import { recordingPauseResponse } from "@api/mock/responses/recording";

export const recordingPauseHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/recordings/:recordingId/pause",
    () => HttpResponse.json(recordingPauseResponse),
  ),
];
