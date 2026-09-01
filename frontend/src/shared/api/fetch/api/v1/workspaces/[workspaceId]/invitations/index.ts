import type { WorkspaceInvitation } from "@/shared/types/workspaceInvitation";
import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 워크스페이스 초대 발급 API 경로를 생성하는 함수
 * @param workspaceId - 워크스페이스 ID
 * @returns API 경로 문자열
 */
export const WORKSPACE_INVITATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/invitations`;

/**
 * @public
 * @category Types
 * @description 워크스페이스 초대 발급 응답 타입. 기존 활성 초대(200)와 새 초대(201) 모두 같은 모양이에요
 */
export type PostWorkspaceInvitationApiResponse = WorkspaceInvitation;

/**
 * @public
 * @category WorkspaceInvitations
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
