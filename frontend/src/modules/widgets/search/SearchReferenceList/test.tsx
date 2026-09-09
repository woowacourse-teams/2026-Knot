import { GetChatMessageSourcesResponseDto } from "@api/dto/chatMessage";
import { chatMessageSourcesResponse } from "@api/mock/responses/chatMessage";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import SearchReferenceList from ".";

const expected = new GetChatMessageSourcesResponseDto(
  chatMessageSourcesResponse,
);
const MESSAGE_ID = expected.searchReferences[0].messageId;
const CHAT_PATH = "/workspace/1/chat/100";

/** 응답 8건 중 서로 다른 페이지 수. 카드 수의 기대값이에요 */
const distinctPageIds = [
  ...new Set(expected.searchReferences.map((ref) => ref.notionPage.id)),
];

const SOURCES_PATH_PATTERN = "*/api/v1/messages/:messageId/sources";

const renderList = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [{ path: PATH_ROUTE.CHAT_SESSION, element: <SearchReferenceList /> }],
    { initialEntries: [`${CHAT_PATH}?messageId=${MESSAGE_ID}`] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router };
};

const getCards = () =>
  screen.getAllByRole("link").filter((link) => link.getAttribute("href"));

describe("SearchReferenceList", () => {
  it("응답이 오기 전에는 제목과 자리 표시를 보여 준다", () => {
    mockServer.use(
      http.get(SOURCES_PATH_PATTERN, async () => {
        await delay("infinite");
        return HttpResponse.json(chatMessageSourcesResponse);
      }),
    );
    renderList();

    expect(
      screen.getByRole("heading", { name: "찾은 문서" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("청크 8건을 페이지로 묶어 대표 점수 순으로 카드를 보여 준다", async () => {
    renderList();

    const firstTitle = expected.searchReferences[0].notionPage.title;
    expect(await screen.findByText(firstTitle)).toBeInTheDocument();

    const cards = getCards();
    expect(cards).toHaveLength(distinctPageIds.length);
    expect(cards[0]).toHaveAttribute(
      "href",
      expected.searchReferences[0].notionPage.notionUrl,
    );

    // 두 번째 카드는 두 번째로 높은 점수의 페이지(ERD)예요
    expect(cards[1]).toHaveAttribute(
      "href",
      expected.searchReferences[1].notionPage.notionUrl,
    );
    expect(cards[0]).toHaveTextContent("Notion");
  });

  it("출처가 없으면 안내 문구를 보여 준다", async () => {
    mockServer.use(
      http.get(SOURCES_PATH_PATTERN, () =>
        HttpResponse.json({ searchReferences: [] }),
      ),
    );
    renderList();

    expect(
      await screen.findByText("이 답변에는 근거로 쓴 문서가 없어요."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("조회에 실패하면 다시 시도를 보여 주고, 누르면 다시 조회한다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(SOURCES_PATH_PATTERN, () => {
        requestCount += 1;
        return requestCount === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json(chatMessageSourcesResponse);
      }),
    );
    renderList();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "찾은 문서를 불러오지 못했어요.",
    );

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(
      await screen.findByText(expected.searchReferences[0].notionPage.title),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("닫기를 누르면 주소에서 messageId를 지운다", async () => {
    const { router } = renderList();
    await screen.findByText(expected.searchReferences[0].notionPage.title);

    fireEvent.click(screen.getByRole("button", { name: "찾은 문서 닫기" }));

    await waitFor(() => {
      expect(router.state.location.search).toBe("");
    });
  });
});
