import { http, HttpResponse } from "msw";

import { retryDocumentGenerationJob } from "@api/mock/state/recording";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const documentGenerationJobRetryHandlers = [
  http.post(
    "*/api/v1/workspaces/:workspaceId/document-generation-jobs/:jobId/retry",
    ({ params }) => {
      // 다시 시도를 기록해, 이 뒤의 녹음 상세 응답이 정리 중으로 바뀌게 해요
      const retry = retryDocumentGenerationJob(Number(params.jobId));

      if (retry.result === "notFound") {
        return HttpResponse.json(
          {
            code: "DOCUMENT_GENERATION_JOB_NOT_FOUND",
            message: "문서 생성 작업을 찾을 수 없습니다",
          },
          { status: 404 },
        );
      }

      if (retry.result === "notAllowed") {
        return HttpResponse.json(
          {
            code: "RETRY_NOT_ALLOWED",
            message: "문서 생성 작업을 재시도할 수 없습니다",
          },
          { status: 409 },
        );
      }

      return HttpResponse.json(retry.response, { status: 202 });
    },
  ),
];
