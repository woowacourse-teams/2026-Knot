import useNavigateToChat from "@hooks/domain/chat/useNavigateToChat";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useMatch, useParams } from "react-router";

/**
 * 워크스페이스 안의 두 화면(홈·탐색) 중 지금 어디에 있는지 알려주고, 서로 오가게 하는 도메인 훅.
 *
 * GNB의 내비 필이 쓰지만 반환값이 화면 이름과 이동 함수뿐이라 다른 화면에서도 그대로 쓸 수 있어요.
 * 이미 있는 화면의 버튼은 눌러도 이동하지 않아 히스토리가 같은 화면으로 쌓이지 않아요.
 */
const useWorkspaceNav = () => {
  const { workspaceId } = useParams();
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const { navigateToChat } = useNavigateToChat();

  const isHomeActive = useMatch(PATH_ROUTE.WORKSPACE_HOME) !== null;
  // 세션이 붙은 `/chat/:sessionId`도 탐색 화면이므로 하위 경로까지 매칭해요
  const isChatActive = useMatch({ path: PATH_ROUTE.CHAT, end: false }) !== null;

  const navigateToHome = () => {
    if (!workspaceId || isHomeActive) return;

    navigateToWorkspaceHome({ workspaceId });
  };

  const navigateToExplore = () => {
    if (!workspaceId || isChatActive) return;

    navigateToChat({ workspaceId });
  };

  return { isHomeActive, isChatActive, navigateToHome, navigateToExplore };
};

export default useWorkspaceNav;
