import styled from "@emotion/styled";
import ChatPanel from "@/modules/widgets/chat/ChatPanel";
import SearchReferenceList from "@/modules/widgets/search/SearchReferenceList";

/**
 * 탐색(채팅) 화면 (`/workspace/:workspaceId/chat`, `/workspace/:workspaceId/chat/:sessionId`)
 *
 * 워크스페이스에 쌓인 기록을 대화로 찾는 화면입니다.
 * `sessionId` 없이 들어오면 입력 전(빈 결과) 상태로 시작하고,
 * 대화가 시작되면 해당 세션(`/chat/:sessionId`)으로 이어집니다.
 * 사이드바 오픈, 채팅 세션 목록도 별도 라우트가 아니라 이 화면 위의 상태 변형입니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10065 탐색 결과/입력 전
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10068 탐색 결과
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10207 탐색 결과/사이드바 오픈
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10217 탐색 결과/채팅 세션 목록
 */
export default function ChatPage() {
  return (
    <Container>
      <ChatPanelWrapper>
        <ChatPanel />
      </ChatPanelWrapper>

      <SearchReferenceListWrapper>
        <SearchReferenceList />
      </SearchReferenceListWrapper>
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  gap: 3.1875rem;
  width: 100vw;
  height: calc(100vh - 3.5rem);
`;

const ChatPanelWrapper = styled.div`
  width: 61%;
`;

const SearchReferenceListWrapper = styled.div`
  width: 26%;
  padding-top: 5rem;
`;
