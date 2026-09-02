import { useParams } from "react-router";
import styled from "@emotion/styled";
import ChatHeader from "./ui/ChatHeader";
import ChatSessionListHeader from "./ui/ChatSessionListHeader";
import ChatSessionList from "@/modules/features/chat/ChatSessionList";
import Conversation from "./ui/Conversation";
import { useChatPanel } from "./model/useChatPanel";
import { useChatStream } from "./model/useChatStream";

/**
 * 탐색 화면 좌측의 채팅 패널.
 *
 * 대화 화면과 대화 목록 화면을 같은 패널 안에서 번갈아 보여주며,
 * 어느 쪽을 보여줄지는 `useChatPanel`이 정합니다.
 *
 * 진행 중인 질문과 도착 중인 답변은 이 패널이 듭니다. 목록을 펼쳐도, 첫 질문으로
 * 세션이 생겨 주소가 바뀌어도 이 패널은 그대로 있어 답변이 끊기지 않기 때문입니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10068 탐색 결과
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10217 채팅 세션 목록
 */
export default function ChatPanel() {
  const { workspaceId } = useParams();
  const {
    isChatSessionListOpen,
    openChatSessionList,
    closeChatSessionList,
    handleStartNewChat,
  } = useChatPanel();
  const {
    streamingQuestion,
    streamedAnswer,
    isStreamFailed,
    isSending,
    notice,
    handleSubmitQuestion,
  } = useChatStream();

  // TODO: 상위 컴포넌트로 책임 위임
  if (!workspaceId) return null;

  // TODO: Header, Content를 props로 뚫어놓은 레이아웃 컴포넌트 -> 합성

  // 채팅 세션 목록
  if (isChatSessionListOpen) {
    return (
      <Container>
        <ChatSessionListHeader
          onBack={closeChatSessionList}
          onStartNewChat={handleStartNewChat}
        />
        <Content>
          <ChatSessionList />
        </Content>
      </Container>
    );
  }

  // 채팅 세션 (실제 대화)
  return (
    <Container>
      <ChatHeader
        onOpenChatList={openChatSessionList}
        onStartNewChat={handleStartNewChat}
      />
      <Content>
        <Conversation
          streamingQuestion={streamingQuestion}
          streamedAnswer={streamedAnswer}
          isStreamFailed={isStreamFailed}
          isSending={isSending}
          notice={notice}
          onSubmit={handleSubmitQuestion}
        />
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
  min-height: 0; /* 내용이 길어져도 패널 밖으로 넘치지 않게 합니다 */
  width: 100%;
`;
