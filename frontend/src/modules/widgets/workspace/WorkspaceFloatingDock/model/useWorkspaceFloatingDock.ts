import useNavigateToChat from "@hooks/domain/chat/useNavigateToChat";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useMatch, useParams } from "react-router";

export const useWorkspaceFloatingDock = () => {
  const { workspaceId } = useParams();
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const { navigateToChat } = useNavigateToChat();

  const isHomeActive = useMatch(PATH_ROUTE.WORKSPACE_HOME) !== null;
  // 세션이 붙은 `/chat/:sessionId`도 탐색 화면이므로 하위 경로까지 매칭해요
  const isChatActive = useMatch({ path: PATH_ROUTE.CHAT, end: false }) !== null;

  const handleHomeClick = () => {
    if (!workspaceId || isHomeActive) return;

    navigateToWorkspaceHome(workspaceId);
  };

  const handleChatClick = () => {
    if (!workspaceId || isChatActive) return;

    navigateToChat(workspaceId);
  };

  return { isHomeActive, isChatActive, handleHomeClick, handleChatClick };
};
