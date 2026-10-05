import { GetWorkspacesResponseDto } from "@api/dto/workspace";
import { workspacesResponse } from "@api/mock/responses/workspace";
import { ThemeProvider } from "@emotion/react";
import { DialogProvider } from "@provider/context/dialogContext";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useRecordingStore } from "@store/recordingStore";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it } from "vitest";

import WorkspaceSwitcher from ".";

const {
  workspaces: [currentWorkspace, otherWorkspace],
} = new GetWorkspacesResponseDto(workspacesResponse);

const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(currentWorkspace.id) },
});
const OTHER_HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(otherWorkspace.id) },
});
const END_RECORDING_DIALOG = "녹음을 끝내고 이동할까요?";

const renderSwitcher = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <WorkspaceSwitcher /> },
      { path: PATH_ROUTE.WORKSPACE_CREATE, element: null },
    ],
    { initialEntries: [HOME_PATH] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <DialogProvider>
          <RouterProvider router={router} />
        </DialogProvider>
      </QueryClientProvider>
    </ThemeProvider>,
  );

  return { router };
};

const getMenuTrigger = () =>
  screen.getByRole("button", { name: "워크스페이스 메뉴" });

/** 메뉴를 열고 목록이 그려질 때까지 기다려요 */
const openMenu = async () => {
  fireEvent.click(getMenuTrigger());

  return screen.findByRole("region", { name: "워크스페이스 메뉴" });
};

const findWorkspaceItem = (name: string) =>
  screen.findByRole("button", { name });

describe("WorkspaceSwitcher", () => {
  afterEach(() => {
    // 전역 저장소라 테스트끼리 녹음이 새지 않도록 처음 상태로 되돌려요
    useRecordingStore.getState().endRecording();
  });

  const startRecording = async () => {
    await act(async () => {
      await useRecordingStore.getState().startRecording();
    });
  };

  it("워크스페이스 이름을 누르면 메뉴를 열고, 다시 누르면 닫는다", async () => {
    renderSwitcher();

    expect(getMenuTrigger()).toHaveAttribute("aria-expanded", "false");

    const menu = await openMenu();

    expect(getMenuTrigger()).toHaveAttribute("aria-expanded", "true");
    expect(within(menu).getByText("워크스페이스")).toBeInTheDocument();
    expect(
      within(menu).getByRole("button", { name: "새 워크스페이스 만들기" }),
    ).toBeInTheDocument();
    expect(
      within(menu).getByRole("button", { name: "워크스페이스 나가기" }),
    ).toBeInTheDocument();

    fireEvent.click(getMenuTrigger());

    expect(
      screen.queryByRole("region", { name: "워크스페이스 메뉴" }),
    ).not.toBeInTheDocument();
  });

  it("내 워크스페이스 목록을 보여주고 지금 워크스페이스를 표시한다", async () => {
    renderSwitcher();
    await openMenu();

    expect(await findWorkspaceItem(currentWorkspace.name)).toHaveAttribute(
      "aria-current",
      "true",
    );
    expect(await findWorkspaceItem(otherWorkspace.name)).not.toHaveAttribute(
      "aria-current",
    );
  });

  it("바깥을 누르면 메뉴를 닫는다", async () => {
    renderSwitcher();
    await openMenu();

    fireEvent.pointerDown(document.body);

    expect(
      screen.queryByRole("region", { name: "워크스페이스 메뉴" }),
    ).not.toBeInTheDocument();
  });

  it("ESC를 누르면 메뉴를 닫는다", async () => {
    renderSwitcher();
    await openMenu();

    fireEvent.keyDown(document, { key: "Escape" });

    expect(
      screen.queryByRole("region", { name: "워크스페이스 메뉴" }),
    ).not.toBeInTheDocument();
  });

  it("다른 워크스페이스를 고르면 그 워크스페이스 홈으로 이동하고 메뉴를 닫는다", async () => {
    const { router } = renderSwitcher();
    await openMenu();

    fireEvent.click(await findWorkspaceItem(otherWorkspace.name));

    expect(router.state.location.pathname).toBe(OTHER_HOME_PATH);
    expect(
      screen.queryByRole("region", { name: "워크스페이스 메뉴" }),
    ).not.toBeInTheDocument();
  });

  it("지금 워크스페이스를 고르면 이동하지 않고 메뉴만 닫는다", async () => {
    const { router } = renderSwitcher();
    await openMenu();

    fireEvent.click(await findWorkspaceItem(currentWorkspace.name));

    expect(router.state.location.pathname).toBe(HOME_PATH);
    expect(
      screen.queryByRole("region", { name: "워크스페이스 메뉴" }),
    ).not.toBeInTheDocument();
  });

  it("새 워크스페이스 만들기를 고르면 생성 화면으로 이동한다", async () => {
    const { router } = renderSwitcher();
    await openMenu();

    fireEvent.click(
      screen.getByRole("button", { name: "새 워크스페이스 만들기" }),
    );

    expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE_CREATE);
  });

  describe("녹음 중이면", () => {
    it("다른 워크스페이스를 고를 때 녹음을 끝낼지 묻고 이동하지 않는다", async () => {
      await startRecording();
      const { router } = renderSwitcher();
      await openMenu();

      fireEvent.click(await findWorkspaceItem(otherWorkspace.name));

      expect(
        screen.getByRole("dialog", { name: END_RECORDING_DIALOG }),
      ).toBeInTheDocument();
      expect(router.state.location.pathname).toBe(HOME_PATH);
    });

    it("일시정지 중이어도 묻는다", async () => {
      await startRecording();
      act(() => useRecordingStore.getState().pauseRecording());
      renderSwitcher();
      await openMenu();

      fireEvent.click(await findWorkspaceItem(otherWorkspace.name));

      expect(
        screen.getByRole("dialog", { name: END_RECORDING_DIALOG }),
      ).toBeInTheDocument();
    });

    it("끝내고 이동을 누르면 녹음을 버리고 그 워크스페이스로 이동한다", async () => {
      await startRecording();
      const { router } = renderSwitcher();
      await openMenu();
      fireEvent.click(await findWorkspaceItem(otherWorkspace.name));

      fireEvent.click(screen.getByRole("button", { name: "끝내고 이동" }));

      expect(useRecordingStore.getState().status).toBe("idle");
      expect(router.state.location.pathname).toBe(OTHER_HOME_PATH);
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("취소를 누르면 녹음과 화면을 그대로 둔다", async () => {
      await startRecording();
      const { router } = renderSwitcher();
      await openMenu();
      fireEvent.click(await findWorkspaceItem(otherWorkspace.name));

      fireEvent.click(screen.getByRole("button", { name: "취소" }));

      expect(useRecordingStore.getState().status).toBe("recording");
      expect(router.state.location.pathname).toBe(HOME_PATH);
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });

    it("새 워크스페이스 만들기도 묻고, 끝내고 이동하면 생성 화면으로 간다", async () => {
      await startRecording();
      const { router } = renderSwitcher();
      await openMenu();

      fireEvent.click(
        screen.getByRole("button", { name: "새 워크스페이스 만들기" }),
      );
      expect(router.state.location.pathname).toBe(HOME_PATH);

      fireEvent.click(screen.getByRole("button", { name: "끝내고 이동" }));

      expect(useRecordingStore.getState().status).toBe("idle");
      expect(router.state.location.pathname).toBe(PATH_ROUTE.WORKSPACE_CREATE);
    });

    it("지금 워크스페이스를 고르면 묻지 않고 메뉴만 닫는다", async () => {
      await startRecording();
      renderSwitcher();
      await openMenu();

      fireEvent.click(await findWorkspaceItem(currentWorkspace.name));

      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(useRecordingStore.getState().status).toBe("recording");
    });
  });
});
