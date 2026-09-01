import { httpClient } from "@api/httpClient";

export const WORKSPACE_INVITATION_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitation`;

// 초대 발급·재발급 응답도 같은 모양을 써요
interface GetWorkspaceInvitationApiResponse {
  /** 6자 초대 코드 (예: X35D3S) */
  code: string;
  /** `/invite/<linkToken>` 진입 경로에 들어가요 */
  linkToken: string;
  /** ISO 8601 */
  expiresAt: string;
}

/**
 * @description 워크스페이스의 활성 초대를 조회합니다. 활성 초대가 없으면 404가 와요
 * @param workspaceId - 워크스페이스 ID
 * @returns 초대 코드·링크 토큰·만료 시각
 * @example
 * const { code, linkToken } = await getWorkspaceInvitationApi(1);
 */
export const getWorkspaceInvitationApi = async (workspaceId: number) => {
  const response = await httpClient<GetWorkspaceInvitationApiResponse>({
    method: "get",
    url: WORKSPACE_INVITATION_API_PATH(workspaceId),
  });

  return response.data;
};
