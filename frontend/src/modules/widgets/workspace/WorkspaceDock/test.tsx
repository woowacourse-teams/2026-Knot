import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import WorkspaceDock from ".";

const WORKSPACE_ID = "1";
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const QUESTION = "지난주 회의에서 정해진 것만 뽑아 줘";

const renderDock = () => {
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <WorkspaceDock /> },
      { path: PATH_ROUTE.CHAT, element: <p>탐색 화면</p> },
    ],
    { initialEntries: [HOME_PATH] },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  return { router };
};

const expandDock = () => {
  fireEvent.click(screen.getByRole("button", { name: "무엇이든 요청하기" }));

  return screen.getByRole("textbox", { name: "무엇이든 요청하세요" });
};

describe("WorkspaceDock", () => {
  it("처음에는 접혀 있어 입력창 대신 버튼만 보여준다", () => {
    renderDock();

    expect(
      screen.getByRole("button", { name: "무엇이든 요청하기" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("누르면 질문 입력창으로 펼쳐진다", () => {
    renderDock();

    const field = expandDock();

    expect(field).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "보내기" })).toBeInTheDocument();
  });

  it("내용이 없으면 보낼 수 없다", () => {
    renderDock();
    const field = expandDock();

    expect(screen.getByRole("button", { name: "보내기" })).toBeDisabled();

    fireEvent.change(field, { target: { value: "   " } });

    expect(screen.getByRole("button", { name: "보내기" })).toBeDisabled();
  });

  it("질문을 보내면 탐색 화면으로 이동하며 그 질문을 들고 간다", async () => {
    const { router } = renderDock();
    const field = expandDock();

    fireEvent.change(field, { target: { value: QUESTION } });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "보내기" }));
    });

    expect(router.state.location.pathname).toBe(CHAT_PATH);
    expect(router.state.location.state).toEqual({ question: QUESTION });
  });

  it("보내고 나면 독은 다시 접힌다", async () => {
    renderDock();
    const field = expandDock();

    fireEvent.change(field, { target: { value: QUESTION } });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "보내기" }));
    });

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("탐색 이동은 push라 뒤로 가기 때 원래 화면으로 돌아온다", async () => {
    const { router } = renderDock();
    const field = expandDock();

    fireEvent.change(field, { target: { value: QUESTION } });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "보내기" }));
    });
    await act(async () => {
      await router.navigate(-1);
    });

    expect(router.state.location.pathname).toBe(HOME_PATH);
  });
});
