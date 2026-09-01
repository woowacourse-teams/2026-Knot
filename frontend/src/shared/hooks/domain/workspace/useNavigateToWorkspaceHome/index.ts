import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 워크스페이스 홈 화면(`/workspace/:workspaceId`)으로 이동하는 도메인 훅.
 */
const useNavigateToWorkspaceHome = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceHome = (workspaceId: string) => {
    navigate(
      getRouterPath({ routeKey: "WORKSPACE_HOME", params: { workspaceId } }),
    );
  };

  return { navigateToWorkspaceHome };
};

export default useNavigateToWorkspaceHome;
