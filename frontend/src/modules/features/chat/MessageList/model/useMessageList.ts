import { useParams } from "react-router";
import useChatMessagesQuery from "@api/queries/useChatMessagesQuery";
import useOpenedSourceMessage from "@hooks/domain/chat/useOpenedSourceMessage";
import { mockSourceCounts } from "../mock";
import type { ChatTurnView } from "../types/chatTurn";
import { formatSourceLabel } from "../utils/formatSourceLabel";
import { toChatTurns } from "../utils/toChatTurns";

/**
 * 메시지 목록을 질문 1개 + 답변 1개인 턴으로 묶고, 각 턴에 근거 문구를 붙입니다.
 *
 * 열어 둔 근거 문서는 useState 대신 URL 쿼리 파라미터에 둡니다.
 * 문서 목록을 그리는 SearchReferenceList가 다른 컴포넌트 트리에 있어 state를 공유할 수 없고,
 * URL은 두 곳에서 모두 읽을 수 있기 때문입니다.
 */
export const useMessageList = () => {
  const { sessionId } = useParams();
  const { openedMessageId, openSourceMessage } = useOpenedSourceMessage();

  const { data } = useChatMessagesQuery({
    sessionId: sessionId ? Number(sessionId) : null,
  });

  const turns: ChatTurnView[] = toChatTurns(data?.messages ?? []).map(
    (turn) => {
      const sourceCount = mockSourceCounts[turn.id];
      const sourceLabel = sourceCount ? formatSourceLabel(sourceCount) : null;

      return { ...turn, sourceLabel: sourceLabel ?? undefined };
    },
  );

  const hasAnsweredTurn = turns.some(({ status }) => status === "done");

  const handleOpenSource = (turnId: number) => openSourceMessage(turnId);

  return {
    turns,
    openedTurnId: openedMessageId,
    hasAnsweredTurn,
    handleOpenSource,
  };
};
