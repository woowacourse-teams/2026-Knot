import {
  GetNotionConnectionResponseDto,
  type GetNotionConnectionResponseRaw,
} from "@api/dto/notionConnection";
import { httpClient } from "@api/httpClient";

export const NOTION_CONNECTION_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/notion-connection`;

/**
 * @description 워크스페이스의 Notion 연결 상태를 조회합니다. 연결 안 됨·연결됨·재인증 필요 중 하나를 받아요
 * @param workspaceId - 워크스페이스 ID
 * @returns Notion 연결 상태
 * @example
 * const { status } = await getNotionConnectionApi(1);
 */
export const getNotionConnectionApi = async (workspaceId: number) => {
  const response = await httpClient<GetNotionConnectionResponseRaw>({
    method: "get",
    url: NOTION_CONNECTION_API_PATH(workspaceId),
  });

  return new GetNotionConnectionResponseDto(response.data);
};
