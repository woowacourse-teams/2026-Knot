import { authCsrfHandlers } from "./api/v1/auth/csrf";
import { authMeHandlers } from "./api/v1/auth/me";
import { authNicknameHandlers } from "./api/v1/auth/nickname";
import { chatMessagesHandlers } from "./api/v1/conversations/[sessionId]";
import { invitationPreviewHandlers } from "./api/v1/invitations/[tokenOrCode]";
import { invitationAcceptHandlers } from "./api/v1/invitations/accept";
import { lastViewedWorkspaceHandlers } from "./api/v1/members/me/lastViewedWorkspace";
import { workspacesHandlers } from "./api/v1/workspaces";
import { workspaceHandlers } from "./api/v1/workspaces/[workspaceId]";
import { workspaceConversationsHandlers } from "./api/v1/workspaces/[workspaceId]/conversations";
import { workspaceInvitationHandlers } from "./api/v1/workspaces/[workspaceId]/invitation";
import { workspaceInvitationsHandlers } from "./api/v1/workspaces/[workspaceId]/invitations";
import { workspaceInvitationReissueHandlers } from "./api/v1/workspaces/[workspaceId]/invitations/reissue";

// 엔드포인트 핸들러는 `fetch/`와 같은 경로 구조(`handlers/api/v1/...`)로 두고 여기서 하나로 합쳐요.
// 브라우저 리다이렉트 엔드포인트(OAuth 시작·로그아웃 302)와 SSE 메시지 전송은 XHR 응답을 흉내낼 대상이 아니라 두지 않아요
export const handlers = [
  ...authMeHandlers,
  ...authCsrfHandlers,
  ...authNicknameHandlers,
  ...lastViewedWorkspaceHandlers,
  ...workspacesHandlers,
  ...workspaceHandlers,
  ...workspaceInvitationHandlers,
  ...workspaceInvitationsHandlers,
  ...workspaceInvitationReissueHandlers,
  ...workspaceConversationsHandlers,
  ...invitationAcceptHandlers,
  ...invitationPreviewHandlers,
  ...chatMessagesHandlers,
];
