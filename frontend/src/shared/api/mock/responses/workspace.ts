import type {
  WorkspaceCreateResponse,
  WorkspaceDetailResponse,
  WorkspacesResponse,
} from "@api/mock/types/workspace";

export const workspacesResponse = {
  lastViewedWorkspaceId: 1,
  workspaces: [
    { id: 1, name: "Knot 팀" },
    { id: 2, name: "노티드의 워크스페이스" },
  ],
} satisfies WorkspacesResponse;

export const workspaceDetailResponse = {
  name: "Knot 팀",
} satisfies WorkspaceDetailResponse;

export const workspaceCreateResponse = {
  id: 3,
} satisfies WorkspaceCreateResponse;
