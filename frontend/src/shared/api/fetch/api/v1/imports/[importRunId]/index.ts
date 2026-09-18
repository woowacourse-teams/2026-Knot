import {
  GetNotionImportStatusResponseDto,
  type GetNotionImportStatusResponseRaw,
} from "@api/dto/notionImport";
import { httpClient } from "@api/httpClient";

export const NOTION_IMPORT_STATUS_API_PATH = (importRunId: number) =>
  `/api/v1/imports/${importRunId}`;

/**
 * @description Notion Import 실행 하나의 진행 상태를 조회합니다
 * @param importRunId - 시작 응답으로 받은 Import 실행 ID
 * @returns Import 진행 상태와 처리한 Page 수
 * @example
 * const { status, processedPageCount } = await getNotionImportStatusApi(1);
 */
export const getNotionImportStatusApi = async (importRunId: number) => {
  const response = await httpClient<GetNotionImportStatusResponseRaw>({
    method: "get",
    url: NOTION_IMPORT_STATUS_API_PATH(importRunId),
  });

  return new GetNotionImportStatusResponseDto(response.data);
};
