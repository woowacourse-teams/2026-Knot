import { http, HttpResponse } from "msw";

import { recordingAudioUploadCompleteResponse } from "@api/mock/responses/recording";

export const recordingAudioUploadCompleteHandlers = [
  // 같은 uploadId 재호출도 실제 서버는 처음 확정한 결과를 200으로 줘요
  http.post(
    "*/api/v1/workspaces/:workspaceId/recordings/:recordingId/audio-upload-complete",
    () => HttpResponse.json(recordingAudioUploadCompleteResponse),
  ),
];
