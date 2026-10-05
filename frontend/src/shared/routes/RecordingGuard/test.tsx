import { useRecordingStore } from "@store/recordingStore";
import { act, render, screen } from "@testing-library/react";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it } from "vitest";

import { getRouterPath, PATH_ROUTE } from "../PATH_ROUTE";

import RecordingGuard from ".";

const WORKSPACE_ID = "1";
const RECORDING_TEXT = "녹음 화면";
const RECORDING_PATH = getRouterPath({
  routeKey: "RECORDING",
  params: { workspaceId: WORKSPACE_ID },
});
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: WORKSPACE_ID },
});

const renderGuard = () => {
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>홈 화면</p> },
      {
        element: <RecordingGuard />,
        children: [
          { path: PATH_ROUTE.RECORDING, element: <p>{RECORDING_TEXT}</p> },
        ],
      },
    ],
    { initialEntries: [HOME_PATH, RECORDING_PATH], initialIndex: 1 },
  );

  render(<RouterProvider router={router} />);

  return { router };
};

/** 녹음은 독에서 마이크를 받은 뒤 시작하므로, 녹음 화면에 들어오기 전에 미리 시작해 둬요. */
const startRecording = async () => {
  await act(async () => {
    await useRecordingStore.getState().startRecording();
  });
};

describe("RecordingGuard", () => {
  afterEach(() => {
    // 전역 저장소라 테스트끼리 녹음이 새지 않도록 처음 상태로 되돌려요
    useRecordingStore.getState().endRecording();
  });

  it("진행 중인 녹음 없이 들어오면 홈으로 보내고, 뒤로 가기로 돌아오지 않는다", async () => {
    const { router } = renderGuard();

    await act(async () => {});

    expect(router.state.location.pathname).toBe(HOME_PATH);
    expect(router.state.historyAction).toBe("REPLACE");
  });

  it("녹음 중이면 녹음 화면에 머문다", async () => {
    await startRecording();
    const { router } = renderGuard();

    await act(async () => {});

    expect(router.state.location.pathname).toBe(RECORDING_PATH);
    expect(screen.getByText(RECORDING_TEXT)).toBeInTheDocument();
  });

  it("녹음을 끝내면 홈으로 나간다", async () => {
    await startRecording();
    const { router } = renderGuard();

    await act(async () => {
      useRecordingStore.getState().endRecording();
    });

    expect(router.state.location.pathname).toBe(HOME_PATH);
  });
});
