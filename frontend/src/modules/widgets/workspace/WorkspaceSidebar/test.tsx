import { GetWorkspaceResponseDto } from "@api/dto/workspace";
import { workspaceDetailResponse } from "@api/mock/responses/workspace";
import { ThemeProvider } from "@emotion/react";
import { WorkspaceSidebarProvider } from "@provider/context/workspaceSidebarContext";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import WorkspaceGnb from "@widgets/workspace/WorkspaceGnb";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceSidebar from ".";

const expected = new GetWorkspaceResponseDto(workspaceDetailResponse);
const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});

const renderSidebar = () => {
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

const openSidebar = () => {
  const { toggleButton } = renderSidebar();

  fireEvent.click(toggleButton);

  return screen.getByRole("complementary");
};

const getFolderRow = (name: string) =>
  screen.getByRole("button", { name: new RegExp(`^${name}`) });

describe("WorkspaceSidebar", () => {
  it("닫혀 있으면 아무것도 그리지 않는다", () => {
    renderSidebar();

    expect(screen.queryByRole("complementary")).not.toBeInTheDocument();
    expect(screen.queryByText(expected.name)).not.toBeInTheDocument();
  });

  it("열리면 조회 응답의 워크스페이스 이름, 폴더 라벨, 임시 트리와 동기화 안내를 보여준다", async () => {
    const sidebar = openSidebar();

    expect(await within(sidebar).findByText(expected.name)).toBeInTheDocument();
    expect(within(sidebar).getByText("폴더")).toBeInTheDocument();

    expect(within(getFolderRow("제품")).getByText("24")).toBeInTheDocument();
    expect(within(getFolderRow("로드맵")).getByText("7")).toBeInTheDocument();
    expect(within(sidebar).getByText("2026 H2 로드맵")).toBeInTheDocument();
    expect(within(getFolderRow("스펙")).getByText("5")).toBeInTheDocument();
    expect(within(getFolderRow("리서치")).getByText("18")).toBeInTheDocument();
    expect(within(getFolderRow("회의록")).getByText("41")).toBeInTheDocument();
    expect(within(getFolderRow("초안")).getByText("6")).toBeInTheDocument();

    expect(within(sidebar).getByText("지금 동기화")).toBeInTheDocument();
    expect(within(sidebar).getByText("2분 전")).toBeInTheDocument();
  });

  it("처음에는 제품과 로드맵 폴더만 펼쳐져 있다", () => {
    openSidebar();

    expect(getFolderRow("제품")).toHaveAttribute("aria-expanded", "true");
    expect(getFolderRow("로드맵")).toHaveAttribute("aria-expanded", "true");
    expect(getFolderRow("스펙")).toHaveAttribute("aria-expanded", "false");
    expect(getFolderRow("리서치")).toHaveAttribute("aria-expanded", "false");
  });

  it("펼쳐진 폴더를 누르면 하위 항목을 접는다", () => {
    openSidebar();

    fireEvent.click(getFolderRow("제품"));

    expect(getFolderRow("제품")).toHaveAttribute("aria-expanded", "false");
    expect(
      screen.queryByRole("button", { name: /^로드맵/ }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("2026 H2 로드맵")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^스펙/ }),
    ).not.toBeInTheDocument();
    expect(getFolderRow("리서치")).toBeInTheDocument();
  });

  it("접힌 폴더를 다시 누르면 하위 항목을 펼친다", () => {
    openSidebar();

    fireEvent.click(getFolderRow("제품"));
    fireEvent.click(getFolderRow("제품"));

    expect(getFolderRow("제품")).toHaveAttribute("aria-expanded", "true");
    expect(getFolderRow("로드맵")).toBeInTheDocument();
    expect(screen.getByText("2026 H2 로드맵")).toBeInTheDocument();
  });

  it("중간 폴더를 접어도 형제 폴더는 그대로 보인다", () => {
    openSidebar();

    fireEvent.click(getFolderRow("로드맵"));

    expect(getFolderRow("로드맵")).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByText("2026 H2 로드맵")).not.toBeInTheDocument();
    expect(getFolderRow("스펙")).toBeInTheDocument();
  });
});
