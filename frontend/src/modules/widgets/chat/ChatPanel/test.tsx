import { GetChatMessagesResponseDto } from "@api/dto/chatMessage";
import { PostChatSessionResponseDto } from "@api/dto/chatSession";
import { chatMessagesResponse } from "@api/mock/responses/chatMessage";
import { chatSessionResponse } from "@api/mock/responses/chatSession";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { theme } from "@provider/themeProvider";
import { MemoryRouter, Route, Routes } from "react-router";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { describe, expect, it } from "vitest";

import type { ChatNavigationState } from "@/shared/types/chat";

import WorkspaceDock from "@widgets/workspace/WorkspaceDock";
import { ChatStreamProvider } from "@provider/context/chatStreamContext";

import ChatPanel from ".";

const WORKSPACE_CONVERSATIONS_PATH =
  "*/api/v1/workspaces/:workspaceId/conversations";
const CHAT_MESSAGES_PATH = "*/api/v1/conversations/:sessionId";
const SEND_CHAT_MESSAGE_PATH = "*/api/v1/conversations/:sessionId/messages";

const QUESTION = "DB 뭐 쓰기로 했지?";
const PARTIAL_ANSWER = "도착 중인 부분 답변";
const SAVED_ANSWER = "서버에 저장된 답변";
const FAILURE_NOTICE = "답변을 만들지 못했어요. 잠시 후 다시 시도해 주세요.";
const ALREADY_ACTIVE_MESSAGE = "이미 답변을 만들고 있어요";

const { messages: expectedMessages } = new GetChatMessagesResponseDto(
  chatMessagesResponse,
);
const expectedNewSession = new PostChatSessionResponseDto(chatSessionResponse);

const encoder = new TextEncoder();

/** SSE 프레임 한 개 */
const toFrame = (event: string, data: object) =>
  `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

/**
 * 프레임을 직접 밀어 넣는 SSE 응답.
 *
 * 조각을 언제 보낼지 테스트가 정해야 완료 전 화면을 확실히 관찰할 수 있어요.
 */
const sseResponse = (produce: (push: (frame: string) => void) => Promise<void>) =>
  new HttpResponse(
    new ReadableStream({
      async start(controller) {
        await produce((frame) => controller.enqueue(encoder.encode(frame)));
        controller.close();
      },
    }),
    { headers: { "Content-Type": "text/event-stream" } },
  );

/** 테스트가 열어 줄 때까지 스트림을 붙잡아 두는 잠금 */
const createGate = () => {
  let open = () => {};
  const opened = new Promise<void>((resolve) => {
    open = resolve;
  });

  return { opened, open: () => open() };
};

/** 서버가 저장했다고 가정하는 메시지 이력 */
const savedMessagesResponse = [
  ...chatMessagesResponse,
  { id: 2001, role: "USER", content: QUESTION, createdAt: new Date().toISOString() },
  {
    id: 2002,
    role: "ASSISTANT",
    content: SAVED_ANSWER,
    createdAt: new Date().toISOString(),
  },
];

/**
 * 대화는 패널이 그리고 질문은 하단 독에서 적으므로, 실제 화면처럼 둘을 함께 놓고 봅니다.
 * 둘 다 `ChatStreamProvider` 안에 있어야 같은 대화를 봅니다(실제로는 워크스페이스 레이아웃이 감싸요).
 */
const renderChatPanel = (
  initialEntry: string | { pathname: string; state: ChatNavigationState },
) => {
  // 테스트끼리 캐시가 새지 않도록 매번 새 client를 씁니다
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  // 프로바이더는 라우트 안에 둬야 `:workspaceId`·`:sessionId`를 봅니다(앱에서는 레이아웃이 그 자리예요)
  const screenElement = (
    <ChatStreamProvider>
      <ChatPanel />
      <WorkspaceDock />
    </ChatStreamProvider>
  );

  return render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path={PATH_ROUTE.CHAT} element={screenElement} />
            <Route path={PATH_ROUTE.CHAT_SESSION} element={screenElement} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ThemeProvider>,
  );
};

const getChatTextarea = () => screen.getByPlaceholderText("무엇이든 요청하세요");

const submitQuestion = (question: string) => {
  fireEvent.change(getChatTextarea(), { target: { value: question } });
  fireEvent.click(screen.getByRole("button", { name: "보내기" }));
};

describe("ChatPanel", () => {
  it("저장된 대화를 보여주고, 질문은 하단 독에서 적는다", async () => {
    renderChatPanel("/workspace/1/chat/100");

    expect(
      await screen.findByText(expectedMessages[0].content),
    ).toBeInTheDocument();
    expect(getChatTextarea()).toBeInTheDocument();
  });

  it("하단 독에서 질문을 들고 들어오면 도착하자마자 그 질문으로 대화를 시작한다", async () => {
    renderChatPanel({ pathname: "/workspace/1/chat/100", state: { question: QUESTION } });

    expect(await screen.findByText(QUESTION)).toBeInTheDocument();
  });

  it("질문을 보내면 부분 답변이 쌓이고, 완료 후 서버 저장본으로 바뀐다", async () => {
    const gate = createGate();
    let isCompleted = false;

    mockServer.use(
      http.get(CHAT_MESSAGES_PATH, () =>
        HttpResponse.json(isCompleted ? savedMessagesResponse : chatMessagesResponse),
      ),
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        sseResponse(async (push) => {
          push(toFrame("chunk", { delta: PARTIAL_ANSWER }));
          await gate.opened;
          isCompleted = true;
          push(toFrame("complete", { messageId: 2002 }));
        }),
      ),
    );

    renderChatPanel("/workspace/1/chat/100");

    submitQuestion(QUESTION);

    expect(await screen.findByText(PARTIAL_ANSWER)).toBeInTheDocument();
    expect(screen.getByText(QUESTION)).toBeInTheDocument();

    gate.open();

    expect(await screen.findByText(SAVED_ANSWER)).toBeInTheDocument();
    expect(screen.queryByText(PARTIAL_ANSWER)).not.toBeInTheDocument();
    expect(screen.getByText(QUESTION)).toBeInTheDocument();
  });

  it("연결 뒤 실패하면 부분 답변을 남기고 그 아래에 실패를 알린다", async () => {
    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        sseResponse(async (push) => {
          push(toFrame("chunk", { delta: PARTIAL_ANSWER }));
          push(
            toFrame("error", {
              code: "LLM_STREAM_FAILED",
              message: "답변 생성에 실패했습니다",
            }),
          );
        }),
      ),
    );

    renderChatPanel("/workspace/1/chat/100");

    submitQuestion(QUESTION);

    expect(await screen.findByText(FAILURE_NOTICE)).toBeInTheDocument();
    expect(screen.getByText(PARTIAL_ANSWER)).toBeInTheDocument();
    expect(screen.getByText(QUESTION)).toBeInTheDocument();
  });

  it("전송 중에는 전송 버튼이 잠기고, 끝나면 풀린다", async () => {
    const gate = createGate();

    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        sseResponse(async (push) => {
          push(toFrame("chunk", { delta: PARTIAL_ANSWER }));
          await gate.opened;
          push(toFrame("complete", { messageId: 2002 }));
        }),
      ),
    );

    renderChatPanel("/workspace/1/chat/100");

    submitQuestion(QUESTION);

    // 답변이 오는 중에 또 보내면 서버가 409로 거절하므로 애초에 못 누르게 합니다
    await waitFor(() => {
      expect(screen.getByRole("button", { name: "보내기" })).toBeDisabled();
    });

    gate.open();

    fireEvent.change(getChatTextarea(), { target: { value: QUESTION } });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "보내기" })).toBeEnabled();
    });
  });

  it("보낸 뒤에도 커서는 입력창에 남는다", async () => {
    const gate = createGate();

    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        sseResponse(async (push) => {
          push(toFrame("chunk", { delta: PARTIAL_ANSWER }));
          await gate.opened;
          push(toFrame("complete", { messageId: 2002 }));
        }),
      ),
    );

    renderChatPanel("/workspace/1/chat/100");

    // 버튼을 눌러 보내면 커서가 버튼으로 옮겨 가므로 되돌아와야 합니다
    submitQuestion(QUESTION);

    expect(getChatTextarea()).toHaveFocus();
    expect(await screen.findByText(PARTIAL_ANSWER)).toBeInTheDocument();
    expect(getChatTextarea()).toHaveFocus();

    gate.open();
  });

  it("새 대화에서 첫 질문을 보내면 세션을 만들어 그 대화로 옮긴 뒤 스트리밍한다", async () => {
    const gate = createGate();
    let streamedSessionId: string | undefined;

    mockServer.use(
      http.post(WORKSPACE_CONVERSATIONS_PATH, () =>
        HttpResponse.json(chatSessionResponse, { status: 201 }),
      ),
      http.post(SEND_CHAT_MESSAGE_PATH, ({ params }) => {
        streamedSessionId = String(params.sessionId);

        return sseResponse(async (push) => {
          push(toFrame("chunk", { delta: PARTIAL_ANSWER }));
          await gate.opened;
          push(toFrame("complete", { messageId: 2002 }));
        });
      }),
    );

    renderChatPanel("/workspace/1/chat");

    submitQuestion(QUESTION);

    expect(await screen.findByText(PARTIAL_ANSWER)).toBeInTheDocument();
    expect(streamedSessionId).toBe(String(expectedNewSession.id));
    // 옮겨 간 대화의 이력이 함께 보이면 라우팅까지 끝난 것입니다
    expect(
      await screen.findByText(expectedMessages[0].content),
    ).toBeInTheDocument();

    gate.open();
  });

  it("409로 거절당하면 안내만 하고 입력을 되살린다", async () => {
    mockServer.use(
      http.post(SEND_CHAT_MESSAGE_PATH, () =>
        HttpResponse.json(
          {
            code: "CHAT_STREAM_ALREADY_ACTIVE",
            message: ALREADY_ACTIVE_MESSAGE,
          },
          { status: 409 },
        ),
      ),
    );

    renderChatPanel("/workspace/1/chat/100");

    submitQuestion(QUESTION);

    expect(await screen.findByText(ALREADY_ACTIVE_MESSAGE)).toBeInTheDocument();
    expect(getChatTextarea()).not.toHaveAttribute("readonly");
    expect(screen.queryByText(QUESTION)).not.toBeInTheDocument();
    expect(screen.queryByText(FAILURE_NOTICE)).not.toBeInTheDocument();
  });
});
