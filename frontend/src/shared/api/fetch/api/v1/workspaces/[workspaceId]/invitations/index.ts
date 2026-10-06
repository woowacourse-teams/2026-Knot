import {
  PostWorkspaceInvitationResponseDto,
  type PostWorkspaceInvitationResponseRaw,
} from "@api/dto/workspaceInvitation";
import { httpClient } from "@api/httpClient";

export const WORKSPACE_INVITATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitations`;

/**
 * @description 워크스페이스 초대를 발급합니다. 활성 초대가 있으면 그대로 돌려주고 없으면 새로 만들어요
 * @param workspaceId - 워크스페이스 ID
 * @returns 초대 코드·링크 토큰·만료 시각
 * @example
 * const { code, linkToken } = await issueWorkspaceInvitationApi(1);
 */
export const issueWorkspaceInvitationApi = async (workspaceId: number) => {
  const response = await httpClient<PostWorkspaceInvitationResponseRaw>({
    method: "post",
    url: WORKSPACE_INVITATIONS_API_PATH(workspaceId),
  });

  return new PostWorkspaceInvitationResponseDto(response.data);
};
