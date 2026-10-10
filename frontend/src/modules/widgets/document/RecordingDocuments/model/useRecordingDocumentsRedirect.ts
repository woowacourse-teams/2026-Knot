import { getRouterPath } from "@routes/PATH_ROUTE";
import { useEffect } from "react";
import { useNavigate } from "react-router";

import type { RecordingDocumentsViewStatus } from "../types/recordingDocuments";

interface UseRecordingDocumentsRedirectParams {
  workspaceId: number;
  viewStatus: RecordingDocumentsViewStatus;
}

/**
 * 정리 화면에 머물 이유가 없는 녹음을 다른 화면으로 보내요.
 *
 * - `completed`: 정리가 끝난 녹음은 워크스페이스 홈으로 보내요. 녹음 직후 확인 화면이 생기면 그 화면으로 바꿔요.
 * - `recording`: 아직 끝내지 않은 녹음은 녹음 화면으로 돌려보내요.
 *
 * 뒤로 가기로 이 주소에 돌아와 다시 보내지는 일이 없도록 지금 기록을 바꿔요(`replace`).
 * 보내는 동안 화면에 그릴 것이 없다는 것을 쓰는 쪽이 알 수 있게 `isRedirecting`을 돌려줘요.
 */
export const useRecordingDocumentsRedirect = ({
  workspaceId,
  viewStatus,
}: UseRecordingDocumentsRedirectParams) => {
  const navigate = useNavigate();

  useEffect(() => {
    const params = { workspaceId: String(workspaceId) };

    if (viewStatus === "completed") {
      navigate(getRouterPath({ routeKey: "WORKSPACE_HOME", params }), {
        replace: true,
      });
    }

    if (viewStatus === "recording") {
      navigate(getRouterPath({ routeKey: "RECORDING", params }), {
        replace: true,
      });
    }
  }, [navigate, viewStatus, workspaceId]);

  return {
    isRedirecting: viewStatus === "completed" || viewStatus === "recording",
  };
};
