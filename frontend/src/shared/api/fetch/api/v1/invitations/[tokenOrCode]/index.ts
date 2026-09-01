import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 초대 토큰 또는 코드로 참여 대상 워크스페이스를 미리 보는 API 경로를 생성하는 함수
 * @param tokenOrCode - 초대 링크 토큰 또는 초대 코드 원문
 * @returns API 경로 문자열
 */
export const INVITATION_PREVIEW_API_PATH = (tokenOrCode: string) =>
  `/api/v1/invitations/${encodeURIComponent(tokenOrCode)}`;

/**
 * @public
 * @category Types
 * @interface GetInvitationPreviewApiResponse
 * @description 참여 대상 워크스페이스 미리보기 응답 타입
 * @property {number} workspaceId - 워크스페이스 ID
 * @property {string} workspaceName - 워크스페이스 이름
 */
export interface GetInvitationPreviewApiResponse {
  workspaceId: number;
  workspaceName: string;
}

/**
 * @public
 * @category WorkspaceInvitations
 * @description 초대 토큰 또는 코드가 가리키는 워크스페이스를 참여 전에 조회합니다. 초대가 없으면 404가 와요
 * @param tokenOrCode - 초대 링크 토큰 또는 초대 코드 원문
 * @returns 워크스페이스 ID와 이름
 * @example
 * const { workspaceName } = await getInvitationPreviewApi("X35D3S");
 */
export const getInvitationPreviewApi = async (tokenOrCode: string) => {
  const response = await httpClient<GetInvitationPreviewApiResponse>({
    method: "get",
    url: INVITATION_PREVIEW_API_PATH(tokenOrCode),
  });

  return response.data;
};
