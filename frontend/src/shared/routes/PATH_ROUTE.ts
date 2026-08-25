import { generatePath, PathParam } from "react-router";

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

  CHAT: "/workspace/:workspaceId/chat",
  CHAT_SESSION: "/workspace/:workspaceId/chat/:sessionId",

  JOIN_ERROR: "/join-error",
} as const;

type RouteKey = keyof typeof PATH_ROUTE;

type RouteParams<K extends RouteKey> = {
  [P in PathParam<(typeof PATH_ROUTE)[K]>]: string;
};

type GetRouterPathParams<K extends RouteKey> = [
  PathParam<(typeof PATH_ROUTE)[K]>,
] extends [never]
  ? { routeConstants: K; params?: never }
  : { routeConstants: K; params: RouteParams<K> };

/**
 * path가 필요한 곳에서 사용할 수 있는 유틸입니다.
 * @example
 * getRouterPath({ routeConstants: "WORKSPACE_HOME", params: { workspaceId: "123" } })
 * // returns "/workspace/123"
 */
export const getRouterPath = <K extends RouteKey>({
  routeConstants,
  params,
}: GetRouterPathParams<K>) => {
  const path: string = PATH_ROUTE[routeConstants];

  return generatePath(path, params);
};
