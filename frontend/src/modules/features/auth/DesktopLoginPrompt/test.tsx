import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import DesktopLoginPrompt from ".";

import type { KnotDesktopApi, LoginPromptState } from "@/shared/types/desktop";

/**
 * 셸의 로그인 뷰 알림만 흉내 내요. 구독 핸들러를 잡아 두고 테스트가 직접 상태를 흘려 보냅니다.
 *
 * `withPrompt: false`면 이 기능이 없는 옛 셸이에요 — 그때는 띠를 그리지 않습니다.
 */
const stubDesktop = ({ withPrompt = true } = {}) => {
  let handler: ((prompt: LoginPromptState) => void) | null = null;
  const unsubscribe = vi.fn();
  const cancelLogin = vi.fn(() => Promise.resolve());
  const api = {
    version: "0.1.0",
    platform: "darwin",
    env: "local",
    openExternal: vi.fn(() => Promise.resolve()),
    onDeepLink: vi.fn(() => vi.fn()),
    getPendingDeepLink: vi.fn(() => Promise.resolve(null)),
    auth: {
      getToken: vi.fn(() => Promise.resolve(null)),
      setToken: vi.fn(() => Promise.resolve()),
      clearToken: vi.fn(() => Promise.resolve()),
      startLogin: vi.fn(() => Promise.resolve()),
      ...(withPrompt
        ? {
            cancelLogin,
            onLoginPromptChanged: vi.fn(
              (next: (prompt: LoginPromptState) => void) => {
                handler = next;
                return unsubscribe;
              },
            ),
          }
        : {}),
    },
  } as unknown as KnotDesktopApi;
  window.knotDesktop = api;

  return {
    cancelLogin,
    unsubscribe,
    emit: (prompt: LoginPromptState) => {
      if (handler === null) throw new Error("아직 구독하지 않았어요");
      handler(prompt);
    },
  };
};

const renderPrompt = () =>
  render(
    <ThemeProvider theme={theme}>
      <DesktopLoginPrompt />
    </ThemeProvider>,
  );

afterEach(() => {
  delete window.knotDesktop;
});

describe("DesktopLoginPrompt", () => {
  it("브라우저(셸 없음)에서는 아무것도 그리지 않는다", () => {
    renderPrompt();

    expect(screen.queryByRole("banner")).not.toBeInTheDocument();
  });

  it("로그인 뷰가 붙으면 셸이 알려 준 높이로 띠를 그린다", async () => {
    const desktop = stubDesktop();
    renderPrompt();

    desktop.emit({ open: true, headerHeight: 44 });

    const banner = await screen.findByRole("banner");
    expect(banner).toHaveStyle({ height: "44px" });
    expect(screen.getByRole("button", { name: "취소" })).toBeInTheDocument();
  });

  it("취소를 누르면 셸에 취소를 알린다", async () => {
    const desktop = stubDesktop();
    renderPrompt();
    desktop.emit({ open: true, headerHeight: 44 });

    fireEvent.click(await screen.findByRole("button", { name: "취소" }));

    expect(desktop.cancelLogin).toHaveBeenCalledTimes(1);
  });

  it("로그인 뷰가 떨어지면 띠도 사라진다", async () => {
    const desktop = stubDesktop();
    renderPrompt();
    desktop.emit({ open: true, headerHeight: 44 });
    await screen.findByRole("banner");

    desktop.emit({ open: false, headerHeight: 0 });

    await waitFor(() => {
      expect(screen.queryByRole("banner")).not.toBeInTheDocument();
    });
  });

  // 로드맵 R31. 셸이 이 알림을 보내지 않는 판이면 띠가 없고, 취소는 뷰 안 `Esc`뿐이에요
  it("알림을 보내지 않는 옛 셸에서는 띠를 그리지 않는다", () => {
    stubDesktop({ withPrompt: false });
    renderPrompt();

    expect(screen.queryByRole("banner")).not.toBeInTheDocument();
  });
});
