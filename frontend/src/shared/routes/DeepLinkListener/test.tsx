import { render, screen, waitFor } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import { PATH_ROUTE } from "../PATH_ROUTE";

import DeepLinkListener from ".";

import type { KnotDeepLink, KnotDesktopApi } from "@/shared/types/desktop";

/** 셸의 딥링크 API만 흉내 내요. 핸들러를 잡아 두고 테스트가 직접 링크를 흘려 보냅니다 */
const stubDesktop = (pending: KnotDeepLink | null = null) => {
  let handler: ((link: KnotDeepLink) => void) | null = null;
  const unsubscribe = vi.fn();
  const api: KnotDesktopApi = {
    version: "0.1.0",
    platform: "darwin",
    env: "local",
    openExternal: vi.fn(() => Promise.resolve()),
    onDeepLink: vi.fn((next: (link: KnotDeepLink) => void) => {
      handler = next;
      return unsubscribe;
    }),
    getPendingDeepLink: vi.fn(() => Promise.resolve(pending)),
  };
  window.knotDesktop = api;

  return {
    api,
    unsubscribe,
    emit: (link: KnotDeepLink) => {
      if (handler === null) throw new Error("아직 구독하지 않았어요");
      handler(link);
    },
  };
};

const renderRouter = () => {
  const router = createMemoryRouter(
    [
      {
        element: <DeepLinkListener />,
        children: [
          { path: PATH_ROUTE.HOME, element: <p>홈</p> },
          { path: PATH_ROUTE.CHAT, element: <p>새 대화</p> },
          { path: PATH_ROUTE.CHAT_SESSION, element: <p>대화</p> },
          { path: PATH_ROUTE.INVITE, element: <p>초대</p> },
        ],
      },
    ],
    { initialEntries: [PATH_ROUTE.HOME] },
  );
  const view = render(<RouterProvider router={router} />);

  return { router, view };
};

afterEach(() => {
  delete window.knotDesktop;
});

describe("DeepLinkListener", () => {
  it("브라우저(셸 없음)에서는 아무것도 하지 않고 자식 화면을 그린다", () => {
    const { router } = renderRouter();

    expect(screen.getByText("홈")).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(PATH_ROUTE.HOME);
  });

  it("채팅 딥링크가 오면 그 세션 화면으로 옮긴다", async () => {
    const desktop = stubDesktop();
    const { router } = renderRouter();

    await waitFor(() => expect(desktop.api.onDeepLink).toHaveBeenCalled());
    desktop.emit({ type: "chat", workspaceId: "7", sessionId: "42" });

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/workspace/7/chat/42");
    });
    expect(screen.getByText("대화")).toBeInTheDocument();
  });

  it("세션 없는 채팅 딥링크는 새 대화 화면으로, 초대 딥링크는 초대 판정 화면으로 옮긴다", async () => {
    const desktop = stubDesktop();
    const { router } = renderRouter();

    await waitFor(() => expect(desktop.api.onDeepLink).toHaveBeenCalled());
    desktop.emit({ type: "chat", workspaceId: "7" });
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/workspace/7/chat");
    });

    desktop.emit({ type: "invite", token: "abc-DEF_1" });
    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/invite/abc-DEF_1");
    });
  });

  it("앱이 꺼져 있을 때 눌린 링크는 처음 그릴 때 한 번 가져와 옮긴다", async () => {
    const desktop = stubDesktop({
      type: "chat",
      workspaceId: "3",
      sessionId: "9",
    });
    const { router } = renderRouter();

    await waitFor(() => {
      expect(router.state.location.pathname).toBe("/workspace/3/chat/9");
    });
    expect(desktop.api.getPendingDeepLink).toHaveBeenCalledTimes(1);
  });

  it("화면이 사라지면 구독을 해제한다", async () => {
    const desktop = stubDesktop();
    const { view } = renderRouter();
    await waitFor(() => expect(desktop.api.onDeepLink).toHaveBeenCalled());

    view.unmount();

    expect(desktop.unsubscribe).toHaveBeenCalledTimes(1);
  });
});
