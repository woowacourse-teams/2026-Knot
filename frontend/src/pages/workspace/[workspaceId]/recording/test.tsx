import { ThemeProvider } from "@emotion/react";
import { DialogProvider } from "@provider/context/dialogContext";
import { theme } from "@provider/themeProvider";
import { useRecordingStore } from "@store/recordingStore";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, describe, expect, it } from "vitest";

import RecordingPage from ".";

const WORKSPACE_ID = "1";
const RECORDING_PATH = getRouterPath({
  routeKey: "RECORDING",
  params: { workspaceId: WORKSPACE_ID },
});
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});

const renderRecordingPage = () => {
  const router = createMemoryRouter(
    [
      {
        element: <Outlet />,
        children: [
          { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>홈 화면</p> },
          { path: PATH_ROUTE.RECORDING, element: <RecordingPage /> },
        ],
      },
    ],
    { initialEntries: [HOME_PATH, RECORDING_PATH], initialIndex: 1 },
  );

  render(
    <ThemeProvider theme={theme}>
      <DialogProvider>
        <RouterProvider router={router} />
      </DialogProvider>
    </ThemeProvider>,
  );

  return { router };
};

/** 녹음은 독에서 마이크를 받은 뒤 시작하므로, 녹음 화면에 들어오기 전에 미리 시작해 둬요. */
const startRecording = async () => {
  await act(async () => {
    await useRecordingStore.getState().startRecording();
  });
};

describe("RecordingPage", () => {
  afterEach(() => {
    // 전역 저장소라 테스트끼리 녹음이 새지 않도록 처음 상태로 되돌려요
    useRecordingStore.getState().endRecording();
  });

  it("진행 중인 녹음 없이 들어오면 홈으로 보내고, 뒤로 가기로 돌아오지 않는다", async () => {
    const { router } = renderRecordingPage();

    await act(async () => {});

    expect(router.state.location.pathname).toBe(HOME_PATH);
    expect(router.state.historyAction).toBe("REPLACE");
  });

  it("녹음 중이면 녹음 화면에 머문다", async () => {
    await startRecording();
    const { router } = renderRecordingPage();

    await act(async () => {});

    expect(router.state.location.pathname).toBe(RECORDING_PATH);
  });

  it("녹음을 끝내면 홈으로 나간다", async () => {
    await startRecording();
    const { router } = renderRecordingPage();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "녹음 끝내기" }));
    });

    expect(router.state.location.pathname).toBe(HOME_PATH);
  });
});
