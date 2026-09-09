import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import type {
  AgentBridgeStatus,
  AgentRegistrationTarget,
} from "@/shared/types/desktop";

import AgentConnectionCard from ".";

const RUNNING_STATUS: AgentBridgeStatus = {
  running: true,
  port: 47871,
  url: "http://127.0.0.1:47871/mcp",
  error: null,
  tokenIssuedAt: new Date(2026, 8, 9, 0, 41).toISOString(),
  lastToolCallAt: null,
  skillPath: "/Applications/Knot.app/Contents/Resources/skills/knot/SKILL.md",
};

const PREVIEW_BY_TARGET: Record<AgentRegistrationTarget, string> = {
  "claude-code":
    'claude mcp add --transport http knot http://127.0.0.1:47871/mcp --header "Authorization: Bearer <연결 토큰>"',
  codex:
    '[mcp_servers.knot]\nurl = "http://127.0.0.1:47871/mcp"\nhttp_headers = { Authorization = "Bearer <연결 토큰>" }',
  gemini:
    'gemini mcp add --transport http --scope user --header "Authorization: Bearer <연결 토큰>" knot http://127.0.0.1:47871/mcp',
  skill:
    "mkdir -p ~/.claude/skills/knot && cp <skillPath> ~/.claude/skills/knot/SKILL.md",
};

/** 셸의 preload `agent` API를 흉내 내요. 상태는 `nextStatus`로 바꿔 가며 응답해요 */
const stubDesktopAgent = (
  initialStatus: AgentBridgeStatus = RUNNING_STATUS,
) => {
  let currentStatus = initialStatus;

  const agent = {
    getStatus: vi.fn(() => Promise.resolve(currentStatus)),
    copyRegistration: vi.fn((target: AgentRegistrationTarget) =>
      Promise.resolve({ preview: PREVIEW_BY_TARGET[target] }),
    ),
    rotateToken: vi.fn(() => {
      currentStatus = {
        ...currentStatus,
        tokenIssuedAt: new Date(2026, 8, 9, 1, 0).toISOString(),
      };
      return Promise.resolve();
    }),
    setPort: vi.fn((port: number) => {
      currentStatus = {
        ...currentStatus,
        port,
        url: `http://127.0.0.1:${port}/mcp`,
      };
      return Promise.resolve();
    }),
  };

  window.knotDesktop = {
    version: "0.1.0",
    platform: "darwin",
    env: "local",
    openExternal: vi.fn(() => Promise.resolve()),
    onDeepLink: vi.fn(() => () => undefined),
    getPendingDeepLink: vi.fn(() => Promise.resolve(null)),
    agent,
  };

  return {
    agent,
    setStatus: (status: AgentBridgeStatus) => {
      currentStatus = status;
    },
  };
};

const renderCard = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.AGENT_CONNECTION, element: <AgentConnectionCard /> },
      { path: PATH_ROUTE.HOME, element: <p>홈</p> },
    ],
    { initialEntries: [PATH_ROUTE.AGENT_CONNECTION] },
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

const getRegistrationGroup = (title: string) =>
  screen.getByRole("group", { name: title });

const getCopyButton = (title: string) =>
  within(getRegistrationGroup(title)).getByRole("button", {
    name: /복사/,
  });

const getPortInput = () => screen.getByLabelText("포트");
const getPortSubmit = () => screen.getByRole("button", { name: "포트 변경" });

describe("AgentConnectionCard", () => {
  afterEach(() => {
    delete window.knotDesktop;
    vi.useRealTimers();
  });

  it("데스크톱 앱이 아니면 데스크톱에서 이어가라는 안내와 이동 버튼만 보여 준다", () => {
    const { router } = renderCard();

    expect(
      screen.getByRole("heading", { name: "데스크톱 앱에서 이어가요" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "CLI 에이전트 연결" }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: "워크스페이스로 이동" }),
    );

    expect(router.state.location.pathname).toBe(PATH_ROUTE.HOME);
  });

  it("셸이 준 MCP 서버 상태(주소·토큰 발급 시각·마지막 도구 호출)를 보여 준다", async () => {
    stubDesktopAgent();
    renderCard();

    expect(
      screen.getByRole("heading", { name: "CLI 에이전트 연결" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("연결 준비됨")).toBeInTheDocument();
    expect(screen.getByText(RUNNING_STATUS.url)).toBeInTheDocument();
    expect(screen.getByText("2026.09.09 00:41")).toBeInTheDocument();
    expect(screen.getByText("아직 없어요")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("서버를 열지 못했으면 실패 상태와 사유를 보여 준다", async () => {
    stubDesktopAgent({
      ...RUNNING_STATUS,
      running: false,
      error: "listen EADDRINUSE: address already in use 127.0.0.1:47871",
    });
    renderCard();

    expect(await screen.findByText("서버를 열지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("EADDRINUSE");
  });

  it("세 CLI와 스킬의 복사 버튼을 보여 주고, 복사하면 셸에 대상을 넘기고 미리보기와 복사됨을 보여 준다", async () => {
    const { agent } = stubDesktopAgent();
    renderCard();
    await screen.findByText("연결 준비됨");

    expect(getRegistrationGroup("Claude Code")).toBeInTheDocument();
    expect(getRegistrationGroup("Codex CLI")).toBeInTheDocument();
    expect(getRegistrationGroup("Gemini CLI")).toBeInTheDocument();
    expect(getRegistrationGroup("Knot 스킬")).toBeInTheDocument();

    fireEvent.click(getCopyButton("Claude Code"));

    await waitFor(() => {
      expect(agent.copyRegistration).toHaveBeenCalledWith("claude-code");
    });
    expect(
      await within(getRegistrationGroup("Claude Code")).findByRole("button", {
        name: "복사됨",
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(PREVIEW_BY_TARGET["claude-code"]),
    ).toBeInTheDocument();
    // 다른 대상의 버튼은 그대로예요
    expect(getCopyButton("Gemini CLI")).toHaveTextContent("복사");

    fireEvent.click(getCopyButton("Knot 스킬"));

    await waitFor(() => {
      expect(agent.copyRegistration).toHaveBeenCalledWith("skill");
    });
  });

  it("복사됨 표시는 2초 뒤 복사로 돌아간다", async () => {
    stubDesktopAgent();
    renderCard();
    await screen.findByText("연결 준비됨");

    vi.useFakeTimers();
    await act(async () => {
      fireEvent.click(getCopyButton("Codex CLI"));
    });
    expect(getCopyButton("Codex CLI")).toHaveTextContent("복사됨");

    await act(async () => {
      vi.advanceTimersByTime(2000);
    });

    expect(getCopyButton("Codex CLI")).toHaveTextContent("복사");
    expect(getCopyButton("Codex CLI")).not.toHaveTextContent("복사됨");
  });

  it("클립보드에 쓰지 못하면 안내 문구를 보여 준다", async () => {
    const { agent } = stubDesktopAgent();
    agent.copyRegistration.mockRejectedValueOnce(new Error("clipboard"));
    renderCard();
    await screen.findByText("연결 준비됨");

    fireEvent.click(getCopyButton("Gemini CLI"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "복사하지 못했어요",
    );
  });

  it("포트 입력이 범위 밖이면 셸을 부르지 않고 안내 문구를 보여 준다", async () => {
    const { agent } = stubDesktopAgent();
    renderCard();
    await screen.findByText("연결 준비됨");
    expect(getPortInput()).toHaveValue("47871");

    fireEvent.change(getPortInput(), { target: { value: "80" } });
    fireEvent.click(getPortSubmit());

    expect(
      await screen.findByText("1024~65535 사이의 정수를 입력해 주세요."),
    ).toBeInTheDocument();
    expect(agent.setPort).not.toHaveBeenCalled();
  });

  it("올바른 포트를 적용하면 셸에 넘기고 새 상태를 다시 읽는다", async () => {
    const { agent } = stubDesktopAgent();
    renderCard();
    await screen.findByText("연결 준비됨");

    fireEvent.change(getPortInput(), { target: { value: "48000" } });
    fireEvent.click(getPortSubmit());

    await waitFor(() => {
      expect(agent.setPort).toHaveBeenCalledWith(48000);
    });
    expect(
      await screen.findByText("http://127.0.0.1:48000/mcp"),
    ).toBeInTheDocument();
    expect(getPortInput()).toHaveValue("48000");
  });

  it("포트 충돌은 셸이 상태로 알려 주므로 적용 뒤 실패 상태를 보여 준다", async () => {
    const { agent, setStatus } = stubDesktopAgent();
    agent.setPort.mockImplementationOnce((port: number) => {
      setStatus({
        ...RUNNING_STATUS,
        running: false,
        port,
        url: `http://127.0.0.1:${port}/mcp`,
        error: `listen EADDRINUSE: address already in use 127.0.0.1:${port}`,
      });
      return Promise.resolve();
    });
    renderCard();
    await screen.findByText("연결 준비됨");

    fireEvent.change(getPortInput(), { target: { value: "8080" } });
    fireEvent.click(getPortSubmit());

    expect(await screen.findByText("서버를 열지 못했어요")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("8080");
  });

  it("토큰 재발급은 한 번 더 확인한 뒤 실행하고, 이전 미리보기를 지운다", async () => {
    const { agent } = stubDesktopAgent();
    renderCard();
    await screen.findByText("연결 준비됨");

    fireEvent.click(getCopyButton("Claude Code"));
    expect(
      await screen.findByText(PREVIEW_BY_TARGET["claude-code"]),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "연결 토큰 재발급" }));
    expect(agent.rotateToken).not.toHaveBeenCalled();

    const confirmGroup = screen.getByRole("group", {
      name: "연결 토큰 재발급 확인",
    });
    fireEvent.click(
      within(confirmGroup).getByRole("button", { name: "재발급" }),
    );

    await waitFor(() => {
      expect(agent.rotateToken).toHaveBeenCalledTimes(1);
    });
    expect(await screen.findByText("2026.09.09 01:00")).toBeInTheDocument();
    expect(
      screen.queryByText(PREVIEW_BY_TARGET["claude-code"]),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("group", { name: "연결 토큰 재발급 확인" }),
    ).not.toBeInTheDocument();
  });

  it("토큰 재발급 확인에서 취소하면 실행하지 않는다", async () => {
    const { agent } = stubDesktopAgent();
    renderCard();
    await screen.findByText("연결 준비됨");

    fireEvent.click(screen.getByRole("button", { name: "연결 토큰 재발급" }));
    fireEvent.click(screen.getByRole("button", { name: "취소" }));

    expect(agent.rotateToken).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "연결 토큰 재발급" }),
    ).toBeInTheDocument();
  });

  it("문서 본문이 사용자의 CLI 에이전트와 모델 제공자로 전송된다는 고지를 보여 준다", async () => {
    stubDesktopAgent();
    renderCard();
    await screen.findByText("연결 준비됨");

    expect(
      screen.getByText(
        /사용자의 CLI 에이전트와 그 에이전트가 쓰는 모델 제공자/,
      ),
    ).toBeInTheDocument();
  });
});
