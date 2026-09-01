import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import WorkspaceInviteLinkGate from ".";

const ELSEWHERE_PATH = "/elsewhere";
const VERIFY_DELAY_MS = 800;
const STATUS_TEXT = "초대 링크를 확인하고 있어요";
// TODO(#243): 미리보기 API 연결 후 msw 응답으로 교체
const VALID_TOKEN = "Xk3vQ9mZp2LrT7wB1nHc4A";
const INVALID_TOKEN = "Xk3vQ9mZp2LrT7wB1nHc4B";

const renderGate = (token: string) => {
  const invitePath = getRouterPath({ routeKey: "INVITE", params: { token } });
  const router = createMemoryRouter(
    [
      { path: ELSEWHERE_PATH, element: <p>다른 화면</p> },
      { path: PATH_ROUTE.INVITE, element: <WorkspaceInviteLinkGate /> },
      { path: PATH_ROUTE.WORKSPACE_JOIN, element: <p>입장 확인</p> },
      { path: PATH_ROUTE.JOIN_ERROR, element: <p>초대 링크 오류</p> },
    ],
    { initialEntries: [ELSEWHERE_PATH, invitePath], initialIndex: 1 },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  return { router, invitePath };
};

const advanceTimers = async (ms: number) => {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
};

const finishVerification = async () => {
  await act(async () => {
    vi.runAllTimers();
  });
};

const goBack = async (router: ReturnType<typeof createMemoryRouter>) => {
  await act(async () => {
    await router.navigate(-1);
  });
};

describe("WorkspaceInviteLinkGate", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("판정 중에는 스피너와 확인 중 안내를 보여준다", () => {
    const { router, invitePath } = renderGate(VALID_TOKEN);

    const status = screen.getByRole("status");

    expect(status).toHaveTextContent(STATUS_TEXT);
    expect(status.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(invitePath);
  });

  it("지연 뒤 임시 linkToken과 같은 토큰이면 임시 workspaceId로 입장 확인 화면에 이동한다", async () => {
    const { router, invitePath } = renderGate(VALID_TOKEN);

    await advanceTimers(VERIFY_DELAY_MS - 1);

    expect(router.state.location.pathname).toBe(invitePath);

    await advanceTimers(1);

    expect(router.state.location.pathname).toBe("/workspace/temp/join");
  });

  it("통과 이동은 replace라 뒤로 가기 때 진입 라우트로 돌아오지 않는다", async () => {
    const { router } = renderGate(VALID_TOKEN);

    await finishVerification();
    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("지연 뒤 다른 토큰이면 초대 링크 오류 화면에 이동한다", async () => {
    const { router } = renderGate(INVALID_TOKEN);

    await finishVerification();

    expect(router.state.location.pathname).toBe(PATH_ROUTE.JOIN_ERROR);
  });

  it("실패 이동도 replace라 뒤로 가기 때 진입 라우트로 돌아오지 않는다", async () => {
    const { router } = renderGate(INVALID_TOKEN);

    await finishVerification();
    await goBack(router);

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });

  it("대소문자만 다른 토큰은 정규화하지 않고 실패로 판정한다", async () => {
    const lowerCasedToken = VALID_TOKEN.toLowerCase();
    expect(lowerCasedToken).not.toBe(VALID_TOKEN);

    const { router } = renderGate(lowerCasedToken);

    await finishVerification();

    expect(router.state.location.pathname).toBe(PATH_ROUTE.JOIN_ERROR);
  });

  it("판정 중 페이지를 벗어나면 이동을 실행하지 않는다", async () => {
    const { router } = renderGate(VALID_TOKEN);

    await act(async () => {
      await router.navigate(ELSEWHERE_PATH);
    });
    await finishVerification();

    expect(router.state.location.pathname).toBe(ELSEWHERE_PATH);
  });
});
