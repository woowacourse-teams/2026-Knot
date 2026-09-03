export interface NotionOAuthAuthorizationResponse {
  /** 사용자를 보낼 Notion 인증 페이지 절대 URL */
  authorizationUrl: string;
}

/** Notion 연결 상태 조회 응답 */
export interface NotionConnectionResponse {
  /** 연결 상태. 연결된 적이 없어도 404가 아니라 NOT_CONNECTED로 와요 */
  status: "NOT_CONNECTED" | "CONNECTED" | "REAUTH_REQUIRED";
}
