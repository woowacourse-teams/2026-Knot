import { LAST_VIEWED_WORKSPACE_API_PATH } from "@api/fetch/api/v1/members/me/lastViewedWorkspace";
import { workspaceDetailResponse } from "@api/mock/responses/workspace";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceLayout from ".";
import { WORKSPACE_DOCK_RAIL_ID } from "./constants/dockRail";

const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const ELSEWHERE_PATH = "/elsewhere";
const HOME_TEXT = "홈 본문";
const CHAT_TEXT = "탐색 본문";
// 경로 파라미터가 있어 fetch 상수 대신 mock 핸들러와 같은 패턴을 적어요
const WORKSPACE_PATH_PATTERN = "*/api/v1/workspaces/:workspaceId";

const renderLayout = (initialPath = HOME_PATH) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const router = createMemoryRouter(
    [
      { path: ELSEWHERE_PATH, element: <p>다른 화면</p> },
      {
        element: <WorkspaceLayout />,
        children: [
          { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>{HOME_TEXT}</p> },
          { path: PATH_ROUTE.CHAT, element: <p>{CHAT_TEXT}</p> },
        ],
      },
      { path: PATH_ROUTE.WORKSPACE, element: <p>워크스페이스 선택</p> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인</p> },
    ],
    { initialEntries: [ELSEWHERE_PATH, initialPath] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router };
};

const overrideWorkspaceStatus = (status: number) => {
  mockServer.use(
    http.get(WORKSPACE_PATH_PATTERN, () => new HttpResponse(null, { status })),
  );
};

const holdWorkspaceResponse = () => {
  mockServer.use(
    http.get(WORKSPACE_PATH_PATTERN, async () => {
      await delay("infinite");
      return HttpResponse.json(workspaceDetailResponse);
    }),
  );
};

/** 마지막으로 본 워크스페이스 갱신 요청의 본문을 모아 호출 횟수와 값을 확인해요 */
const captureLastViewedRequests = () => {
  const bodies: unknown[] = [];

  mockServer.use(
    http.put(`*${LAST_VIEWED_WORKSPACE_API_PATH}`, async ({ request }) => {
      bodies.push(await request.json());
      return new HttpResponse(null, { status: 204 });
    }),
  );

  return bodies;
};

const waitForPath = (
  router: ReturnType<typeof createMemoryRouter>,
  pathname: string,
) =>
  waitFor(() => {
    expect(router.state.location.pathname).toBe(pathname);
  });

const waitRealTime = async (ms: number) => {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, ms));
  });
};

const goBack = async (router: ReturnType<typeof createMemoryRouter>) => {
  await act(async () => {
    await router.navigate(-1);
  });
};

describe("WorkspaceLayout", () => {
  it("워크스페이스를 조회하는 동안에는 본문 대신 스피너를 보여준다", () => {
    holdWorkspaceResponse();
    renderLayout();

    expect(
      screen.getByRole("status", { name: "워크스페이스를 불러오고 있어요" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText(HOME_TEXT)).not.toBeInTheDocument();
  });

  it("조회에 성공하면 본문을 보여주고 마지막으로 본 워크스페이스를 한 번 갱신한다", async () => {
    const bodies = captureLastViewedRequests();
    renderLayout();

    expect(await screen.findByText(HOME_TEXT)).toBeInTheDocument();
    expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "false");
    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(bodies).toEqual([{ workspaceId: WORKSPACE_ID }]);
    });
    await waitRealTime(100);
    expect(bodies).toHaveLength(1);
  });

  it("하위 라우트(탐색)로 들어와도 같은 판정을 거쳐 갱신한다", async () => {
    const bodies = captureLastViewedRequests();
    renderLayout(CHAT_PATH);

    expect(await screen.findByText(CHAT_TEXT)).toBeInTheDocument();
    await waitFor(() => {
      expect(bodies).toEqual([{ workspaceId: WORKSPACE_ID }]);
    });
  });

  it("갱신에 실패해도 본문은 그대로 보여준다", async () => {
    mockServer.use(
      http.put(
        `*${LAST_VIEWED_WORKSPACE_API_PATH}`,
        () => new HttpResponse(null, { status: 500 }),
      ),
    );
    const { router } = renderLayout();

    expect(await screen.findByText(HOME_TEXT)).toBeInTheDocument();
    await waitRealTime(100);

    expect(screen.getByText(HOME_TEXT)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(HOME_PATH);
  });

  it("401이면 로그인 화면으로 replace 이동한다", async () => {
    overrideWorkspaceStatus(401);
    const { router } = renderLayout();

    await waitForPath(router, PATH_ROUTE.LOGIN);
    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it.each([403, 404])(
    "%i면 워크스페이스 선택 화면으로 replace 이동한다",
    async (status) => {
      overrideWorkspaceStatus(status);
      const { router } = renderLayout();

      await waitForPath(router, PATH_ROUTE.WORKSPACE);
      await goBack(router);

      expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
    },
  );

  it("workspaceId가 정수가 아니면 조회하지 않고 워크스페이스 선택 화면으로 replace 이동한다", async () => {
    const { router } = renderLayout(
      getRouterPath({
        routeKey: "WORKSPACE_HOME",
        params: { workspaceId: "abc" },
      }),
    );

    await waitForPath(router, PATH_ROUTE.WORKSPACE);
    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("대화 목록 버튼은 탐색 화면에서만 둔다", async () => {
    renderLayout();

    expect(await screen.findByText(HOME_TEXT)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "대화 목록" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "사이드바" })).toBeInTheDocument();
  });

  it("탐색 화면에서는 사이드바와 대화 목록을 둘 다 열 수 있다", async () => {
    renderLayout(CHAT_PATH);

    expect(await screen.findByText(CHAT_TEXT)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "사이드바" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "대화 목록" }),
    ).toBeInTheDocument();
  });

  it("사이드바 버튼을 누르면 사이드바가 왼쪽 레일로 옮겨 가 자리를 차지한다", async () => {
    renderLayout();

    expect(await screen.findByText(HOME_TEXT)).toBeInTheDocument();
    const rail = document.getElementById(WORKSPACE_DOCK_RAIL_ID);

    expect(rail).toBeEmptyDOMElement();

    fireEvent.click(screen.getByRole("button", { name: "사이드바" }));

    expect(rail).toContainElement(
      screen.getByRole("complementary", { name: "워크스페이스 사이드바" }),
    );
  });

  it("그 외 실패면 이동하지 않고 스피너를 유지한다", async () => {
    overrideWorkspaceStatus(500);
    const { router } = renderLayout();

    await waitRealTime(200);

    expect(router.state.location.pathname).toBe(HOME_PATH);
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.queryByText(HOME_TEXT)).not.toBeInTheDocument();
  });
});
