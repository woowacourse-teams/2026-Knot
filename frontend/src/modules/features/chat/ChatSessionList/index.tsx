import styled from "@emotion/styled";
import { useChatSessionList } from "./model/useChatSessionList";
import ChatSessionGroup from "./ui/ChatSessionGroup";

/**
 * 워크스페이스에 쌓인 대화 목록.
 *
 * 대화를 기간별로 묶어 보여주고, 고른 대화의 화면으로 이동시킵니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1961
 */
export default function ChatSessionList() {
  const { groups, openedSessionId, handleSelectSession } = useChatSessionList();

  if (groups.length === 0) {
    return <EmptyMessage>아직 나눈 대화가 없어요</EmptyMessage>;
  }

  return (
    <Container>
      {groups.map(({ label, sessions }) => (
        <ChatSessionGroup
          key={label}
          label={label}
          sessions={sessions}
          openedSessionId={openedSessionId}
          onSelectSession={handleSelectSession}
        />
      ))}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  width: 100%;
  height: 100%;
  overflow-y: auto;
`;

const EmptyMessage = styled.p`
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[500]};
`;
