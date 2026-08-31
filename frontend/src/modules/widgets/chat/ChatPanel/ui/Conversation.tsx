import styled from "@emotion/styled";
import { useParams } from "react-router";
import ChatField from "@/modules/features/chat/ChatField";
import MessageList from "@/modules/features/chat/MessageList";

import EmptyHint from "./EmptyHint";

/**
 * 주고받은 대화와 입력창을 세로로 배치하는 영역.
 *
 * 세션이 없으면 대화 대신 빈 화면 안내를 보여줍니다.
 * 대화가 길어져도 입력창이 아래에 남도록 대화 영역만 스크롤합니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1171-7563
 */
export default function Conversation() {
  const { sessionId } = useParams();

  return (
    <Container>
      <Content>{sessionId ? <MessageList /> : <EmptyHint />}</Content>

      <ChatField />
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  height: 100%;
`;

const Content = styled.div`
  flex: 1;
  min-height: 0; /* 대화가 길어져도 입력창이 밀려나지 않게 합니다 */
  overflow-y: auto;
`;
