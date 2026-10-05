import { ThemeProvider } from "@emotion/react";
import { SEARCH_MESSAGES_MOCK } from "@hooks/domain/search/useSearchMessages/mock";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import SearchConversation from ".";

const WORKSPACE_ID = "1";
const CONVERSATION_ID = "10";
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const CONVERSATION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: WORKSPACE_ID, sessionId: CONVERSATION_ID },
});

const questions = SEARCH_MESSAGES_MOCK.filter(({ role }) => role === "USER");
const answers = SEARCH_MESSAGES_MOCK.filter(({ role }) => role === "ASSISTANT");
const answersWithEvidence = answers.filter(
  ({ evidences }) => evidences.length > 0,
);

const evidenceButtonName = (count: number) => `기록 ${count}개에서 찾았어요`;

const renderConversation = (initialPath = CONVERSATION_PATH) => {
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.CHAT, element: <SearchConversation /> },
      { path: PATH_ROUTE.CHAT_SESSION, element: <SearchConversation /> },
    ],
    { initialEntries: [initialPath] },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  return { router };
};

describe("SearchConversation", () => {
  // 도메인 규칙 7. 탐색 · 대화: 빈 대화는 저장하지 않고, 첫 질문을 받은 뒤 대화가 된다
  it("질문이 없는 새 대화에서는 '무엇을 찾고 있나요?' 제목과 설명을 보여준다", () => {
    renderConversation(CHAT_PATH);

    expect(
      screen.getByRole("heading", { name: "무엇을 찾고 있나요?" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("모아 둔 문서에서 찾아 근거와 함께 답해요."),
    ).toBeInTheDocument();
  });

  it("대화가 있으면 빈 화면 대신 질문을 순서대로 보여준다", () => {
    renderConversation();

    expect(
      screen.queryByRole("heading", { name: "무엇을 찾고 있나요?" }),
    ).not.toBeInTheDocument();

    const turns = screen.getAllByRole("article");

    expect(turns).toHaveLength(questions.length);
    turns.forEach((turn, index) => {
      expect(
        within(turn).getByText(questions[index].content),
      ).toBeInTheDocument();
    });
  });

  it("답변은 줄바꿈마다 문단으로 나눠 보여준다", () => {
    renderConversation();

    const [firstParagraph, secondParagraph] = answers[0].content.split("\n");

    expect(screen.getByText(firstParagraph).tagName).toBe("P");
    expect(screen.getByText(secondParagraph).tagName).toBe("P");
  });

  it("근거가 있는 답변 아래에만 '기록 N개에서 찾았어요' 버튼을 둔다", () => {
    renderConversation();

    const buttons = screen.getAllByRole("button", { name: /에서 찾았어요$/ });

    expect(buttons.map((button) => button.textContent)).toEqual(
      answersWithEvidence.map(({ evidences }) =>
        evidenceButtonName(evidences.length),
      ),
    );
  });

  it("'기록 N개에서 찾았어요'를 누르면 그 답변의 찾은 기록을 연다", async () => {
    const { router } = renderConversation();
    const [, answer] = answersWithEvidence;
    const button = screen.getByRole("button", {
      name: evidenceButtonName(answer.evidences.length),
    });

    expect(button).toHaveAttribute("aria-pressed", "false");

    await act(async () => {
      fireEvent.click(button);
    });

    expect(router.state.location.search).toBe(`?messageId=${answer.id}`);
    expect(button).toHaveAttribute("aria-pressed", "true");
  });

  it("다른 답변의 버튼을 누르면 찾은 기록이 그 답변으로 바뀐다", async () => {
    const { router } = renderConversation();
    const [firstAnswer, secondAnswer] = answersWithEvidence;
    const firstButton = screen.getByRole("button", {
      name: evidenceButtonName(firstAnswer.evidences.length),
    });
    const secondButton = screen.getByRole("button", {
      name: evidenceButtonName(secondAnswer.evidences.length),
    });

    await act(async () => {
      fireEvent.click(firstButton);
    });
    await act(async () => {
      fireEvent.click(secondButton);
    });

    expect(router.state.location.search).toBe(`?messageId=${secondAnswer.id}`);
    expect(firstButton).toHaveAttribute("aria-pressed", "false");
    expect(secondButton).toHaveAttribute("aria-pressed", "true");
  });
});
