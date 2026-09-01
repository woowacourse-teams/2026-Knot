import { httpClient } from "@api/httpClient";

/**
 * @public
 * @category Constants
 * @description 내 워크스페이스 목록 조회·생성 API 경로
 */
export const WORKSPACES_API_PATH = "/api/v1/workspaces";

/**
 * @public
 * @category Types
 * @interface WorkspaceListItem
 * @description 워크스페이스 목록의 한 항목
 * @property {number} id - 워크스페이스 ID
 * @property {string} name - 워크스페이스 이름
 */
export interface WorkspaceListItem {
  id: number;
  name: string;
}

/**
 * @public
 * @category Types
 * @interface GetWorkspacesApiResponse
 * @description 내 워크스페이스 목록 조회 응답 타입
 * @property {number | null} lastViewedWorkspaceId - 마지막으로 본 워크스페이스 ID. 본 적이 없으면 null
 * @property {WorkspaceListItem[]} workspaces - 내가 속한 워크스페이스 목록
 */
export interface GetWorkspacesApiResponse {
  lastViewedWorkspaceId: number | null;
  workspaces: WorkspaceListItem[];
}

/**
 * @public
 * @category Types
 * @interface PostWorkspaceApiRequest
 * @description 워크스페이스 생성 요청 타입
 * @property {string} name - 워크스페이스 이름. 한글·영문·공백만, 최대 20자, 한글이나 영문을 하나 이상 포함
 */
export interface PostWorkspaceApiRequest {
  name: string;
}

/**
 * @public
 * @category Types
 * @interface PostWorkspaceApiResponse
 * @description 워크스페이스 생성 응답 타입
 * @property {number} id - 생성된 워크스페이스 ID
 */
export interface PostWorkspaceApiResponse {
  id: number;
}

/**
 * @public
 * @category Workspaces
 * @description 내가 속한 워크스페이스 목록을 조회합니다
 * @returns 마지막으로 본 워크스페이스 ID와 워크스페이스 목록
 * @example
 * const { workspaces } = await getWorkspacesApi();
 */
export const getWorkspacesApi = async () => {
  const response = await httpClient<GetWorkspacesApiResponse>({
    method: "get",
    url: WORKSPACES_API_PATH,
  });

  return response.data;
};

/**
 * @public
 * @category Workspaces
 * @description 워크스페이스를 생성합니다
 * @param body - 워크스페이스 이름
 * @returns 생성된 워크스페이스 ID
 * @example
 * const { id } = await createWorkspaceApi({ name: "Knot 팀" });
 */
export const createWorkspaceApi = async (body: PostWorkspaceApiRequest) => {
  const response = await httpClient<PostWorkspaceApiResponse>({
    method: "post",
    url: WORKSPACES_API_PATH,
    data: body,
  });

  return response.data;
};
