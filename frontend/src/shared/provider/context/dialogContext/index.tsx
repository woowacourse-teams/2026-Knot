import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

interface DialogRenderParams {
  /** 이 모달을 닫아요 */
  close: () => void;
}

type DialogRender = (params: DialogRenderParams) => ReactNode;

export interface DialogControls {
  /**
   * 넘긴 함수가 그리는 모달을 띄워요. 이미 떠 있으면 그 모달로 바뀌어요.
   * 모달 안에서 다음 모달을 열 때는 `close()`를 먼저 부르고 열어요
   */
  open: (render: DialogRender) => void;
  /** 떠 있는 모달을 닫아요 */
  close: () => void;
}

const DialogContext = createContext<DialogControls | null>(null);

/**
 * 어느 화면에서든 `useDialog`로 모달을 띄울 수 있게 해 주는 프로바이더.
 *
 * 지금 떠 있는 모달 하나만 들고 있고, 무엇을 어떻게 그릴지는 모르므로
 * 모달의 종류나 겉모양이 바뀌어도 이 파일은 바뀌지 않아요.
 * 모달이 모두 닫히면 처음 모달을 열기 전 포커스 자리로 돌아가요.
 */
export function DialogProvider({ children }: { children: ReactNode }) {
  const [render, setRender] = useState<DialogRender | null>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const open = useCallback((nextRender: DialogRender) => {
    // 모달에서 다음 모달로 이어질 때는 처음 연 자리를 그대로 기억해요
    if (
      returnFocusRef.current === null &&
      document.activeElement instanceof HTMLElement
    ) {
      returnFocusRef.current = document.activeElement;
    }

    // 함수를 그대로 넘기면 상태 갱신 함수로 불리므로 한 번 감싸요
    setRender(() => nextRender);
  }, []);

  const close = useCallback(() => setRender(null), []);

  useEffect(() => {
    if (render !== null) return;

    returnFocusRef.current?.focus();
    returnFocusRef.current = null;
  }, [render]);

  const controls = useMemo(() => ({ open, close }), [open, close]);

  return (
    <DialogContext.Provider value={controls}>
      {children}
      {render?.({ close })}
    </DialogContext.Provider>
  );
}

/**
 * 모달을 여닫는 함수를 돌려줘요. `DialogProvider` 안에서만 부를 수 있어요.
 *
 * @example
 * const { open } = useDialog();
 *
 * open(({ close }) => (
 *   <Dialog label="녹음을 끝낼까요?" onEscape={close} onDimClick={close}>
 *     <ConfirmDialog
 *       title="녹음을 끝낼까요?"
 *       description="끝낸 녹음은 다시 이어 갈 수 없어요."
 *       cancelLabel="계속 녹음"
 *       confirmLabel="녹음 끝내기"
 *       onCancel={close}
 *       onConfirm={() => {
 *         close();
 *         endRecording();
 *       }}
 *     />
 *   </Dialog>
 * ));
 */
export const useDialog = () => {
  const controls = useContext(DialogContext);

  if (controls === null) {
    throw new Error(
      "useDialog는 DialogProvider 안에서만 쓸 수 있어요. DialogProvider를 앱 최상단에 넣어 주세요.",
    );
  }

  return controls;
};
