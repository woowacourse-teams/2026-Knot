import { GetChatMessagesResponseDto } from "@api/dto/chatMessage";
import {
  GetChatSessionsResponseDto,
  PostChatSessionResponseDto,
} from "@api/dto/chatSession";
import { chatMessagesResponse } from "@api/mock/responses/chatMessage";
import {
  chatSessionResponse,
  chatSessionsResponse,
} from "@api/mock/responses/chatSession";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { theme } from "@provider/themeProvider";
import { MemoryRouter, Route, Routes } from "react-router";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { describe, expect, it } from "vitest";

import ChatPanel from ".";

const WORKSPACE_CONVERSATIONS_PATH = "*/api/v1/workspaces/:workspaceId/conversations";

const { sessions: expectedSessions } = new GetChatSessionsResponseDto(
  chatSessionsResponse,
);
const { messages: expectedMessages } = new GetChatMessagesResponseDto(
  chatMessagesResponse,
);
const expectedNewSession = new PostChatSessionResponseDto(chatSessionResponse);

const renderChatPanel = (initialEntry: string) => {
  // 테스트끼리 캐시가 새지 않도록 매번 새 client를 씁니다
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  return render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path={PATH_ROUTE.CHAT} element={<ChatPanel />} />
            <Route path={PATH_ROUTE.CHAT_SESSION} element={<ChatPanel />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
};

const submitQuestion = (question: string) => {
  fireEvent.change(screen.getByPlaceholderText("무엇이든 요청하세요"), {
    target: { value: question },
  });
  fireEvent.click(screen.getByRole("button", { name: "질문 보내기" }));
};

describe("ChatPanel", () => {
  it("워크스페이스에 쌓인 대화 목록을 보여준다", async () => {
    renderChatPanel("/workspace/1/chat?chatSessionList=open");

    expect(
      await screen.findByRole("button", {
        name: new RegExp(expectedSessions[0].title),
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: new RegExp(expectedSessions[1].title),
      }),
    ).toBeInTheDocument();
  });

  it("대화를 고르면 목록이 닫히고 그 대화의 이력을 보여준다", async () => {
    renderChatPanel("/workspace/1/chat?chatSessionList=open");

    fireEvent.click(
      await screen.findByRole("button", {
        name: new RegExp(expectedSessions[0].title),
      }),
    );

    expect(
      screen.queryByRole("heading", { name: "대화 목록" }),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText(expectedMessages[0].content),
    ).toBeInTheDocument();
    expect(screen.getByText(expectedMessages[1].content)).toBeInTheDocument();
  });

  it("대화가 하나도 없으면 빈 목록 안내를 보여준다", async () => {
    mockServer.use(
      http.get(WORKSPACE_CONVERSATIONS_PATH, () => HttpResponse.json([])),
    );

    renderChatPanel("/workspace/1/chat?chatSessionList=open");

    expect(
      await screen.findByText("아직 나눈 대화가 없어요"),
    ).toBeInTheDocument();
  });

  it("목록을 열어둔 주소가 아니면 대화 화면을 보여준다", () => {
    renderChatPanel("/workspace/1/chat/100");

    expect(
      screen.queryByRole("heading", { name: "대화 목록" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "knotted" })).toBeInTheDocument();
  });

  it("새 대화에서 첫 질문을 보내면 세션이 생기고 목록에 나타난다", async () => {
    // 생성 전에는 기존 세션만, 생성 뒤에는 새 세션까지 돌려줍니다
    let isCreated = false;
    mockServer.use(
      http.get(WORKSPACE_CONVERSATIONS_PATH, () =>
        HttpResponse.json(
          isCreated
            ? [chatSessionResponse, ...chatSessionsResponse]
            : chatSessionsResponse,
        ),
      ),
      http.post(WORKSPACE_CONVERSATIONS_PATH, () => {
        isCreated = true;

        return HttpResponse.json(chatSessionResponse, { status: 201 });
      }),
    );

    renderChatPanel("/workspace/1/chat");

    submitQuestion("DB 뭐 쓰기로 했지?");

    fireEvent.click(
      await screen.findByRole("button", { name: "대화 목록 열기" }),
    );

    expect(
      await screen.findByRole("button", {
        name: new RegExp(expectedNewSession.title),
      }),
    ).toBeInTheDocument();
  });
});
