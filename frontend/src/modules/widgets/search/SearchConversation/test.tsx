import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import SearchConversation from ".";

const renderConversation = () => {
  render(
    <ThemeProvider theme={theme}>
      <SearchConversation />
    </ThemeProvider>,
  );
};

describe("SearchConversation", () => {
  // 도메인 규칙 7. 탐색 · 대화: 빈 대화는 저장하지 않고, 첫 질문을 받은 뒤 대화가 된다
  it("질문이 없는 새 대화에서는 '무엇을 찾고 있나요?' 제목과 설명을 보여준다", () => {
    renderConversation();

    expect(
      screen.getByRole("heading", { name: "무엇을 찾고 있나요?" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("모아 둔 문서에서 찾아 근거와 함께 답해요."),
    ).toBeInTheDocument();
  });
});
