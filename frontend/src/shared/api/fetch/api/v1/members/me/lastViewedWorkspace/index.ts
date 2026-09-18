import type { PutLastViewedWorkspaceRequestDto } from "@api/dto/workspace";
import { httpClient } from "@api/httpClient";

export const LAST_VIEWED_WORKSPACE_API_PATH =
  "/api/v1/members/me/last-viewed-workspace";

/**
 * @description 마지막으로 본 워크스페이스를 갱신합니다. 성공 시 응답 본문은 없어요(204)
 * @param body - 마지막으로 본 워크스페이스 갱신 요청 본문
 * @example
 * await updateLastViewedWorkspaceApi(new PutLastViewedWorkspaceRequestDto({ workspaceId: 1 }));
 */
export const updateLastViewedWorkspaceApi = async (
  body: PutLastViewedWorkspaceRequestDto,
) => {
  await httpClient({
    method: "put",
    url: LAST_VIEWED_WORKSPACE_API_PATH,
    data: body,
  });
};
