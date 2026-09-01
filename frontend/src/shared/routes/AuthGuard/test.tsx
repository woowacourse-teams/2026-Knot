import { ThemeProvider } from "@emotion/react";
import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { mockServer } from "@api/mock/server";
import { theme } from "@provider/themeProvider";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import { PATH_ROUTE } from "../PATH_ROUTE";

import AuthGuard from ".";

const PROTECTED_TEXT = "워크스페이스 생성 및 참여";

const renderGuard = () => {
  const router = createMemoryRouter(
    [
      {
        element: <AuthGuard />,
        children: [
          { path: PATH_ROUTE.WORKSPACE, element: <p>{PROTECTED_TEXT}</p> },
        ],
      },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인 화면</p> },
    ],
    { initialEntries: [PATH_ROUTE.WORKSPACE] },
  );

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

describe("AuthGuard", () => {
  it("확인하는 동안에는 보호 화면을 보여주지 않는다", () => {
    renderGuard();

    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(screen.queryByText(PROTECTED_TEXT)).not.toBeInTheDocument();
  });

  it("로그인한 사용자에게는 보호 화면을 보여준다", async () => {
    renderGuard();

    expect(await screen.findByText(PROTECTED_TEXT)).toBeInTheDocument();
  });

  it("로그인하지 않았으면 로그인 화면으로 보낸다", async () => {
    mockServer.use(
      http.get(
        `*${AUTH_ME_API_PATH}`,
        () => new HttpResponse(null, { status: 401 }),
      ),
    );

    const { router } = renderGuard();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
    });
  });

  it("로그인 여부를 확인하지 못하면 로그인으로 보내지 않고 다시 시도를 안내한다", async () => {
    mockServer.use(
      http.get(
        `*${AUTH_ME_API_PATH}`,
        () => new HttpResponse(null, { status: 500 }),
      ),
    );

    const { router } = renderGuard();

    expect(
      await screen.findByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE);
  });
});
