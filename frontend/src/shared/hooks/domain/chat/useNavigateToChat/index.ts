import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

/**
 * 탐색(채팅) 화면(`/workspace/:workspaceId/chat`)으로 이동하는 도메인 훅.
 *
 * 히스토리에 push하므로 뒤로 가기 때 원래 화면으로 돌아와요.
 */
const useNavigateToChat = () => {
  const navigate = useNavigate();

  const navigateToChat = (workspaceId: string) => {
    navigate(getRouterPath({ routeKey: "CHAT", params: { workspaceId } }));
  };

  return { navigateToChat };
};

export default useNavigateToChat;
