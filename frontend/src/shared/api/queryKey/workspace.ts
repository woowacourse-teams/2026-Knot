export const workspaceKeys = {
  all: ["workspaces"] as const,
  list: () => [...workspaceKeys.all, "list"] as const,
  detail: (workspaceId: number) =>
    [...workspaceKeys.all, "detail", workspaceId] as const,
};
