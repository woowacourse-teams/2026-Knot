import { ThemeProvider } from "@emotion/react";
import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { WORKSPACES_API_PATH } from "@api/fetch/api/v1/workspaces";
import { mockServer } from "@api/mock/server";
import { theme } from "@provider/themeProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import { PATH_ROUTE } from "../PATH_ROUTE";

import EntryRedirect from ".";

const renderEntry = () => {
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.HOME, element: <EntryRedirect /> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인 화면</p> },
      { path: PATH_ROUTE.WORKSPACE, element: <p>워크스페이스 생성 및 참여</p> },
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>워크스페이스 홈</p> },
    ],
    { initialEntries: [PATH_ROUTE.HOME] },
  );

  // 테스트마다 새 클라이언트를 써서 앞선 테스트의 캐시를 물려받지 않아요
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router };
};

describe("EntryRedirect", () => {
  it("판정하는 동안 로딩을 알린다", () => {
    renderEntry();

    expect(screen.getByRole("status")).toBeInTheDocument();
  });

  it("로그인하지 않았으면 로그인 화면으로 보낸다", async () => {
    mockServer.use(
      http.get(`*${AUTH_ME_API_PATH}`, () => new HttpResponse(null, { status: 401 })),
    );

    const { router } = renderEntry();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
    });
  });

  it("마지막으로 본 워크스페이스가 있으면 그 워크스페이스 홈으로 보낸다", async () => {
    const { router } = renderEntry();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/workspace/1");
    });
  });

  it("첫 워크스페이스로 보낼 때는 목록의 첫 워크스페이스를 쓴다", async () => {
    mockServer.use(
      http.get(`*${WORKSPACES_API_PATH}`, () =>
        HttpResponse.json({
          lastViewedWorkspaceId: null,
          workspaces: [{ id: 42, name: "Knot 팀" }],
        }),
      ),
    );

    const { router } = renderEntry();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/workspace/42");
    });
  });

  it("속한 워크스페이스가 없으면 워크스페이스 생성 및 참여 화면으로 보낸다", async () => {
    mockServer.use(
      http.get(`*${WORKSPACES_API_PATH}`, () =>
        HttpResponse.json({ lastViewedWorkspaceId: null, workspaces: [] }),
      ),
    );

    const { router } = renderEntry();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE);
    });
  });

  it("워크스페이스 목록 조회가 실패하면 화면을 옮기지 않고 다시 시도를 안내한다", async () => {
    mockServer.use(
      http.get(`*${WORKSPACES_API_PATH}`, () => new HttpResponse(null, { status: 500 })),
    );

    const { router } = renderEntry();

    expect(
      await screen.findByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PATH_ROUTE.HOME);
  });
});
