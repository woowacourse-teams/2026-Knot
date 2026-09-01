import { useParams } from "react-router";
import styled from "@emotion/styled";
import ChatHeader from "./ui/ChatHeader";
import ChatSessionListHeader from "./ui/ChatSessionListHeader";
import ChatSessionList from "@/modules/features/chat/ChatSessionList";
import Conversation from "./ui/Conversation";
import { useChatPanel } from "./model/useChatPanel";

/**
 * 탐색 화면 좌측의 채팅 패널.
 *
 * 대화 화면과 대화 목록 화면을 같은 패널 안에서 번갈아 보여주며,
 * 어느 쪽을 보여줄지는 `useChatPanel`이 정합니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10068 탐색 결과
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10217 채팅 세션 목록
 */
export default function ChatPanel() {
  const { workspaceId } = useParams();
  const { view, handleOnOpenChatList, handleOnBack, handleStartNewChat } =
    useChatPanel();

  // TODO: 상위 컴포넌트로 책임 위임
  if (!workspaceId) return null;

  if (view === "list") {
    return (
      <Container>
        <ChatSessionListHeader
          onBack={handleOnBack}
          onStartNewChat={handleStartNewChat}
        />
        <Content>
          <ChatSessionList />
        </Content>
      </Container>
    );
  }

  return (
    <Container>
      <ChatHeader
        onOpenChatList={handleOnOpenChatList}
        onStartNewChat={handleStartNewChat}
      />
      <Content>
        <Conversation />
      </Content>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.25rem;
  width: 100%;
  height: 100%;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 1.5rem 1.5rem 1.5rem 0;
  padding: 1.5rem;
  background-color: rgba(60, 59, 57, 0.92);
`;

const Content = styled.div`
  flex: 1;
  width: 100%;
`;
