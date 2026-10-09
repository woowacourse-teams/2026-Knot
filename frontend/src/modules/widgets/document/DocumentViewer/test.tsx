import { GetDocumentResponseDto } from "@api/dto/document";
import { documentDetailsResponse } from "@api/mock/responses/document";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import DocumentViewer from ".";

const expected = new GetDocumentResponseDto(documentDetailsResponse[0]);
const WORKSPACE_ID = "1";
// 경로 파라미터 자리에 무엇이 와도 잡도록 fetch 상수 대신 패턴을 적어요
const DOCUMENT_REQUEST =
  "*/api/v1/workspaces/:workspaceId/documents/:documentId";

// 주소에 id가 빠진 경우를 보려고 위젯을 놓는 경로예요
const NO_DOCUMENT_ID_PATH = "/workspace/:workspaceId/no-document-id";
const NO_WORKSPACE_ID_PATH = "/no-workspace-id/:documentId";

const renderViewer = (documentId: string) =>
  renderViewerAt(
    getRouterPath({
      routeKey: "DOCUMENT",
      params: { workspaceId: WORKSPACE_ID, documentId },
    }),
  );

const renderViewerAt = (entry: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouterFor(entry);

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );
};

// 이동을 확인할 수 있게 로그인 경로에 표시만 하는 화면을 둬요
const createMemoryRouterFor = (entry: string) =>
  createMemoryRouter(
    [
      { path: PATH_ROUTE.DOCUMENT, element: <DocumentViewer /> },
      { path: NO_DOCUMENT_ID_PATH, element: <DocumentViewer /> },
      { path: NO_WORKSPACE_ID_PATH, element: <DocumentViewer /> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인 화면</p> },
    ],
    { initialEntries: [entry] },
  );

describe("DocumentViewer", () => {
  it("문서를 불러오는 동안에는 불러오는 중이라고 알린다", () => {
    mockServer.use(
      http.get(DOCUMENT_REQUEST, async () => {
        await delay("infinite");
        return HttpResponse.json(documentDetailsResponse[0]);
      }),
    );
    renderViewer(String(expected.id));

    expect(
      screen.getByRole("region", { name: "문서를 불러오고 있어요" }),
    ).toHaveAttribute("aria-busy", "true");
  });

  it("문서 제목과 본문의 구역 제목·목록 항목을 보여준다", async () => {
    renderViewer(String(expected.id));

    expect(
      await screen.findByRole("heading", { level: 2, name: expected.title }),
    ).toBeInTheDocument();
    // 결정 있는 문서: 구역 제목(##) 4개와 미결정 항목 2개
    expect(screen.getAllByRole("heading", { level: 4 })).toHaveLength(4);
    expect(screen.getAllByRole("listitem")).toHaveLength(2);
  });

  it("없는 문서면 문서를 불러오지 못했다고 알리고, 다시 시도를 누르면 다시 조회한다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () => {
        requestCount += 1;
        return new HttpResponse(null, { status: 404 });
      }),
    );
    renderViewer(String(expected.id));

    expect(
      await screen.findByRole("heading", { name: "문서를 불러오지 못했어요" }),
    ).toBeInTheDocument();
    expect(requestCount).toBe(1);

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => expect(requestCount).toBe(2));
    expect(
      screen.getByRole("heading", { name: "문서를 불러오지 못했어요" }),
    ).toBeInTheDocument();
  });

  it.each([400, 403])(
    "%i 응답도 문서를 불러오지 못했다고 알린다",
    async (status) => {
      mockServer.use(
        http.get(DOCUMENT_REQUEST, () => new HttpResponse(null, { status })),
      );
      renderViewer(String(expected.id));

      expect(
        await screen.findByRole("heading", {
          name: "문서를 불러오지 못했어요",
        }),
      ).toBeInTheDocument();
    },
  );

  it.each([
    [
      "문서 id가 숫자가 아니면",
      getRouterPath({
        routeKey: "DOCUMENT",
        params: { workspaceId: WORKSPACE_ID, documentId: "abc" },
      }),
    ],
    ["주소에 문서 id가 없으면", `/workspace/${WORKSPACE_ID}/no-document-id`],
    ["주소에 워크스페이스 id가 없으면", `/no-workspace-id/${expected.id}`],
  ])(
    "%s 요청하지 않고 문서를 불러오지 못했다고 알린다",
    async (_condition, entry) => {
      let requestCount = 0;
      mockServer.use(
        http.get(DOCUMENT_REQUEST, () => {
          requestCount += 1;
          return HttpResponse.json(documentDetailsResponse[0]);
        }),
      );
      renderViewerAt(entry);

      expect(
        await screen.findByRole("heading", {
          name: "문서를 불러오지 못했어요",
        }),
      ).toBeInTheDocument();
      expect(requestCount).toBe(0);
    },
  );

  it("서버 오류면 문서를 불러오지 못했다고 알리고, 다시 시도를 누르면 다시 불러와 본문을 보여준다", async () => {
    mockServer.use(
      http.get(
        DOCUMENT_REQUEST,
        () => new HttpResponse(null, { status: 500 }),
        { once: true },
      ),
    );
    renderViewer(String(expected.id));

    expect(
      await screen.findByRole("heading", { name: "문서를 불러오지 못했어요" }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(
      await screen.findByRole("heading", { level: 2, name: expected.title }),
    ).toBeInTheDocument();
  });

  it("네트워크가 끊겨도 문서를 불러오지 못했다고 알리고, 다시 시도를 누르면 다시 불러와 본문을 보여준다", async () => {
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () => HttpResponse.error(), { once: true }),
    );
    renderViewer(String(expected.id));

    expect(
      await screen.findByRole("heading", { name: "문서를 불러오지 못했어요" }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(
      await screen.findByRole("heading", { level: 2, name: expected.title }),
    ).toBeInTheDocument();
  });

  it("로그인이 풀렸으면(401) 로그인 화면으로 보낸다", async () => {
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () => new HttpResponse(null, { status: 401 })),
    );
    renderViewer(String(expected.id));

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });
});
