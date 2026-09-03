import styled from "@emotion/styled";
import ChatSessionList from "@features/chat/ChatSessionList";
import useNavigateToNewChat from "@hooks/domain/chat/useNavigateToNewChat";
import { useParams } from "react-router";

import PlusIcon from "@/assets/icons/plus.svg";

/**
 * 대화 목록 드로어.
 *
 * GNB 좌측의 목록 버튼이 여닫으며, 탐색 화면에서만 열 수 있어요.
 * 스쳐 지나가면 겹쳐 뜨고 누르면 왼쪽에 자리를 잡는데, 그 판단은 감싸는 `DockablePanel`이 해요.
 *
 * 목록 자체는 `ChatSessionList`가 그대로 그리고, 여기서는 드로어 껍데기와 새 채팅 버튼만 얹어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=526-772 탐색 결과/채팅 세션 목록}
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
          <PlusIcon size={14} />
          새 채팅
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

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1379-8238 DrawerHead} */
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

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1379-8240 Btn/새채팅} */
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
