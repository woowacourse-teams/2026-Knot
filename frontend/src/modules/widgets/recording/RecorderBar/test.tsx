import { ThemeProvider } from "@emotion/react";
import { useRecordingStore } from "@store/recordingStore";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { createMemoryRouter, Outlet, RouterProvider } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import RecorderBar from ".";

const WORKSPACE_ID = "1";
const RECORDING_PATH = getRouterPath({
  routeKey: "RECORDING",
  params: { workspaceId: WORKSPACE_ID },
});
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});

/** 녹음 상태는 전역 저장소에 있으므로, 녹음 화면과 홈을 오가며 녹음이 이어지는지 봐요. */
const renderRecorderBar = () => {
  const router = createMemoryRouter(
    [
      {
        element: <Outlet />,
        children: [
          { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>홈 화면</p> },
          { path: PATH_ROUTE.RECORDING, element: <RecorderBar /> },
        ],
      },
    ],
    { initialEntries: [RECORDING_PATH] },
  );

  render(
    <ThemeProvider theme={theme}>
      <RouterProvider router={router} />
    </ThemeProvider>,
  );

  return { router };
};

const getElapsedTime = () => screen.getByLabelText("녹음한 시간");

/** 녹음 시간은 1초마다 다시 그려지므로, 시계를 앞당긴 뒤 그 변화를 반영해요. */
const passSeconds = (seconds: number) => {
  act(() => {
    vi.advanceTimersByTime(seconds * 1000);
  });
};

describe("RecorderBar", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: false });
  });

  afterEach(() => {
    // 전역 저장소라 테스트끼리 녹음이 새지 않도록 처음 상태로 되돌려요
    useRecordingStore.getState().endRecording();
    vi.useRealTimers();
  });

  it("녹음 화면에 들어오면 바로 녹음을 시작한다", () => {
    renderRecorderBar();

    expect(screen.getByText("녹음 중")).toBeInTheDocument();
    expect(getElapsedTime()).toHaveTextContent("00:00");

    passSeconds(3);

    expect(getElapsedTime()).toHaveTextContent("00:03");
  });

  it("일시정지하면 시간이 멈추고, 이어서 녹음하면 멈춘 자리부터 다시 흐른다", () => {
    renderRecorderBar();
    passSeconds(5);

    fireEvent.click(screen.getByRole("button", { name: "일시정지" }));
    passSeconds(10);

    expect(screen.getByText("일시정지")).toBeInTheDocument();
    expect(getElapsedTime()).toHaveTextContent("00:05");

    fireEvent.click(screen.getByRole("button", { name: "이어서 녹음" }));
    passSeconds(2);

    expect(screen.getByText("녹음 중")).toBeInTheDocument();
    expect(getElapsedTime()).toHaveTextContent("00:07");
  });

  it("다른 화면에 다녀와도 새로 시작하지 않고 이어지던 녹음을 보여준다", async () => {
    const { router } = renderRecorderBar();
    passSeconds(4);

    await act(async () => {
      await router.navigate(HOME_PATH);
    });
    passSeconds(6);
    await act(async () => {
      await router.navigate(RECORDING_PATH);
    });

    expect(getElapsedTime()).toHaveTextContent("00:10");
  });

  it("녹음을 끝내면 홈으로 나간다", async () => {
    const { router } = renderRecorderBar();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "녹음 끝내기" }));
    });

    expect(router.state.location.pathname).toBe(HOME_PATH);
  });

  it("끝낸 뒤 다시 들어오면 새 녹음을 0초부터 시작한다", async () => {
    const { router } = renderRecorderBar();
    passSeconds(8);

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "녹음 끝내기" }));
    });
    await act(async () => {
      await router.navigate(RECORDING_PATH);
    });

    expect(getElapsedTime()).toHaveTextContent("00:00");
  });
});
