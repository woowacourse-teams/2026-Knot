import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 워크스페이스 단건 조회 API 경로를 생성하는 함수
 * @param workspaceId - 워크스페이스 ID
 * @returns API 경로 문자열
 */
export const WORKSPACE_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}`;

/**
 * @public
 * @category Types
 * @interface GetWorkspaceApiResponse
 * @description 워크스페이스 단건 조회 응답 타입
 * @property {string} name - 워크스페이스 이름
 */
export interface GetWorkspaceApiResponse {
  name: string;
}

/**
 * @public
 * @category Workspaces
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
