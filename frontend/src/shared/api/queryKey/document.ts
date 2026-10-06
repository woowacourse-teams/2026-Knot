interface DocumentKeyParams {
  workspaceId: number;
  documentId: number;
}

export const documentKeys = {
  all: ["documents"] as const,
  detail: ({ workspaceId, documentId }: DocumentKeyParams) =>
    [...documentKeys.all, "detail", workspaceId, documentId] as const,
};
