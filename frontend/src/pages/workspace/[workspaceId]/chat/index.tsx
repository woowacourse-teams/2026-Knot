import styled from "@emotion/styled";
import useOpenedSourceMessage from "@hooks/domain/chat/useOpenedSourceMessage";

import ChatPanel from "@/modules/widgets/chat/ChatPanel";
import SearchReferenceList from "@/modules/widgets/search/SearchReferenceList";

/**
 * 탐색(채팅) 화면 (`/workspace/:workspaceId/chat`, `/workspace/:workspaceId/chat/:sessionId`)
 *
 * 워크스페이스에 쌓인 기록을 대화로 찾는 화면입니다.
 * `sessionId` 없이 들어오면 입력 전(빈 결과) 상태로 시작하고,
 * 대화가 시작되면 해당 세션(`/chat/:sessionId`)으로 이어집니다.
 *
 * 처음에는 대화만 놓여 화면 가운데를 넓게 씁니다. 답변의 근거 버튼을 누르면 그때 오른쪽에
 * 찾은 문서 레일이 열리면서 대화가 왼쪽으로 좁아지고, 레일을 닫으면 다시 넓어집니다.
 * 어느 답변의 문서를 펼쳐 뒀는지는 주소(`?messageId=`)에 있어 새로고침해도 그대로예요.
 *
 * 사이드바 오픈, 채팅 세션 목록도 별도 라우트가 아니라 이 화면 위의 상태 변형입니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1283-7940 탐색 결과/문서 닫힘
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1434-2024 탐색 결과/문서 열림
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=413-14915 전체 플로우
 */
export default function ChatPage() {
  const { openedMessageId } = useOpenedSourceMessage();

  const isReferenceOpen = openedMessageId !== null;

  return (
    <Container>
      <ChatColumn $isNarrow={isReferenceOpen}>
        <ChatPanel />
      </ChatColumn>

      {isReferenceOpen && (
        <ReferenceColumn>
          <SearchReferenceList />
        </ReferenceColumn>
      )}
    </Container>
  );
}

/**
 * 대화와 문서 레일을 나란히 두는 자리.
 *
 * 좌우 여백·칸 사이 간격은 디자인 프레임(1440)의 값을 그대로 옮겼어요.
 * 그래야 레일이 열렸을 때 대화 상자가 x=80에서 시작하고 레일이 x=939에 놓입니다.
 */
const Container = styled.div`
  display: flex;
  gap: 1.1875rem; /* 19px */
  width: 100%;
  height: 100%;
  padding: 0 5rem 7rem; /* 0 80px 112px — 아래는 하단 Dock 자리 */
`;

/**
 * 대화가 놓이는 칸.
 *
 * 문서 레일이 닫혀 있으면 가운데에서 넓게 쓰고, 열리면 남은 자리를 채우며 왼쪽으로 좁아져요.
 * 닫혔을 때 폭을 재는 이유는 글줄이 화면 끝까지 늘어나면 읽기 어려워지기 때문이에요.
 */
const ChatColumn = styled.div<{ $isNarrow: boolean }>`
  flex: 1;
  min-width: 0;
  max-width: 52.5rem; /* 840px — 레일이 열렸을 때의 대화 상자 폭 */

  ${({ $isNarrow }) =>
    $isNarrow
      ? ""
      : `
        max-width: 55rem; /* 880px */
        margin: 0 auto;
      `}
`;

/**
 * 찾은 문서 레일이 놓이는 칸.
 *
 * 위 여백은 대화 상자 안쪽 여백(40px)에 맞춘 값이에요.
 * 8px을 더 두면 "찾은 문서" 제목과 첫 질문 말풍선의 가운데가 나란히 놓입니다.
 */
const ReferenceColumn = styled.div`
  flex-shrink: 0;
  width: 23.75rem; /* 380px */
  padding-top: 3rem; /* 48px — 대화 첫 줄과 눈높이를 맞춰요 */
  overflow-y: auto;
`;
