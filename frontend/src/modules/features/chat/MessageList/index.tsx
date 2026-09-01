import styled from "@emotion/styled";
import { useMessageList } from "./model/useMessageList";
import AnswerFeedback from "./ui/AnswerFeedback";
import ChatTurn from "./ui/ChatTurn";

/**
 * 지금 보고 있는 세션의 대화를 턴 단위로 쌓아 보여줍니다.
 *
 * 대화가 없으면 아무것도 그리지 않고, 빈 화면 안내는 상위에 맡깁니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1171-7563
 */
export default function MessageList() {
  const { turns, openedTurnId, hasAnsweredTurn, handleOpenSource } =
    useMessageList();

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
