import type { WorkspaceInvitation } from "@/shared/types/workspaceInvitation";
import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 워크스페이스의 활성 초대 조회 API 경로를 생성하는 함수
 * @param workspaceId - 워크스페이스 ID
 * @returns API 경로 문자열
 */
export const WORKSPACE_INVITATION_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitation`;

/**
 * @public
 * @category Types
 * @description 워크스페이스 초대 조회 응답 타입. 초대 코드·링크 토큰·만료 시각
 */
export type GetWorkspaceInvitationApiResponse = WorkspaceInvitation;

/**
 * @public
 * @category WorkspaceInvitations
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
