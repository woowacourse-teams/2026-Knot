import { http, HttpResponse } from "msw";

import { documentConfirmationsResponse } from "@api/mock/responses/document";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const documentConfirmationsHandlers = [
  http.get(
    "*/api/v1/workspaces/:workspaceId/documents/:documentId/confirmations",
    ({ params }) => {
      const confirmations = documentConfirmationsResponse.find(
        ({ documentId }) => documentId === Number(params.documentId),
      );

      if (confirmations === undefined) {
        return HttpResponse.json(
          { code: "DOCUMENT_NOT_FOUND", message: "문서를 찾을 수 없습니다." },
          { status: 404 },
        );
      }

      return HttpResponse.json(confirmations);
    },
  ),
];
