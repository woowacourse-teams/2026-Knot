import useReturnFocus from "@hooks/common/useReturnFocus";
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
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
 * 지금 떠 있는 모달 하나를 들고 있어요.
 *
 * - `dialog`: 지금 떠 있는 모달 컴포넌트(예: `<ConfirmDialog />`)를 돌려주는 함수. 떠 있는 모달이 없으면 `null`이에요
 * - `show`: 넘긴 모달을 띄워요. 이미 떠 있으면 그 자리의 모달이 새 모달로 바뀌어요
 * - `hide`: 떠 있는 모달을 없애요
 *
 * 모달을 하나만 들고 있어서, 떠 있는 동안 `show`를 부르면 두 모달이 겹치지 않고
 * 원래 모달 자리에 새 모달이 대신 들어가요.
 */
const useOpenedDialog = () => {
  const [currentDialog, setCurrentDialog] = useState<DialogRender | null>(null);

  // 함수를 그대로 넘기면 상태 갱신 함수로 불리므로 한 번 감싸요
  const show = useCallback((nextRender: DialogRender) => {
    setCurrentDialog(() => nextRender);
  }, []);

  const hide = useCallback(() => setCurrentDialog(null), []);

  return { dialog: currentDialog, show, hide };
};

/**
 * 어느 화면에서든 `useDialog`로 모달을 띄울 수 있게 해 주는 프로바이더.
 *
 * 지금 떠 있는 모달 하나만 들고 있고, 무엇을 어떻게 그릴지는 모르므로
 * 모달의 종류나 겉모양이 바뀌어도 이 파일은 바뀌지 않아요.
 * 어떤 모달이 떠 있는지는 `useOpenedDialog`가, 모두 닫히면 처음 모달을 열기 전
 * 포커스 자리로 돌려주는 일은 `useReturnFocus`가 맡고, 여기서는 둘을 이어 주기만 해요.
 */
export function DialogProvider({ children }: { children: ReactNode }) {
  const { dialog, show, hide } = useOpenedDialog();
  const { remember } = useReturnFocus({ isActive: dialog !== null });

  const controlsValue = useMemo<DialogControls>(
    () => ({
      open: (nextRender) => {
        remember();
        show(nextRender);
      },
      close: hide,
    }),
    [remember, show, hide],
  );

  return (
    <DialogContext.Provider value={controlsValue}>
      {children}
      {dialog?.({ close: hide })}
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
 *   <Dim onClick={close}>
 *     <ConfirmDialog
 *       role="dialog"
 *       aria-modal="true"
 *       aria-label="녹음을 끝낼까요?"
 *       onClick={(e) => e.stopPropagation()}
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
 *   </Dim>
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
