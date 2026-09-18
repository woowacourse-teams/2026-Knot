import { getChatSessionsApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/conversations";
import { chatKeys } from "@api/queryKey/chat";
import { skipToken, useQuery } from "@tanstack/react-query";

interface UseChatSessionsQueryParams {
  workspaceId: number | null;
}

/**
 * 워크스페이스의 대화 세션 목록을 조회합니다.
 *
 * 서버가 마지막 메시지 시각 내림차순으로 주므로 여기서 다시 정렬하지 않습니다.
 */
const useChatSessionsQuery = ({ workspaceId }: UseChatSessionsQueryParams) => {
  return useQuery({
    queryKey: chatKeys.sessions(workspaceId),
    queryFn:
      workspaceId === null ? skipToken : () => getChatSessionsApi(workspaceId),
  });
};

export default useChatSessionsQuery;
