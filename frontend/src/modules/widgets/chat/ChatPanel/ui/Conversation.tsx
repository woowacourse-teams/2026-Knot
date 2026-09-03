import styled from "@emotion/styled";
import { useParams } from "react-router";

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
  /** 질문을 아예 보내지 못했을 때의 안내. 없으면 null */
  notice: string | null;
}

/**
 * 주고받은 대화가 쌓이는 영역.
 *
 * 세션도 진행 중인 질문도 없으면 대화 대신 빈 화면 안내를 보여줍니다.
 * 질문을 적는 자리는 이 안이 아니라 화면 아래 독이므로, 여기는 대화만 그리고 남는 자리는 비워 둡니다.
 *
 * 보내지 못한 질문의 안내는 답변이 놓였을 자리인 대화 맨 아래에 남깁니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7216
 */
export default function Conversation({
  streamingQuestion,
  streamedAnswer,
  isStreamFailed,
  notice,
}: ConversationProps) {
  const { sessionId } = useParams();
  // 질문을 보낸 순간과 답변이 길어지는 동안 모두 바닥을 따라갑니다
  const { containerRef, handleScroll } = useAutoScroll(
    `${streamingQuestion ?? ""}${streamedAnswer}`,
  );

  const hasConversation = Boolean(sessionId) || streamingQuestion !== null;

  return (
    <Container ref={containerRef} onScroll={handleScroll}>
      {hasConversation ? (
        <MessageList
          streamingQuestion={streamingQuestion}
          streamedAnswer={streamedAnswer}
          isStreamFailed={isStreamFailed}
        />
      ) : (
        <EmptyHint />
      )}

      {notice && <Notice role="alert">{notice}</Notice>}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 3rem; /* 48px — 턴 사이 간격과 같아, 실패 안내도 대화의 다음 줄처럼 놓여요 */
  height: 100%;
  /* 스크롤바가 생겨도 글이 밀리거나 그 아래에 깔리지 않게 자리를 미리 비워 둬요 */
  padding-right: 0.75rem; /* 12px */
  overflow-y: auto;
  scrollbar-gutter: stable;
`;

/**
 * 보내지 못했을 때 대화 맨 아래에 남기는 문구.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1432-2031 탐색 결과/전송 실패}
 */
const Notice = styled.p`
  color: ${({ theme }) => theme.sub.warning[600]};
  ${({ theme }) => theme.text.caption02};
`;
