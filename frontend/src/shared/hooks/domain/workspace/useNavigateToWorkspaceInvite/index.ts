import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 팀원 초대 화면(`/workspace/:workspaceId/invite`)으로 이동하는 도메인 훅.
 */
const useNavigateToWorkspaceInvite = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceInvite = (workspaceId: string) => {
    navigate(
      getRouterPath({ routeKey: "WORKSPACE_INVITE", params: { workspaceId } }),
    );
  };

  return { navigateToWorkspaceInvite };
};

export default useNavigateToWorkspaceInvite;
