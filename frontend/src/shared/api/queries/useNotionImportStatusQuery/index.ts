import { getNotionImportStatusApi } from "@api/fetch/api/v1/imports/[importRunId]";
import { notionImportKeys } from "@api/queryKey/notionImport";
import { skipToken, useQuery } from "@tanstack/react-query";

/** 진행 중인 Import의 상태를 다시 물어보는 간격(ms) */
const POLL_INTERVAL_MS = 1000;

interface UseNotionImportStatusQueryParams {
  importRunId: number | null;
}

/**
 * Notion Import 실행 상태를 조회하는 쿼리 훅.
 *
 * 시작 응답의 실행 ID로 상태를 조회하고, 끝나기 전(PENDING·RUNNING)에는 1초 간격으로
 * 다시 물어봐요. COMPLETED·FAILED가 오거나 조회가 실패하면 폴링을 멈춰요.
 * 추적할 실행이 없으면(`importRunId === null`) 요청하지 않아요.
 */
const useNotionImportStatusQuery = ({
  importRunId,
}: UseNotionImportStatusQueryParams) => {
  return useQuery({
    queryKey: notionImportKeys.detail(importRunId),
    queryFn:
      importRunId === null
        ? skipToken
        : () => getNotionImportStatusApi(importRunId),
    refetchInterval: (query) => {
      if (query.state.status === "error") return false;

      const status = query.state.data?.status;
      if (status === "COMPLETED" || status === "FAILED") return false;

      return POLL_INTERVAL_MS;
    },
  });
};

export default useNotionImportStatusQuery;
