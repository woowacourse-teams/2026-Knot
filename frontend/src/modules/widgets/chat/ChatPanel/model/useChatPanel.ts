import useNavigateToNewChat from "@/shared/hooks/domain/chat/useNavigateToNewChat";
import { useState } from "react";
import { useParams } from "react-router";

/**
 * 채팅 패널이 어떤 화면을 보여줄지 정합니다.
 *
 * 대화 목록은 별도 라우트가 아니라 패널 위의 상태라서 `isListOpen`으로 다루고,
 * 목록을 닫은 뒤에는 URL의 `sessionId` 유무로 대화 화면과 빈 화면을 가릅니다.
 */
export const useChatPanel = () => {
  const { workspaceId, sessionId } = useParams();
  const { navigateToNewChat } = useNavigateToNewChat();

  const [isListOpen, setIsListOpen] = useState(false);

  const view = isListOpen ? "list" : sessionId ? "session" : "empty";

  const handleOnOpenChatList = () => setIsListOpen(true);

  const handleOnBack = () => setIsListOpen(false);

  const handleStartNewChat = () => {
    setIsListOpen(false);

    if (!workspaceId) return;
    navigateToNewChat(workspaceId);
  };

  return {
    view,
    handleOnOpenChatList,
    handleOnBack,
    handleStartNewChat,
  };
};
