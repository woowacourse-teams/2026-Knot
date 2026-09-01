import { getWorkspaceApi } from "@api/fetch/api/v1/workspaces/[workspaceId]";
import { workspaceKeys } from "@api/queryKey/workspace";
import { useQuery } from "@tanstack/react-query";

interface UseWorkspaceQueryParams {
  workspaceId: number;
}

/**
 * 워크스페이스 하나를 조회하는 쿼리 훅.
 *
 * 워크스페이스 레이아웃의 진입 판정(`useWorkspaceEntry`)과 사이드바의 이름 표시가 같은 키를 써서
 * 요청은 한 번만 나가고 결과는 캐시로 공유돼요.
 *
 * 라우트 파라미터를 `Number`로 바꾼 값이 정수가 아니면(`/workspace/abc` 같은 잘못된 주소) 요청하지 않아요.
 * `NaN`이 그대로 가면 `/workspaces/NaN`으로 요청이 나가고 캐시 키도 `null`로 뭉개져요.
 */
const useWorkspaceQuery = ({ workspaceId }: UseWorkspaceQueryParams) => {
  return useQuery({
    queryKey: workspaceKeys.detail(workspaceId),
    queryFn: () => getWorkspaceApi(workspaceId),
    enabled: Number.isInteger(workspaceId),
  });
};

export default useWorkspaceQuery;
