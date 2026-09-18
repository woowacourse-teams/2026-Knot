import { getNotionPageTreeApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionPages/tree";
import { notionPageKeys } from "@api/queryKey/notionPage";
import { useQuery } from "@tanstack/react-query";

interface UseNotionPageTreeQueryParams {
  workspaceId: number;
}

/**
 * 워크스페이스에 발행된 Notion Page Tree를 조회하는 쿼리 훅.
 *
 * 응답은 부모 ID만 들고 있는 평평한 목록이라, 트리로 묶는 일은 보여주는 쪽이 합니다.
 *
 * 라우트 파라미터를 `Number`로 바꾼 값이 정수가 아니면(`/workspace/abc` 같은 잘못된 주소) 요청하지 않아요.
 * `NaN`이 그대로 가면 `/workspaces/NaN/...`으로 요청이 나가고 캐시 키도 뭉개져요.
 */
const useNotionPageTreeQuery = ({
  workspaceId,
}: UseNotionPageTreeQueryParams) => {
  return useQuery({
    queryKey: notionPageKeys.tree(workspaceId),
    queryFn: () => getNotionPageTreeApi(workspaceId),
    enabled: Number.isInteger(workspaceId),
  });
};

export default useNotionPageTreeQuery;
