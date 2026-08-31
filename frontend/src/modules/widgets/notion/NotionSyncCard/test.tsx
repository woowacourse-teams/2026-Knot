import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import NotionSyncCard from ".";

// TODO(동기화 API Issue 미정): API 연결 후 msw 응답으로 교체
const SYNC_DELAY_MS = 1000;
const LAST_SYNCED_TEXT = "어제 오후 3:12에 동기화";
const SYNCED_TEXT = "문서 8개가 새로 들어왔어요";

const renderCard = () => {
  const view = render(
    <ThemeProvider theme={theme}>
      <NotionSyncCard />
    </ThemeProvider>,
  );

  const syncButton = screen.getByRole("button", { name: "지금 동기화" });

  return { ...view, syncButton };
};

const advanceTimers = async (ms: number) => {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
};

describe("NotionSyncCard", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

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

  it("지금 동기화를 누르면 버튼이 로딩 상태로 잠긴다", () => {
    const { syncButton } = renderCard();

    fireEvent.click(syncButton);

    expect(syncButton).toBeDisabled();
    expect(syncButton).toHaveAttribute("aria-busy", "true");
    expect(screen.getByText(LAST_SYNCED_TEXT)).toBeInTheDocument();
  });

  it("지연이 끝나기 전에는 완료로 바뀌지 않는다", async () => {
    const { syncButton } = renderCard();

    fireEvent.click(syncButton);
    await advanceTimers(SYNC_DELAY_MS - 1);

    expect(syncButton).toHaveAttribute("aria-busy", "true");
    expect(screen.queryByText(SYNCED_TEXT)).not.toBeInTheDocument();
  });

  it("지연 뒤 새로 들어온 문서 안내와 비활성 완료 버튼으로 바뀐다", async () => {
    const { syncButton } = renderCard();

    fireEvent.click(syncButton);
    await advanceTimers(SYNC_DELAY_MS);

    expect(screen.getByText(SYNCED_TEXT)).toBeInTheDocument();
    expect(screen.queryByText(LAST_SYNCED_TEXT)).not.toBeInTheDocument();

    const doneButton = screen.getByRole("button", { name: "완료" });
    expect(doneButton).toBeDisabled();
    expect(doneButton).toHaveAttribute("aria-busy", "false");
    expect(
      screen.queryByRole("button", { name: "지금 동기화" }),
    ).not.toBeInTheDocument();
  });

  it("지연 중 언마운트되면 타이머를 정리해 오류 없이 끝난다", async () => {
    const consoleError = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const { syncButton, unmount } = renderCard();

    fireEvent.click(syncButton);
    unmount();
    await act(async () => {
      vi.runAllTimers();
    });

    expect(consoleError).not.toHaveBeenCalled();
    expect(screen.queryByText(SYNCED_TEXT)).not.toBeInTheDocument();
  });
});
