import type {
  NotionImportStartResponse,
  NotionImportStatusResponse,
} from "@api/mock/types/notionImport";

export const notionImportStartResponse = {
  id: 1,
} satisfies NotionImportStartResponse;

// 기본값은 바로 완료된 실행 하나만 둬요. RUNNING·FAILED 같은 변형은 테스트에서 mockServer.use로 덮어요
export const notionImportStatusResponse = {
  id: 1,
  status: "COMPLETED",
  totalPageCount: 8,
  processedPageCount: 8,
  failureReason: null,
  createdAt: "2026-09-02T00:00:00Z",
  startedAt: "2026-09-02T00:00:01Z",
  completedAt: "2026-09-02T00:00:30Z",
} satisfies NotionImportStatusResponse;
