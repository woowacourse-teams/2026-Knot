import {
  PostInvitationAcceptResponseDto,
  type PostInvitationAcceptRequestDto,
  type PostInvitationAcceptResponseRaw,
} from "@api/dto/workspaceInvitation";
import { httpClient } from "@api/httpClient";

export const INVITATIONS_ACCEPT_API_PATH = "/api/v1/invitations/accept";

/**
 * @description 초대 코드 또는 링크 토큰으로 워크스페이스에 참여합니다
 * @param body - 초대 수락 요청 본문
 * @returns 참여한 워크스페이스 ID와 이름
 * @example
 * const { workspaceId } = await acceptInvitationApi(new PostInvitationAcceptRequestDto({ credential: "X35D3S" }));
 */
export const acceptInvitationApi = async (
  body: PostInvitationAcceptRequestDto,
) => {
  const response = await httpClient<PostInvitationAcceptResponseRaw>({
    method: "post",
    url: INVITATIONS_ACCEPT_API_PATH,
    data: body,
  });

  return new PostInvitationAcceptResponseDto(response.data);
};
