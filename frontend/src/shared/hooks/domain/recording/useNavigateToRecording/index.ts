import { getRouterPath } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

interface NavigateToRecordingParams {
  workspaceId: string;
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 녹음 화면(`/workspace/:workspaceId/recording`)으로 이동하는 도메인 훅.
 *
 * 녹음 뒤 문서 정리 화면처럼 뒤로 가기로 다시 볼 이유가 없는 곳에서 넘어올 때는 `replace`를 켜요.
 * `useEffect` 안에서 부르는 곳(정리 화면)이 있어 참조를 `useCallback`으로 고정해요.
 */
const useNavigateToRecording = () => {
  const navigate = useNavigate();

  const navigateToRecording = useCallback(
    ({ workspaceId, replace = false }: NavigateToRecordingParams) => {
      navigate(
        getRouterPath({ routeKey: "RECORDING", params: { workspaceId } }),
        { replace },
      );
    },
    [navigate],
  );

  return { navigateToRecording };
};

export default useNavigateToRecording;
