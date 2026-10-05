import { ThemeProvider } from "@emotion/react";
import { SEARCH_MESSAGES_MOCK } from "@hooks/domain/search/useSearchMessages/mock";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { render, screen, within } from "@testing-library/react";
import { formatDate } from "@utils/formatDate";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import SearchEvidenceList from ".";

const WORKSPACE_ID = "1";
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const CONVERSATION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: WORKSPACE_ID, sessionId: "10" },
});

const answers = SEARCH_MESSAGES_MOCK.filter(({ role }) => role === "ASSISTANT");
const [, answerWithThreeEvidences, answerWithoutEvidence] = answers;

const renderEvidenceList = (initialPath: string) => {
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.CHAT, element: <SearchEvidenceList /> },
      { path: PATH_ROUTE.CHAT_SESSION, element: <SearchEvidenceList /> },
    ],
    { initialEntries: [initialPath] },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );
};

describe("SearchEvidenceList", () => {
  it("펼친 답변의 근거 문서를 서버가 준 순서대로 '찾은 기록'에 보여준다 (도메인 규칙: SearchEvidence)", () => {
    renderEvidenceList(
      `${CONVERSATION_PATH}?messageId=${answerWithThreeEvidences.id}`,
    );

    const panel = screen.getByRole("region", { name: "찾은 기록" });
    const items = within(panel).getAllByRole("listitem");
    const expected = answerWithThreeEvidences.evidences;

    expect(items).toHaveLength(expected.length);
    items.forEach((item, index) => {
      expect(within(item).getByText(expected[index].title)).toBeInTheDocument();
      expect(
        within(item).getByText(expected[index].sourceType),
      ).toBeInTheDocument();
      expect(
        within(item).getByText(formatDate(expected[index].createdAt)),
      ).toBeInTheDocument();
    });
  });

  it("찾은 기록 카드는 그 문서 보기 화면으로 가는 링크다 (Figma Search/FoundRecords: 카드를 누르면 그 문서를 연다)", () => {
    renderEvidenceList(
      `${CONVERSATION_PATH}?messageId=${answerWithThreeEvidences.id}`,
    );

    const panel = screen.getByRole("region", { name: "찾은 기록" });
    const items = within(panel).getAllByRole("listitem");

    items.forEach((item, index) => {
      const { documentId } = answerWithThreeEvidences.evidences[index];

      expect(within(item).getByRole("link")).toHaveAttribute(
        "href",
        getRouterPath({
          routeKey: "DOCUMENT",
          params: { workspaceId: WORKSPACE_ID, documentId: String(documentId) },
        }),
      );
    });
  });

  it("펼친 답변이 없으면 패널을 그리지 않는다", () => {
    renderEvidenceList(CONVERSATION_PATH);

    expect(
      screen.queryByRole("region", { name: "찾은 기록" }),
    ).not.toBeInTheDocument();
  });

  it("펼친 답변에 근거가 없으면 패널을 그리지 않는다", () => {
    renderEvidenceList(
      `${CONVERSATION_PATH}?messageId=${answerWithoutEvidence.id}`,
    );

    expect(
      screen.queryByRole("region", { name: "찾은 기록" }),
    ).not.toBeInTheDocument();
  });

  it("대화가 없는 새 대화에서는 패널을 그리지 않는다", () => {
    renderEvidenceList(`${CHAT_PATH}?messageId=${answerWithThreeEvidences.id}`);

    expect(
      screen.queryByRole("region", { name: "찾은 기록" }),
    ).not.toBeInTheDocument();
  });
});
