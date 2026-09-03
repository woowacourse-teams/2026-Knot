import useStartNotionImportMutation from "@api/mutations/useStartNotionImportMutation";
import useNotionConnectionQuery from "@api/queries/useNotionConnectionQuery";
import useNotionImportStatusQuery from "@api/queries/useNotionImportStatusQuery";
import { useEffect, useState } from "react";
import { useParams } from "react-router";

import {
  SYNC_FAILED_MESSAGE,
  SYNC_RESULT_RESET_DELAY_MS,
} from "../constants/notionSync";

/**
 * Notion 동기화 카드의 연결 상태와 진행 상태.
 *
 * 현재 `:workspaceId`의 Notion 연결 상태(연결 안 됨·연결됨·재인증 필요)를 조회해 `connectionStatus`로
 * 넘기고, 조회가 실패하면 `isConnectionStatusError`로 알려요. 문구 매핑은 카드가 맡아요.
 *
 * `startSync`가 현재 `:workspaceId`의 동기화(Import)를 시작하고, 응답의 실행 ID로
 * 상태를 폴링해요. 완료(COMPLETED)·실패(FAILED 또는 시작 요청 실패) 결과를 보여준 뒤
 * 2초 뒤에 기본 상태로 돌아가요. 결과가 바뀌거나 언마운트되면 복귀 타이머는 정리돼요.
 */
export const useNotionSync = () => {
  const { workspaceId } = useParams();
  const [importRunId, setImportRunId] = useState<number | null>(null);
  const [isStartFailed, setIsStartFailed] = useState(false);

  const { data: connection, isError: isConnectionStatusError } =
    useNotionConnectionQuery({ workspaceId: Number(workspaceId) });
  const { mutate: startImport, isPending: isStarting } =
    useStartNotionImportMutation();
  const { data: importStatus, isError: isStatusError } =
    useNotionImportStatusQuery({ importRunId });

  const status = importStatus?.status;
  const isSynced = status === "COMPLETED";
  const isSyncFailed = isStartFailed || isStatusError || status === "FAILED";
  const isSyncing =
    isStarting || (importRunId !== null && !isSynced && !isSyncFailed);

  // 완료·실패 결과를 보여준 뒤 기본 상태로 돌아가요. 새 동기화가 시작되거나 언마운트되면 정리돼요
  useEffect(() => {
    const hasResult =
      isStartFailed ||
      isStatusError ||
      status === "COMPLETED" ||
      status === "FAILED";
    if (!hasResult) return;

    // TODO(페이지 트리·마지막 동기화 시각 API 미정): 완료 시 그 쿼리들을 무효화
    const timer = setTimeout(() => {
      setImportRunId(null);
      setIsStartFailed(false);
    }, SYNC_RESULT_RESET_DELAY_MS);

    return () => clearTimeout(timer);
  }, [isStartFailed, isStatusError, status]);

  const startSync = () => {
    if (isSyncing || isSynced) return;

    setIsStartFailed(false);
    startImport(Number(workspaceId), {
      onSuccess: ({ id }) => setImportRunId(id),
      onError: () => setIsStartFailed(true),
    });
  };

  return {
    connectionStatus: connection?.status,
    isConnectionStatusError,
    isSyncing,
    isSynced,
    isSyncFailed,
    syncedDocumentCount: importStatus?.processedPageCount ?? 0,
    failureMessage: importStatus?.failureReason ?? SYNC_FAILED_MESSAGE,
    startSync,
  };
};
