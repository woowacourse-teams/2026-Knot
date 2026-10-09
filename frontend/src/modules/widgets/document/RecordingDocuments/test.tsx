import { RECORDING_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/recordings/[recordingId]";
import { recordingDetailsResponse } from "@api/mock/responses/recording";
import { mockServer } from "@api/mock/server";
import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { getRouterPath, PATH_ROUTE } from "@routes/PATH_ROUTE";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";

import RecordingDocuments from ".";

const WORKSPACE_ID = 1;
// 상태가 서로 다른 mock 녹음: 정리 중 · 내용 없음 · 문서 만들기 실패 · 전사 실패
const [
  organizingRecording,
  noContentRecording,
  failedRecording,
  transcriptionFailedRecording,
] = recordingDetailsResponse;

const ORGANIZING_TITLE = "회의 내용을 바탕으로 문서를 정리하고 있어요";
const FAILED_TITLE = "문서를 만들지 못했어요";
const RECORDING_KEPT_NOTICE = "녹음은 보관해 두었어요.";

const renderRecordingDocuments = (recordingId: number) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  // 이동을 확인할 수 있게 홈 경로에 표시만 하는 화면을 둬요
  const router = createMemoryRouter(
    [
      {
        path: PATH_ROUTE.RECORDING_DOCUMENTS,
        element: <RecordingDocuments />,
      },
      { path: PATH_ROUTE.WORKSPACE_HOME, element: <p>워크스페이스 홈</p> },
    ],
    {
      initialEntries: [
        getRouterPath({
          routeKey: "RECORDING_DOCUMENTS",
          params: {
            workspaceId: String(WORKSPACE_ID),
            recordingId: String(recordingId),
          },
        }),
      ],
    },
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
      await screen.findByRole("heading", {
        level: 2,
        name: "문서로 만들 내용이 없었어요",
      }),
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
});
