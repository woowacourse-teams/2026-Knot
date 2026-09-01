import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 마지막으로 본 워크스페이스 갱신 API 경로
 */
export const LAST_VIEWED_WORKSPACE_API_PATH =
  "/api/v1/members/me/last-viewed-workspace";

/**
 * @public
 * @category Types
 * @interface PutLastViewedWorkspaceApiRequest
 * @description 마지막으로 본 워크스페이스 갱신 요청 타입
 * @property {number} workspaceId - 워크스페이스 ID
 */
export interface PutLastViewedWorkspaceApiRequest {
  workspaceId: number;
}

/**
 * @public
 * @category Members
 * @description 마지막으로 본 워크스페이스를 갱신합니다. 성공 시 응답 본문은 없어요(204)
 * @param body - 워크스페이스 ID
 * @example
 * await updateLastViewedWorkspaceApi({ workspaceId: 1 });
 */
export const updateLastViewedWorkspaceApi = async (
  body: PutLastViewedWorkspaceApiRequest,
) => {
  await httpClient({
    method: "put",
    url: LAST_VIEWED_WORKSPACE_API_PATH,
    data: body,
  });
};
