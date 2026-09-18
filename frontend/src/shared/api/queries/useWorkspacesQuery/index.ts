import { getWorkspacesApi } from "@api/fetch/api/v1/workspaces";
import { workspaceKeys } from "@api/queryKey/workspace";
import { useQuery } from "@tanstack/react-query";

interface UseWorkspacesQueryParams {
  /** `false`면 요청을 보내지 않아요. 로그인 여부를 아직 모를 때 씁니다. */
  isEnabled?: boolean;
}

/**
 * 내가 속한 워크스페이스 목록을 조회합니다.
 *
 * 마지막으로 본 워크스페이스 ID가 함께 오므로, 로그인 직후 어느 워크스페이스로
 * 보낼지도 이 응답 하나로 정할 수 있어요.
 *
 * 로그인해야 볼 수 있는 목록이라 미로그인 상태에서는 401이 옵니다. 로그인 여부를
 * 먼저 확인하는 화면에서는 `isEnabled`로 확인이 끝날 때까지 요청을 미루세요.
 */
const useWorkspacesQuery = ({ isEnabled = true }: UseWorkspacesQueryParams = {}) => {
  return useQuery({
    queryKey: workspaceKeys.list(),
    queryFn: getWorkspacesApi,
    enabled: isEnabled,
  });
};

export default useWorkspacesQuery;
