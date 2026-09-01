/** 초대 조회·발급·재발급 응답이 같은 모양을 써요 */
export interface WorkspaceInvitation {
  /** 6자 초대 코드 (예: X35D3S) */
  code: string;
  /** `/invite/<linkToken>` 진입 경로에 들어가요 */
  linkToken: string;
  /** ISO 8601 */
  expiresAt: string;
}

export interface InvitationPreviewResponse {
  workspaceId: number;
  workspaceName: string;
}

export interface InvitationAcceptanceResponse {
  workspaceId: number;
  workspaceName: string;
}
