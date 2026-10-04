import styled from "@emotion/styled";

import EmptyConversation from "./ui/EmptyConversation";

/**
 * 탐색 화면의 대화 칸. 지금 대화의 상태에 따라 칸에 무엇을 그릴지 정해요.
 *
 * 아직 대화 데이터를 받지 않으므로 질문이 없는 빈 대화만 그려요.
 */
export default function SearchConversation() {
  return (
    <Root aria-label="대화">
      <EmptyConversation />
    </Root>
  );
}

const Root = styled.section`
  height: 100%;
`;
