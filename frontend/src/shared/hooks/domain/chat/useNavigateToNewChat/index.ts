import { useNavigate } from "react-router";
import { getRouterPath } from "@routes/PATH_ROUTE";

/**
 * 새 채팅 화면(`/workspace/:workspaceId/chat`)으로 이동하는 도메인 훅.
 */
const useNavigateToNewChat = () => {
  const navigate = useNavigate();

  const navigateToNewChat = (workspaceId: string) => {
    navigate(
      getRouterPath({
        routeKey: "CHAT",
        params: { workspaceId },
      }),
      { replace: true },
    );
  };

  return { navigateToNewChat };
};

export default useNavigateToNewChat;
