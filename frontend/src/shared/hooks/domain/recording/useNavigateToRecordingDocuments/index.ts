import { getRouterPath } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

interface NavigateToRecordingDocumentsParams {
  workspaceId: string;
  /** 문서를 정리하는 녹음의 ID */
  recordingId: number;
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 녹음 뒤 문서 정리 화면(`/workspace/:workspaceId/recordings/:recordingId`)으로 이동하는 도메인 훅.
 *
 * 녹음을 끝낸 뒤 그 녹음의 문서가 만들어지는 과정을 보여 줄 때 써요.
 * 끝낸 녹음 화면은 뒤로 가기로 다시 볼 이유가 없으니, 그 화면에서 넘어올 때는 `replace`를 켜요.
 */
const useNavigateToRecordingDocuments = () => {
  const navigate = useNavigate();

  const navigateToRecordingDocuments = useCallback(
    ({
      workspaceId,
      recordingId,
      replace = false,
    }: NavigateToRecordingDocumentsParams) => {
      navigate(
        getRouterPath({
          routeKey: "RECORDING_DOCUMENTS",
          params: { workspaceId, recordingId: String(recordingId) },
        }),
        { replace },
      );
    },
    [navigate],
  );

  return { navigateToRecordingDocuments };
};

export default useNavigateToRecordingDocuments;
