import { getRouterPath } from "@routes/PATH_ROUTE";
import { useNavigate } from "react-router";

import type { ChatNavigationState } from "@/shared/types/chat";

interface NavigateToChatParams {
  workspaceId: string;
  /** 도착하자마자 보낼 질문. 하단 독에서 적어 보낸 경우에만 있어요 */
  question?: string;
}

/**
 * 탐색(채팅) 화면(`/workspace/:workspaceId/chat`)으로 이동하는 도메인 훅.
 *
 * 히스토리에 push하므로 뒤로 가기 때 원래 화면으로 돌아와요.
 * 질문을 함께 넘기면 탐색 화면이 그 질문으로 대화를 시작해요.
 */
const useNavigateToChat = () => {
  const navigate = useNavigate();

  const navigateToChat = ({ workspaceId, question }: NavigateToChatParams) => {
    const state: ChatNavigationState | undefined = question
      ? { question }
      : undefined;

    navigate(getRouterPath({ routeKey: "CHAT", params: { workspaceId } }), {
      state,
    });
  };

  return { navigateToChat };
};

export default useNavigateToChat;
