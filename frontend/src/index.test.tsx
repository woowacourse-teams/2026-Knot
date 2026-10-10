import { useDialog } from "@provider/context/dialogContext";
import CenteredLayout from "@pages/_layout/CenteredLayout";
import { useToast } from "@provider/context/toastContext";
import { act, fireEvent, screen } from "@testing-library/react";
import { useNavigate } from "react-router";
import { describe, expect, it, vi } from "vitest";

const { NEXT_PATH, NEXT_TEXT } = vi.hoisted(() => ({
  NEXT_PATH: "/next",
  NEXT_TEXT: "다음 화면",
}));

const COPY_FAILED = "복사하지 못했어요. 다시 시도해 주세요.";

// 실제 화면 대신 모달을 띄우는 테스트용 화면만 두고, 프로바이더 조립(index.tsx)은 그대로 써요.
// 이 호출은 import보다 먼저 실행되므로 JSX 대신 끌어올려지는 함수 선언을 넘겨요
vi.mock("@routes/routes", () => ({
  routes: [
    {
      Component: CenteredLayout,
      children: [
        { path: "/", Component: OpenerPage },
        { path: NEXT_PATH, Component: NextPage },
      ],
    },
  ],
}));

function NextPage() {
  return <p>{NEXT_TEXT}</p>;
}

interface MoveDialogProps {
  close: () => void;
}

/** 모달 안에서 라우터 훅과 토스트를 직접 쓰는 모달 */
function MoveDialog({ close }: MoveDialogProps) {
  const navigate = useNavigate();
  const { show } = useToast();

  const handleMoveClick = () => {
    close();
    navigate(NEXT_PATH);
  };

  return (
    <div role="dialog" aria-modal="true" aria-label="이동할까요?">
      <button type="button" onClick={handleMoveClick}>
        이동
      </button>
      <button
        type="button"
        onClick={() => show({ variant: "error", message: COPY_FAILED })}
      >
        복사 실패 알리기
      </button>
    </div>
  );
}

function OpenerPage() {
  const { open } = useDialog();

  return (
    <button
      type="button"
      onClick={() => open(({ close }) => <MoveDialog close={close} />)}
    >
      모달 열기
    </button>
  );
}

describe("앱 진입점", () => {
  it("화면에서 띄운 모달 안에서 다른 화면으로 이동할 수 있다", async () => {
    document.body.innerHTML = '<div id="root"></div>';

    // index.tsx는 불러오는 순간 #root에 앱을 그려요
    await import("./index");

    fireEvent.click(await screen.findByRole("button", { name: "모달 열기" }));
    fireEvent.click(screen.getByRole("button", { name: "이동" }));

    expect(await screen.findByText(NEXT_TEXT)).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("화면에서 띄운 모달 안에서 토스트를 띄울 수 있다", async () => {
    if (!document.getElementById("root")) {
      document.body.innerHTML = '<div id="root"></div>';
    }

    // index.tsx는 처음 불러올 때만 앱을 그려서, 이미 그려졌으면 뒤로 가기처럼 첫 화면으로 되돌려요
    await import("./index");
    act(() => {
      window.history.pushState(null, "", "/");
      window.dispatchEvent(new PopStateEvent("popstate"));
    });

    fireEvent.click(await screen.findByRole("button", { name: "모달 열기" }));
    fireEvent.click(screen.getByRole("button", { name: "복사 실패 알리기" }));

    expect(await screen.findByText(COPY_FAILED)).toBeInTheDocument();
  });
});
