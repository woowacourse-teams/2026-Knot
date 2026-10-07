import {
  GetDocumentConfirmationsResponseDto,
  GetDocumentResponseDto,
} from "@api/dto/document";
import {
  documentConfirmationsResponse,
  documentDetailsResponse,
} from "@api/mock/responses/document";
import { mockServer } from "@api/mock/server";
import type { DocumentConfirmationsResponse } from "@api/mock/types/document";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  fireEvent,
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from "@testing-library/react";
import { formatDate } from "@utils/formatDate";
import { formatDurationFromSeconds } from "@utils/formatDurationFromSeconds";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import DocumentViewer from ".";

const expected = new GetDocumentResponseDto(documentDetailsResponse[0]);
const WORKSPACE_ID = "1";
// 경로 파라미터 자리에 무엇이 와도 잡도록 fetch 상수 대신 패턴을 적어요
const DOCUMENT_REQUEST =
  "*/api/v1/workspaces/:workspaceId/documents/:documentId";
const CONFIRMATIONS_REQUEST = `${DOCUMENT_REQUEST}/confirmations`;

const expectedConfirmations = new GetDocumentConfirmationsResponseDto(
  documentConfirmationsResponse[0],
);

// 서버 정렬을 믿지 않는지 보려고 순서를 섞고, 확인하지 않고 나간 사람(EXCLUDED)을 끼워 넣은 응답이에요
const [confirmedItem, , pendingItem] = documentConfirmationsResponse[0].items;
const shuffledConfirmationsResponse = {
  ...documentConfirmationsResponse[0],
  items: [
    pendingItem,
    {
      memberId: 9,
      nickname: "루루",
      profileImageUrl: null,
      confirmedAt: null,
      state: "EXCLUDED",
    },
    confirmedItem,
  ],
} satisfies DocumentConfirmationsResponse;
const [pendingPerson, excludedPerson, confirmedPerson] =
  new GetDocumentConfirmationsResponseDto(shuffledConfirmationsResponse).items;

const LOAD_FAILED_NOTICE = "목록을 불러오지 못했어요";

const renderViewer = (documentId: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouterFor(documentId);

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );
};

// 이동을 확인할 수 있게 홈·로그인 경로에 표시만 하는 화면을 둬요
const createMemoryRouterFor = (documentId: string) =>
  createMemoryRouter(
    [
      { path: PATH_ROUTE.DOCUMENT, element: <DocumentViewer /> },
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>워크스페이스 홈</p> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인 화면</p> },
    ],
    {
      initialEntries: [
        getRouterPath({
          routeKey: "DOCUMENT",
          params: { workspaceId: WORKSPACE_ID, documentId },
        }),
      ],
    },
  );

/** 확인 수 문구에 포인터를 올려 확인한 사람 팝오버를 열어요 */
const hoverConfirmCount = async () => {
  const confirmCount = await screen.findByText(
    `${expected.confirmationSummary.confirmedCount}명 확인했어요`,
  );

  fireEvent.pointerEnter(confirmCount);

  return confirmCount;
};

const findPeopleRows = async () => {
  const peopleList = await screen.findByRole("list", {
    name: "문서 확인 현황",
  });

  return within(peopleList).getAllByRole("listitem");
};

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

  it("문서 머리에 경로 · 만든 날짜 · 녹음 길이를 보여준다", async () => {
    renderViewer(String(expected.id));

    await screen.findByRole("heading", { level: 2, name: expected.title });

    // 경로는 위 단계 「문서」와 지금 문서의 제목이에요. 그래서 제목은 경로와 제목 줄 두 곳에 보여요
    expect(screen.getByText("문서")).toBeInTheDocument();
    expect(screen.getAllByText(expected.title)).toHaveLength(2);
    expect(
      screen.getByText(formatDate(expected.createdAt)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        formatDurationFromSeconds(expected.recordingDurationSeconds),
      ),
    ).toBeInTheDocument();
  });

  it("확인한 사람 수를 보여주고, 포인터를 올리면 확인 대상의 이름을 보여준다", async () => {
    renderViewer(String(expected.id));

    await hoverConfirmCount();

    const rows = await findPeopleRows();

    expect(rows).toHaveLength(expectedConfirmations.items.length);
    expectedConfirmations.items.forEach(({ nickname }, index) => {
      expect(within(rows[index]).getByText(nickname)).toBeInTheDocument();
    });
  });

  it("응답의 순서와 관계없이 확인한 사람을 먼저 보여주고, 확인하지 않고 나간 사람은 보여주지 않는다", async () => {
    mockServer.use(
      http.get(CONFIRMATIONS_REQUEST, () =>
        HttpResponse.json(shuffledConfirmationsResponse),
      ),
    );
    renderViewer(String(expected.id));

    await hoverConfirmCount();

    const rows = await findPeopleRows();

    expect(rows).toHaveLength(2);
    expect(
      within(rows[0]).getByText(confirmedPerson.nickname),
    ).toBeInTheDocument();
    expect(
      within(rows[1]).getByText(pendingPerson.nickname),
    ).toBeInTheDocument();
    expect(screen.queryByText(excludedPerson.nickname)).not.toBeInTheDocument();
  });

  it("확인 대상을 불러오지 못하면 팝오버에 알리고, 포인터를 다시 올리면 다시 불러와 보여준다", async () => {
    mockServer.use(
      http.get(
        CONFIRMATIONS_REQUEST,
        () => new HttpResponse(null, { status: 500 }),
      ),
    );
    renderViewer(String(expected.id));

    const confirmCount = await hoverConfirmCount();

    expect(await screen.findByText(LOAD_FAILED_NOTICE)).toBeInTheDocument();

    // 서버가 돌아온 뒤 팝오버를 닫았다가 다시 열어요
    mockServer.resetHandlers();
    fireEvent.pointerLeave(confirmCount);
    await waitForElementToBeRemoved(() =>
      screen.queryByText(LOAD_FAILED_NOTICE),
    );
    fireEvent.pointerEnter(confirmCount);

    expect(await findPeopleRows()).toHaveLength(
      expectedConfirmations.items.length,
    );
  });

  it("없는 문서면 문서를 찾을 수 없다고 알리고, 홈으로를 누르면 워크스페이스 홈으로 간다", async () => {
    // 기본 mock 핸들러는 응답에 없는 id에 404를 돌려줘요
    renderViewer("999");

    expect(
      await screen.findByText("문서를 찾을 수 없어요"),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "홈으로" }));

    expect(await screen.findByText("워크스페이스 홈")).toBeInTheDocument();
  });

  it.each([400, 403])(
    "%i 응답도 잘못된 요청이라 문서를 찾을 수 없다고 알린다",
    async (status) => {
      mockServer.use(
        http.get(DOCUMENT_REQUEST, () => new HttpResponse(null, { status })),
      );
      renderViewer(String(expected.id));

      expect(
        await screen.findByText("문서를 찾을 수 없어요"),
      ).toBeInTheDocument();
    },
  );

  it("문서 id가 숫자가 아니면 요청하지 않고 문서를 찾을 수 없다고 알린다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () => {
        requestCount += 1;
        return HttpResponse.json(documentDetailsResponse[0]);
      }),
    );
    renderViewer("abc");

    expect(
      await screen.findByText("문서를 찾을 수 없어요"),
    ).toBeInTheDocument();
    expect(requestCount).toBe(0);
  });

  it("서버 오류면 다시 시도를 보여주고, 누르면 다시 불러와 본문을 보여준다", async () => {
    mockServer.use(
      http.get(
        DOCUMENT_REQUEST,
        () => new HttpResponse(null, { status: 500 }),
        { once: true },
      ),
    );
    renderViewer(String(expected.id));

    expect(
      await screen.findByText("문서를 불러오지 못했어요"),
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
