import {
  PostInvitationAcceptRequestDto,
  type PostInvitationAcceptRequestInput,
} from "@api/dto/workspaceInvitation";
import { acceptInvitationApi } from "@api/fetch/api/v1/invitations/accept";
import { workspaceKeys } from "@api/queryKey/workspace";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * 초대 코드·링크 토큰으로 워크스페이스에 참여하는 뮤테이션 훅.
 *
 * 참여 API는 workspaceId가 아니라 credential만 받아요(ADR 244).
 * 기존 멤버십(200)과 새 멤버십(201)은 구분하지 않고 둘 다 성공으로 봐요.
 * 성공하면 내가 속한 워크스페이스 목록 캐시를 무효화해 참여한 워크스페이스가 목록에 반영되게 해요.
 */
const useAcceptInvitationMutation = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (input: PostInvitationAcceptRequestInput) =>
      acceptInvitationApi(new PostInvitationAcceptRequestDto(input)),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: workspaceKeys.list() }),
  });
};

export default useAcceptInvitationMutation;
