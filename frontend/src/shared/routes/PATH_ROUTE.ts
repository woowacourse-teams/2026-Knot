export const PATH_ROUTE = {
  HOME: "/",

  LOGIN: "/login",

  ONBOARDING: "/onboarding",
  ONBOARDING_COMPLETE: "/onboarding/complete",

  WORKSPACE: "/workspace",
  WORKSPACE_CREATE: "/workspace/create",
  WORKSPACE_CODE: "/workspace/code",
  WORKSPACE_HOME: "/workspace/:workspaceId",
  WORKSPACE_INVITE: "/workspace/:workspaceId/invite",
  WORKSPACE_NOTION_CONNECTION: "/workspace/:workspaceId/notion-connection",
  WORKSPACE_JOIN: "/workspace/:workspaceId/join",

  WORKSPACE_CHAT: "/workspace/:workspaceId/chat",
  WORKSPACE_CHAT_SESSION: "/workspace/:workspaceId/chat/:sessionId",

  JOIN_ERROR: "/join-error",
} as const;
