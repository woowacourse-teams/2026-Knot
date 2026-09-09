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
import { afterEach, describe, expect, it, vi } from "vitest";

import MemberProfileMenu from ".";

const LOGIN_SCREEN_TEXT = "로그인 화면";
const AGENT_CONNECTION_SCREEN_TEXT = "CLI 에이전트 연결 화면";
const AGENT_MENU_ITEM = "CLI 에이전트 연결";
const CLAUDE_SUBSCRIPTION_SCREEN_TEXT = "Claude 구독 화면";
const CLAUDE_SUBSCRIPTION_MENU_ITEM = "Claude 구독";

/** 로컬 MCP 서버를 띄우는 데스크톱 셸을 흉내 내요. `agent`가 있어야 연결 항목이 보여요 */
const stubDesktopWithAgent = () => {
  window.knotDesktop = {
    version: "0.1.0",
    platform: "darwin",
    env: "local",
    openExternal: vi.fn(() => Promise.resolve()),
    onDeepLink: vi.fn(() => () => undefined),
    getPendingDeepLink: vi.fn(() => Promise.resolve(null)),
    agent: {
      getStatus: vi.fn(),
      copyRegistration: vi.fn(),
      rotateToken: vi.fn(),
      setPort: vi.fn(),
    },
  };
};

/** 사용자 구독으로 답하는 데스크톱 셸을 흉내 내요. `llm`이 있어야 구독 항목이 보여요 */
const stubDesktopWithLlm = () => {
  window.knotDesktop = {
    version: "0.1.0",
    platform: "darwin",
    env: "local",
    openExternal: vi.fn(() => Promise.resolve()),
    onDeepLink: vi.fn(() => () => undefined),
    getPendingDeepLink: vi.fn(() => Promise.resolve(null)),
    llm: {
      getStatus: vi.fn(),
      signIn: vi.fn(),
      signOut: vi.fn(),
      onStatusChanged: vi.fn(() => () => undefined),
      streamAnswer: vi.fn(() => () => undefined),
      getSettings: vi.fn(),
      updateSettings: vi.fn(),
    },
  };
};

const expectedMe = new GetMeResponseDto(meResponse);

const renderMenu = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.HOME, element: <MemberProfileMenu /> },
      { path: PATH_ROUTE.LOGIN, element: <p>{LOGIN_SCREEN_TEXT}</p> },
      {
        path: PATH_ROUTE.AGENT_CONNECTION,
        element: <p>{AGENT_CONNECTION_SCREEN_TEXT}</p>,
      },
      {
        path: PATH_ROUTE.CLAUDE_SUBSCRIPTION,
        element: <p>{CLAUDE_SUBSCRIPTION_SCREEN_TEXT}</p>,
      },
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
  afterEach(() => {
    delete window.knotDesktop;
  });

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

  it("브라우저에서는 CLI 에이전트 연결 항목이 없다", () => {
    const { trigger } = renderMenu();

    openMenu(trigger);

    expect(
      screen.queryByRole("menuitem", { name: AGENT_MENU_ITEM }),
    ).not.toBeInTheDocument();
  });

  it("데스크톱 셸에서는 CLI 에이전트 연결 항목이 보이고, 누르면 연결 화면으로 옮긴다", async () => {
    stubDesktopWithAgent();
    const { router, trigger } = renderMenu();

    openMenu(trigger);
    fireEvent.click(screen.getByRole("menuitem", { name: AGENT_MENU_ITEM }));

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.AGENT_CONNECTION);
    });
    expect(screen.getByText(AGENT_CONNECTION_SCREEN_TEXT)).toBeInTheDocument();
  });

  it("브라우저에서는 Claude 구독 항목이 없다", () => {
    const { trigger } = renderMenu();

    openMenu(trigger);

    expect(
      screen.queryByRole("menuitem", { name: CLAUDE_SUBSCRIPTION_MENU_ITEM }),
    ).not.toBeInTheDocument();
  });

  it("데스크톱 셸에서는 Claude 구독 항목이 보이고, 누르면 구독 화면으로 옮긴다", async () => {
    stubDesktopWithLlm();
    const { router, trigger } = renderMenu();

    openMenu(trigger);
    fireEvent.click(
      screen.getByRole("menuitem", { name: CLAUDE_SUBSCRIPTION_MENU_ITEM }),
    );

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(
        PATH_ROUTE.CLAUDE_SUBSCRIPTION,
      );
    });
    expect(
      screen.getByText(CLAUDE_SUBSCRIPTION_SCREEN_TEXT),
    ).toBeInTheDocument();
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
