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
import { createBrowserRouter } from "react-router";

import AuthGuard from "./AuthGuard";
import EntryRedirect from "./EntryRedirect";
import GuestGuard from "./GuestGuard";
import { PATH_ROUTE } from "./PATH_ROUTE";

/**
 * 로그인 여부로 나뉘는 세 구역이 있어요.
 *
 * - 누구나: 로그인, 온보딩, 초대 링크 판정과 초대 오류
 * - 로그인한 사람만(`AuthGuard`): 워크스페이스 관련 화면 전부
 * - 로그인하지 않은 사람용(`GuestGuard`): 로그인 화면
 *
 * 온보딩(`/onboarding`)은 닉네임을 아직 정하지 않아 `/api/v1/auth/me`가 401인 상태에서
 * 열어야 하므로 `AuthGuard`로 감싸지 않아요. 가입 완료(`/onboarding/complete`)는
 * 방금 등록한 닉네임을 라우터 state로 받아야 열리므로 자체 검사로 충분합니다.
 *
 * 가드는 레이아웃 안쪽에 둬요. 판정 중 로딩과 실패 안내가 로고·GNB가 있는 자리에서
 * 보여야 화면이 비어 보이지 않기 때문이에요.
 */
export const router = createBrowserRouter([
  // 화면 가운데 정렬 — 워크스페이스 진입 전 플로우
  {
    element: <CenteredLayout />,
    children: [
      {
        path: PATH_ROUTE.HOME,
        element: <EntryRedirect />,
      },
      {
        element: <GuestGuard />,
        children: [
          {
            path: PATH_ROUTE.LOGIN,
            element: <LoginPage />,
          },
        ],
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
        path: PATH_ROUTE.INVITE,
        element: <InvitePage />,
      },
      {
        path: PATH_ROUTE.JOIN_ERROR,
        element: <JoinErrorPage />,
      },
      {
        element: <AuthGuard />,
        children: [
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
            path: PATH_ROUTE.WORKSPACE_JOIN,
            element: <WorkspaceJoinPage />,
          },
        ],
      },
    ],
  },

  // GNB — 워크스페이스 입장 후
  {
    element: <WorkspaceLayout />,
    children: [
      {
        element: <AuthGuard />,
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
    ],
  },
]);
