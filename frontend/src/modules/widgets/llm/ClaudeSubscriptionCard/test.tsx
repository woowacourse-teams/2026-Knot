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
} from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import type {
  LlmSettingsView,
  LlmSubscriptionStatus,
} from "@/shared/types/desktop";

import { BILLING_NOTICE } from "./constants/claudeSubscription";

import ClaudeSubscriptionCard from ".";

const SIGNED_OUT_STATUS: LlmSubscriptionStatus = {
  signedIn: false,
  expiresAt: null,
  model: "claude-fable-5-1",
  lastError: null,
  lastAnsweredBy: null,
};

const SIGNED_IN_STATUS: LlmSubscriptionStatus = {
  signedIn: true,
  expiresAt: new Date(2026, 8, 9, 20, 0).toISOString(),
  model: "claude-fable-5-1",
  lastError: null,
  lastAnsweredBy: "subscription",
};

const SETTINGS: LlmSettingsView = {
  model: "claude-fable-5-1",
  effort: "high",
  models: ["claude-fable-5-1", "claude-opus-5", "claude-sonnet-5"],
  efforts: ["low", "medium", "high", "xhigh", "max"],
};

/** 셸의 preload `llm` API를 흉내 내요. 로그인·설정은 메모리 값을 바꾸고, 상태 변경은 `emit`으로 보내요 */
const stubDesktopLlm = (
  initialStatus: LlmSubscriptionStatus = SIGNED_OUT_STATUS,
) => {
  let currentStatus = initialStatus;
  let currentSettings = SETTINGS;
  const listeners = new Set<(status: LlmSubscriptionStatus) => void>();

  const llm = {
    getStatus: vi.fn(() => Promise.resolve(currentStatus)),
    signIn: vi.fn(() => {
      currentStatus = SIGNED_IN_STATUS;
      return Promise.resolve();
    }),
    signOut: vi.fn(() => {
      currentStatus = SIGNED_OUT_STATUS;
      return Promise.resolve();
    }),
    onStatusChanged: vi.fn(
      (handler: (status: LlmSubscriptionStatus) => void) => {
        listeners.add(handler);
        return () => listeners.delete(handler);
      },
    ),
    streamAnswer: vi.fn(() => () => undefined),
    getSettings: vi.fn(() => Promise.resolve(currentSettings)),
    updateSettings: vi.fn((input: { model: string; effort: string }) => {
      currentSettings = { ...currentSettings, ...input };
      return Promise.resolve(currentSettings);
    }),
  };

  window.knotDesktop = {
    version: "0.1.0",
    platform: "darwin",
    env: "local",
    openExternal: vi.fn(() => Promise.resolve()),
    onDeepLink: vi.fn(() => () => undefined),
    getPendingDeepLink: vi.fn(() => Promise.resolve(null)),
    llm,
  };

  return {
    llm,
    emit: (status: LlmSubscriptionStatus) => {
      currentStatus = status;
      listeners.forEach((listener) => listener(status));
    },
  };
};

const renderCard = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      {
        path: PATH_ROUTE.CLAUDE_SUBSCRIPTION,
        element: <ClaudeSubscriptionCard />,
      },
      { path: PATH_ROUTE.HOME, element: <p>홈</p> },
    ],
    { initialEntries: [PATH_ROUTE.CLAUDE_SUBSCRIPTION] },
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

describe("ClaudeSubscriptionCard", () => {
  afterEach(() => {
    delete window.knotDesktop;
  });

  it("데스크톱 앱이 아니면 데스크톱에서 이어가라는 안내와 이동 버튼만 보여 준다", async () => {
    const { router } = renderCard();

    expect(
      screen.getByRole("heading", { name: "데스크톱 앱에서 이어가요" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Claude 구독" }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: "워크스페이스로 이동" }),
    );

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(PATH_ROUTE.HOME);
    });
  });

  it("로그인 전에는 로그인 안 됨과 로그인 버튼, 과금·정책 안내를 보여 준다", async () => {
    stubDesktopLlm();
    renderCard();

    expect(await screen.findByText("로그인 안 됨")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Claude로 로그인" }),
    ).toBeInTheDocument();
    expect(screen.getByText(BILLING_NOTICE)).toBeInTheDocument();
    expect(screen.getByText("아직 없어요")).toBeInTheDocument();
  });

  it("로그인을 누르면 셸의 signIn을 부르고, 끝나면 로그인됨과 로그아웃 버튼으로 바뀐다", async () => {
    const { llm } = stubDesktopLlm();
    renderCard();

    fireEvent.click(
      await screen.findByRole("button", { name: "Claude로 로그인" }),
    );

    expect(llm.signIn).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("로그인됨")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "구독 로그아웃" }),
    ).toBeInTheDocument();
    expect(screen.getByText("내 Claude 구독")).toBeInTheDocument();
  });

  it("로그인이 거절되면 안내를 보여 주고 로그인 전 상태로 남는다", async () => {
    const { llm } = stubDesktopLlm();
    llm.signIn.mockRejectedValueOnce(new Error("timeout"));
    renderCard();

    fireEvent.click(
      await screen.findByRole("button", { name: "Claude로 로그인" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Claude 로그인을 마치지 못했어요",
    );
    expect(screen.getByText("로그인 안 됨")).toBeInTheDocument();
  });

  it("로그아웃을 누르면 셸의 signOut을 부르고 로그인 전 상태로 돌아간다", async () => {
    const { llm } = stubDesktopLlm(SIGNED_IN_STATUS);
    renderCard();

    fireEvent.click(
      await screen.findByRole("button", { name: "구독 로그아웃" }),
    );

    expect(llm.signOut).toHaveBeenCalledTimes(1);
    expect(await screen.findByText("로그인 안 됨")).toBeInTheDocument();
  });

  it("셸이 상태 변경을 알리면 다시 읽지 않고 그 값으로 바뀐다", async () => {
    const { llm, emit } = stubDesktopLlm(SIGNED_IN_STATUS);
    renderCard();
    await screen.findByText("로그인됨");
    const readsBefore = llm.getStatus.mock.calls.length;

    act(() => {
      emit({
        ...SIGNED_IN_STATUS,
        lastAnsweredBy: "server-sse",
        lastError: "SUBSCRIPTION_RATE_LIMITED",
      });
    });

    expect(await screen.findByText("서버 모델")).toBeInTheDocument();
    expect(screen.getByText("SUBSCRIPTION_RATE_LIMITED")).toBeInTheDocument();
    expect(llm.getStatus.mock.calls.length).toBe(readsBefore);
  });

  it("셸이 준 목록에서 모델·effort를 고르고 저장하면 셸에 쓰고 저장됨을 보여 준다", async () => {
    const { llm } = stubDesktopLlm();
    renderCard();

    const modelSelect = await screen.findByLabelText("모델");
    await waitFor(() => {
      expect(modelSelect).toHaveValue("claude-fable-5-1");
    });
    const saveButton = screen.getByRole("button", { name: "저장" });
    expect(saveButton).toBeDisabled();

    fireEvent.change(modelSelect, { target: { value: "claude-sonnet-5" } });
    fireEvent.change(screen.getByLabelText("effort"), {
      target: { value: "low" },
    });
    expect(saveButton).toBeEnabled();
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(llm.updateSettings).toHaveBeenCalledWith({
        model: "claude-sonnet-5",
        effort: "low",
      });
    });
    expect(await screen.findByRole("status")).toHaveTextContent("저장됨");
    expect(modelSelect).toHaveValue("claude-sonnet-5");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
  });

  it("저장이 거절되면 안내를 보여 준다", async () => {
    const { llm } = stubDesktopLlm();
    llm.updateSettings.mockRejectedValueOnce(new Error("목록 밖"));
    renderCard();

    const modelSelect = await screen.findByLabelText("모델");
    await waitFor(() => {
      expect(modelSelect).toHaveValue("claude-fable-5-1");
    });
    fireEvent.change(modelSelect, { target: { value: "claude-opus-5" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "설정을 저장하지 못했어요",
    );
  });
});
