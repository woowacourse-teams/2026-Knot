import { http, HttpResponse } from "msw";

import { notionImportStatusResponse } from "@api/mock/responses/notionImport";

// 경로 파라미터가 있어 fetch 상수 대신 패턴을 직접 적어요. RUNNING·FAILED 상태는 테스트에서 덮어요
export const notionImportStatusHandlers = [
  http.get("*/api/v1/imports/:importRunId", () =>
    HttpResponse.json(notionImportStatusResponse),
  ),
];
