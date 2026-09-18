import styled from "@emotion/styled";
import { useMessageList } from "./model/useMessageList";
import AnswerFeedback from "./ui/AnswerFeedback";
import ChatTurn from "./ui/ChatTurn";

interface MessageListProps {
  /** 아직 서버에 저장되지 않은, 진행 중인 턴의 질문. 없으면 null */
  streamingQuestion?: string | null;
  /** 도착한 조각을 이어 붙인 부분 답변 */
  streamedAnswer?: string;
  /** 답변이 오다가 끊겼는지 여부 */
  isStreamFailed?: boolean;
}

/**
 * 지금 보고 있는 세션의 대화를 턴 단위로 쌓아 보여줍니다.
 *
 * 저장된 이력 뒤에 진행 중인 턴을 이어 붙여, 질문을 보낸 순간부터 답변이 쌓이는 모습이
 * 같은 자리에서 이어집니다. 보여 줄 턴이 하나도 없으면 아무것도 그리지 않고,
 * 빈 화면 안내는 상위에 맡깁니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1171-7563
 */
export default function MessageList({
  streamingQuestion = null,
  streamedAnswer = "",
  isStreamFailed = false,
}: MessageListProps) {
  const { turns, openedTurnId, hasAnsweredTurn, handleOpenSource } =
    useMessageList({ streamingQuestion, streamedAnswer, isStreamFailed });

  if (turns.length === 0) return null;

  return (
    <Container>
      {turns.map(({ id, question, answer, status, sourceLabel }) => (
        <ChatTurn
          key={id}
          question={question}
          answer={answer}
          status={status}
          sourceLabel={sourceLabel}
          isSourceOpen={openedTurnId === id}
          onOpenSource={() => handleOpenSource(id)}
        />
      ))}

      {hasAnsweredTurn && <AnswerFeedback />}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 3rem;
  width: 100%;
`;
