export interface WorkspaceListItem {
  id: number;
  name: string;
}

export interface WorkspacesResponse {
  /** 본 적이 없으면 null */
  lastViewedWorkspaceId: number | null;
  workspaces: WorkspaceListItem[];
}

export interface WorkspaceDetailResponse {
  name: string;
}

export interface WorkspaceCreateResponse {
  id: number;
}
