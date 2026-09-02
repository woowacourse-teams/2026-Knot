import styled from "@emotion/styled";
import { useChatStreamContext } from "@provider/context/chatStreamContext";
import { useParams } from "react-router";

import { useChatPanel } from "./model/useChatPanel";
import Conversation from "./ui/Conversation";

/**
 * 탐색 화면 좌측의 채팅 패널.
 *
 * 주고받은 대화와 도착 중인 답변을 보여줍니다. 질문을 적는 자리는 화면 아래 독이고,
 * 대화 목록은 GNB 좌측의 목록 드로어라 여기서는 둘 다 다루지 않습니다.
 *
 * 진행 중인 질문과 답변은 이 패널보다 위(`ChatStreamProvider`)에 있습니다. 독과 함께 봐야 하고,
 * 첫 질문으로 세션이 생겨 주소가 바뀌어도 끊기지 않아야 하기 때문입니다.
 *
 * 홈에서 질문을 적어 들어온 경우에는 도착하자마자 그 질문으로 대화를 시작합니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7216 탐색 결과
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1198-7725 탐색 결과/대화 여러 개
 */
export default function ChatPanel() {
  const { workspaceId } = useParams();
  const {
    streamingQuestion,
    streamedAnswer,
    isStreamFailed,
    notice,
    handleSubmitQuestion,
  } = useChatStreamContext();

  useChatPanel({ onSubmitPendingQuestion: handleSubmitQuestion });

  // TODO: 상위 컴포넌트로 책임 위임
  if (!workspaceId) return null;

  return (
    <Container>
      <Conversation
        streamingQuestion={streamingQuestion}
        streamedAnswer={streamedAnswer}
        isStreamFailed={isStreamFailed}
        notice={notice}
      />
    </Container>
  );
}

/**
 * 대화가 놓이는 자리.
 *
 * 카드가 아니라 화면 배경 위에 글이 그대로 놓이는 모양이라 배경·테두리를 두지 않아요.
 * 좌우 여백은 화면(`ChatPage`)이 이미 주므로 위아래만 둡니다.
 */
const Container = styled.section`
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding: 2.5rem 0 1.5rem; /* 40px 0 24px */
`;
