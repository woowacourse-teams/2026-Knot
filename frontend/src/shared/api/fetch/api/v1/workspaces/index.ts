import {
  GetWorkspacesResponseDto,
  PostWorkspaceResponseDto,
  type GetWorkspacesResponseRaw,
  type PostWorkspaceRequestDto,
  type PostWorkspaceResponseRaw,
} from "@api/dto/workspace";
import { httpClient } from "@api/httpClient";

export const WORKSPACES_API_PATH = "/api/v1/workspaces";

/**
 * @description 내가 속한 워크스페이스 목록을 조회합니다
 * @returns 마지막으로 본 워크스페이스 ID와 워크스페이스 목록
 * @example
 * const { workspaces } = await getWorkspacesApi();
 */
export const getWorkspacesApi = async () => {
  const response = await httpClient<GetWorkspacesResponseRaw>({
    method: "get",
    url: WORKSPACES_API_PATH,
  });

  return new GetWorkspacesResponseDto(response.data);
};

/**
 * @description 워크스페이스를 생성합니다
 * @param body - 워크스페이스 생성 요청 본문
 * @returns 생성된 워크스페이스 ID
 * @example
 * const { id } = await createWorkspaceApi(new PostWorkspaceRequestDto({ name: "Knot 팀" }));
 */
export const createWorkspaceApi = async (body: PostWorkspaceRequestDto) => {
  const response = await httpClient<PostWorkspaceResponseRaw>({
    method: "post",
    url: WORKSPACES_API_PATH,
    data: body,
  });

  return new PostWorkspaceResponseDto(response.data);
};
