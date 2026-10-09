import { RECORDING_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]";
import { recordingDetailsResponse } from "@api/mock/responses/recording";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import RecordingDocuments from ".";

const WORKSPACE_ID = 1;
// 상태가 서로 다른 mock 녹음: 정리 중 · 내용 없음 · 문서 만들기 실패 · 전사 실패 · 정리 완료
const [
  organizingRecording,
  noContentRecording,
  failedRecording,
  transcriptionFailedRecording,
  completedRecording,
] = recordingDetailsResponse;
// 경로 파라미터 자리에 무엇이 와도 잡도록 fetch 상수 대신 패턴을 적어요
const RECORDING_REQUEST =
  "*/api/v1/workspaces/:workspaceId/recordings/:recordingId";
const RETRY_REQUEST =
  "*/api/v1/workspaces/:workspaceId/document-generation-jobs/:jobId/retry";
const POLL_INTERVAL_MS = 3000;

const ORGANIZING_TITLE = "회의 내용을 바탕으로 문서를 정리하고 있어요";
const FAILED_TITLE = "문서를 만들지 못했어요";
const NO_CONTENT_TITLE = "문서로 만들 내용이 없었어요";
const LOAD_FAILED_TITLE = "문서를 불러오지 못했어요";
const RECORDING_KEPT_NOTICE = "녹음은 보관해 두었어요.";

// 주소에 id가 빠진 경우를 보려고 위젯을 놓는 경로예요
const NO_RECORDING_ID_PATH = "/workspace/:workspaceId/no-recording-id";
const NO_WORKSPACE_ID_PATH = "/no-workspace-id/:recordingId";

const renderRecordingDocuments = (recordingId: number | string) =>
  renderRecordingDocumentsAt(
    getRouterPath({
      routeKey: "RECORDING_DOCUMENTS",
      params: {
        workspaceId: String(WORKSPACE_ID),
        recordingId: String(recordingId),
      },
    }),
  );

const renderRecordingDocumentsAt = (entry: string) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  // 이동을 확인할 수 있게 홈 · 녹음 · 로그인 경로에 표시만 하는 화면을 둬요
  const router = createMemoryRouter(
    [
      {
        path: PATH_ROUTE.RECORDING_DOCUMENTS,
        element: <RecordingDocuments />,
      },
      { path: NO_RECORDING_ID_PATH, element: <RecordingDocuments /> },
      { path: NO_WORKSPACE_ID_PATH, element: <RecordingDocuments /> },
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>워크스페이스 홈</p> },
      { path: PATH_ROUTE.RECORDING, element: <p>녹음 화면</p> },
      { path: PATH_ROUTE.LOGIN, element: <p>로그인 화면</p> },
    ],
    { initialEntries: [entry] },
  );

  render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );
};

describe("RecordingDocuments", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("문서를 정리하는 중이면 정리 중이라고 알리고 버튼은 두지 않는다", async () => {
    renderRecordingDocuments(organizingRecording.recordingId);

    expect(
      await screen.findByRole("heading", { level: 2, name: ORGANIZING_TITLE }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("주제별로 문서를 나누는 중이에요."),
    ).toBeInTheDocument();
    expect(screen.getByText("조금만 기다려 주세요.")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("문서로 만들 내용이 없었으면 그 결과를 알리고, 홈으로를 누르면 워크스페이스 홈으로 간다", async () => {
    renderRecordingDocuments(noContentRecording.recordingId);

    expect(
      await screen.findByRole("heading", { level: 2, name: NO_CONTENT_TITLE }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("대화가 너무 짧거나 정리할 논의를 찾지 못했어요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("녹음은 따로 저장하지 않았어요."),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "홈으로" }));

    expect(await screen.findByText("워크스페이스 홈")).toBeInTheDocument();
  });

  it("문서 만들기에 실패했으면 녹음을 보관했다고 알리고 다시 시도 버튼을 보여준다", async () => {
    renderRecordingDocuments(failedRecording.recordingId);

    expect(
      await screen.findByRole("heading", { level: 2, name: FAILED_TITLE }),
    ).toBeInTheDocument();
    expect(screen.getByText(RECORDING_KEPT_NOTICE)).toBeInTheDocument();
    expect(
      screen.getByText(
        "다시 시도하거나, 홈의 진행 중인 녹음에서 나중에 다시 시도할 수 있어요.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("다시 시도를 누르면 문서 만들기를 다시 요청하고, 접수되면 정리 중 화면으로 돌아간다", async () => {
    renderRecordingDocuments(failedRecording.recordingId);

    fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));

    // 기본 mock은 다시 시도를 접수하면 그 녹음의 상태를 정리 중으로 바꿔 돌려줘요
    expect(
      await screen.findByRole("heading", { level: 2, name: ORGANIZING_TITLE }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it.each([
    { reason: "전사에 실패", recording: transcriptionFailedRecording },
    {
      reason: "오디오 업로드에 실패",
      recording: {
        ...transcriptionFailedRecording,
        audioUploadStatus: "FAILED",
        transcriptionStatus: "NOT_STARTED",
        failureStage: "AUDIO_UPLOAD",
        failureReason: "AUDIO_UPLOAD_FAILED",
      },
    },
    {
      reason: "문서 만들기에 실패했지만 다시 시도할 작업이 없음",
      recording: { ...failedRecording, documentGenerationJobId: null },
    },
  ])(
    "$reason: 설명과 다시 시도 없이 홈으로만 보여준다",
    async ({ recording }) => {
      mockServer.use(
        http.get(
          `*${RECORDING_API_PATH(WORKSPACE_ID, recording.recordingId)}`,
          () => HttpResponse.json(recording),
        ),
      );
      renderRecordingDocuments(recording.recordingId);

      expect(
        await screen.findByRole("heading", { level: 2, name: FAILED_TITLE }),
      ).toBeInTheDocument();
      expect(screen.queryByText(RECORDING_KEPT_NOTICE)).not.toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "다시 시도" }),
      ).not.toBeInTheDocument();

      fireEvent.click(screen.getByRole("button", { name: "홈으로" }));

      expect(await screen.findByText("워크스페이스 홈")).toBeInTheDocument();
    },
  );

  it("정리 중에는 3초마다 다시 조회하고, 결과가 정해지면 화면을 바꾸고 더 조회하지 않는다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(RECORDING_REQUEST, () => {
        requestCount += 1;
        // 두 번째 조회부터는 문서로 만들 내용이 없었다는 결과를 돌려줘요
        return HttpResponse.json(
          requestCount === 1
            ? organizingRecording
            : {
                ...noContentRecording,
                recordingId: organizingRecording.recordingId,
              },
        );
      }),
    );
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderRecordingDocuments(organizingRecording.recordingId);

    await screen.findByRole("heading", { level: 2, name: ORGANIZING_TITLE });
    expect(requestCount).toBe(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
    });

    expect(
      await screen.findByRole("heading", { level: 2, name: NO_CONTENT_TITLE }),
    ).toBeInTheDocument();
    expect(requestCount).toBe(2);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);
    });

    expect(requestCount).toBe(2);
  });

  it.each([403, 404])(
    "정리 중에 녹음이 없어지거나 볼 수 없게 되면(%i) 문서를 불러오지 못했다고 알리고 더 조회하지 않는다",
    async (status) => {
      let requestCount = 0;
      mockServer.use(
        http.get(RECORDING_REQUEST, () => {
          requestCount += 1;
          // 두 번째 조회부터는 녹음이 없어졌거나 볼 수 없게 된 응답을 돌려줘요
          return requestCount === 1
            ? HttpResponse.json(organizingRecording)
            : new HttpResponse(null, { status });
        }),
      );
      vi.useFakeTimers({ shouldAdvanceTime: true });
      renderRecordingDocuments(organizingRecording.recordingId);

      await screen.findByRole("heading", { level: 2, name: ORGANIZING_TITLE });

      await act(async () => {
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
      });

      expect(
        await screen.findByRole("heading", {
          level: 2,
          name: LOAD_FAILED_TITLE,
        }),
      ).toBeInTheDocument();
      expect(requestCount).toBe(2);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);
      });

      expect(requestCount).toBe(2);
    },
  );

  it("정리 중에 다시 조회가 서버 오류로 실패하면 정리 중 화면을 유지하고 계속 조회한다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(RECORDING_REQUEST, () => {
        requestCount += 1;
        return requestCount === 1
          ? HttpResponse.json(organizingRecording)
          : new HttpResponse(null, { status: 500 });
      }),
    );
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderRecordingDocuments(organizingRecording.recordingId);

    await screen.findByRole("heading", { level: 2, name: ORGANIZING_TITLE });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
    });
    await waitFor(() => expect(requestCount).toBe(2));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
    });
    await waitFor(() => expect(requestCount).toBe(3));

    expect(
      screen.getByRole("heading", { level: 2, name: ORGANIZING_TITLE }),
    ).toBeInTheDocument();
  });

  it("문서 정리가 끝났으면 워크스페이스 홈으로 보낸다", async () => {
    renderRecordingDocuments(completedRecording.recordingId);

    expect(await screen.findByText("워크스페이스 홈")).toBeInTheDocument();
  });

  it.each(["RECORDING", "PAUSED"] as const)(
    "아직 녹음 중이면(%s) 녹음 화면으로 보낸다",
    async (status) => {
      mockServer.use(
        http.get(RECORDING_REQUEST, () =>
          HttpResponse.json({
            ...organizingRecording,
            status,
            sessionStatus: status,
            endedAt: null,
            durationMillis: null,
          }),
        ),
      );
      renderRecordingDocuments(organizingRecording.recordingId);

      expect(await screen.findByText("녹음 화면")).toBeInTheDocument();
    },
  );

  it("없는 녹음이면 문서를 불러오지 못했다고 알리고, 다시 시도를 누르면 다시 조회한다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(RECORDING_REQUEST, () => {
        requestCount += 1;
        return new HttpResponse(null, { status: 404 });
      }),
    );
    renderRecordingDocuments(organizingRecording.recordingId);

    expect(
      await screen.findByRole("heading", { level: 2, name: LOAD_FAILED_TITLE }),
    ).toBeInTheDocument();
    expect(requestCount).toBe(1);

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => expect(requestCount).toBe(2));
    expect(
      screen.getByRole("heading", { level: 2, name: LOAD_FAILED_TITLE }),
    ).toBeInTheDocument();
  });

  it.each([400, 403])(
    "%i 응답도 문서를 불러오지 못했다고 알린다",
    async (status) => {
      mockServer.use(
        http.get(RECORDING_REQUEST, () => new HttpResponse(null, { status })),
      );
      renderRecordingDocuments(organizingRecording.recordingId);

      expect(
        await screen.findByRole("heading", {
          level: 2,
          name: LOAD_FAILED_TITLE,
        }),
      ).toBeInTheDocument();
    },
  );

  it.each([
    [
      "녹음 id가 숫자가 아니면",
      getRouterPath({
        routeKey: "RECORDING_DOCUMENTS",
        params: { workspaceId: String(WORKSPACE_ID), recordingId: "abc" },
      }),
    ],
    ["주소에 녹음 id가 없으면", `/workspace/${WORKSPACE_ID}/no-recording-id`],
    [
      "주소에 워크스페이스 id가 없으면",
      `/no-workspace-id/${organizingRecording.recordingId}`,
    ],
  ])(
    "%s 요청하지 않고 문서를 불러오지 못했다고 알린다",
    async (_condition, entry) => {
      let requestCount = 0;
      mockServer.use(
        http.get(RECORDING_REQUEST, () => {
          requestCount += 1;
          return HttpResponse.json(organizingRecording);
        }),
      );
      renderRecordingDocumentsAt(entry);

      expect(
        await screen.findByRole("heading", {
          level: 2,
          name: LOAD_FAILED_TITLE,
        }),
      ).toBeInTheDocument();
      expect(requestCount).toBe(0);
    },
  );

  it("서버 오류로 처음 조회에 실패하면 문서를 불러오지 못했다고 알리고, 다시 시도를 누르면 상태를 보여준다", async () => {
    mockServer.use(
      http.get(
        RECORDING_REQUEST,
        () => new HttpResponse(null, { status: 500 }),
        { once: true },
      ),
    );
    renderRecordingDocuments(organizingRecording.recordingId);

    expect(
      await screen.findByRole("heading", { level: 2, name: LOAD_FAILED_TITLE }),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(
      await screen.findByRole("heading", { level: 2, name: ORGANIZING_TITLE }),
    ).toBeInTheDocument();
  });

  it("서버 오류로 처음 조회에 실패한 뒤에는 저절로 다시 조회하지 않고 실패 화면을 유지한다", async () => {
    let requestCount = 0;
    mockServer.use(
      http.get(RECORDING_REQUEST, () => {
        requestCount += 1;
        return new HttpResponse(null, { status: 500 });
      }),
    );
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderRecordingDocuments(organizingRecording.recordingId);

    await screen.findByRole("heading", { level: 2, name: LOAD_FAILED_TITLE });
    expect(requestCount).toBe(1);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2);
    });

    expect(requestCount).toBe(1);
    expect(
      screen.getByRole("heading", { level: 2, name: LOAD_FAILED_TITLE }),
    ).toBeInTheDocument();
  });

  it("로그인이 풀렸으면(401) 로그인 화면으로 보낸다", async () => {
    mockServer.use(
      http.get(
        RECORDING_REQUEST,
        () => new HttpResponse(null, { status: 401 }),
      ),
    );
    renderRecordingDocuments(organizingRecording.recordingId);

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });

  it.each([409, 404])(
    "다시 시도가 거절되면(%i) 설명과 다시 시도 없이 홈으로만 보여준다",
    async (status) => {
      mockServer.use(
        http.post(RETRY_REQUEST, () => new HttpResponse(null, { status })),
      );
      renderRecordingDocuments(failedRecording.recordingId);

      fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));

      expect(
        await screen.findByRole("button", { name: "홈으로" }),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "다시 시도" }),
      ).not.toBeInTheDocument();
      expect(screen.queryByText(RECORDING_KEPT_NOTICE)).not.toBeInTheDocument();
      expect(
        screen.getByRole("heading", { level: 2, name: FAILED_TITLE }),
      ).toBeInTheDocument();
    },
  );

  it("다시 시도가 서버 오류로 실패하면 버튼을 남겨 다시 누를 수 있게 한다", async () => {
    let retryCount = 0;
    mockServer.use(
      http.post(RETRY_REQUEST, () => {
        retryCount += 1;
        return new HttpResponse(null, { status: 500 });
      }),
    );
    renderRecordingDocuments(failedRecording.recordingId);

    fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(retryCount).toBe(1));

    await waitFor(() =>
      expect(screen.getByRole("button", { name: "다시 시도" })).toBeEnabled(),
    );
    expect(screen.getByText(RECORDING_KEPT_NOTICE)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    await waitFor(() => expect(retryCount).toBe(2));
  });

  it("다시 시도에서 로그인이 풀렸으면(401) 로그인 화면으로 보낸다", async () => {
    mockServer.use(
      http.post(RETRY_REQUEST, () => new HttpResponse(null, { status: 401 })),
    );
    renderRecordingDocuments(failedRecording.recordingId);

    fireEvent.click(await screen.findByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("로그인 화면")).toBeInTheDocument();
  });
});
