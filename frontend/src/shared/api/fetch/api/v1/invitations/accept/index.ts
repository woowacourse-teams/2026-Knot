import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 초대 코드 또는 링크 토큰으로 워크스페이스에 참여하는 API 경로
 */
export const INVITATIONS_ACCEPT_API_PATH = "/api/v1/invitations/accept";

/**
 * @public
 * @category Types
 * @interface PostInvitationAcceptApiRequest
 * @description 워크스페이스 참여 요청 타입
 * @property {string} credential - 초대 코드 또는 링크 토큰 원문
 */
export interface PostInvitationAcceptApiRequest {
  credential: string;
}

/**
 * @public
 * @category Types
 * @interface PostInvitationAcceptApiResponse
 * @description 워크스페이스 참여 응답 타입. 기존 멤버십(200)과 새 멤버십(201) 모두 같은 모양이에요
 * @property {number} workspaceId - 참여한 워크스페이스 ID
 * @property {string} workspaceName - 참여한 워크스페이스 이름
 */
export interface PostInvitationAcceptApiResponse {
  workspaceId: number;
  workspaceName: string;
}

/**
 * @public
 * @category WorkspaceInvitations
 * @description 초대 코드 또는 링크 토큰으로 워크스페이스에 참여합니다
 * @param body - 초대 코드 또는 링크 토큰
 * @returns 참여한 워크스페이스 ID와 이름
 * @example
 * const { workspaceId } = await acceptInvitationApi({ credential: "X35D3S" });
 */
export const acceptInvitationApi = async (
  body: PostInvitationAcceptApiRequest,
) => {
  const response = await httpClient<PostInvitationAcceptApiResponse>({
    method: "post",
    url: INVITATIONS_ACCEPT_API_PATH,
    data: body,
  });

  return response.data;
};
