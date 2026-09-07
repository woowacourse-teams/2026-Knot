import { authLogoutHandlers } from "./api/v1/auth/logout";
import { authMeHandlers } from "./api/v1/auth/me";
import { authNicknameHandlers } from "./api/v1/auth/nickname";
import { chatMessagesHandlers } from "./api/v1/conversations/[sessionId]";
import { sendChatMessageHandlers } from "./api/v1/conversations/[sessionId]/messages";
import { notionImportStatusHandlers } from "./api/v1/imports/[importRunId]";
import { invitationPreviewHandlers } from "./api/v1/invitations/[tokenOrCode]";
import { invitationAcceptHandlers } from "./api/v1/invitations/accept";
import { lastViewedWorkspaceHandlers } from "./api/v1/members/me/lastViewedWorkspace";
import { workspacesHandlers } from "./api/v1/workspaces";
import { workspaceHandlers } from "./api/v1/workspaces/[workspaceId]";
import { workspaceConversationsHandlers } from "./api/v1/workspaces/[workspaceId]/conversations";
import { workspaceNotionImportsHandlers } from "./api/v1/workspaces/[workspaceId]/imports";
import { workspaceInvitationHandlers } from "./api/v1/workspaces/[workspaceId]/invitation";
import { workspaceInvitationsHandlers } from "./api/v1/workspaces/[workspaceId]/invitations";
import { workspaceInvitationReissueHandlers } from "./api/v1/workspaces/[workspaceId]/invitations/reissue";
import { workspaceNotionConnectionHandlers } from "./api/v1/workspaces/[workspaceId]/notionConnection";
import { workspaceNotionOAuthAuthorizationsHandlers } from "./api/v1/workspaces/[workspaceId]/notionOauthAuthorizations";
import { workspaceNotionPageTreeHandlers } from "./api/v1/workspaces/[workspaceId]/notionPages/tree";

// 페이지를 통째로 이동시키는 엔드포인트(OAuth 시작)는 XHR 응답이 아니라 두지 않아요
export const handlers = [
  ...authMeHandlers,
  ...authLogoutHandlers,
  ...authNicknameHandlers,
  ...lastViewedWorkspaceHandlers,
  ...workspacesHandlers,
  ...workspaceHandlers,
  ...workspaceInvitationHandlers,
  ...workspaceInvitationsHandlers,
  ...workspaceInvitationReissueHandlers,
  ...workspaceConversationsHandlers,
  ...workspaceNotionImportsHandlers,
  ...notionImportStatusHandlers,
  ...workspaceNotionOAuthAuthorizationsHandlers,
  ...workspaceNotionConnectionHandlers,
  ...workspaceNotionPageTreeHandlers,
  ...invitationAcceptHandlers,
  ...invitationPreviewHandlers,
  ...chatMessagesHandlers,
  ...sendChatMessageHandlers,
];
