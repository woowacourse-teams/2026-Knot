import type {
  InvitationAcceptanceResponse,
  InvitationPreviewResponse,
  WorkspaceInvitation,
} from "@api/mock/types/workspaceInvitation";

const DAY = 24 * 60 * 60 * 1000;

// 고정 만료 시각은 언젠가 전부 만료되므로 지금 기준으로 만들어요
const afterNow = (remaining: number) =>
  new Date(Date.now() + remaining).toISOString();

// 초대 카드·홈 초대 카드 테스트가 이 응답을 DTO로 변환해 기대값으로 쓰고,
// 홈 E2E(workspaceHomePage)는 code와 `/invite/<linkToken>`을 복사 기대값으로 써요
export const workspaceInvitationResponse = {
  code: "X35D3S",
  linkToken: "Xk3vQ9mZp2LrT7wB1nHc4A",
  expiresAt: afterNow(7 * DAY),
} satisfies WorkspaceInvitation;

export const invitationPreviewResponse = {
  workspaceId: 1,
  workspaceName: "Knot 팀",
} satisfies InvitationPreviewResponse;

export const invitationAcceptanceResponse = {
  workspaceId: 1,
  workspaceName: "Knot 팀",
} satisfies InvitationAcceptanceResponse;
