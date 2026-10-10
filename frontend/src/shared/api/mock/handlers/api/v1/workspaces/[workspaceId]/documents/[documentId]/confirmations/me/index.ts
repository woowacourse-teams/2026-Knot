import { http, HttpResponse } from "msw";

import { confirmDocument } from "@api/mock/state/document";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const documentMyConfirmationHandlers = [
  http.put(
    "*/api/v1/workspaces/:workspaceId/documents/:documentId/confirmations/me",
    ({ params }) => {
      // 확인을 기록해, 이 뒤의 문서 상세 · 확인 대상 응답이 바뀌게 해요
      const confirmation = confirmDocument(Number(params.documentId));

      if (confirmation === undefined) {
        return HttpResponse.json(
          { code: "DOCUMENT_NOT_FOUND", message: "문서를 찾을 수 없습니다." },
          { status: 404 },
        );
      }

      return HttpResponse.json(confirmation);
    },
  ),
];
