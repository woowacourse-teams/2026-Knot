import type { GetInvitationPreviewApiResponse } from "@api/fetch/api/v1/invitations/[tokenOrCode]";
import type { PostInvitationAcceptApiResponse } from "@api/fetch/api/v1/invitations/accept";
import type { GetWorkspaceInvitationApiResponse } from "@api/fetch/api/v1/workspaces/[workspaceId]/invitation";

const DAY = 24 * 60 * 60 * 1000;

// 만료 시각을 고정값으로 두면 언젠가 전부 만료로 묶이므로 지금을 기준으로 생성해요
const afterNow = (remaining: number) =>
  new Date(Date.now() + remaining).toISOString();

// 코드·링크 토큰은 위젯이 임시로 박아 둔 값과 같아요. API 연결 시 위젯 테스트 기대값을 이 응답으로 바꾸기 위해서예요
export const workspaceInvitationResponse = {
  code: "X35D3S",
  linkToken: "Xk3vQ9mZp2LrT7wB1nHc4A",
  expiresAt: afterNow(7 * DAY),
} satisfies GetWorkspaceInvitationApiResponse;

export const invitationPreviewResponse = {
  workspaceId: 1,
  workspaceName: "Knot 팀",
} satisfies GetInvitationPreviewApiResponse;

export const invitationAcceptanceResponse = {
  workspaceId: 1,
  workspaceName: "Knot 팀",
} satisfies PostInvitationAcceptApiResponse;
