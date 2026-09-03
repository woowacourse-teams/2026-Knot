import { useParams } from "react-router";
import useChatSessionsQuery from "@api/queries/useChatSessionsQuery";
import useNavigateToChatSession from "@hooks/domain/chat/useNavigateToChatSession";
import { groupChatSessions } from "../utils/groupChatSessions";

/**
 * 워크스페이스의 채팅 세션 목록을 기간별로 묶고, 선택한 세션으로 이동시킵니다.
 *
 * 지금 보고 있는 대화가 무엇인지는 URL이 들고 있으므로 여기서는 읽기만 합니다.
 */
export const useChatSessionList = () => {
  const { workspaceId, sessionId } = useParams();
  const { navigateToChatSession } = useNavigateToChatSession();

  const { data } = useChatSessionsQuery({
    workspaceId: workspaceId ? Number(workspaceId) : null,
  });

  const groups = groupChatSessions({ sessions: data?.sessions ?? [] });

  const openedSessionId = sessionId ?? null;

  const handleSelectSession = (selectedSessionId: number) => {
    if (!workspaceId) return;

    navigateToChatSession({
      workspaceId,
      sessionId: String(selectedSessionId),
    });
  };

  return { groups, openedSessionId, handleSelectSession };
};
