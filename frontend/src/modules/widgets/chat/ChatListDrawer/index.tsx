import styled from "@emotion/styled";
import ChatSessionList from "@features/chat/ChatSessionList";
import useNavigateToNewChat from "@hooks/domain/chat/useNavigateToNewChat";
import { useParams } from "react-router";

import PlusIcon from "@/assets/icons/plus.svg";

/**
 * 대화 목록 드로어.
 *
 * 동작 규칙은 스토리북 `Chat/ChatListDrawer`에서 확인해요.
 */
export default function ChatListDrawer() {
  const { workspaceId } = useParams();
  const { navigateToNewChat } = useNavigateToNewChat();

  const handleStartNewChat = () => {
    if (!workspaceId) return;

    navigateToNewChat(workspaceId);
  };

  return (
    <Container aria-label="대화 목록">
      <DrawerHead>
        <Title>대화</Title>
        <NewChatButton type="button" onClick={handleStartNewChat}>
          <PlusIcon size={14} />새 채팅
        </NewChatButton>
      </DrawerHead>

      <ChatSessionList />
    </Container>
  );
}

const Container = styled.aside`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
  width: 17.5rem; /* 280px */
  height: 100%;
  padding: 1.125rem 1rem; /* 18px 16px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow03};
`;

const DrawerHead = styled.div`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding-left: 0.25rem; /* 4px */
`;

const Title = styled.h2`
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.label01};
`;

const NewChatButton = styled.button`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.3125rem; /* 5px */
  height: 1.875rem; /* 30px */
  padding: 0 0.75rem 0 0.625rem; /* 0 12px 0 10px */
  border-radius: 62.4375rem; /* 999px */
  background-color: ${({ theme }) => theme.neutral[700]};
  color: ${({ theme }) => theme.neutral[0]};
  white-space: nowrap;
  transition: background-color 0.2s ease-in;
  ${({ theme }) => theme.text.caption01};

  &:hover {
    background-color: ${({ theme }) => theme.neutral[800]};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;
