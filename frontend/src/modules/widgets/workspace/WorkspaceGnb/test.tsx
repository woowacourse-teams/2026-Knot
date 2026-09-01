import { ThemeProvider } from "@emotion/react";
import { WorkspaceSidebarProvider } from "@provider/context/workspaceSidebarContext";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import WorkspaceSidebar from "@widgets/workspace/WorkspaceSidebar";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceGnb from ".";

const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: "1" },
});

// 사이드바가 현재 `:workspaceId`의 워크스페이스를 조회하므로 라우터와 QueryClient를 함께 감싸요
const renderGnb = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      {
        path: PATH_ROUTE.WORKSPACE_HOME,
        element: (
          <WorkspaceSidebarProvider>
            <WorkspaceGnb />
            <WorkspaceSidebar />
          </WorkspaceSidebarProvider>
        ),
      },
    ],
    { initialEntries: [HOME_PATH] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  const toggleButton = screen.getByRole("button", { name: "사이드바" });

  return { toggleButton };
};

describe("WorkspaceGnb", () => {
  it("처음에는 사이드바가 닫혀 있고 토글 버튼이 접힘 상태를 알린다", () => {
    const { toggleButton } = renderGnb();

    expect(toggleButton).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
  });

  it("토글 버튼을 누르면 사이드바가 열리고 펼침 상태를 알린다", () => {
    const { toggleButton } = renderGnb();

    fireEvent.click(toggleButton);

    expect(toggleButton).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByRole("complementary")).toBeInTheDocument();
  });

  it("열린 상태에서 다시 누르면 사이드바가 닫힌다", () => {
    const { toggleButton } = renderGnb();

    fireEvent.click(toggleButton);
    fireEvent.click(toggleButton);

    expect(toggleButton).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
  });

  it("우측에 프로필 아바타를 보여준다", () => {
    renderGnb();

    expect(screen.getByRole("img", { name: "내 프로필" })).toBeInTheDocument();
  });
});
