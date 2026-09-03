import { useNavigate } from "react-router";
import { getRouterPath } from "@routes/PATH_ROUTE";

interface NavigateToChatSessionParams {
  workspaceId: string;
  sessionId: string;
  replace?: boolean;
}

/**
 * 특정 대화 화면(`/workspace/:workspaceId/chat/:sessionId`)으로 이동하는 도메인 훅.
 */
const useNavigateToChatSession = () => {
  const navigate = useNavigate();

  const navigateToChatSession = ({
    workspaceId,
    sessionId,
    replace = false,
  }: NavigateToChatSessionParams) => {
    navigate(
      getRouterPath({
        routeKey: "CHAT_SESSION",
        params: { workspaceId, sessionId },
      }),
      { replace },
    );
  };

  return { navigateToChatSession };
};

export default useNavigateToChatSession;
