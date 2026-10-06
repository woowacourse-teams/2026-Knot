import styled from "@emotion/styled";
import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import Toast, { type ToastVariant } from "@primitives/ui/Toast";

/** 한 번에 보이는 토스트 개수. 넘치면 가장 오래된 토스트부터 빠져요 */
const MAX_TOAST_COUNT = 3;

/** show에 넘기는 토스트 내용 */
interface ToastOptions {
  variant: ToastVariant;
  /** 알릴 문구 */
  message: string;
}

export interface ToastControls {
  /**
   * 토스트를 띄워요.
   * 종류와 문구가 모두 같은 토스트가 이미 떠 있으면 새로 쌓지 않고 그대로 둬요
   */
  show: (toast: ToastOptions) => void;
}

const ToastContext = createContext<ToastControls | null>(null);

const isSameToast = (a: ToastOptions, b: ToastOptions) =>
  a.variant === b.variant && a.message === b.message;

/**
 * 지금 떠 있는 토스트들을 띄운 순서대로 들고 있어요.
 *
 * - `toasts`: 떠 있는 토스트 목록. 가장 오래된 토스트가 맨 앞이에요
 * - `show`: 토스트를 맨 뒤에 쌓아요. `MAX_TOAST_COUNT`를 넘으면 맨 앞(가장 오래된) 토스트를 바로 빼요
 *
 * 종류와 문구가 모두 같은 토스트가 이미 떠 있으면 쌓지 않아서, 같은 알림이 여러 줄로 겹치지 않고
 * 떠 있던 자리도 그대로예요. 문구가 같아도 종류가 다르면 다른 알림으로 보고 따로 쌓아요.
 */
const useToastStack = () => {
  const [toasts, setToasts] = useState<ToastOptions[]>([]);

  const show = useCallback((nextToast: ToastOptions) => {
    setToasts((current) => {
      if (current.some((toast) => isSameToast(toast, nextToast))) {
        return current;
      }

      return [...current, nextToast].slice(-MAX_TOAST_COUNT);
    });
  }, []);

  return { toasts, show };
};

/**
 * 어느 화면에서든 `useToast`로 토스트를 띄울 수 있게 해 주는 프로바이더.
 *
 * 떠 있는 토스트를 띄운 순서대로 위에서 아래로 쌓아 그려요. 가장 최근 토스트가 맨 아래예요.
 * 토스트 목록 상자는 토스트가 없어도 늘 그려 둬서, 나중에 낭독기가 새로 들어온 토스트를 놓치지 않게 해요.
 * 겉모양은 `Toast`가, 무엇이 떠 있는지는 `useToastStack`이 맡아요.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { toasts, show } = useToastStack();

  const controlsValue = useMemo<ToastControls>(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={controlsValue}>
      {children}
      {/* role은 #510에서 정해요. 그 전까지 테스트는 testid로 이 상자를 찾아요 */}
      <ToastList data-testid="toast-list">
        {toasts.map(({ variant, message }) => (
          // 같은 종류·문구는 한 번만 떠 있으므로 둘을 합치면 겹치지 않는 key가 돼요.
          // 사라지는 애니메이션처럼 같은 토스트가 잠깐 둘 존재할 수 있게 되면 id로 key를 바꿔야 해요
          <Toast
            key={`${variant}:${message}`}
            variant={variant}
            message={message}
          />
        ))}
      </ToastList>
    </ToastContext.Provider>
  );
}

/**
 * 토스트를 띄우는 함수를 돌려줘요. `ToastProvider` 안에서만 부를 수 있어요.
 *
 * 토스트는 띄우기만 하고 돌려받는 값은 없어요.
 *
 * @example
 * const { show } = useToast();
 *
 * // 녹음 직후 문서를 모두 확인했을 때
 * const handleAllDocumentsChecked = () => {
 *   show({ variant: "success", message: "문서를 모두 확인했어요" });
 * };
 *
 * @example
 * // 실패했고 다시 하면 될 때는 error로 띄워요
 * const { show } = useToast();
 *
 * show({
 *   variant: "error",
 *   message: "질문을 보내지 못했어요. 잠시 후 다시 시도해 주세요.",
 * });
 */
export const useToast = () => {
  const controls = useContext(ToastContext);

  if (controls === null) {
    throw new Error(
      "useToast는 ToastProvider 안에서만 쓸 수 있어요. ToastProvider를 앱 최상단에 넣어 주세요.",
    );
  }

  return controls;
};

const ToastList = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem; /* 8px */
`;
