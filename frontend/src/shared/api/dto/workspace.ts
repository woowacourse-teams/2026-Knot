/**
 * 워크스페이스 DTO
 *
 * - GET  /api/v1/workspaces
 * - POST /api/v1/workspaces
 * - GET  /api/v1/workspaces/{workspaceId}
 * - PUT  /api/v1/members/me/last-viewed-workspace
 */

/** 워크스페이스 목록 항목의 서버 응답 모양 */
export interface WorkspaceListItemRaw {
  id: number;
  name: string;
}

/** 워크스페이스 목록 항목. 목록 조회 응답의 `workspaces`에 들어감 */
export class WorkspaceListItemDto {
  /** 워크스페이스 ID */
  id: number;
  /** 워크스페이스 이름. 최대 20자 */
  name: string;

  constructor(raw: WorkspaceListItemRaw) {
    this.id = raw.id;
    this.name = raw.name;
  }
}

// GET /api/v1/workspaces

/** 워크스페이스 목록 조회의 서버 응답 모양 */
export interface GetWorkspacesResponseRaw {
  lastViewedWorkspaceId: number | null;
  workspaces: WorkspaceListItemRaw[];
}

/** 내가 속한 워크스페이스 목록 조회 응답 */
export class GetWorkspacesResponseDto {
  /** 마지막으로 본 워크스페이스 ID. 본 적이 없으면 null */
  lastViewedWorkspaceId: number | null;
  /** 내가 속한 워크스페이스 목록. 없으면 빈 배열 */
  workspaces: WorkspaceListItemDto[];

  constructor(raw: GetWorkspacesResponseRaw) {
    this.lastViewedWorkspaceId = raw.lastViewedWorkspaceId;
    this.workspaces = raw.workspaces.map(
      (item) => new WorkspaceListItemDto(item),
    );
  }
}

// POST /api/v1/workspaces

/** 워크스페이스 생성 시 앱이 넘기는 값 */
export interface PostWorkspaceRequestInput {
  name: string;
}

/** 워크스페이스 생성 요청 본문 */
export class PostWorkspaceRequestDto {
  /** 워크스페이스 이름. 앞뒤 공백 제거. 한글·영문·공백만, 최대 20자, 한글이나 영문을 하나 이상 포함 */
  name: string;

  constructor({ name }: PostWorkspaceRequestInput) {
    this.name = name.trim();
  }
}

/** 워크스페이스 생성의 서버 응답 모양 */
export interface PostWorkspaceResponseRaw {
  id: number;
}

/** 워크스페이스 생성 응답 */
export class PostWorkspaceResponseDto {
  /** 생성된 워크스페이스 ID */
  id: number;

  constructor(raw: PostWorkspaceResponseRaw) {
    this.id = raw.id;
  }
}

// GET /api/v1/workspaces/{workspaceId}

/** 워크스페이스 단건 조회의 서버 응답 모양 */
export interface GetWorkspaceResponseRaw {
  name: string;
}

/** 워크스페이스 단건 조회 응답 */
export class GetWorkspaceResponseDto {
  /** 워크스페이스 이름. 최대 20자 */
  name: string;

  constructor(raw: GetWorkspaceResponseRaw) {
    this.name = raw.name;
  }
}

// PUT /api/v1/members/me/last-viewed-workspace

/** 마지막으로 본 워크스페이스 갱신 시 앱이 넘기는 값 */
export interface PutLastViewedWorkspaceRequestInput {
  workspaceId: number;
}

/** 마지막으로 본 워크스페이스 갱신 요청 본문. 성공 시 응답 본문 없음(204) */
export class PutLastViewedWorkspaceRequestDto {
  /** 마지막으로 본 워크스페이스 ID */
  workspaceId: number;

  constructor({ workspaceId }: PutLastViewedWorkspaceRequestInput) {
    this.workspaceId = workspaceId;
  }
}
