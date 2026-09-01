import { httpClient } from "@api/httpClient";

export const WORKSPACE_INVITATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitations`;

// 기존 활성 초대(200)와 새 초대(201) 모두 같은 모양이에요
interface PostWorkspaceInvitationApiResponse {
  /** 6자 초대 코드 (예: X35D3S) */
  code: string;
  /** `/invite/<linkToken>` 진입 경로에 들어가요 */
  linkToken: string;
  /** ISO 8601 */
  expiresAt: string;
}

/**
 * @description 워크스페이스 초대를 발급합니다. 활성 초대가 있으면 그대로 돌려주고 없으면 새로 만들어요
 * @param workspaceId - 워크스페이스 ID
 * @returns 초대 코드·링크 토큰·만료 시각
 * @example
 * const { code, linkToken } = await issueWorkspaceInvitationApi(1);
 */
export const issueWorkspaceInvitationApi = async (workspaceId: number) => {
  const response = await httpClient<PostWorkspaceInvitationApiResponse>({
    method: "post",
    url: WORKSPACE_INVITATIONS_API_PATH(workspaceId),
  });

  return response.data;
};
