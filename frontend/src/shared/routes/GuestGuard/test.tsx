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

import GuestGuard from ".";

const LOGIN_TEXT = "로그인 화면";

const renderGuard = () => {
  const router = createMemoryRouter(
    [
      {
        element: <GuestGuard />,
        children: [{ path: PATH_ROUTE.LOGIN, element: <p>{LOGIN_TEXT}</p> }],
      },
      { path: PATH_ROUTE.HOME, element: <p>진입 분기</p> },
    ],
    { initialEntries: [PATH_ROUTE.LOGIN] },
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

describe("GuestGuard", () => {
  it("로그인 여부를 확인하는 동안에도 로그인 화면을 바로 보여준다", () => {
    renderGuard();

    expect(screen.getByText(LOGIN_TEXT)).toBeInTheDocument();
  });

  it("로그인하지 않은 사용자는 로그인 화면에 머문다", async () => {
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
    expect(screen.getByText(LOGIN_TEXT)).toBeInTheDocument();
  });

  it("이미 로그인한 사용자는 홈 경로의 진입 분기로 보낸다", async () => {
    const { router } = renderGuard();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.HOME);
    });
  });

  it("로그인 여부를 확인하지 못해도 로그인 화면에 머문다", async () => {
    mockServer.use(
      http.get(
        `*${AUTH_ME_API_PATH}`,
        () => new HttpResponse(null, { status: 500 }),
      ),
    );

    const { router } = renderGuard();

    await waitFor(() => {
      expect(screen.getByText(LOGIN_TEXT)).toBeInTheDocument();
    });
    expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
  });
});
