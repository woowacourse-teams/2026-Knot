import type {
  GetWorkspacesApiResponse,
  PostWorkspaceApiResponse,
} from "@api/fetch/api/v1/workspaces";
import type { GetWorkspaceApiResponse } from "@api/fetch/api/v1/workspaces/[workspaceId]";

export const workspacesResponse = {
  lastViewedWorkspaceId: 1,
  workspaces: [
    { id: 1, name: "Knot 팀" },
    { id: 2, name: "노티드의 워크스페이스" },
  ],
} satisfies GetWorkspacesApiResponse;

export const workspaceDetailResponse = {
  name: "Knot 팀",
} satisfies GetWorkspaceApiResponse;

export const workspaceCreateResponse = {
  id: 3,
} satisfies PostWorkspaceApiResponse;
