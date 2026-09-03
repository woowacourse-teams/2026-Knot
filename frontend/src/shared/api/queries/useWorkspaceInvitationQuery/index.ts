import { issueWorkspaceInvitationApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitations";
import { workspaceInvitationKeys } from "@api/queryKey/workspaceInvitation";
import { useQuery } from "@tanstack/react-query";

interface UseWorkspaceInvitationQueryParams {
  workspaceId: number;
}

/**
 * 워크스페이스의 활성 초대(참여 코드·링크 토큰)를 조회하는 쿼리 훅.
 *
 * 조회지만 `POST /workspaces/{workspaceId}/invitations`를 써요. 활성 초대가 있으면 그대로 돌려주고(200)
 * 없으면 새로 발급하므로(201), 생성 직후의 초대 카드와 홈의 초대 카드가 같은 요청 하나로 초대를 얻어요.
 * `GET /workspaces/{workspaceId}/invitation`은 생성 직후 활성 초대가 없어 404라 쓰지 않아요.
 *
 * 라우트 파라미터를 `Number`로 바꾼 값이 정수가 아니면(`/workspace/abc/invite` 같은 잘못된 주소) 요청하지 않아요.
 * `NaN`이 그대로 가면 `/workspaces/NaN/invitations`로 요청이 나가고 캐시 키도 `null`로 뭉개져요.
 * 쓰기 성격의 요청이라 창 포커스 복귀마다 다시 보내지 않고, 마운트 때만 활성 초대를 확인해요.
 */
const useWorkspaceInvitationQuery = ({
  workspaceId,
}: UseWorkspaceInvitationQueryParams) => {
  return useQuery({
    queryKey: workspaceInvitationKeys.active(workspaceId),
    queryFn: () => issueWorkspaceInvitationApi(workspaceId),
    enabled: Number.isInteger(workspaceId),
    refetchOnWindowFocus: false,
  });
};

export default useWorkspaceInvitationQuery;
