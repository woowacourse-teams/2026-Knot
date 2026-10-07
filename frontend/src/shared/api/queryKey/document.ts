interface DocumentDetailKeyParams {
  workspaceId: number;
  documentId: number;
}

export const documentKeys = {
  all: ["documents"] as const,
  detail: ({ workspaceId, documentId }: DocumentDetailKeyParams) =>
    [...documentKeys.all, "detail", workspaceId, documentId] as const,
};
