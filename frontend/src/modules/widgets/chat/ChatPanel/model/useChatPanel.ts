import { useParams } from "react-router";
import useNavigateToNewChat from "@hooks/domain/chat/useNavigateToNewChat";
import useOpenedChatSessionList from "@hooks/domain/chat/useOpenedChatSessionList";

/**
 * 채팅 패널이 어떤 화면을 보여줄지 정합니다.
 *
 * 대화 목록은 별도 라우트가 아니라 패널 위에 겹쳐 여는 화면이라 쿼리 파라미터로 다룹니다.
 * 목록을 닫았을 때 대화가 없으면 무엇을 보여줄지는 `Conversation`이 정하므로 여기서는 가리지 않습니다.
 *
 * 목록에서 대화를 고르거나 새 대화를 시작하면 경로가 바뀌면서 파라미터가 사라지므로,
 * 여기서 목록을 따로 닫아 주지 않습니다.
 */
export const useChatPanel = () => {
  const { workspaceId } = useParams();
  const { navigateToNewChat } = useNavigateToNewChat();
  const { isChatSessionListOpen, openChatSessionList, closeChatSessionList } =
    useOpenedChatSessionList();

  const handleStartNewChat = () => {
    if (!workspaceId) return;

    navigateToNewChat(workspaceId);
  };

  return {
    isChatSessionListOpen,
    openChatSessionList,
    closeChatSessionList,
    handleStartNewChat,
  };
};
