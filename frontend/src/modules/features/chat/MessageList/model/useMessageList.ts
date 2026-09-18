import { useParams } from "react-router";
import useChatMessagesQuery from "@api/queries/useChatMessagesQuery";
import useOpenedSourceMessage from "@hooks/domain/chat/useOpenedSourceMessage";
import { mockSourceCounts } from "../mock";
import type { ChatTurnView } from "../types/chatTurn";
import { formatSourceLabel } from "../utils/formatSourceLabel";
import { toChatTurns } from "../utils/toChatTurns";
import { toStreamingTurnStatus } from "../utils/toStreamingTurnStatus";

/**
 * 진행 중인 턴의 키.
 *
 * 아직 저장 전이라 메시지 ID가 없습니다. 서버 ID는 양수라 음수를 써도 겹치지 않습니다.
 */
const STREAMING_TURN_ID = -1;

interface UseMessageListParams {
  /** 아직 서버에 저장되지 않은, 진행 중인 턴의 질문 */
  streamingQuestion: string | null;
  /** 도착한 조각을 이어 붙인 부분 답변 */
  streamedAnswer: string;
  /** 답변이 오다가 끊겼는지 여부 */
  isStreamFailed: boolean;
}

/**
 * 메시지 목록을 질문 1개 + 답변 1개인 턴으로 묶고, 각 턴에 근거 문구를 붙입니다.
 *
 * 저장된 이력 뒤에 진행 중인 턴 하나를 덧붙입니다. 답변이 끝나면 상위에서 그 턴을 비우고
 * 서버 저장본이 이력으로 들어오므로, 화면의 정답은 언제나 서버가 준 목록입니다.
 *
 * 열어 둔 근거 문서는 useState 대신 URL 쿼리 파라미터에 둡니다.
 * 문서 목록을 그리는 SearchReferenceList가 다른 컴포넌트 트리에 있어 state를 공유할 수 없고,
 * URL은 두 곳에서 모두 읽을 수 있기 때문입니다.
 */
export const useMessageList = ({
  streamingQuestion,
  streamedAnswer,
  isStreamFailed,
}: UseMessageListParams) => {
  const { sessionId } = useParams();
  const { openedMessageId, openSourceMessage } = useOpenedSourceMessage();

  const { data } = useChatMessagesQuery({
    sessionId: sessionId ? Number(sessionId) : null,
  });

  const savedTurns: ChatTurnView[] = toChatTurns(data?.messages ?? []).map(
    (turn) => {
      const sourceCount = mockSourceCounts[turn.id];
      const sourceLabel = sourceCount ? formatSourceLabel(sourceCount) : null;

      return { ...turn, sourceLabel: sourceLabel ?? undefined };
    },
  );

  // 근거 문구는 답변이 끝나야 오므로 진행 중인 턴에는 붙이지 않습니다
  const streamingTurn: ChatTurnView | null =
    streamingQuestion === null
      ? null
      : {
          id: STREAMING_TURN_ID,
          question: streamingQuestion,
          answer: streamedAnswer.length === 0 ? null : streamedAnswer,
          status: toStreamingTurnStatus({
            answer: streamedAnswer,
            isFailed: isStreamFailed,
          }),
        };

  const turns = streamingTurn ? [...savedTurns, streamingTurn] : savedTurns;

  const hasAnsweredTurn = turns.some(({ status }) => status === "done");

  const handleOpenSource = (turnId: number) => openSourceMessage(turnId);

  return {
    turns,
    openedTurnId: openedMessageId,
    hasAnsweredTurn,
    handleOpenSource,
  };
};
