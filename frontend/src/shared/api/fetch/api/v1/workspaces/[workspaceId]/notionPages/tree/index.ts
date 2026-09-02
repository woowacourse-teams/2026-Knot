import {
  GetNotionPageTreeResponseDto,
  type GetNotionPageTreeResponseRaw,
} from "@api/dto/notionPage";
import { httpClient } from "@api/httpClient";

export const WORKSPACE_NOTION_PAGE_TREE_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/notion-pages/tree`;

/**
 * @description 워크스페이스에 발행된 Notion Page Tree를 조회합니다
 * @param workspaceId - 워크스페이스 ID
 * @returns 부모를 가리키는 평평한 Page 목록
 * @example
 * const { pages } = await getNotionPageTreeApi(1);
 */
export const getNotionPageTreeApi = async (workspaceId: number) => {
  const response = await httpClient<GetNotionPageTreeResponseRaw>({
    method: "get",
    url: WORKSPACE_NOTION_PAGE_TREE_API_PATH(workspaceId),
  });

  return new GetNotionPageTreeResponseDto(response.data);
};
