import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useCallback } from "react";
import { useNavigate } from "react-router";

interface NavigateToWorkspaceParams {
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 워크스페이스 생성·참여 선택 화면(`/workspace`)으로 이동하는 도메인 훅.
 *
 * 멤버가 아니거나 없는 워크스페이스에 들어가려 했을 때처럼, 다시 고르게 해야 할 때 씁니다.
 * `useEffect` 안에서 부르는 곳(`useWorkspaceAccessGuard`)이 있어 참조를 `useCallback`으로 고정해요.
 */
const useNavigateToWorkspace = () => {
  const navigate = useNavigate();

  const navigateToWorkspace = useCallback(
    ({ replace = false }: NavigateToWorkspaceParams = {}) => {
      navigate(PATH_ROUTE.WORKSPACE, { replace });
    },
    [navigate],
  );

  return { navigateToWorkspace };
};

export default useNavigateToWorkspace;
