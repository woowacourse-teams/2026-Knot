/** 워크스페이스 초대. 초대 조회·발급·재발급 응답이 같은 모양을 써요 */
export interface WorkspaceInvitation {
  /** 6자 초대 코드 (예: X35D3S) */
  code: string;
  /** 초대 링크 토큰. `/invite/<linkToken>` 진입 경로에 들어가요 */
  linkToken: string;
  /** 초대 만료 시각 (ISO 8601) */
  expiresAt: string;
}
