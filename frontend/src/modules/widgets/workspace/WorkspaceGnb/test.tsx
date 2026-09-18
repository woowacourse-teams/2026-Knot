import { GetMeResponseDto } from "@api/dto/auth";
import { meResponse } from "@api/mock/responses/auth";
import DockablePanel from "@composites/DockablePanel";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceGnb from ".";

const WORKSPACE_ID = "1";
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const DOCK_RAIL_ID = "test-dock-rail";
const PANEL_TEXT = "패널 내용";

const expectedMe = new GetMeResponseDto(meResponse);

const renderGnb = (initialPath = HOME_PATH) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const element = (
    <>
      <div id={DOCK_RAIL_ID} />
      <WorkspaceGnb>
        <DockablePanel
          label="사이드바"
          icon={<span />}
          dockTargetId={DOCK_RAIL_ID}
        >
          <p>{PANEL_TEXT}</p>
        </DockablePanel>
      </WorkspaceGnb>
    </>
  );
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.WORKSPACE_HOME, element },
      { path: PATH_ROUTE.CHAT, element },
    ],
    { initialEntries: [initialPath] },
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
    homeButton: screen.getByRole("button", { name: "홈" }),
    exploreButton: screen.getByRole("button", { name: "탐색" }),
    panelTrigger: screen.getByRole("button", { name: "사이드바" }),
  };
};

describe("WorkspaceGnb", () => {
  it("홈 화면에서는 내비 필의 홈만 현재 화면으로 표시한다", () => {
    const { homeButton, exploreButton } = renderGnb();

    expect(homeButton).toHaveAttribute("aria-current", "page");
    expect(exploreButton).not.toHaveAttribute("aria-current");
  });

  it("탐색을 누르면 탐색 화면으로 이동하고 표시가 옮겨간다", async () => {
    const { router, exploreButton } = renderGnb();

    await act(async () => {
      fireEvent.click(exploreButton);
    });

    expect(router.state.location.pathname).toBe(CHAT_PATH);
    expect(screen.getByRole("button", { name: "탐색" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByRole("button", { name: "홈" })).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("이미 있는 화면의 버튼은 눌러도 이동하지 않는다", async () => {
    const { router, homeButton } = renderGnb();

    await act(async () => {
      fireEvent.click(homeButton);
    });

    expect(router.state.historyAction).toBe("POP");
    expect(router.state.location.pathname).toBe(HOME_PATH);
  });

  it("우측에 로그인한 회원의 프로필 이미지를 아바타로 보여준다", async () => {
    renderGnb();

    const avatar = screen.getByRole("img", { name: "내 프로필" });

    await waitFor(() => {
      expect(avatar.querySelector("img")).toHaveAttribute(
        "src",
        expectedMe.profileImageUrl,
      );
    });
  });

  it("좌측 패널 트리거에 포인터를 얹으면 패널이 겹쳐 뜬다", () => {
    const { panelTrigger } = renderGnb();

    expect(screen.queryByText(PANEL_TEXT)).not.toBeInTheDocument();

    fireEvent.pointerOver(panelTrigger);

    expect(screen.getByText(PANEL_TEXT)).toBeInTheDocument();
    expect(panelTrigger).toHaveAttribute("aria-expanded", "true");
  });

  it("좌측 패널 트리거를 누르면 패널이 고정 자리로 옮겨 간다", () => {
    const { panelTrigger } = renderGnb();

    fireEvent.click(panelTrigger);

    expect(panelTrigger).toHaveAttribute("aria-pressed", "true");
    expect(document.getElementById(DOCK_RAIL_ID)).toContainElement(
      screen.getByText(PANEL_TEXT),
    );
  });

  it("고정된 패널은 다시 누르면 접힌다", () => {
    const { panelTrigger } = renderGnb();

    fireEvent.click(panelTrigger);
    fireEvent.click(panelTrigger);

    expect(panelTrigger).toHaveAttribute("aria-pressed", "false");
    expect(screen.queryByText(PANEL_TEXT)).not.toBeInTheDocument();
  });
});
