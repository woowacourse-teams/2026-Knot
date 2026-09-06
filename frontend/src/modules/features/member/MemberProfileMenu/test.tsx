import { GetMeResponseDto } from "@api/dto/auth";
import { AUTH_LOGOUT_API_PATH } from "@api/fetch/api/v1/auth/logout";
import { meResponse } from "@api/mock/responses/auth";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import MemberProfileMenu from ".";

const LOGIN_SCREEN_TEXT = "로그인 화면";

const expectedMe = new GetMeResponseDto(meResponse);

const renderMenu = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.HOME, element: <MemberProfileMenu /> },
      { path: PATH_ROUTE.LOGIN, element: <p>{LOGIN_SCREEN_TEXT}</p> },
    ],
    { initialEntries: [PATH_ROUTE.HOME] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return {
    router,
    trigger: screen.getByRole("button", { name: "내 계정 메뉴" }),
  };
};

const openMenu = (trigger: HTMLElement) => {
  fireEvent.click(trigger);

  return screen.getByRole("menuitem", { name: "로그아웃" });
};

describe("MemberProfileMenu", () => {
  it("로그인한 회원의 프로필 이미지를 아바타로 보여준다", async () => {
    renderMenu();

    const avatar = screen.getByRole("img", { name: "내 프로필" });

    await waitFor(() => {
      expect(avatar.querySelector("img")).toHaveAttribute(
        "src",
        expectedMe.profileImageUrl,
      );
    });
  });

  it("아바타를 누르기 전에는 메뉴가 없다", () => {
    const { trigger } = renderMenu();

    expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("아바타를 누르면 로그아웃 항목이 열린다", () => {
    const { trigger } = renderMenu();

    const logoutItem = openMenu(trigger);

    expect(logoutItem).toBeInTheDocument();
    expect(trigger).toHaveAttribute("aria-expanded", "true");
  });

  it("메뉴 바깥을 누르면 닫힌다", () => {
    const { trigger } = renderMenu();

    openMenu(trigger);
    fireEvent.pointerDown(document.body);

    expect(screen.queryByRole("menuitem")).not.toBeInTheDocument();
  });

  it("로그아웃을 누르면 로그인 화면으로 옮긴다", async () => {
    const { router, trigger } = renderMenu();
    const logoutItem = openMenu(trigger);

    await act(async () => {
      fireEvent.click(logoutItem);
    });

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
    });
    expect(screen.getByText(LOGIN_SCREEN_TEXT)).toBeInTheDocument();
  });

  it("로그아웃 요청이 실패해도 로그인 화면으로 옮긴다", async () => {
    mockServer.use(
      http.post(`*${AUTH_LOGOUT_API_PATH}`, () => HttpResponse.error()),
    );
    const { router, trigger } = renderMenu();
    const logoutItem = openMenu(trigger);

    await act(async () => {
      fireEvent.click(logoutItem);
    });

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
    });
  });

  it("뒤로 가기로 로그아웃 직전 화면에 돌아가지 않는다", async () => {
    const { router, trigger } = renderMenu();
    const logoutItem = openMenu(trigger);

    await act(async () => {
      fireEvent.click(logoutItem);
    });

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.LOGIN);
    });
    expect(router.state.historyAction).toBe("REPLACE");
  });
});
