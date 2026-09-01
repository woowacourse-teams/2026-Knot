import { httpClient } from "@api/httpClient";

export const WORKSPACE_INVITATIONS_REISSUE_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitations/reissue`;

interface PostWorkspaceInvitationReissueApiResponse {
  /** 6자 초대 코드 (예: X35D3S) */
  code: string;
  /** `/invite/<linkToken>` 진입 경로에 들어가요 */
  linkToken: string;
  /** ISO 8601 */
  expiresAt: string;
}

/**
 * @description 기존 초대를 무효화하고 새 초대를 발급합니다
 * @param workspaceId - 워크스페이스 ID
 * @returns 새 초대 코드·링크 토큰·만료 시각
 * @example
 * const { code } = await reissueWorkspaceInvitationApi(1);
 */
export const reissueWorkspaceInvitationApi = async (workspaceId: number) => {
  const response = await httpClient<PostWorkspaceInvitationReissueApiResponse>({
    method: "post",
    url: WORKSPACE_INVITATIONS_REISSUE_API_PATH(workspaceId),
  });

  return response.data;
};
