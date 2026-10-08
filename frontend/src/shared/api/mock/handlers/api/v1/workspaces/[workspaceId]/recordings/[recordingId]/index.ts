import { http, HttpResponse } from "msw";

import { recordingDetailsResponse } from "@api/mock/responses/recording";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const recordingHandlers = [
  http.get(
    "*/api/v1/workspaces/:workspaceId/recordings/:recordingId",
    ({ params }) => {
      const recording = recordingDetailsResponse.find(
        ({ recordingId }) => recordingId === Number(params.recordingId),
      );

      if (recording === undefined) {
        return HttpResponse.json(
          {
            code: "RECORDING_NOT_FOUND",
            message: "녹음 세션을 찾을 수 없습니다.",
          },
          { status: 404 },
        );
      }

      return HttpResponse.json(recording);
    },
  ),
];
