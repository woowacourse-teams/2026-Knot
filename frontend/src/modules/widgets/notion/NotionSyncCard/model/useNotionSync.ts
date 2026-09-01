import useTimeout from "@hooks/common/useTimeout";
import { useState } from "react";

import { SYNC_DELAY_MS, SYNCED_DOCUMENT_COUNT } from "../constants/notionSync";

/**
 * Notion 동기화 카드의 진행 상태.
 *
 * `startSync`를 부르면 임시 지연 동안 `isSyncing`이 되고, 지연이 끝나면 `isSynced`로 바뀌어요.
 * 지연 중에 카드가 언마운트되면 `useTimeout`이 타이머를 정리해 완료로 넘어가지 않아요.
 */
export const useNotionSync = () => {
  const [isSynced, setIsSynced] = useState(false);
  const { isTimedOut: isSyncing, start } = useTimeout({
    timeout: SYNC_DELAY_MS,
    callback: () => setIsSynced(true),
  });

  const startSync = () => {
    if (isSyncing || isSynced) return;

    start();
  };

  return {
    isSyncing,
    isSynced,
    syncedDocumentCount: SYNCED_DOCUMENT_COUNT,
    startSync,
  };
};
