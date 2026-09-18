import { GetNotionPageTreeResponseDto } from "@api/dto/notionPage";
import { GetWorkspaceResponseDto } from "@api/dto/workspace";
import { notionPageTreeResponse } from "@api/mock/responses/notionPage";
import { workspaceDetailResponse } from "@api/mock/responses/workspace";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceSidebar from ".";

const NOTION_PAGE_TREE_PATH =
  "*/api/v1/workspaces/:workspaceId/notion-pages/tree";

const expectedWorkspace = new GetWorkspaceResponseDto(workspaceDetailResponse);
const { pages: expectedPages } = new GetNotionPageTreeResponseDto(
  notionPageTreeResponse,
);

const [product, roadmap, roadmap2026, spec, , research] = expectedPages;

/** 제품 아래에 딸린 문서 수(로드맵·2026 H2 로드맵·스펙·탐색 스펙) */
const PRODUCT_DOCUMENT_COUNT = "4";

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
    [{ path: PATH_ROUTE.WORKSPACE_HOME, element: <WorkspaceSidebar /> }],
    { initialEntries: [HOME_PATH] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return screen.getByRole("complementary", { name: "워크스페이스 사이드바" });
};

const findFolderRow = (name: string) =>
  screen.findByRole("button", { name: new RegExp(`^${name}`) });

const getFolderRow = (name: string) =>
  screen.getByRole("button", { name: new RegExp(`^${name}`) });

describe("WorkspaceSidebar", () => {
  it("조회한 워크스페이스 이름과 페이지 트리를 보여준다", async () => {
    const sidebar = renderSidebar();

    expect(
      await within(sidebar).findByText(expectedWorkspace.name),
    ).toBeInTheDocument();
    expect(within(sidebar).getByText("폴더")).toBeInTheDocument();

    // 하위 페이지가 있으면 문서 수를 단 폴더 행이에요
    expect(
      within(await findFolderRow(product.title)).getByText(
        PRODUCT_DOCUMENT_COUNT,
      ),
    ).toBeInTheDocument();
    expect(await findFolderRow(research.title)).toBeInTheDocument();
  });

  it("처음에는 모든 폴더가 접혀 있다", async () => {
    renderSidebar();

    expect(await findFolderRow(product.title)).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(screen.queryByText(roadmap.title)).not.toBeInTheDocument();
  });

  it("폴더를 누르면 하위 항목을 펼치고, 다시 누르면 접는다", async () => {
    renderSidebar();

    fireEvent.click(await findFolderRow(product.title));

    expect(getFolderRow(product.title)).toHaveAttribute(
      "aria-expanded",
      "true",
    );
    expect(getFolderRow(roadmap.title)).toBeInTheDocument();
    expect(getFolderRow(spec.title)).toBeInTheDocument();

    fireEvent.click(getFolderRow(product.title));

    expect(getFolderRow(product.title)).toHaveAttribute(
      "aria-expanded",
      "false",
    );
    expect(screen.queryByText(roadmap.title)).not.toBeInTheDocument();
  });

  it("하위 페이지가 없는 페이지는 펼칠 수 없는 문서 행으로 보여준다", async () => {
    renderSidebar();

    fireEvent.click(await findFolderRow(product.title));
    fireEvent.click(getFolderRow(roadmap.title));

    expect(screen.getByText(roadmap2026.title)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", {
        name: new RegExp(`^${roadmap2026.title}`),
      }),
    ).not.toBeInTheDocument();
  });

  it("중간 폴더를 접어도 형제 폴더는 그대로 보인다", async () => {
    renderSidebar();

    fireEvent.click(await findFolderRow(product.title));
    fireEvent.click(getFolderRow(roadmap.title));
    fireEvent.click(getFolderRow(roadmap.title));

    expect(screen.queryByText(roadmap2026.title)).not.toBeInTheDocument();
    expect(getFolderRow(spec.title)).toBeInTheDocument();
  });

  it("발행된 페이지가 없으면 폴더 라벨만 남긴다", async () => {
    mockServer.use(
      http.get(NOTION_PAGE_TREE_PATH, () => HttpResponse.json([])),
    );

    const sidebar = renderSidebar();

    expect(
      await within(sidebar).findByText(expectedWorkspace.name),
    ).toBeInTheDocument();
    expect(within(sidebar).getByText("폴더")).toBeInTheDocument();
    expect(within(sidebar).queryByRole("button")).not.toBeInTheDocument();
  });
});
