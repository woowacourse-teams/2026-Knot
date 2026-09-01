import { ThemeProvider } from "@emotion/react";
import { theme } from "@provider/themeProvider";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import WorkspaceInviteMemberCard from ".";

// TODO(#229): 초대 API 연결 후 msw 응답으로 교체
const INVITE_CODE = "X35D3S";
// TODO(#229): 초대 API 연결 후 응답의 linkToken으로 교체. 링크 진입 게이트의 임시 통과 토큰과 같은 값이에요
const LINK_TOKEN = "Xk3vQ9mZp2LrT7wB1nHc4A";
const DISPLAY_INVITE_LINK = `/invite/${LINK_TOKEN}`;
const COPIED_DURATION_MS = 2000;

const writeText = vi.fn<(text: string) => Promise<void>>();

const renderCard = () => {
  render(
    <ThemeProvider theme={theme}>
      <WorkspaceInviteMemberCard />
    </ThemeProvider>,
  );

  const linkInput = screen.getByRole("textbox", { name: "초대 링크" });
  const copyLinkButton = screen.getByRole("button", { name: "복사" });
  const copyCodeButton = screen.getByRole("button", { name: "초대 코드 복사" });

  return { linkInput, copyLinkButton, copyCodeButton };
};

const click = async (element: HTMLElement) => {
  await act(async () => {
    fireEvent.click(element);
  });
};

const advanceTimers = async (ms: number) => {
  await act(async () => {
    vi.advanceTimersByTime(ms);
  });
};

describe("WorkspaceInviteMemberCard", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    writeText.mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText },
      configurable: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    writeText.mockReset();
    Reflect.deleteProperty(navigator, "clipboard");
  });

  it("초대 링크를 읽기 전용으로 보여준다", () => {
    const { linkInput } = renderCard();

    expect(linkInput).toHaveValue(DISPLAY_INVITE_LINK);
    expect(linkInput).toHaveAttribute("readonly");
  });

  it("복사를 누르면 전체 링크를 복사하고 2초 동안 복사됨을 보여준다", async () => {
    const { copyLinkButton } = renderCard();

    await click(copyLinkButton);

    expect(writeText).toHaveBeenCalledWith(
      `${window.location.origin}${DISPLAY_INVITE_LINK}`,
    );
    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "복사" }),
    ).not.toBeInTheDocument();

    await advanceTimers(COPIED_DURATION_MS - 1);

    expect(screen.getByRole("button", { name: "복사됨" })).toBeInTheDocument();

    await advanceTimers(1);

    expect(screen.getByRole("button", { name: "복사" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "복사됨" }),
    ).not.toBeInTheDocument();
  });

  it("초대 코드 복사를 누르면 6자 코드를 복사하고 2초 동안 복사됨으로 바꾼다", async () => {
    const { copyCodeButton } = renderCard();

    await click(copyCodeButton);

    expect(writeText).toHaveBeenCalledWith(INVITE_CODE);
    expect(copyCodeButton).toHaveTextContent("복사됨");
    expect(screen.getByRole("button", { name: "복사" })).toBeInTheDocument();

    await advanceTimers(COPIED_DURATION_MS);

    expect(copyCodeButton).toHaveTextContent("초대 코드 복사");
  });

  it("클립보드에 쓰지 못하면 화면이 바뀌지 않는다", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    writeText.mockRejectedValue(new Error("denied"));
    const { copyLinkButton, copyCodeButton } = renderCard();

    await click(copyLinkButton);
    await click(copyCodeButton);

    expect(copyLinkButton).toHaveTextContent("복사");
    expect(copyLinkButton).not.toHaveTextContent("복사됨");
    expect(copyCodeButton).toHaveTextContent("초대 코드 복사");
  });
});
