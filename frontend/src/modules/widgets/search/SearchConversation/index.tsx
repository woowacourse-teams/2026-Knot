import styled from "@emotion/styled";
import useConversationId from "@hooks/domain/search/useConversationId";
import useSearchMessages from "@hooks/domain/search/useSearchMessages";

import EmptyConversation from "./ui/EmptyConversation";
import SearchTurn from "./ui/SearchTurn";
import { toSearchTurns } from "./utils/toSearchTurns";

/**
 * 탐색 화면의 대화 칸. 지금 대화의 상태에 따라 칸에 무엇을 그릴지 정해요.
 *
 * 질문이 없는 빈 대화에서는 안내 문구를, 대화가 있으면 질문·답변 턴을 위에서부터 차례로 그려요.
 */
export default function SearchConversation() {
  const { conversationId } = useConversationId();
  const { messages } = useSearchMessages({ conversationId });

  const turns = toSearchTurns(messages);

  if (turns.length === 0) {
    return (
      <Root aria-label="대화">
        <EmptyConversation />
      </Root>
    );
  }

  return (
    <TurnList aria-label="대화">
      {turns.map((turn) => (
        <SearchTurn key={turn.question.id} turn={turn} />
      ))}
    </TurnList>
  );
}

const Root = styled.section`
  height: 100%;
`;

const TurnList = styled.section`
  display: flex;
  flex-direction: column;
  gap: 3rem; /* 48px */
  padding-top: 3rem; /* 48px — 본문 위 여백 20px과 더해 Figma처럼 화면 위 136px에 첫 줄이 놓여요 */
`;
