import { ThemeProvider } from "@emotion/react";
import { SEARCH_MESSAGES_MOCK } from "@hooks/domain/search/useSearchMessages/mock";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import SearchEvidenceToggle from ".";

const WORKSPACE_ID = "1";
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const CONVERSATION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: WORKSPACE_ID, sessionId: "10" },
});

const answersWithEvidence = SEARCH_MESSAGES_MOCK.filter(
  ({ role, evidences }) => role === "ASSISTANT" && evidences.length > 0,
);
const [firstAnswerWithEvidence] = answersWithEvidence;
const latestAnswerWithEvidence =
  answersWithEvidence[answersWithEvidence.length - 1];
const answerWithoutEvidence = SEARCH_MESSAGES_MOCK.find(
  ({ role, evidences }) => role === "ASSISTANT" && evidences.length === 0,
)!;

const renderToggle = (initialPath = CONVERSATION_PATH) => {
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.CHAT, element: <SearchEvidenceToggle /> },
      { path: PATH_ROUTE.CHAT_SESSION, element: <SearchEvidenceToggle /> },
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

describe("SearchEvidenceToggle", () => {
  it("근거가 있는 답변이 하나도 없으면 버튼을 두지 않는다 (Figma GNB/Floating)", () => {
    renderToggle(CHAT_PATH);

    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("근거가 있는 답변이 있으면 닫힌 상태의 '찾은 기록 보기' 버튼을 둔다 (Figma GNB/Floating)", () => {
    renderToggle();

    expect(
      screen.getByRole("button", { name: "찾은 기록 보기" }),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("닫힌 상태에서 누르면 근거가 있는 가장 최근 답변의 찾은 기록을 연다", async () => {
    const { router } = renderToggle();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "찾은 기록 보기" }));
    });

    expect(router.state.location.search).toBe(
      `?messageId=${latestAnswerWithEvidence.id}`,
    );
    expect(
      screen.getByRole("button", { name: "찾은 기록 닫기" }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("근거 없는 답변이 주소에 열려 있으면 닫힘으로 보인다 (Figma Search/FoundRecords)", () => {
    renderToggle(`${CONVERSATION_PATH}?messageId=${answerWithoutEvidence.id}`);

    expect(
      screen.getByRole("button", { name: "찾은 기록 보기" }),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("찾은 기록이 열려 있으면 '찾은 기록 닫기'이고, 누르면 닫는다", async () => {
    const { router } = renderToggle(
      `${CONVERSATION_PATH}?messageId=${firstAnswerWithEvidence.id}`,
    );
    const toggle = screen.getByRole("button", { name: "찾은 기록 닫기" });

    expect(toggle).toHaveAttribute("aria-expanded", "true");

    await act(async () => {
      fireEvent.click(toggle);
    });

    expect(router.state.location.search).toBe("");
    expect(
      screen.getByRole("button", { name: "찾은 기록 보기" }),
    ).toHaveAttribute("aria-expanded", "false");
  });
});
