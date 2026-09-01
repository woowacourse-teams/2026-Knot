import CenteredLayout from "@pages/_layout/CenteredLayout";
import WorkspaceLayout from "@pages/_layout/WorkspaceLayout";
import InvitePage from "@pages/invite/[token]";
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
import { createBrowserRouter, replace } from "react-router";

import { PATH_ROUTE } from "./PATH_ROUTE";

export const router = createBrowserRouter([
  {
    path: PATH_ROUTE.HOME,
    loader: () => replace(PATH_ROUTE.LOGIN),
  },

  // 화면 가운데 정렬 — 워크스페이스 진입 전 플로우
  {
    element: <CenteredLayout />,
    children: [
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
        path: PATH_ROUTE.WORKSPACE_INVITE,
        element: <WorkspaceInvitePage />,
      },
      {
        path: PATH_ROUTE.WORKSPACE_CODE,
        element: <WorkspaceCodePage />,
      },
      {
        path: PATH_ROUTE.INVITE,
        element: <InvitePage />,
      },
      {
        path: PATH_ROUTE.WORKSPACE_JOIN,
        element: <WorkspaceJoinPage />,
      },
      {
        path: PATH_ROUTE.JOIN_ERROR,
        element: <JoinErrorPage />,
      },
    ],
  },

  // GNB — 워크스페이스 입장 후
  {
    element: <WorkspaceLayout />,
    children: [
      {
        path: PATH_ROUTE.WORKSPACE_HOME,
        element: <WorkspaceHomePage />,
      },
      {
        path: PATH_ROUTE.WORKSPACE_NOTION_CONNECTION,
        element: <WorkspaceNotionConnectionPage />,
      },
      {
        path: PATH_ROUTE.CHAT,
        element: <ChatPage />,
      },
      {
        path: PATH_ROUTE.CHAT_SESSION,
        element: <ChatPage />,
      },
    ],
  },
]);
