import { getNotionConnectionApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionConnection";
import { notionConnectionKeys } from "@api/queryKey/notionConnection";
import { useQuery } from "@tanstack/react-query";

interface UseNotionConnectionQueryParams {
  workspaceId: number;
}

/**
 * 워크스페이스의 Notion 연결 상태를 조회하는 쿼리 훅.
 *
 * 홈의 Notion 동기화 카드가 연결 안 됨·연결됨·재인증 필요를 보여주는 데 써요.
 * 워크스페이스 멤버면 누구나 조회할 수 있고, 연결된 적이 없어도 404가 아니라 NOT_CONNECTED가 와요.
 *
 * 라우트 파라미터를 `Number`로 바꾼 값이 정수가 아니면(`/workspace/abc` 같은 잘못된 주소) 요청하지 않아요.
 */
const useNotionConnectionQuery = ({
  workspaceId,
}: UseNotionConnectionQueryParams) => {
  return useQuery({
    queryKey: notionConnectionKeys.detail(workspaceId),
    queryFn: () => getNotionConnectionApi(workspaceId),
    enabled: Number.isInteger(workspaceId),
  });
};

export default useNotionConnectionQuery;
