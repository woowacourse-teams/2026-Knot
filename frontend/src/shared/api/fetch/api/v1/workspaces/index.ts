import { httpClient } from "@api/httpClient";

export const WORKSPACES_API_PATH = "/api/v1/workspaces";

export interface WorkspaceListItem {
  id: number;
  name: string;
}

export interface GetWorkspacesApiResponse {
  /** 본 적이 없으면 null */
  lastViewedWorkspaceId: number | null;
  workspaces: WorkspaceListItem[];
}

export interface PostWorkspaceApiRequest {
  /** 한글·영문·공백만, 최대 20자, 한글이나 영문을 하나 이상 포함 */
  name: string;
}

export interface PostWorkspaceApiResponse {
  id: number;
}

/**
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
