export const recordingKeys = {
  all: ["recordings"] as const,
  current: (workspaceId: number) =>
    [...recordingKeys.all, "current", workspaceId] as const,
};
