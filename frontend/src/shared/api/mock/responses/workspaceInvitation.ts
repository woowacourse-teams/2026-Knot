import type {
  InvitationAcceptanceResponse,
  InvitationPreviewResponse,
  WorkspaceInvitation,
} from "@api/mock/types/workspaceInvitation";

const DAY = 24 * 60 * 60 * 1000;

// 고정 만료 시각은 언젠가 전부 만료되므로 지금 기준으로 만들어요
const afterNow = (remaining: number) =>
  new Date(Date.now() + remaining).toISOString();

// 위젯의 임시 상수와 같은 값이에요. API 연결 시 위젯 테스트 기대값을 이 응답으로 바꿔요
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
