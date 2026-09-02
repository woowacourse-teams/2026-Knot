import styled from "@emotion/styled";
import { useParams } from "react-router";
import ChatField from "@/modules/features/chat/ChatField";
import MessageList from "@/modules/features/chat/MessageList";

import { useAutoScroll } from "../model/useAutoScroll";

import EmptyHint from "./EmptyHint";

interface ConversationProps {
  /** 아직 서버에 저장되지 않은, 진행 중인 턴의 질문 */
  streamingQuestion: string | null;
  /** 도착한 조각을 이어 붙인 부분 답변 */
  streamedAnswer: string;
  /** 답변이 오다가 끊겼는지 여부 */
  isStreamFailed: boolean;
  /** 질문을 보내는 중인지 여부. 입력과 전송 버튼을 잠급니다 */
  isSending: boolean;
  /** 보낼 수 없었던 이유. 답변 자리에 남길 것이 없을 때만 씁니다 */
  notice: string | null;
  onSubmit: (message: string) => void;
}

/**
 * 주고받은 대화와 입력창을 세로로 배치하는 영역.
 *
 * 세션도 진행 중인 질문도 없으면 대화 대신 빈 화면 안내를 보여줍니다.
 * 대화가 길어져도 입력창이 아래에 남도록 대화 영역만 스크롤합니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1171-7563
 */
export default function Conversation({
  streamingQuestion,
  streamedAnswer,
  isStreamFailed,
  isSending,
  notice,
  onSubmit,
}: ConversationProps) {
  const { sessionId } = useParams();
  // 질문을 보낸 순간과 답변이 길어지는 동안 모두 바닥을 따라갑니다
  const { containerRef, handleScroll } = useAutoScroll(
    `${streamingQuestion ?? ""}${streamedAnswer}`,
  );

  const hasConversation = Boolean(sessionId) || streamingQuestion !== null;

  return (
    <Container>
      <Content ref={containerRef} onScroll={handleScroll}>
        {hasConversation ? (
          <MessageList
            streamingQuestion={streamingQuestion}
            streamedAnswer={streamedAnswer}
            isStreamFailed={isStreamFailed}
          />
        ) : (
          <EmptyHint />
        )}
      </Content>

      {notice && <Notice role="status">{notice}</Notice>}

      <ChatField isSending={isSending} onSubmit={onSubmit} />
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

const Notice = styled.p`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;
