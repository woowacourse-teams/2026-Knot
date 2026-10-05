import { useParams } from "react-router";

/**
 * 지금 탐색 대화의 id를 돌려주는 도메인 훅.
 * 주소에 대화가 없으면(입력 전 화면) `undefined`입니다.
 */
const useConversationId = () => {
  // 주소 파라미터 이름(sessionId)과 v2 계약 이름(conversationId)의 차이를 이 훅만 알게 해, 데이터 연결 때 여기만 바꿉니다
  const { sessionId } = useParams();
  const conversationId = sessionId ? Number(sessionId) : undefined;

  return { conversationId };
};

export default useConversationId;
