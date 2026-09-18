import { PostInvitationAcceptResponseDto } from "@api/dto/workspaceInvitation";
import { INVITATIONS_ACCEPT_API_PATH } from "@api/fetch/api/v1/invitations/accept";
import { invitationAcceptanceResponse } from "@api/mock/responses/workspaceInvitation";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceJoinCard from ".";

const expected = new PostInvitationAcceptResponseDto(
  invitationAcceptanceResponse,
);
const WORKSPACE_ID = String(expected.workspaceId);
const HOME_PATH = `/workspace/${expected.workspaceId}`;
const ELSEWHERE_PATH = "/elsewhere";
const CREDENTIAL = "X35D3S";
const WORKSPACE_NAME = "Knot 팀";
const DESCRIPTION = "초대를 수락하면 함께 기록할 수 있어요";

interface RenderCardParams {
  /** `false`면 새로고침·직접 진입처럼 라우터 state 없이 들어와요 */
  hasState?: boolean;
}

const renderCard = ({ hasState = true }: RenderCardParams = {}) => {
  const joinPath = getRouterPath({
    routeKey: "WORKSPACE_JOIN",
    params: { workspaceId: WORKSPACE_ID },
  });
  const state = hasState
    ? { credential: CREDENTIAL, workspaceName: WORKSPACE_NAME }
    : undefined;
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const router = createMemoryRouter(
    [
      { path: ELSEWHERE_PATH, element: <p>다른 화면</p> },
      { path: PATH_ROUTE.WORKSPACE_JOIN, element: <WorkspaceJoinCard /> },
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>워크스페이스 홈</p> },
      { path: PATH_ROUTE.WORKSPACE, element: <p>워크스페이스 선택</p> },
      { path: PATH_ROUTE.JOIN_ERROR, element: <p>초대 링크 오류</p> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인</p> },
    ],
    { initialEntries: [ELSEWHERE_PATH, { pathname: joinPath, state }] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router, joinPath };
};

const getJoinButton = () => screen.getByRole("button", { name: "참여할게요" });

const overrideAcceptStatus = (status: number) => {
  mockServer.use(
    http.post(
      `*${INVITATIONS_ACCEPT_API_PATH}`,
      () => new HttpResponse(null, { status }),
    ),
  );
};

/** 요청 본문을 붙잡아 두고 상태 코드를 바꿔 응답해요. 성공(200·201)이면 본문도 함께 줘요 */
const captureAcceptRequest = (status: number) => {
  const bodies: unknown[] = [];
  const isSuccess = status === 200 || status === 201;

  mockServer.use(
    http.post(`*${INVITATIONS_ACCEPT_API_PATH}`, async ({ request }) => {
      bodies.push(await request.json());

      return isSuccess
        ? HttpResponse.json(invitationAcceptanceResponse, { status })
        : new HttpResponse(null, { status });
    }),
  );

  return bodies;
};

const holdAcceptResponse = () => {
  mockServer.use(
    http.post(`*${INVITATIONS_ACCEPT_API_PATH}`, async () => {
      await delay("infinite");
      return HttpResponse.json(invitationAcceptanceResponse, { status: 201 });
    }),
  );
};

const waitForPath = (
  router: ReturnType<typeof createMemoryRouter>,
  pathname: string,
) =>
  waitFor(() => {
    expect(router.state.location.pathname).toBe(pathname);
  });

const goBack = async (router: ReturnType<typeof createMemoryRouter>) => {
  await act(async () => {
    await router.navigate(-1);
  });
};

describe("WorkspaceJoinCard", () => {
  it("state의 워크스페이스 이름과 안내 문구를 보여준다", () => {
    renderCard();

    expect(
      screen.getByRole("heading", { name: WORKSPACE_NAME }),
    ).toBeInTheDocument();
    expect(screen.getByText(DESCRIPTION)).toBeInTheDocument();
    expect(getJoinButton()).toBeEnabled();
  });

  it.each([200, 201] as const)(
    "참여할게요를 누르면 state의 credential로 요청하고 %i이면 응답의 workspaceId로 홈에 이동한다",
    async (status) => {
      const bodies = captureAcceptRequest(status);
      const { router } = renderCard();

      fireEvent.click(getJoinButton());

      await waitForPath(router, HOME_PATH);
      expect(bodies).toEqual([{ credential: CREDENTIAL }]);
    },
  );

  it("요청 중에는 버튼이 로딩 상태로 잠긴다", async () => {
    holdAcceptResponse();
    renderCard();

    fireEvent.click(getJoinButton());

    await waitFor(() => {
      expect(getJoinButton()).toHaveAttribute("aria-busy", "true");
    });
    expect(getJoinButton()).toBeDisabled();
  });

  it.each([404, 429])(
    "참여가 %i면 초대 링크 오류 화면으로 replace 이동한다",
    async (status) => {
      overrideAcceptStatus(status);
      const { router } = renderCard();

      fireEvent.click(getJoinButton());

      await waitForPath(router, PATH_ROUTE.JOIN_ERROR);
      await goBack(router);

      expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
    },
  );

  it("401이면 로그인 화면으로 replace 이동한다", async () => {
    overrideAcceptStatus(401);
    const { router } = renderCard();

    fireEvent.click(getJoinButton());

    await waitForPath(router, PATH_ROUTE.LOGIN);
    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("그 외 실패면 이동하지 않고 버튼을 다시 연다", async () => {
    const bodies = captureAcceptRequest(500);
    const { router, joinPath } = renderCard();

    fireEvent.click(getJoinButton());

    await waitFor(() => {
      expect(bodies).toHaveLength(1);
    });
    await waitFor(() => {
      expect(getJoinButton()).toBeEnabled();
    });
    expect(getJoinButton()).toHaveAttribute("aria-busy", "false");
    expect(router.state.location.pathname).toBe(joinPath);
  });

  it("state 없이 들어오면 아무것도 그리지 않고 워크스페이스 선택 화면으로 replace 이동한다", async () => {
    const { router } = renderCard({ hasState: false });

    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "참여할게요" }),
    ).not.toBeInTheDocument();

    await waitForPath(router, PATH_ROUTE.WORKSPACE);
    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });
});
