import { http, HttpResponse } from "msw";

import { documentDetailsResponse } from "@api/mock/responses/document";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const documentHandlers = [
  http.get(
    "*/api/v1/workspaces/:workspaceId/documents/:documentId",
    ({ params }) => {
      const document = documentDetailsResponse.find(
        ({ id }) => id === Number(params.documentId),
      );

      // 없는 id는 명세의 404로 답해, 개발 서버에서도 주소만 바꿔 문서 없음 화면을 볼 수 있어요
      if (document === undefined) {
        return HttpResponse.json(
          { code: "DOCUMENT_NOT_FOUND", message: "문서를 찾을 수 없습니다." },
          { status: 404 },
        );
      }

      return HttpResponse.json(document);
    },
  ),
];
