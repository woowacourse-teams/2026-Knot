import { ThemeProvider } from "@emotion/react";
import { ChatStreamProvider } from "@provider/context/chatStreamContext";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";

import WorkspaceDock from ".";
import { DOCK_HINT_MAX_SEEN_COUNT, DOCK_HINT_TEXT } from "./constants/dockHint";

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

/**
 * 실제로는 두 화면이 공유하는 레이아웃에 놓이므로, 홈·탐색을 함께 덮어 화면이 바뀌어도 같은 독이 남게 해요.
 * 탐색에서는 독이 대화의 입력창이라 진행 중인 대화(`ChatStreamProvider`)도 함께 필요해요.
 */
const renderDock = (initialPath = HOME_PATH) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      {
        path: `${PATH_ROUTE.WORKSPACE_HOME}/*`,
        element: (
          <ChatStreamProvider>
            <WorkspaceDock />
          </ChatStreamProvider>
        ),
      },
    ],
    { initialEntries: [initialPath] },
  );

  const { unmount } = render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router, unmount };
};

const expandDock = () => {
  fireEvent.click(screen.getByRole("button", { name: "무엇이든 요청하기" }));

  return screen.getByRole("textbox", { name: "무엇이든 요청하세요" });
};

describe("WorkspaceDock", () => {
  // 안내를 몇 번, 이번 방문에 보여줬는지 브라우저에 남기므로 테스트마다 지워요
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

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

  it("보내고 나면 옮겨 간 탐색 화면에서 입력창이 비워진 채 열려 있다", async () => {
    renderDock();
    const field = expandDock();

    fireEvent.change(field, { target: { value: QUESTION } });
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "보내기" }));
    });

    expect(
      screen.getByRole("textbox", { name: "무엇이든 요청하세요" }),
    ).toHaveValue("");
  });

  it("탐색 화면에서는 처음부터 입력창이 열려 있다", () => {
    renderDock(CHAT_PATH);

    expect(
      screen.getByRole("textbox", { name: "무엇이든 요청하세요" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "무엇이든 요청하기" }),
    ).not.toBeInTheDocument();
  });

  it("글자 키를 누르면 입력창이 열리면서 그 글자부터 담긴다", () => {
    renderDock();

    fireEvent.keyDown(document.body, { key: "회" });

    expect(
      screen.getByRole("textbox", { name: "무엇이든 요청하세요" }),
    ).toHaveValue("회");
  });

  it("단축키 조합이나 글자가 아닌 키는 가로채지 않는다", () => {
    renderDock();

    fireEvent.keyDown(document.body, { key: "k", metaKey: true });
    fireEvent.keyDown(document.body, { key: "Tab" });
    fireEvent.keyDown(document.body, { key: "ArrowDown" });

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });

  it("이미 입력창에 적고 있을 때는 가로채지 않는다", () => {
    renderDock();
    const field = expandDock();

    fireEvent.change(field, { target: { value: QUESTION } });
    fireEvent.keyDown(field, { key: "a" });

    expect(field).toHaveValue(QUESTION);
  });

  it("독 안내는 처음 세 번 방문할 때까지만 보여준다", () => {
    for (let visit = 1; visit <= DOCK_HINT_MAX_SEEN_COUNT; visit += 1) {
      const { unmount } = renderDock();

      expect(screen.getByText(DOCK_HINT_TEXT)).toBeInTheDocument();

      unmount();
      sessionStorage.clear(); // 탭을 닫고 다시 들어온 셈이에요
    }

    renderDock();

    expect(screen.queryByText(DOCK_HINT_TEXT)).not.toBeInTheDocument();
  });

  it("같은 방문 안에서 다시 그려도 방문 횟수를 더 쓰지 않는다", () => {
    // 새로고침이나 홈·탐색 오가기로 독이 다시 그려져도 같은 방문이에요
    for (let render = 1; render <= DOCK_HINT_MAX_SEEN_COUNT + 2; render += 1) {
      const { unmount } = renderDock();

      expect(screen.getByText(DOCK_HINT_TEXT)).toBeInTheDocument();

      unmount();
    }

    sessionStorage.clear();
    renderDock();

    expect(screen.getByText(DOCK_HINT_TEXT)).toBeInTheDocument();
  });

  it("독을 열면 안내는 사라진다", () => {
    renderDock();

    expect(screen.getByText(DOCK_HINT_TEXT)).toBeInTheDocument();

    expandDock();

    expect(screen.queryByText(DOCK_HINT_TEXT)).not.toBeInTheDocument();
  });

  it("이미 열려 있는 탐색 화면에서는 안내를 보여주지 않는다", () => {
    renderDock(CHAT_PATH);

    expect(screen.queryByText(DOCK_HINT_TEXT)).not.toBeInTheDocument();
  });

  it("독 바깥을 누르면 접히고, 적던 글은 다시 열 때 그대로 있다", () => {
    renderDock();
    const field = expandDock();

    fireEvent.change(field, { target: { value: QUESTION } });
    fireEvent.pointerDown(document.body);

    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();

    expect(expandDock()).toHaveValue(QUESTION);
  });

  it("독 안을 누르는 것으로는 접히지 않는다", () => {
    renderDock();
    const field = expandDock();

    fireEvent.pointerDown(field);

    expect(field).toBeInTheDocument();
  });

  it("늘 펼쳐 두는 탐색 화면은 바깥을 눌러도 접히지 않는다", () => {
    renderDock(CHAT_PATH);

    fireEvent.pointerDown(document.body);

    expect(
      screen.getByRole("textbox", { name: "무엇이든 요청하세요" }),
    ).toBeInTheDocument();
  });

  it("펼치면 커서가 입력창에 놓인다", () => {
    renderDock();

    expect(expandDock()).toHaveFocus();
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
