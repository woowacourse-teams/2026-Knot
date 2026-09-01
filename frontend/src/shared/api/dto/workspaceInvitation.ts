/**
 * 워크스페이스 초대 DTO
 *
 * - GET  /api/v1/workspaces/{workspaceId}/invitation
 * - POST /api/v1/workspaces/{workspaceId}/invitations
 * - POST /api/v1/workspaces/{workspaceId}/invitations/reissue
 * - GET  /api/v1/invitations/{tokenOrCode}
 * - POST /api/v1/invitations/accept
 */

/** 워크스페이스 초대의 서버 응답 모양 */
export interface WorkspaceInvitationRaw {
  code: string;
  linkToken: string;
  expiresAt: string;
}

/** 워크스페이스 초대. 초대 조회·발급·재발급 응답이 공유 */
export class WorkspaceInvitationDto {
  /** 6자 대문자 초대 코드 (예: X35D3S) */
  code: string;
  /** 초대 링크 토큰. `/invite/<linkToken>` 진입 경로에 들어감 */
  linkToken: string;
  /** 초대 만료 시각(ISO 8601) */
  expiresAt: string;

  constructor(raw: WorkspaceInvitationRaw) {
    this.code = raw.code;
    this.linkToken = raw.linkToken;
    this.expiresAt = raw.expiresAt;
  }
}

// GET /api/v1/workspaces/{workspaceId}/invitation

/** 활성 초대 조회의 서버 응답 모양 */
export interface GetWorkspaceInvitationResponseRaw {
  code: string;
  linkToken: string;
  expiresAt: string;
}

/** 워크스페이스의 활성 초대 조회 응답. 활성 초대가 없으면 404 */
export class GetWorkspaceInvitationResponseDto {
  /** 6자 대문자 초대 코드 (예: X35D3S) */
  code: string;
  /** 초대 링크 토큰. `/invite/<linkToken>` 진입 경로에 들어감 */
  linkToken: string;
  /** 초대 만료 시각(ISO 8601) */
  expiresAt: string;

  constructor(raw: GetWorkspaceInvitationResponseRaw) {
    const invitation = new WorkspaceInvitationDto(raw);

    this.code = invitation.code;
    this.linkToken = invitation.linkToken;
    this.expiresAt = invitation.expiresAt;
  }
}

// POST /api/v1/workspaces/{workspaceId}/invitations

/** 초대 발급의 서버 응답 모양 */
export interface PostWorkspaceInvitationResponseRaw {
  code: string;
  linkToken: string;
  expiresAt: string;
}

/** 워크스페이스 초대 발급 응답. 기존 활성 초대(200)와 새 초대(201) 모두 같은 모양 */
export class PostWorkspaceInvitationResponseDto {
  /** 6자 대문자 초대 코드 (예: X35D3S) */
  code: string;
  /** 초대 링크 토큰. `/invite/<linkToken>` 진입 경로에 들어감 */
  linkToken: string;
  /** 초대 만료 시각(ISO 8601) */
  expiresAt: string;

  constructor(raw: PostWorkspaceInvitationResponseRaw) {
    const invitation = new WorkspaceInvitationDto(raw);

    this.code = invitation.code;
    this.linkToken = invitation.linkToken;
    this.expiresAt = invitation.expiresAt;
  }
}

// POST /api/v1/workspaces/{workspaceId}/invitations/reissue

/** 초대 재발급의 서버 응답 모양 */
export interface PostWorkspaceInvitationReissueResponseRaw {
  code: string;
  linkToken: string;
  expiresAt: string;
}

/** 기존 초대를 무효화하고 새로 발급한 초대 응답 */
export class PostWorkspaceInvitationReissueResponseDto {
  /** 6자 대문자 초대 코드 (예: X35D3S) */
  code: string;
  /** 초대 링크 토큰. `/invite/<linkToken>` 진입 경로에 들어감 */
  linkToken: string;
  /** 초대 만료 시각(ISO 8601) */
  expiresAt: string;

  constructor(raw: PostWorkspaceInvitationReissueResponseRaw) {
    const invitation = new WorkspaceInvitationDto(raw);

    this.code = invitation.code;
    this.linkToken = invitation.linkToken;
    this.expiresAt = invitation.expiresAt;
  }
}

// GET /api/v1/invitations/{tokenOrCode}

/** 초대 미리보기의 서버 응답 모양 */
export interface GetInvitationPreviewResponseRaw {
  workspaceId: number;
  workspaceName: string;
}

/** 초대 토큰 또는 코드가 가리키는 워크스페이스의 참여 전 조회 응답. 초대가 없으면 404 */
export class GetInvitationPreviewResponseDto {
  /** 초대가 가리키는 워크스페이스 ID */
  workspaceId: number;
  /** 초대가 가리키는 워크스페이스 이름. 최대 20자 */
  workspaceName: string;

  constructor(raw: GetInvitationPreviewResponseRaw) {
    this.workspaceId = raw.workspaceId;
    this.workspaceName = raw.workspaceName;
  }
}

// POST /api/v1/invitations/accept

/** 초대 수락 시 앱이 넘기는 값 */
export interface PostInvitationAcceptRequestInput {
  credential: string;
}

/** 초대 코드 또는 링크 토큰으로 워크스페이스에 참여하는 요청 본문 */
export class PostInvitationAcceptRequestDto {
  /** 6자 초대 코드 또는 링크 토큰 원문 */
  credential: string;

  constructor({ credential }: PostInvitationAcceptRequestInput) {
    this.credential = credential;
  }
}

/** 초대 수락의 서버 응답 모양 */
export interface PostInvitationAcceptResponseRaw {
  workspaceId: number;
  workspaceName: string;
}

/** 초대 수락 응답. 기존 멤버십(200)과 새 멤버십(201) 모두 같은 모양 */
export class PostInvitationAcceptResponseDto {
  /** 참여한 워크스페이스 ID */
  workspaceId: number;
  /** 참여한 워크스페이스 이름. 최대 20자 */
  workspaceName: string;

  constructor(raw: PostInvitationAcceptResponseRaw) {
    this.workspaceId = raw.workspaceId;
    this.workspaceName = raw.workspaceName;
  }
}
