import { ThemeProvider } from "@emotion/react";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { theme } from "@provider/themeProvider";

import { ToastProvider, useToast } from ".";

const SAVED = "녹음을 저장했어요";
const COPIED = "초대 링크를 복사했어요";
const SYNCED = "노션과 동기화했어요";
const RENAMED = "대화 이름을 바꿨어요";

/** 버튼을 눌러 토스트를 띄워 보는 테스트용 화면 */
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

// 토스트 목록 상자의 role은 #510에서 정해요. 그 전까지는 이름으로 찾을 방법이 없어 testid로 찾아요
const getToastList = () => screen.getByTestId("toast-list");

/** 목록 상자 안에 보이는 토스트 문구를 위에서부터 순서대로 돌려줘요 */
const getShownMessages = () =>
  Array.from(getToastList().children, (toast) => toast.textContent);

describe("ToastProvider", () => {
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

  it("[공통 UI 규칙·접근성] 토스트가 없어도 감싸는 영역은 미리 렌더링된다", () => {
    renderToastTriggerPage();

    expect(getToastList()).toBeInTheDocument();
    expect(getShownMessages()).toHaveLength(0);
  });

  it("프로바이더 밖에서 useToast를 부르면 안내 오류가 난다", () => {
    // 렌더 중 던진 오류를 React가 콘솔에 한 번 더 찍어요. 기대한 오류라 출력만 막아요
    vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => render(<ToastTriggerPage />)).toThrow(
      "useToast는 ToastProvider 안에서만 쓸 수 있어요",
    );
  });
});
