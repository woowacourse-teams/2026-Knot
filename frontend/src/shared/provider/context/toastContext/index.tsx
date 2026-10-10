import ToastList, {
  type ShownToast,
  type ToastPlacement,
} from "@composites/ToastList";
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
import { useLocation, useNavigate, type To } from "react-router";

import type { ToastVariant } from "@primitives/ui/Toast";

const MAX_TOAST_COUNT = 3;

interface ToastOptions {
  variant: ToastVariant;
  message: string;
}

interface NavigateWithToastParams {
  /** 옮겨 갈 화면 */
  to: To;
  /** 도착한 화면에서 띄울 토스트 */
  toast: ToastOptions;
  /** 지금 화면의 기록을 바꿔 뒤로가기로 돌아오지 않게 해요. 기본값은 `false` */
  replace?: boolean;
}

/** `navigateWithToast`가 이동 기록에 실어 보내는 값 */
interface ToastLocationState {
  toast?: ToastOptions;
}

export interface ToastControls {
  /**
   * 토스트를 띄워요.
   * 종류와 문구가 모두 같은 토스트가 이미 떠 있으면 새로 쌓지 않고, 떠 있는 시간만 처음부터 다시 세요
   */
  show: (toast: ToastOptions) => void;
  /** 화면을 옮기고, 도착한 화면에서 토스트를 띄워요 */
  navigateWithToast: (params: NavigateWithToastParams) => void;
}

const ToastContext = createContext<ToastControls | null>(null);
const ToastStackContext = createContext<ReturnType<typeof useToastStack> | null>(
  null,
);

// 종류·문구가 모두 같아야 같은 알림이에요. 문구가 같아도 종류가 다르면 따로 쌓는 게 정책이에요
const isSameToast = (a: ToastOptions, b: ToastOptions) =>
  a.variant === b.variant && a.message === b.message;

const useToastStack = (hasDock: boolean) => {
  const [toasts, setToasts] = useState<ShownToast[]>([]);
  const [previousHasDock, setPreviousHasDock] = useState(hasDock);
  const nextIdRef = useRef(0);

  // 자식이 새 화면의 알림을 요청하기 전에 이전 목록을 정리해요.
  if (previousHasDock !== hasDock) {
    setPreviousHasDock(hasDock);
    setToasts([]);
  }

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

/**
 * 앱 전체에서 `useToast`로 토스트를 띄울 수 있게 해 주는 프로바이더.
 * 가장 최근 토스트가 맨 아래에 쌓여요.
 *
 * 라우터 안에 한 번 두고, 독 유무가 바뀌면 이전 알림을 정리해요.
 */
interface ToastProviderProps {
  children: ReactNode;
  /** 현재 화면에 독이 있는지. 독이 있으면 레이아웃이 Viewport를 직접 배치해요. */
  hasDock?: boolean;
}

export function ToastProvider({
  children,
  hasDock = false,
}: ToastProviderProps) {
  const stack = useToastStack(hasDock);
  const { show } = stack;
  const location = useLocation();
  const navigate = useNavigate();

  const navigateWithToast = useCallback(
    ({ to, toast, replace = false }: NavigateWithToastParams) => {
      navigate(to, {
        state: { toast } satisfies ToastLocationState,
        replace,
      });
    },
    [navigate],
  );

  useEffect(() => {
    const { toast, ...remainingState } = (location.state ?? {}) as
      ToastLocationState & Record<string, unknown>;

    if (!toast) return;

    show(toast);
    // 기록에 토스트를 남겨 두면 새로고침·뒤로가기 때 다시 떠서 비워요
    navigate(
      {
        pathname: location.pathname,
        search: location.search,
        hash: location.hash,
      },
      {
        replace: true,
        state: Object.keys(remainingState).length > 0 ? remainingState : null,
      },
    );
  }, [location, navigate, show]);

  const contextValue = useMemo<ToastControls>(
    () => ({ show, navigateWithToast }),
    [show, navigateWithToast],
  );

  return (
    <ToastContext.Provider value={contextValue}>
      <ToastStackContext.Provider value={stack}>
        {children}
        {!hasDock && <ToastViewport />}
      </ToastStackContext.Provider>
    </ToastContext.Provider>
  );
}

const useToastContext = (caller: string) => {
  const contextValue = useContext(ToastContext);

  if (contextValue === null) {
    throw new Error(
      `${caller}는 ToastProvider 안에서만 쓸 수 있어요. ToastProvider를 앱 최상단에 넣어 주세요.`,
    );
  }

  return contextValue;
};

/**
 * 토스트를 띄우는 함수를 돌려줘요. `ToastProvider` 안에서만 부를 수 있어요.
 *
 * 지금 화면에 띄울 때는 `show`를, 화면을 옮기며 도착한 화면에 띄울 때는 `navigateWithToast`를 써요.
 * 독 유무가 바뀌면 이전 알림을 지워요. 이때 도착 알림은 `navigateWithToast`로 전달해요.
 *
 * @example
 * const { show } = useToast();
 *
 * show({ variant: "success", message: "문서를 모두 확인했어요" });
 *
 * @example
 * // 로그인이 만료돼 로그인 화면으로 옮기며 알려요. 뒤로가기로 만료된 화면에 돌아오지 않게 기록을 바꿔요
 * const { navigateWithToast } = useToast();
 *
 * navigateWithToast({
 *   to: PATH_ROUTE.LOGIN,
 *   toast: { variant: "caution", message: "로그인이 만료됐어요. 다시 로그인해 주세요" },
 *   replace: true,
 * });
 */
export const useToast = () => {
  const { show, navigateWithToast } = useToastContext("useToast");

  return { show, navigateWithToast };
};

interface ToastViewportProps {
  placement?: ToastPlacement;
}

/**
 * 독 위에 직접 배치하는 토스트 목록. 독 없는 화면의 목록은 Provider가 그려요.
 */
export function ToastViewport({ placement = "floating" }: ToastViewportProps) {
  const stack = useContext(ToastStackContext);

  if (stack === null) {
    throw new Error("ToastViewport는 ToastProvider 안에서만 쓸 수 있어요.");
  }

  return (
    <ToastList
      toasts={stack.toasts}
      placement={placement}
      onExpire={stack.leave}
      onLeaveEnd={stack.remove}
    />
  );
}
