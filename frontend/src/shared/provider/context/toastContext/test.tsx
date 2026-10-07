import { ThemeProvider } from "@emotion/react";
import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { theme } from "@provider/themeProvider";

import { ToastProvider, useToast } from ".";

const SAVED = "녹음을 저장했어요";
const COPIED = "초대 링크를 복사했어요";
const SYNCED = "노션과 동기화했어요";
const RENAMED = "대화 이름을 바꿨어요";

const SUCCESS_DURATION_MS = 5000;
const CAUTION_DURATION_MS = 8000;
const ERROR_DURATION_MS = 8000;

function ToastTriggerPage() {
  const { show } = useToast();

  return (
    <>
      {[SAVED, COPIED, SYNCED, RENAMED].map((message) => (
        <button
          key={message}
          type="button"
          onClick={() => show({ variant: "success", message })}
        >
          {message} 알리기
        </button>
      ))}
      <button
        type="button"
        onClick={() => show({ variant: "caution", message: SAVED })}
      >
        {SAVED} 주의로 알리기
      </button>
      <button
        type="button"
        onClick={() => show({ variant: "error", message: SAVED })}
      >
        {SAVED} 오류로 알리기
      </button>
    </>
  );
}

const renderToastTriggerPage = () =>
  render(
    <ThemeProvider theme={theme}>
      <ToastProvider>
        <ToastTriggerPage />
      </ToastProvider>
    </ThemeProvider>,
  );

const clickShow = (message: string) =>
  fireEvent.click(screen.getByRole("button", { name: `${message} 알리기` }));

const advanceTimers = (ms: number) => {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
};

// 목록 상자에 아직 role이 없어 testid로 찾아요
const getToastList = () => screen.getByTestId("toast-list");

const getShownMessages = () =>
  Array.from(getToastList().children, (toast) => toast.textContent);

describe("ToastProvider", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("[ERR-R5] show로 띄운 토스트가 보인다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);

    expect(within(getToastList()).getByText(SAVED)).toBeVisible();
  });

  it("[공통 UI 규칙·여러 개] 4개를 띄우면 3개만 보이고 가장 먼저 띄운 토스트가 사라진다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    clickShow(COPIED);
    clickShow(SYNCED);
    clickShow(RENAMED);

    expect(getShownMessages()).toHaveLength(3);
    expect(within(getToastList()).queryByText(SAVED)).not.toBeInTheDocument();
    // 밀려난 토스트의 시간도 정리돼서 떠 있는 3개의 시간만 남아요
    expect(vi.getTimerCount()).toBe(3);
  });

  it("[공통 UI 규칙·여러 개] 가장 최근 토스트가 맨 아래(독에 가장 가까이)에 놓인다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    clickShow(COPIED);
    clickShow(SYNCED);

    expect(getShownMessages()).toEqual([SAVED, COPIED, SYNCED]);
  });

  it("[공통 UI 규칙·여러 개] 같은 종류·문구를 다시 띄우면 쌓이지 않고 하나만 보인다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    clickShow(SAVED);

    expect(within(getToastList()).getAllByText(SAVED)).toHaveLength(1);
  });

  it("[공통 UI 규칙·여러 개] 같은 종류·문구를 다시 띄워도 떠 있는 토스트는 제자리에 있다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    clickShow(COPIED);
    clickShow(SAVED);

    expect(getShownMessages()).toEqual([SAVED, COPIED]);
  });

  it("[공통 UI 규칙·여러 개] 문구가 같아도 종류가 다르면 따로 쌓인다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    fireEvent.click(
      screen.getByRole("button", { name: `${SAVED} 오류로 알리기` }),
    );

    expect(within(getToastList()).getAllByText(SAVED)).toHaveLength(2);
  });

  it("[공통 UI 규칙·떠 있는 시간] 정상 토스트는 5초 동안 보이고 5초가 지나면 사라진다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    advanceTimers(SUCCESS_DURATION_MS - 1);

    expect(within(getToastList()).getByText(SAVED)).toBeVisible();

    advanceTimers(1);

    expect(within(getToastList()).queryByText(SAVED)).not.toBeInTheDocument();
  });

  it("[공통 UI 규칙·떠 있는 시간] 주의 토스트는 8초가 지나면 사라진다", () => {
    renderToastTriggerPage();

    fireEvent.click(
      screen.getByRole("button", { name: `${SAVED} 주의로 알리기` }),
    );
    advanceTimers(CAUTION_DURATION_MS - 1);

    expect(within(getToastList()).getByText(SAVED)).toBeVisible();

    advanceTimers(1);

    expect(within(getToastList()).queryByText(SAVED)).not.toBeInTheDocument();
  });

  it("[공통 UI 규칙·떠 있는 시간] 오류 토스트는 8초가 지나면 사라진다", () => {
    renderToastTriggerPage();

    fireEvent.click(
      screen.getByRole("button", { name: `${SAVED} 오류로 알리기` }),
    );
    advanceTimers(ERROR_DURATION_MS - 1);

    expect(within(getToastList()).getByText(SAVED)).toBeVisible();

    advanceTimers(1);

    expect(within(getToastList()).queryByText(SAVED)).not.toBeInTheDocument();
  });

  it("[공통 UI 규칙·여러 개] 시차를 두고 띄운 토스트는 각자 시간이 지나면 따로 사라진다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    advanceTimers(2000);
    clickShow(COPIED);
    advanceTimers(SUCCESS_DURATION_MS - 2000);

    expect(getShownMessages()).toEqual([COPIED]);

    advanceTimers(2000);

    expect(getShownMessages()).toHaveLength(0);
  });

  it("[공통 UI 규칙·여러 개] 같은 토스트를 다시 띄우면 시간이 처음부터 다시 간다", () => {
    renderToastTriggerPage();

    clickShow(SAVED);
    advanceTimers(3000);
    clickShow(SAVED);
    advanceTimers(SUCCESS_DURATION_MS - 1);

    expect(within(getToastList()).getByText(SAVED)).toBeVisible();

    advanceTimers(1);

    expect(within(getToastList()).queryByText(SAVED)).not.toBeInTheDocument();
  });

  it("[공통 UI 규칙·접근성] 토스트가 없어도 감싸는 영역은 미리 렌더링된다", () => {
    renderToastTriggerPage();

    expect(getToastList()).toBeInTheDocument();
    expect(getShownMessages()).toHaveLength(0);
  });

  it("프로바이더 밖에서 useToast를 부르면 안내 오류가 난다", () => {
    // 기대한 오류를 React가 콘솔에 한 번 더 찍어서 출력만 막아요
    vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => render(<ToastTriggerPage />)).toThrow(
      "useToast는 ToastProvider 안에서만 쓸 수 있어요",
    );
  });
});
