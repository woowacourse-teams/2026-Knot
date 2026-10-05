import { http, HttpResponse } from "msw";

import {
  MOCK_AUDIO_STORAGE_ORIGIN,
  recordingAudioUploadUrlResponse,
} from "@api/mock/responses/recording";

export const recordingAudioUploadUrlHandlers = [
  // 새 예약은 201이에요. 아직 쓰지 않은 예약의 재발급은 실제 서버가 200으로 줘요
  http.post(
    "*/api/v1/workspaces/:workspaceId/recordings/:recordingId/audio-upload-url",
    () => HttpResponse.json(recordingAudioUploadUrlResponse, { status: 201 }),
  ),
  // 발급한 Presigned URL로 오는 PUT. 객체 저장소는 본문 없이 200을 줘요
  http.put(
    `${MOCK_AUDIO_STORAGE_ORIGIN}/*`,
    () => new HttpResponse(null, { status: 200 }),
  ),
];
