import { getRouterPath } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

interface NavigateToWorkspaceHomeParams {
  workspaceId: string;
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 워크스페이스 홈 화면(`/workspace/:workspaceId`)으로 이동하는 도메인 훅.
 *
 * 노션 연동을 마치고 돌아온 화면처럼 뒤로 가기로 다시 볼 이유가 없는 곳에서는 `replace`를 켜요.
 * `useEffect` 안에서 부르는 곳(노션 연동 카드)이 있어 참조를 `useCallback`으로 고정해요.
 */
const useNavigateToWorkspaceHome = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceHome = useCallback(
    ({ workspaceId, replace = false }: NavigateToWorkspaceHomeParams) => {
      navigate(
        getRouterPath({ routeKey: "WORKSPACE_HOME", params: { workspaceId } }),
        { replace },
      );
    },
    [navigate],
  );

  return { navigateToWorkspaceHome };
};

export default useNavigateToWorkspaceHome;
