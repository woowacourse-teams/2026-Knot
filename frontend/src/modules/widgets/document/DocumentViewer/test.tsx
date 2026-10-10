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
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from "@testing-library/react";
import { formatDate } from "@utils/formatDate";
import { formatDurationFromSeconds } from "@utils/formatDurationFromSeconds";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import DocumentViewer from ".";

const expected = new GetDocumentResponseDto(documentDetailsResponse[0]);
const WORKSPACE_ID = "1";
// 경로 파라미터 자리에 무엇이 와도 잡도록 fetch 상수 대신 패턴을 적어요
const DOCUMENT_REQUEST =
  "*/api/v1/workspaces/:workspaceId/documents/:documentId";
const CONFIRMATIONS_REQUEST = `${DOCUMENT_REQUEST}/confirmations`;
const MY_CONFIRMATION_REQUEST = `${CONFIRMATIONS_REQUEST}/me`;

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
const [pendingMember, excludedMember, confirmedMember] =
  new GetDocumentConfirmationsResponseDto(shuffledConfirmationsResponse).items;

const LOAD_FAILED_NOTICE = "목록을 불러오지 못했어요";
const COPIED_DURATION_MS = 3000;
const CONFIRM_BUTTON_NAME = "문서를 확인했어요";
const CONFIRMED_BUTTON_NAME = "확인했어요";

const writeText = vi.fn<(text: string) => Promise<void>>();

// 이동을 확인할 수 있게 로그인 경로에 표시만 하는 화면을 둬요
const renderViewer = (documentId: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.DOCUMENT, element: <DocumentViewer /> },
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

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );
};

/** 확인 수 문구에 포인터를 올려 확인한 사람 팝오버를 열어요 */
const hoverConfirmCount = async () => {
  const confirmCount = await screen.findByText(
    `${expected.confirmationSummary.confirmedCount}명 확인했어요`,
  );

  fireEvent.pointerEnter(confirmCount);

  return confirmCount;
};

const findMemberRows = async () => {
  const memberList = await screen.findByRole("list", {
    name: "문서 확인 현황",
  });

  return within(memberList).getAllByRole("listitem");
};

const queryConfirmButton = () =>
  screen.queryByRole("button", { name: CONFIRM_BUTTON_NAME });

const click = async (element: HTMLElement) => {
  await act(async () => {
    fireEvent.click(element);
  });
};

const advanceTimers = async (ms: number) => {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
};

describe("DocumentViewer", () => {
  beforeEach(() => {
    writeText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    writeText.mockReset();
    Reflect.deleteProperty(navigator, "clipboard");
  });

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

  it("복사 버튼 · 확인 수 · 확인 버튼이 그려져도 문서 상세는 한 번만 요청한다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () => {
        requestCount += 1;
        return HttpResponse.json(documentDetailsResponse[0]);
      }),
    );
    renderViewer(String(expected.id));

    // 문서를 받은 뒤에 그려지는 컴포넌트들이 같은 문서를 다시 요청하지 않는지 보려고,
    // 그 뒤에 나가는 확인 대상 조회가 끝날 때까지 기다린 다음 세요
    await hoverConfirmCount();
    await findMemberRows();

    expect(requestCount).toBe(1);
  });

  it("문서를 받은 직후에 다른 탭에 다녀와도 문서를 다시 받는다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () => {
        requestCount += 1;
        return HttpResponse.json(documentDetailsResponse[0]);
      }),
    );
    renderViewer(String(expected.id));

    await screen.findByRole("heading", { level: 2, name: expected.title });
    expect(requestCount).toBe(1);

    // TanStack Query는 탭이 다시 보이게 된 것을 visibilitychange 이벤트로 알아요
    act(() => {
      window.dispatchEvent(new Event("visibilitychange"));
    });

    await waitFor(() => expect(requestCount).toBe(2));
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

    const rows = await findMemberRows();

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

    const rows = await findMemberRows();

    expect(rows).toHaveLength(2);
    expect(
      within(rows[0]).getByText(confirmedMember.nickname),
    ).toBeInTheDocument();
    expect(
      within(rows[1]).getByText(pendingMember.nickname),
    ).toBeInTheDocument();
    expect(screen.queryByText(excludedMember.nickname)).not.toBeInTheDocument();
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

    expect(await findMemberRows()).toHaveLength(
      expectedConfirmations.items.length,
    );
  });

  it("복사를 누르면 제목과 본문을 마크다운으로 복사하고, 3초 동안 복사됨을 보여준다", async () => {
    renderViewer(String(expected.id));
    await screen.findByRole("heading", { level: 2, name: expected.title });
    vi.useFakeTimers();

    await click(screen.getByRole("button", { name: "복사" }));

    // 본문에는 제목이 없어서 제목을 `#` 제목으로 앞에 붙여요
    expect(writeText).toHaveBeenCalledWith(
      `# ${expected.title}\n\n${expected.content}`,
    );
    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();

    await advanceTimers(COPIED_DURATION_MS - 1);

    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();

    await advanceTimers(1);

    expect(screen.getByRole("button", { name: "복사" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "복사됨" }),
    ).not.toBeInTheDocument();
  });

  it("아직 확인하지 않았으면 확인 버튼을 보여주고, 누르면 누를 수 없는 「확인했어요」로 바뀌고 확인 수가 늘어난다", async () => {
    renderViewer(String(expected.id));

    fireEvent.click(
      await screen.findByRole("button", { name: CONFIRM_BUTTON_NAME }),
    );

    expect(
      await screen.findByRole("button", { name: CONFIRMED_BUTTON_NAME }),
    ).toBeDisabled();
    expect(queryConfirmButton()).not.toBeInTheDocument();
    expect(
      screen.getByText(
        `${expected.confirmationSummary.confirmedCount + 1}명 확인했어요`,
      ),
    ).toBeInTheDocument();
  });

  it("이미 확인한 문서를 열면 누를 수 없는 「확인했어요」를 보여준다", async () => {
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () =>
        HttpResponse.json({
          ...documentDetailsResponse[0],
          myConfirmationState: "CONFIRMED",
        }),
      ),
    );
    renderViewer(String(expected.id));

    expect(
      await screen.findByRole("button", { name: CONFIRMED_BUTTON_NAME }),
    ).toBeDisabled();
    expect(queryConfirmButton()).not.toBeInTheDocument();
  });

  it("확인 대상이 아니면 확인 버튼도 「확인했어요」도 보여주지 않는다", async () => {
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () =>
        HttpResponse.json({
          ...documentDetailsResponse[0],
          myConfirmationState: "NOT_REQUIRED",
        }),
      ),
    );
    renderViewer(String(expected.id));

    await screen.findByRole("heading", { level: 2, name: expected.title });

    expect(queryConfirmButton()).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: CONFIRMED_BUTTON_NAME }),
    ).not.toBeInTheDocument();
  });

  it("확인 대상이 아니라는 응답(409)을 받으면 문서를 다시 불러와 확인 버튼을 없앤다", async () => {
    // 확인 요청을 받기 전에는 확인이 필요하다고, 받은 뒤에는 확인 대상이 아니라고 답해요.
    // 요청 순서로 나누면 409와 관계없는 문서 조회가 바뀐 응답을 먼저 받아, 409 처리가 없어도 통과해요
    let isConfirmRequested = false;
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () =>
        HttpResponse.json(
          isConfirmRequested
            ? {
                ...documentDetailsResponse[0],
                myConfirmationState: "NOT_REQUIRED",
              }
            : documentDetailsResponse[0],
        ),
      ),
      http.put(MY_CONFIRMATION_REQUEST, () => {
        isConfirmRequested = true;

        return HttpResponse.json(
          {
            code: "CONFIRMATION_NOT_REQUIRED",
            message: "확인 대상이 아닙니다.",
          },
          { status: 409 },
        );
      }),
    );
    renderViewer(String(expected.id));

    fireEvent.click(
      await screen.findByRole("button", { name: CONFIRM_BUTTON_NAME }),
    );

    await waitForElementToBeRemoved(queryConfirmButton);
  });

  it("확인 요청이 서버 오류로 실패하면 버튼을 남겨 다시 누를 수 있게 한다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.put(MY_CONFIRMATION_REQUEST, async () => {
        requestCount += 1;
        // 응답이 바로 오면 누를 수 없는 상태가 그려지기 전에 지나가서, 잠깐 늦게 답해요
        await delay(100);
        return new HttpResponse(null, { status: 500 });
      }),
    );
    renderViewer(String(expected.id));

    const confirmButton = await screen.findByRole("button", {
      name: CONFIRM_BUTTON_NAME,
    });
    fireEvent.click(confirmButton);

    // 응답을 기다리는 동안에는 누를 수 없고, 실패하면 다시 누를 수 있어요
    await waitFor(() => expect(confirmButton).toBeDisabled());
    await waitFor(() => expect(confirmButton).toBeEnabled());

    fireEvent.click(confirmButton);

    await waitFor(() => expect(requestCount).toBe(2));
  });

  it("확인 요청에서 로그인이 풀렸으면(401) 로그인 화면으로 보낸다", async () => {
    mockServer.use(
      http.put(
        MY_CONFIRMATION_REQUEST,
        () => new HttpResponse(null, { status: 401 }),
      ),
    );
    renderViewer(String(expected.id));

    fireEvent.click(
      await screen.findByRole("button", { name: CONFIRM_BUTTON_NAME }),
    );

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
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

  it("주소의 문서 id가 숫자가 아니어도 확인하지 않고 그대로 요청하고, 서버가 거절하면 문서를 불러오지 못했다고 알린다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(DOCUMENT_REQUEST, () => {
        requestCount += 1;
        // 서버는 숫자가 아닌 문서 id를 잘못된 요청으로 거절해요
        return new HttpResponse(null, { status: 400 });
      }),
    );
    renderViewer("abc");

    expect(
      await screen.findByRole("heading", {
        name: "문서를 불러오지 못했어요",
      }),
    ).toBeInTheDocument();
    expect(requestCount).toBe(1);
  });

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
