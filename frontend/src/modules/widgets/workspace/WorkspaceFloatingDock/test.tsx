import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceFloatingDock from ".";

const WORKSPACE_ID = "temp";
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});

const renderDock = (hasActiveChatSession = false) => {
  const router = createMemoryRouter(
    [
      {
        path: PATH_ROUTE.WORKSPACE_HOME,
        element: (
          <WorkspaceFloatingDock hasActiveChatSession={hasActiveChatSession} />
        ),
      },
      { path: PATH_ROUTE.CHAT, element: <p>탐색 화면</p> },
    ],
    { initialEntries: [HOME_PATH] },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  const homeButton = screen.getByRole("button", { name: "홈" });
  const exploreButton = screen.getByRole("button", { name: /^탐색/ });

  return { router, homeButton, exploreButton };
};

describe("WorkspaceFloatingDock", () => {
  it("홈 라우트에서는 홈 슬롯만 현재 화면으로 표시한다", () => {
    const { homeButton, exploreButton } = renderDock();

    expect(homeButton).toHaveAttribute("aria-current", "page");
    expect(exploreButton).not.toHaveAttribute("aria-current");
  });

  it("탐색을 누르면 /workspace/:workspaceId/chat으로 이동한다", async () => {
    const { router, exploreButton } = renderDock();

    await act(async () => {
      fireEvent.click(exploreButton);
    });

    expect(router.state.location.pathname).toBe(CHAT_PATH);
    expect(screen.getByText("탐색 화면")).toBeInTheDocument();
  });

  it("탐색 이동은 push라 뒤로 가기 때 홈으로 돌아온다", async () => {
    const { router, exploreButton } = renderDock();

    await act(async () => {
      fireEvent.click(exploreButton);
    });
    await act(async () => {
      await router.navigate(-1);
    });

    expect(router.state.location.pathname).toBe(HOME_PATH);
  });

  it("진행 중인 대화가 있으면 탐색 슬롯에 진행 중 표시를 그린다", () => {
    const { exploreButton } = renderDock(true);

    expect(exploreButton).toHaveTextContent("진행 중인 대화 있음");
  });

  it("진행 중인 대화가 없으면 진행 중 표시를 그리지 않는다", () => {
    const { exploreButton } = renderDock(false);

    expect(exploreButton).not.toHaveTextContent("진행 중인 대화 있음");
  });
});
