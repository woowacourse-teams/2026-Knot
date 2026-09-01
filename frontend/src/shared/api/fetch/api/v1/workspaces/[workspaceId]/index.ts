import { httpClient } from "@api/httpClient";

export const WORKSPACE_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}`;

export interface GetWorkspaceApiResponse {
  name: string;
}

/**
 * @description 워크스페이스 하나를 조회합니다
 * @param workspaceId - 워크스페이스 ID
 * @returns 워크스페이스 이름
 * @example
 * const { name } = await getWorkspaceApi(1);
 */
export const getWorkspaceApi = async (workspaceId: number) => {
  const response = await httpClient<GetWorkspaceApiResponse>({
    method: "get",
    url: WORKSPACE_API_PATH(workspaceId),
  });

  return response.data;
};
