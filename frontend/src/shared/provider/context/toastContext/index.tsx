import styled from "@emotion/styled";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import useTimeout from "@hooks/common/useTimeout";
import Toast, { type ToastVariant } from "@primitives/ui/Toast";

const MAX_TOAST_COUNT = 3;

/** 주의·오류는 읽고 대처할 시간이 더 필요해 길게 둬요 */
const TOAST_DURATION_MS = {
  success: 5000,
  caution: 8000,
  error: 8000,
} as const satisfies Record<ToastVariant, number>;

interface ToastOptions {
  variant: ToastVariant;
  message: string;
}

interface ShownToast extends ToastOptions {
  /** 마지막으로 띄운 시각이에요. 바뀌면 떠 있는 시간을 처음부터 다시 세요 */
  shownAt: number;
}

export interface ToastControls {
  /**
   * 토스트를 띄워요.
   * 종류와 문구가 모두 같은 토스트가 이미 떠 있으면 새로 쌓지 않고, 떠 있는 시간만 처음부터 다시 세요
   */
  show: (toast: ToastOptions) => void;
}

const ToastContext = createContext<ToastControls | null>(null);

// 종류·문구가 모두 같아야 같은 알림이에요. 문구가 같아도 종류가 다르면 따로 쌓는 게 정책이에요
const isSameToast = (a: ToastOptions, b: ToastOptions) =>
  a.variant === b.variant && a.message === b.message;

const useToastStack = () => {
  const [toasts, setToasts] = useState<ShownToast[]>([]);

  const show = useCallback((nextToast: ToastOptions) => {
    const shownAt = Date.now();

    setToasts((current) => {
      if (current.some((toast) => isSameToast(toast, nextToast))) {
        return current.map((toast) =>
          isSameToast(toast, nextToast) ? { ...toast, shownAt } : toast,
        );
      }

      return [...current, { ...nextToast, shownAt }].slice(-MAX_TOAST_COUNT);
    });
  }, []);

  const remove = useCallback((expiredToast: ToastOptions) => {
    setToasts((current) =>
      current.filter((toast) => !isSameToast(toast, expiredToast)),
    );
  }, []);

  return { toasts, show, remove };
};

interface ToastItemProps {
  toast: ShownToast;
  onExpire: (toast: ToastOptions) => void;
}

/** 토스트 하나가 떠 있는 시간을 세는 컴포넌트 */
function ToastItem({ toast, onExpire }: ToastItemProps) {
  const { variant, message, shownAt } = toast;

  const { start } = useTimeout({
    timeout: TOAST_DURATION_MS[variant],
    callback: () => onExpire({ variant, message }),
  });

  useEffect(() => {
    start();
  }, [start, shownAt]);

  return <Toast variant={variant} message={message} />;
}

/**
 * 어느 화면에서든 `useToast`로 토스트를 띄울 수 있게 해 주는 프로바이더.
 * 가장 최근 토스트가 맨 아래에 쌓여요.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { toasts, show, remove } = useToastStack();

  const controlsValue = useMemo<ToastControls>(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={controlsValue}>
      {children}
      {/* 토스트가 없어도 늘 그려 둬야 낭독기가 새로 들어온 토스트를 놓치지 않아요 */}
      <ToastList data-testid="toast-list">
        {toasts.map((toast) => (
          // key에 shownAt을 넣지 않아요. 다시 띄울 때 새로 그려지면 나타나는 애니메이션이 다시 재생돼요
          <ToastItem
            key={`${toast.variant}:${toast.message}`}
            toast={toast}
            onExpire={remove}
          />
        ))}
      </ToastList>
    </ToastContext.Provider>
  );
}

/**
 * 토스트를 띄우는 함수를 돌려줘요. `ToastProvider` 안에서만 부를 수 있어요.
 *
 * @example
 * const { show } = useToast();
 *
 * show({ variant: "success", message: "문서를 모두 확인했어요" });
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
