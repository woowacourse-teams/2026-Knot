import { GetChatSessionsResponseDto } from "@api/dto/chatSession";
import { chatSessionsResponse } from "@api/mock/responses/chatSession";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import ChatListDrawer from ".";

const WORKSPACE_ID = "1";
const CHAT_PATH = getRouterPath({
  routeKey: "CHAT",
  params: { workspaceId: WORKSPACE_ID },
});
const SESSION_PATH = getRouterPath({
  routeKey: "CHAT_SESSION",
  params: { workspaceId: WORKSPACE_ID, sessionId: "100" },
});

const expected = new GetChatSessionsResponseDto(chatSessionsResponse);

const renderDrawer = (initialPath = SESSION_PATH) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.CHAT, element: <ChatListDrawer /> },
      { path: PATH_ROUTE.CHAT_SESSION, element: <ChatListDrawer /> },
    ],
    { initialEntries: [initialPath] },
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

describe("ChatListDrawer", () => {
  it("제목과 새 채팅 버튼, 워크스페이스의 대화 목록을 보여준다", async () => {
    renderDrawer();

    expect(
      screen.getByRole("heading", { name: "대화" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "새 채팅" }),
    ).toBeInTheDocument();

    expect(
      await screen.findByText(expected.sessions[0].title),
    ).toBeInTheDocument();
    expect(screen.getByText(expected.sessions[1].title)).toBeInTheDocument();
  });

  it("보고 있는 대화를 현재 항목으로 표시한다", async () => {
    renderDrawer();

    const openedRow = await screen.findByText(expected.sessions[0].title);

    expect(openedRow.closest("button")).toHaveAttribute("aria-current", "page");
  });

  it("목록에서 대화를 고르면 그 대화로 이동한다", async () => {
    const { router } = renderDrawer(CHAT_PATH);

    const row = await screen.findByText(expected.sessions[1].title);
    await act(async () => {
      fireEvent.click(row);
    });

    expect(router.state.location.pathname).toBe(
      getRouterPath({
        routeKey: "CHAT_SESSION",
        params: {
          workspaceId: WORKSPACE_ID,
          sessionId: String(expected.sessions[1].id),
        },
      }),
    );
  });

  it("새 채팅을 누르면 세션 없는 탐색 화면으로 이동한다", async () => {
    const { router } = renderDrawer();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "새 채팅" }));
    });

    expect(router.state.location.pathname).toBe(CHAT_PATH);
  });
});
