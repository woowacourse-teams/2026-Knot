/**
 * Notion 연결 DTO
 *
 * - POST /api/v1/workspaces/{workspaceId}/notion-oauth-authorizations
 */

// POST /api/v1/workspaces/{workspaceId}/notion-oauth-authorizations

/** Notion OAuth 연결 시작의 서버 응답 모양 */
export interface PostNotionOAuthAuthorizationResponseRaw {
  authorizationUrl: string;
}

/**
 * Notion OAuth 연결 시작 응답(201). 요청 본문은 없고 워크스페이스 ID만 경로로 받아요.
 * 워크스페이스 OWNER만 시작할 수 있어 다른 멤버는 403이 와요.
 */
export class PostNotionOAuthAuthorizationResponseDto {
  /** 사용자를 보낼 Notion 인증 페이지의 절대 URL. state·redirect가 이미 담겨 있어 그대로 이동하면 돼요 */
  authorizationUrl: string;

  constructor(raw: PostNotionOAuthAuthorizationResponseRaw) {
    this.authorizationUrl = raw.authorizationUrl;
  }
}
