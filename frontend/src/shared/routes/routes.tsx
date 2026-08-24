import JoinErrorPage from "@pages/join-error";
import LoginPage from "@pages/login";
import OnboardingPage from "@pages/onboarding";
import OnboardingCompletePage from "@pages/onboarding/complete";
import WorkspacePage from "@pages/workspace";
import WorkspaceHomePage from "@pages/workspace/[workspaceId]";
import ChatPage from "@pages/workspace/[workspaceId]/chat";
import WorkspaceInvitePage from "@pages/workspace/[workspaceId]/invite";
import WorkspaceJoinPage from "@pages/workspace/[workspaceId]/join";
import WorkspaceNotionConnectionPage from "@pages/workspace/[workspaceId]/notion-connection";
import WorkspaceCodePage from "@pages/workspace/code";
import WorkspaceCreatePage from "@pages/workspace/create";
import { createBrowserRouter } from "react-router";

import { PATH_ROUTE } from "./PATH_ROUTE";

export const router = createBrowserRouter([
  {
    path: PATH_ROUTE.HOME,
    element: <div>Hello World</div>,
  },
  {
    path: PATH_ROUTE.LOGIN,
    element: <LoginPage />,
  },
  {
    path: PATH_ROUTE.ONBOARDING,
    element: <OnboardingPage />,
  },
  {
    path: PATH_ROUTE.ONBOARDING_COMPLETE,
    element: <OnboardingCompletePage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE,
    element: <WorkspacePage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_CREATE,
    element: <WorkspaceCreatePage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_CODE,
    element: <WorkspaceCodePage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_HOME,
    element: <WorkspaceHomePage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_INVITE,
    element: <WorkspaceInvitePage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_NOTION_CONNECTION,
    element: <WorkspaceNotionConnectionPage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_JOIN,
    element: <WorkspaceJoinPage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_CHAT,
    element: <ChatPage />,
  },
  {
    path: PATH_ROUTE.WORKSPACE_CHAT_SESSION,
    element: <ChatPage />,
  },
  {
    path: PATH_ROUTE.JOIN_ERROR,
    element: <JoinErrorPage />,
  },
]);
