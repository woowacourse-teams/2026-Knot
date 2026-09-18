import { skipToken, useQuery } from "@tanstack/react-query";
import { getChatMessagesApi } from "@api/fetch/api/v1/conversations/[sessionId]";
import { chatKeys } from "@api/queryKey/chat";

interface UseChatMessagesQueryParams {
  sessionId: number | null;
}

/**
 * 대화 세션의 메시지 이력을 조회합니다.
 *
 * 세션이 없는 새 대화 화면에서는 조회할 대상이 없으므로 요청을 보내지 않습니다.
 */
const useChatMessagesQuery = ({ sessionId }: UseChatMessagesQueryParams) => {
  return useQuery({
    queryKey: chatKeys.messages(sessionId),
    queryFn:
      sessionId === null ? skipToken : () => getChatMessagesApi(sessionId),
  });
};

export default useChatMessagesQuery;
