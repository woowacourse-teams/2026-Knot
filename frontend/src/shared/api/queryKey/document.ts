interface DocumentListKeyParams {
  workspaceId: number;
  /** 이 녹음에서 나온 문서만 담은 목록. 없으면 워크스페이스의 문서 전체 */
  recordingSessionId?: number;
}

interface DocumentDetailKeyParams {
  workspaceId: number;
  documentId: number;
}

export const documentKeys = {
  all: ["documents"] as const,
  list: ({ workspaceId, recordingSessionId }: DocumentListKeyParams) =>
    [...documentKeys.all, "list", workspaceId, { recordingSessionId }] as const,
  detail: ({ workspaceId, documentId }: DocumentDetailKeyParams) =>
    [...documentKeys.all, "detail", workspaceId, documentId] as const,
  // 문서 상세 키 아래에 둬서, 상세를 무효화하면 확인 대상도 함께 다시 받아요
  confirmations: (params: DocumentDetailKeyParams) =>
    [...documentKeys.detail(params), "confirmations"] as const,
};
