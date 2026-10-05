import { http, HttpResponse } from "msw";

import { recordingEndResponse } from "@api/mock/responses/recording";

export const recordingEndHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/recordings/:recordingId/end",
    () => HttpResponse.json(recordingEndResponse),
  ),
];
