import { GetMeResponseDto } from "@api/dto/auth";
import { GetCurrentRecordingResponseDto } from "@api/dto/recording";
import { AUTH_ME_API_PATH } from "@api/fetch/api/v1/auth/me";
import { CURRENT_RECORDING_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/current";
import { meResponse } from "@api/mock/responses/auth";
import { currentRecordingResponse } from "@api/mock/responses/recording";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { useRecordingStore } from "@store/recordingStore";
import {
  focusManager,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { formatRecordingTime } from "@utils/formatRecordingTime";
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import RecordingListCard from ".";

const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const RECORDING_PATH = getRouterPath({
  routeKey: "RECORDING",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const CURRENT_URL = `*${CURRENT_RECORDING_API_PATH(WORKSPACE_ID)}`;

const expectedRecording = new GetCurrentRecordingResponseDto(
  currentRecordingResponse,
);
const RECORDING_TITLE = `${new GetMeResponseDto(meResponse).nickname} 님의 녹음`;
const EMPTY_TEXT = "진행 중인 녹음이 없어요";
const OPEN_RECORDING = "녹음 화면으로";
const MS_PER_SECOND = 1000;

/** 응답의 누적 시간에 초를 더한 시간 표시예요 */
const elapsedTimeAfter = (seconds: number) =>
  formatRecordingTime(
    expectedRecording.elapsedMillis / MS_PER_SECOND + seconds,
  );

const renderCard = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <RecordingListCard /> },
      { path: PATH_ROUTE.RECORDING, element: null },
    ],
    { initialEntries: [HOME_PATH] },
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

/** 현재 녹음 조회가 이 응답으로 답하게 해요 */
const respondCurrentRecording = (
  response: () => Response | Promise<Response>,
) => {
  mockServer.use(http.get(CURRENT_URL, response));
};

const respondNoRecording = () =>
  respondCurrentRecording(() => new HttpResponse(null, { status: 204 }));

const respondStatus = (
  status: "RECORDING" | "PAUSED" | "ENDED" | "PROCESSING" | "FAILED",
) =>
  respondCurrentRecording(() =>
    HttpResponse.json({ ...currentRecordingResponse, status }),
  );

/** 가짜 타이머를 ms만큼 진행시키며 사이사이의 응답 마이크로태스크도 흘려보내요 */
const advanceTimers = async (ms: number) => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};

/** 독에서 시작한 것처럼 이 탭에서 녹음을 시작해 둬요 */
const startRecordingInThisTab = async () => {
  await act(async () => {
    await useRecordingStore.getState().connectMicrophone();
    useRecordingStore.getState().startRecording({
      workspaceId: WORKSPACE_ID,
      recordingId: expectedRecording.recordingId,
    });
  });
};

describe("RecordingListCard", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    // 전역 저장소라 테스트끼리 녹음이 새지 않도록 처음 상태로 되돌려요
    useRecordingStore.getState().discardRecording();
  });

  it("현재 녹음을 조회하는 동안에는 카드가 불러오는 중으로 표시된다", () => {
    respondCurrentRecording(async () => {
      await delay("infinite");
      return new HttpResponse(null, { status: 204 });
    });

    renderCard();

    expect(
      screen.getByRole("region", { name: "진행 중인 녹음" }),
    ).toHaveAttribute("aria-busy", "true");
  });

  it("내 닉네임을 조회하는 동안에는 녹음이 있어도 녹음 칸 대신 불러오는 중으로 표시된다", async () => {
    mockServer.use(
      http.get(`*${AUTH_ME_API_PATH}`, async () => {
        await delay("infinite");
        return HttpResponse.json(meResponse);
      }),
    );
    vi.useFakeTimers();
    renderCard();
    await advanceTimers(0); // 현재 녹음 조회 응답을 흘려보내요

    expect(
      screen.getByRole("region", { name: "진행 중인 녹음" }),
    ).toHaveAttribute("aria-busy", "true");
    expect(
      screen.queryByText(`녹음 중 · ${elapsedTimeAfter(0)}`),
    ).not.toBeInTheDocument();
  });

  it("진행 중인 녹음이 없으면(204) 빈 상태 문구를 보여 주고 버튼은 두지 않는다", async () => {
    respondNoRecording();

    renderCard();

    expect(await screen.findByText(EMPTY_TEXT)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: OPEN_RECORDING }),
    ).not.toBeInTheDocument();
  });

  it("녹음 중이면 녹음 중 상태·경과 시간과 내 닉네임의 녹음 이름을 보여 준다", async () => {
    renderCard();

    expect(
      await screen.findByText(`녹음 중 · ${elapsedTimeAfter(0)}`),
    ).toBeInTheDocument();
    expect(await screen.findByText(RECORDING_TITLE)).toBeInTheDocument();
  });

  it("녹음 중이면 응답을 받은 뒤 흐른 시간만큼 경과 시간이 매초 늘어난다", async () => {
    vi.useFakeTimers();
    renderCard();
    await advanceTimers(0); // 조회 응답을 흘려보내요

    expect(
      screen.getByText(`녹음 중 · ${elapsedTimeAfter(0)}`),
    ).toBeInTheDocument();

    await advanceTimers(3 * MS_PER_SECOND);

    expect(
      screen.getByText(`녹음 중 · ${elapsedTimeAfter(3)}`),
    ).toBeInTheDocument();
  });

  it("일시정지면 일시정지 상태와 멈춘 경과 시간을 보여 준다", async () => {
    respondStatus("PAUSED");
    vi.useFakeTimers();
    renderCard();
    await advanceTimers(0); // 조회 응답을 흘려보내요

    await advanceTimers(3 * MS_PER_SECOND);

    expect(
      screen.getByText(`일시정지 · ${elapsedTimeAfter(0)}`),
    ).toBeInTheDocument();
  });

  it.each(["ENDED", "PROCESSING", "FAILED"] as const)(
    "%s처럼 녹음 중·일시정지가 아닌 녹음은 빈 상태로 보여 준다",
    async (status) => {
      respondStatus(status);

      renderCard();

      expect(await screen.findByText(EMPTY_TEXT)).toBeInTheDocument();
    },
  );

  it("조회에 실패하면 빈 상태로 보여 주고 오류는 콘솔에만 남긴다", async () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    respondCurrentRecording(() => HttpResponse.json(null, { status: 500 }));

    renderCard();

    expect(await screen.findByText(EMPTY_TEXT)).toBeInTheDocument();
    expect(consoleError).toHaveBeenCalled();
  });

  it("다른 탭에서 시작한 녹음이면 「녹음 화면으로」 버튼을 숨긴다", async () => {
    renderCard();

    expect(await screen.findByText(RECORDING_TITLE)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: OPEN_RECORDING }),
    ).not.toBeInTheDocument();
  });

  it("이 탭에서 시작한 녹음이면 「녹음 화면으로」를 눌러 녹음 화면으로 간다", async () => {
    await startRecordingInThisTab();
    const { router } = renderCard();

    await act(async () => {
      fireEvent.click(
        await screen.findByRole("button", { name: OPEN_RECORDING }),
      );
    });

    expect(router.state.location.pathname).toBe(RECORDING_PATH);
  });

  it("이 탭에서 시작한 녹음이면 서버 응답이 아니라 이 탭의 상태와 시간을 보여 준다", async () => {
    // 서버가 아직 녹음을 모르는 것처럼 답해도 이 탭의 녹음을 보여 줘요
    respondNoRecording();
    await startRecordingInThisTab();
    renderCard();

    expect(
      await screen.findByText(`녹음 중 · ${formatRecordingTime(0)}`),
    ).toBeInTheDocument();

    await act(async () => {
      useRecordingStore.getState().pauseRecording();
    });

    expect(
      screen.getByText(`일시정지 · ${formatRecordingTime(0)}`),
    ).toBeInTheDocument();
  });

  it("창으로 돌아오면 현재 녹음을 다시 조회한다", async () => {
    renderCard();
    expect(await screen.findByText(RECORDING_TITLE)).toBeInTheDocument();

    respondNoRecording();
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    await waitFor(() =>
      expect(screen.getByText(EMPTY_TEXT)).toBeInTheDocument(),
    );
    focusManager.setFocused(undefined);
  });
});
