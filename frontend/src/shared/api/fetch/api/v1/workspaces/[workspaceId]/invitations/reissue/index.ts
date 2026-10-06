import {
  PostWorkspaceInvitationReissueResponseDto,
  type PostWorkspaceInvitationReissueResponseRaw,
} from "@api/dto/workspaceInvitation";
import { httpClient } from "@api/httpClient";

export const WORKSPACE_INVITATIONS_REISSUE_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitations/reissue`;

/**
 * @description 기존 초대를 무효화하고 새 초대를 발급합니다
 * @param workspaceId - 워크스페이스 ID
 * @returns 새 초대 코드·링크 토큰·만료 시각
 * @example
 * const { code } = await reissueWorkspaceInvitationApi(1);
 */
export const reissueWorkspaceInvitationApi = async (workspaceId: number) => {
  const response = await httpClient<PostWorkspaceInvitationReissueResponseRaw>({
    method: "post",
    url: WORKSPACE_INVITATIONS_REISSUE_API_PATH(workspaceId),
  });

  return new PostWorkspaceInvitationReissueResponseDto(response.data);
};
