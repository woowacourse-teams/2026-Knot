import { http, HttpResponse } from "msw";

import { notionImportStartResponse } from "@api/mock/responses/notionImport";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요. 409(이미 실행 중)·에러는 테스트에서 덮어요
export const workspaceNotionImportsHandlers = [
  http.post("*/api/v1/workspaces/:workspaceId/imports", () =>
    HttpResponse.json(notionImportStartResponse, { status: 202 }),
  ),
];
