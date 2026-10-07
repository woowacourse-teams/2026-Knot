import styled from "@emotion/styled";
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

import useTimeout from "@hooks/common/useTimeout";
import FadeCollapse, {
  FADE_COLLAPSE_LEAVE_MS,
} from "@primitives/animation/FadeCollapse";
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
  /** 띄울 때마다 늘어나는 번호예요. 같은 토스트를 다시 띄워 시간만 다시 셀 때는 그대로라 나타나는 애니메이션이 다시 재생되지 않아요 */
  id: number;
  /** 마지막으로 띄운 시각이에요. 바뀌면 떠 있는 시간을 처음부터 다시 세요 */
  shownAt: number;
  /** 화면에 떠 있는 시간이 다 돼 사라지는 애니메이션이 재생되는 중이에요 */
  isLeaving: boolean;
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
  const nextIdRef = useRef(0);

  const show = useCallback((nextToast: ToastOptions) => {
    const shownAt = Date.now();
    const id = nextIdRef.current++;

    setToasts((current) => {
      // 사라지는 중인 토스트는 이미 끝난 알림이라, 같은 토스트를 다시 띄우면 새로 쌓아요
      const isStayingSameToast = (toast: ShownToast) =>
        !toast.isLeaving && isSameToast(toast, nextToast);

      if (current.some(isStayingSameToast)) {
        return current.map((toast) =>
          isStayingSameToast(toast) ? { ...toast, shownAt } : toast,
        );
      }

      const next = [
        ...current,
        { ...nextToast, id, shownAt, isLeaving: false },
      ];
      const stayingToasts = next.filter((toast) => !toast.isLeaving);

      if (stayingToasts.length <= MAX_TOAST_COUNT) return next;

      return next.filter((toast) => toast !== stayingToasts[0]);
    });
  }, []);

  const leave = useCallback((id: number) => {
    setToasts((current) =>
      current.map((toast) =>
        toast.id === id ? { ...toast, isLeaving: true } : toast,
      ),
    );
  }, []);

  const remove = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  return { toasts, show, leave, remove };
};

interface ToastItemProps {
  toast: ShownToast;
  /** 떠 있는 시간이 다 되면 불러요 */
  onExpire: (id: number) => void;
  /** 사라지는 애니메이션이 끝나면 불러요 */
  onLeaveEnd: (id: number) => void;
}

/** 토스트 하나가 나타나고, 떠 있는 시간을 세고, 사라지기까지를 맡는 컴포넌트 */
function ToastItem({ toast, onExpire, onLeaveEnd }: ToastItemProps) {
  const { id, variant, message, shownAt, isLeaving } = toast;

  const { start: startShowing } = useTimeout({
    timeout: TOAST_DURATION_MS[variant],
    callback: () => onExpire(id),
  });

  const { start: startLeaving } = useTimeout({
    timeout: FADE_COLLAPSE_LEAVE_MS,
    callback: () => onLeaveEnd(id),
  });

  useEffect(() => {
    startShowing();
  }, [startShowing, shownAt]);

  useEffect(() => {
    if (isLeaving) startLeaving();
  }, [isLeaving, startLeaving]);

  return (
    // 사라지는 중인 토스트는 이미 끝난 알림이라 낭독기가 다시 읽지 않게 숨겨요
    <div aria-hidden={isLeaving}>
      <FadeCollapse isLeaving={isLeaving}>
        <ToastGapWrapper>
          <Toast variant={variant} message={message} />
        </ToastGapWrapper>
      </FadeCollapse>
    </div>
  );
}

/**
 * 어느 화면에서든 `useToast`로 토스트를 띄울 수 있게 해 주는 프로바이더.
 * 가장 최근 토스트가 맨 아래에 쌓여요.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const { toasts, show, leave, remove } = useToastStack();

  const controlsValue = useMemo<ToastControls>(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={controlsValue}>
      {children}
      {/* 토스트가 없어도 늘 그려 둬야 낭독기가 새로 들어온 토스트를 놓치지 않아요 */}
      <ToastList data-testid="toast-list">
        {toasts.map((toast) => (
          <ToastItem
            key={toast.id}
            toast={toast}
            onExpire={leave}
            onLeaveEnd={remove}
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
`;

// 간격을 목록의 gap이 아니라 칸 안에 둬야 칸이 접힐 때 간격도 함께 사라져요
const ToastGapWrapper = styled.div`
  padding-top: 0.5rem; /* 8px */
`;
