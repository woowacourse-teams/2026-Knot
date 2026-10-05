import { http, HttpResponse } from "msw";

import { recordingResumeResponse } from "@api/mock/responses/recording";

export const recordingResumeHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/recordings/:recordingId/resume",
    () => HttpResponse.json(recordingResumeResponse),
  ),
];
