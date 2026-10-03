import { fireEvent, render, screen } from "@testing-library/react";
import { useRef } from "react";
import { describe, expect, it } from "vitest";

import { DialogProvider, useDialog } from ".";

const FIRST_LABEL = "첫 모달";
const SECOND_LABEL = "둘째 모달";

function SecondDialog() {
  return <div role="dialog" aria-modal="true" aria-label={SECOND_LABEL} />;
}

/** 모달을 열고, 연 쪽이 돌려받은 `close`로 밖에서 닫아 보는 테스트용 화면 */
function OpenerPage() {
  const { open } = useDialog();
  const closeFirstRef = useRef<(() => void) | null>(null);

  const handleOpenFirstClick = () => {
    closeFirstRef.current = open(({ close }) => (
      <div role="dialog" aria-modal="true" aria-label={FIRST_LABEL}>
        <button
          type="button"
          onClick={() => {
            open(() => <SecondDialog />);
            close();
          }}
        >
          다음 모달로
        </button>
      </div>
    ));
  };

  return (
    <>
      <button type="button" onClick={handleOpenFirstClick}>
        첫 모달 열기
      </button>
      <button type="button" onClick={() => open(() => <SecondDialog />)}>
        둘째 모달 열기
      </button>
      <button type="button" onClick={() => closeFirstRef.current?.()}>
        밖에서 첫 모달 닫기
      </button>
    </>
  );
}

const renderOpenerPage = () =>
  render(
    <DialogProvider>
      <OpenerPage />
    </DialogProvider>,
  );

describe("DialogProvider", () => {
  it("모달을 연 쪽이 돌려받은 close로 그 모달을 닫는다", () => {
    renderOpenerPage();

    fireEvent.click(screen.getByRole("button", { name: "첫 모달 열기" }));
    expect(screen.getByRole("dialog", { name: FIRST_LABEL })).toBeVisible();

    fireEvent.click(
      screen.getByRole("button", { name: "밖에서 첫 모달 닫기" }),
    );

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("다른 모달로 바뀐 뒤에는 이전 모달의 close가 지금 모달을 닫지 않는다", () => {
    renderOpenerPage();

    fireEvent.click(screen.getByRole("button", { name: "첫 모달 열기" }));
    fireEvent.click(screen.getByRole("button", { name: "둘째 모달 열기" }));
    fireEvent.click(
      screen.getByRole("button", { name: "밖에서 첫 모달 닫기" }),
    );

    expect(screen.getByRole("dialog", { name: SECOND_LABEL })).toBeVisible();
  });

  it("모달 안에서 다음 모달을 먼저 열고 close를 불러도 다음 모달이 남는다", () => {
    renderOpenerPage();

    fireEvent.click(screen.getByRole("button", { name: "첫 모달 열기" }));
    fireEvent.click(screen.getByRole("button", { name: "다음 모달로" }));

    expect(screen.getByRole("dialog", { name: SECOND_LABEL })).toBeVisible();
    expect(
      screen.queryByRole("dialog", { name: FIRST_LABEL }),
    ).not.toBeInTheDocument();
  });
});
