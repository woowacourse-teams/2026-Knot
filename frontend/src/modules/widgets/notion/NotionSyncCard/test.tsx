import { GetNotionImportStatusResponseDto } from "@api/dto/notionImport";
import { NOTION_IMPORT_STATUS_API_PATH } from "@api/fetch/api/v1/imports/[importRunId]";
import { NOTION_IMPORTS_API_PATH } from "@api/fetch/api/v1/workspaces/[workspaceId]/imports";
import {
  notionImportStartResponse,
  notionImportStatusResponse,
} from "@api/mock/responses/notionImport";
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
import { delay, http, HttpResponse } from "msw";
import { createMemoryRouter, RouterProvider } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";

import NotionSyncCard from ".";

const expectedStatus = new GetNotionImportStatusResponseDto(
  notionImportStatusResponse,
);

const WORKSPACE_ID = 1;
const HOME_PATH = getRouterPath({
  routeKey: "WORKSPACE_HOME",
  params: { workspaceId: String(WORKSPACE_ID) },
});
const LAST_SYNCED_TEXT = "어제 오후 3:12에 동기화";
const SYNCED_TEXT = `문서 ${expectedStatus.processedPageCount}개가 새로 들어왔어요`;
const START_FAILED_TEXT = "동기화에 실패했어요. 잠시 후 다시 시도해 주세요";
const RESULT_RESET_DELAY_MS = 2000;

// FAILED 상태로 덮을 때 쓰는 mock 변형. 기대 문구도 같은 값을 DTO로 변환해 가져와요
const failedStatusResponse = {
  ...notionImportStatusResponse,
  status: "FAILED" as const,
  failureReason: "Notion 문서를 가져오지 못했습니다",
};
const FAILED_REASON_TEXT =
  new GetNotionImportStatusResponseDto(failedStatusResponse).failureReason ??
  "";

const renderCard = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const router = createMemoryRouter(
    [{ path: PATH_ROUTE.WORKSPACE_HOME, element: <NotionSyncCard /> }],
    { initialEntries: [HOME_PATH] },
  );

  const view = render(
    <ThemeProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </ThemeProvider>,
  );

  const syncButton = screen.getByRole("button", { name: "지금 동기화" });

  return { ...view, syncButton };
};

const click = async (element: HTMLElement) => {
  await act(async () => {
    fireEvent.click(element);
  });
};

/** 가짜 타이머를 ms만큼 진행시키며 사이사이의 응답 마이크로태스크도 흘려보내요 */
const advanceTimers = async (ms: number) => {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
};

/** 시작 응답을 붙잡아 요청 중 상태를 관찰할 수 있게 해요 */
const holdStartResponse = () => {
  mockServer.use(
    http.post(`*${NOTION_IMPORTS_API_PATH(WORKSPACE_ID)}`, async () => {
      await delay("infinite");
      return HttpResponse.json(notionImportStartResponse, { status: 202 });
    }),
  );
};

const failStartResponse = () => {
  mockServer.use(
    http.post(`*${NOTION_IMPORTS_API_PATH(WORKSPACE_ID)}`, () =>
      HttpResponse.json(null, { status: 500 }),
    ),
  );
};

const failImportStatus = () => {
  mockServer.use(
    http.get(
      `*${NOTION_IMPORT_STATUS_API_PATH(notionImportStatusResponse.id)}`,
      () => HttpResponse.json(failedStatusResponse),
    ),
  );
};

describe("NotionSyncCard", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("처음에는 마지막 동기화 시각과 지금 동기화 버튼을 보여준다", () => {
    const { syncButton } = renderCard();

    expect(screen.getByText(LAST_SYNCED_TEXT)).toBeInTheDocument();
    expect(syncButton).toBeEnabled();
    expect(syncButton).toHaveAttribute("aria-busy", "false");
  });

  it("지금 동기화를 누르면 서버 응답이 올 때까지 버튼이 로딩 상태로 잠긴다", async () => {
    holdStartResponse();
    const { syncButton } = renderCard();

    await click(syncButton);

    await waitFor(() => expect(syncButton).toBeDisabled());
    expect(syncButton).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText(LAST_SYNCED_TEXT)).toBeInTheDocument();
  });

  it("동기화가 완료되면 새로 들어온 문서 안내와 비활성 완료 버튼으로 바뀐다", async () => {
    const { syncButton } = renderCard();

    await click(syncButton);

    // 문서 수는 mock 상태 응답에서만 올 수 있어, 보이면 시작·상태 요청이 실제로 나간 거예요
    expect(await screen.findByText(SYNCED_TEXT)).toBeInTheDocument();
    expect(screen.queryByText(LAST_SYNCED_TEXT)).not.toBeInTheDocument();

    const doneButton = screen.getByRole("button", { name: "완료" });
    expect(doneButton).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "지금 동기화" }),
    ).not.toBeInTheDocument();
  });

  it("완료 안내는 2초 뒤 기본 상태로 돌아온다", async () => {
    vi.useFakeTimers();
    const { syncButton } = renderCard();

    await click(syncButton);
    await advanceTimers(0); // 시작·상태 응답을 흘려보내요

    expect(screen.getByText(SYNCED_TEXT)).toBeInTheDocument();

    await advanceTimers(RESULT_RESET_DELAY_MS - 1);

    expect(screen.getByText(SYNCED_TEXT)).toBeInTheDocument();

    await advanceTimers(1);

    expect(screen.getByText(LAST_SYNCED_TEXT)).toBeInTheDocument();
    expect(screen.queryByText(SYNCED_TEXT)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "지금 동기화" })).toBeEnabled();
  });

  it("동기화가 실패로 끝나면 실패 사유를 보여주고 2초 뒤 기본 상태로 돌아온다", async () => {
    failImportStatus();
    vi.useFakeTimers();
    const { syncButton } = renderCard();

    await click(syncButton);
    await advanceTimers(0); // 시작·상태 응답을 흘려보내요

    expect(screen.getByText(FAILED_REASON_TEXT)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "지금 동기화" })).toBeEnabled();

    await advanceTimers(RESULT_RESET_DELAY_MS);

    expect(screen.getByText(LAST_SYNCED_TEXT)).toBeInTheDocument();
    expect(screen.queryByText(FAILED_REASON_TEXT)).not.toBeInTheDocument();
  });

  it("시작 요청이 실패하면 실패 안내를 보여주고 2초 뒤 기본 상태로 돌아온다", async () => {
    failStartResponse();
    vi.useFakeTimers();
    const { syncButton } = renderCard();

    await click(syncButton);
    await advanceTimers(0); // 실패 응답을 흘려보내요

    expect(screen.getByText(START_FAILED_TEXT)).toBeInTheDocument();

    await advanceTimers(RESULT_RESET_DELAY_MS);

    expect(screen.getByText(LAST_SYNCED_TEXT)).toBeInTheDocument();
    expect(screen.queryByText(START_FAILED_TEXT)).not.toBeInTheDocument();
  });

  it("완료 안내 중 언마운트되면 복귀 타이머를 정리해 오류 없이 끝난다", async () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    vi.useFakeTimers();
    const { syncButton, unmount } = renderCard();

    await click(syncButton);
    await advanceTimers(0); // 시작·상태 응답을 흘려보내요
    expect(screen.getByText(SYNCED_TEXT)).toBeInTheDocument();

    unmount();
    await advanceTimers(RESULT_RESET_DELAY_MS);

    expect(consoleError).not.toHaveBeenCalled();
  });
});
