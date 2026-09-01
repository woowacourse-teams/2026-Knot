import { describe, it, expect } from "vitest";
import { ThemeProvider } from "@emotion/react";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { theme } from "@provider/themeProvider";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";

import ChatPanel from ".";

const renderChatPanel = (initialEntry: string) =>
  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route path={PATH_ROUTE.CHAT} element={<ChatPanel />} />
          <Route path={PATH_ROUTE.CHAT_SESSION} element={<ChatPanel />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );

describe("ChatPanel", () => {
  it("대화 목록에서 대화를 고르면 목록이 닫히고 그 대화 화면으로 넘어간다", () => {
    renderChatPanel("/workspace/1/chat?chatSessionList=open");

    expect(
      screen.getByRole("heading", { name: "대화 목록" }),
    ).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: /DB 기술 선정 관련 문서/ }),
    );

    expect(
      screen.queryByRole("heading", { name: "대화 목록" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "knotted" })).toBeInTheDocument();
  });

  it("목록을 열어둔 주소가 아니면 대화 화면을 보여준다", () => {
    renderChatPanel("/workspace/1/chat/100");

    expect(
      screen.queryByRole("heading", { name: "대화 목록" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "knotted" })).toBeInTheDocument();
  });
});
