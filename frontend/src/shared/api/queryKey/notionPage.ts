/**
 * Notion Page 쿼리 키
 *
 * - `tree` 워크스페이스에 발행된 Page Tree
 */
export const notionPageKeys = {
  all: ["notionPages"] as const,

  tree: (workspaceId: number) =>
    [...notionPageKeys.all, "tree", workspaceId] as const,
};
