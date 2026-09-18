/**
 * Notion 연결 DTO
 *
 * - POST /api/v1/workspaces/{workspaceId}/notion-oauth-authorizations
 * - GET  /api/v1/workspaces/{workspaceId}/notion-connection
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

// GET /api/v1/workspaces/{workspaceId}/notion-connection

/** Notion 연결 상태 조회의 서버 응답 모양 */
export interface GetNotionConnectionResponseRaw {
  status: "NOT_CONNECTED" | "CONNECTED" | "REAUTH_REQUIRED";
}

/**
 * 워크스페이스의 Notion 연결 상태 조회 응답(200). 워크스페이스 멤버면 누구나 조회할 수 있고,
 * 연결된 적이 없어도 404가 아니라 NOT_CONNECTED로 와요
 */
export class GetNotionConnectionResponseDto {
  /**
   * Notion 연결 상태.
   * NOT_CONNECTED는 연결 안 됨, CONNECTED는 연결됨,
   * REAUTH_REQUIRED는 연결은 남아 있지만 연결을 승인한 멤버가 더 이상 OWNER가 아니라 OWNER가 다시 연결해야 하는 상태예요
   */
  status: "NOT_CONNECTED" | "CONNECTED" | "REAUTH_REQUIRED";

  constructor(raw: GetNotionConnectionResponseRaw) {
    this.status = raw.status;
  }
}
