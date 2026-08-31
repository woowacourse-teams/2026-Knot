import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 워크스페이스 입장 확인 화면(`/workspace/:workspaceId/join`)으로 이동하는 도메인 훅.
 */
const useNavigateToWorkspaceJoin = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceJoin = (workspaceId: string) => {
    navigate(
      getRouterPath({ routeKey: "WORKSPACE_JOIN", params: { workspaceId } }),
    );
  };

  return { navigateToWorkspaceJoin };
};

export default useNavigateToWorkspaceJoin;
