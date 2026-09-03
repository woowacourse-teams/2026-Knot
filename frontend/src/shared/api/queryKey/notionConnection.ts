export const notionConnectionKeys = {
  all: ["notionConnection"] as const,
  detail: (workspaceId: number) =>
    [...notionConnectionKeys.all, "detail", workspaceId] as const,
};
