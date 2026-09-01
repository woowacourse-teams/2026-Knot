import { getInvitationPreviewApi } from "@api/fetch/api/v1/invitations/[tokenOrCode]";
import { workspaceInvitationKeys } from "@api/queryKey/workspaceInvitation";
import { useQuery } from "@tanstack/react-query";

interface UseInvitationPreviewQueryParams {
  /** 6자 초대 코드 또는 링크 토큰 원문. BE 계약(ADR 243)대로 정규화하지 않고 그대로 보내요 */
  credential: string;
  /** `false`면 요청하지 않아요. 코드 입력 카드는 6자를 채웠을 때만 켜요 */
  enabled: boolean;
}

/**
 * 초대 코드·링크 토큰이 가리키는 워크스페이스를 참여 전에 조회하는 쿼리 훅.
 *
 * 초대 코드 입력 카드와 초대 링크 게이트가 함께 써요. 초대가 없거나 만료됐으면 404, 너무 잦으면 429가 와요.
 * 실패를 문구로 보여주는 코드 카드에서 창 포커스가 돌아올 때마다 다시 확인하면 에러 자리가 스피너로
 * 깜빡이므로 포커스 refetch는 꺼요. 같은 값을 다시 입력하면 마운트 때 다시 확인해요.
 */
const useInvitationPreviewQuery = ({
  credential,
  enabled,
}: UseInvitationPreviewQueryParams) => {
  return useQuery({
    queryKey: workspaceInvitationKeys.preview(credential),
    queryFn: () => getInvitationPreviewApi(credential),
    enabled,
    refetchOnWindowFocus: false,
  });
};

export default useInvitationPreviewQuery;
