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
 * 보내지 못했을 때의 안내도 여기서 보여줍니다. 독은 홈에도 그대로 있어 거기에 붙이면 화면을 나가도
 * 실패 문구가 따라다니지만, 실패한 질문이 놓인 자리는 이 대화이기 때문입니다.
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
 * 여백만 디자인의 Chat/Panel 상자를 그대로 따릅니다.
 */
const Container = styled.section`
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  padding: 2.5rem 2.5rem 1.5rem; /* 40px 40px 24px */
`;
