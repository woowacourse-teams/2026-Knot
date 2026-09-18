import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 노션 연동 화면(`/workspace/:workspaceId/notion-connection`)으로 이동하는 도메인 훅.
 */
const useNavigateToWorkspaceNotionConnection = () => {
  const navigate = useNavigate();

  const navigateToWorkspaceNotionConnection = (workspaceId: string) => {
    navigate(
      getRouterPath({
        routeKey: "WORKSPACE_NOTION_CONNECTION",
        params: { workspaceId },
      }),
    );
  };

  return { navigateToWorkspaceNotionConnection };
};

export default useNavigateToWorkspaceNotionConnection;
