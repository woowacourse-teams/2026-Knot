interface RecordingDetailKeyParams {
  workspaceId: number;
  recordingId: number;
}

export const recordingKeys = {
  all: ["recordings"] as const,
  detail: ({ workspaceId, recordingId }: RecordingDetailKeyParams) =>
    [...recordingKeys.all, "detail", workspaceId, recordingId] as const,
};
