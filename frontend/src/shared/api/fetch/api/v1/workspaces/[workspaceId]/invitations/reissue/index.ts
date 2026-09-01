import type { WorkspaceInvitation } from "@/shared/types/workspaceInvitation";
import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 워크스페이스 초대 재발급 API 경로를 생성하는 함수
 * @param workspaceId - 워크스페이스 ID
 * @returns API 경로 문자열
 */
export const WORKSPACE_INVITATIONS_REISSUE_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitations/reissue`;

/**
 * @public
 * @category Types
 * @description 워크스페이스 초대 재발급 응답 타입. 새로 만든 초대(201)
 */
export type PostWorkspaceInvitationReissueApiResponse = WorkspaceInvitation;

/**
 * @public
 * @category WorkspaceInvitations
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
