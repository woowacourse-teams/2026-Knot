import styled from "@emotion/styled";
import { useChatSessionList } from "./model/useChatSessionList";
import ChatSessionGroup from "./ui/ChatSessionGroup";
import EmptyChatSessionList from "./ui/EmptyChatSessionList";

/**
 * 워크스페이스에 쌓인 대화 목록.
 *
 * 동작 규칙은 스토리북 `Chat/ChatSessionList`에서 확인해요.
 */
export default function ChatSessionList() {
  const { groups, openedSessionId, handleSelectSession } = useChatSessionList();

  if (groups.length === 0) {
    return <EmptyChatSessionList />;
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
  gap: 1rem;
  width: 100%;
  height: 100%;
  overflow-y: auto;
`;
