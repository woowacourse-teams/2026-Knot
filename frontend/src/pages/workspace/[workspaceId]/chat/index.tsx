import styled from "@emotion/styled";
import useOpenedEvidenceMessage from "@hooks/domain/chat/useOpenedEvidenceMessage";
import DockColumn from "@primitives/layout/DockColumn";

import SearchConversation from "@/modules/widgets/search/SearchConversation";
import SearchEvidenceList from "@/modules/widgets/search/SearchEvidenceList";

/**
 * 탐색(채팅) 화면 (`/workspace/:workspaceId/chat`, `/workspace/:workspaceId/chat/:sessionId`)
 *
 * 워크스페이스에 쌓인 기록을 대화로 찾는 화면입니다.
 * `sessionId` 없이 들어오면 입력 전(빈 결과) 상태로 시작하고,
 * 대화가 시작되면 해당 세션(`/chat/:sessionId`)으로 이어집니다.
 *
 * 대화 열은 하단 독과 같은 `DockColumn`에 놓여 늘 같은 폭·같은 위치예요(SEARCH-R11).
 * 어느 답변의 문서를 펼쳐 뒀는지는 주소(`?messageId=`)에 있어 새로고침해도 그대로예요.
 *
 * 사이드바 오픈, 채팅 세션 목록도 별도 라우트가 아니라 이 화면 위의 상태 변형입니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2098-29235 탐색/시작 전
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1283-7940 탐색 결과/문서 닫힘
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1434-2024 탐색 결과/문서 열림
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=413-14915 전체 플로우
 */
export default function ChatPage() {
  const { openedMessageId } = useOpenedEvidenceMessage();

  const isEvidenceOpen = openedMessageId !== null;

  return (
    <Container>
      <ChatColumn>
        <SearchConversation />
      </ChatColumn>

      {isEvidenceOpen && (
        <EvidenceColumn>
          <SearchEvidenceList />
        </EvidenceColumn>
      )}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  gap: 1.1875rem; /* 19px */
  width: 100%;
  height: 100%;
  padding-bottom: 7rem; /* 112px — 하단 독 자리 */
`;

const ChatColumn = styled(DockColumn)`
  flex-shrink: 1;
  min-width: 0;
`;

// 찾은 기록을 레이아웃 레일로 올리기 전까지는 이 화면 안에서 대화 열 옆에 붙여요
const EvidenceColumn = styled.div`
  flex-shrink: 0;
  width: 23.75rem; /* 380px */
  margin-right: 2.5rem; /* 40px */
  padding-top: 3rem; /* 48px — 대화 첫 줄과 눈높이를 맞춰요 */
  overflow-y: auto;
`;
