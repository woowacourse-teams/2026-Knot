export const workspaceInvitationKeys = {
  all: ["workspaceInvitations"] as const,
  active: (workspaceId: number) =>
    [...workspaceInvitationKeys.all, "active", workspaceId] as const,
  preview: (credential: string) =>
    [...workspaceInvitationKeys.all, "preview", credential] as const,
};
