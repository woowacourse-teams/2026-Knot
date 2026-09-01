import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

interface NavigateToWorkspaceJoinParams {
  workspaceId: string;
  /** `true`면 현재 히스토리 항목을 대체해 뒤로 가기 때 지금 화면으로 돌아오지 않아요. */
  replace?: boolean;
}

/**
 * 워크스페이스 입장 확인 화면(`/workspace/:workspaceId/join`)으로 이동하는 도메인 훅.
 */
const useNavigateToWorkspaceJoin = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceJoin = ({
    workspaceId,
    replace = false,
  }: NavigateToWorkspaceJoinParams) => {
    navigate(
      getRouterPath({ routeKey: "WORKSPACE_JOIN", params: { workspaceId } }),
      { replace },
    );
  };

  return { navigateToWorkspaceJoin };
};

export default useNavigateToWorkspaceJoin;
