import { getChatMessageSourcesApi } from "@api/fetch/api/v1/messages/[messageId]/sources";
import { chatKeys } from "@api/queryKey/chat";
import { skipToken, useQuery } from "@tanstack/react-query";

interface UseChatMessageSourcesQueryParams {
  messageId: number | null;
}

/**
 * 답변 메시지의 검색 출처 목록을 조회합니다.
 *
 * 근거 문서를 펼쳐 둔 답변이 없으면(`messageId`가 null) 조회할 대상이 없으므로 요청을 보내지 않습니다.
 */
const useChatMessageSourcesQuery = ({
  messageId,
}: UseChatMessageSourcesQueryParams) => {
  return useQuery({
    queryKey: chatKeys.sources(messageId),
    queryFn:
      messageId === null
        ? skipToken
        : () => getChatMessageSourcesApi(messageId),
  });
};

export default useChatMessageSourcesQuery;
