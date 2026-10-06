import {
  PostNotionOAuthAuthorizationResponseDto,
  type PostNotionOAuthAuthorizationResponseRaw,
} from "@api/dto/notionConnection";
import { httpClient } from "@api/httpClient";

export const NOTION_OAUTH_AUTHORIZATIONS_API_PATH = (workspaceId: number) =>
  `/api/v1/workspaces/${workspaceId}/notion-oauth-authorizations`;

/**
 * @description 워크스페이스의 Notion OAuth 연결을 시작합니다. 사용자를 보낼 Notion 인증 URL을 받아요
 * @param workspaceId - 워크스페이스 ID
 * @returns Notion 인증 페이지 URL
 * @example
 * const { authorizationUrl } = await startNotionOAuthApi(1);
 */
export const startNotionOAuthApi = async (workspaceId: number) => {
  const response = await httpClient<PostNotionOAuthAuthorizationResponseRaw>({
    method: "post",
    url: NOTION_OAUTH_AUTHORIZATIONS_API_PATH(workspaceId),
  });

  return new PostNotionOAuthAuthorizationResponseDto(response.data);
};
