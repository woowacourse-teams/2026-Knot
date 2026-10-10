import { http, HttpResponse } from "msw";

import { findDocuments } from "@api/mock/state/document";

/** size를 보내지 않았을 때 서버가 쓰는 값 */
const DEFAULT_PAGE_SIZE = 50;
const MAX_PAGE_SIZE = 100;

const invalidParameterResponse = () =>
  HttpResponse.json(
    { code: "INVALID_PARAMETER", message: "요청 값이 올바르지 않습니다." },
    { status: 400 },
  );

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요
export const documentsHandlers = [
  http.get("*/api/v1/workspaces/:workspaceId/documents", ({ request }) => {
    const { searchParams } = new URL(request.url);
    const size = Number(searchParams.get("size") ?? DEFAULT_PAGE_SIZE);

    if (!Number.isInteger(size) || size < 1 || size > MAX_PAGE_SIZE) {
      return invalidParameterResponse();
    }

    // 확인을 누른 문서는 내 상태가 바뀐 응답을 받도록 mock 상태를 거쳐 찾아요
    const documents = findDocuments({
      cursor: searchParams.get("cursor"),
      size,
    });

    // 없는 커서도 명세의 400으로 답해요. 첫 페이지를 다시 주면 이어 받는 쪽이 같은 문서를 계속 받아요
    if (documents === undefined) return invalidParameterResponse();

    return HttpResponse.json(documents);
  }),
];
