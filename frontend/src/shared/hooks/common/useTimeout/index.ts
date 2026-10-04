import { useCallback, useEffect, useRef, useState } from "react";

interface UseTimeoutParams {
  /** `start`를 호출한 뒤 `isTimedOut`이 다시 `false`로 돌아가기까지의 시간(ms). */
  timeout?: number;
  /** 시간이 지나면 실행될 콜백 함수 */
  callback?: () => void;
}

/**
 * 일정 시간이 지나면 상태를 리셋하는 훅.
 *
 * `start`를 호출하면 `isTimedOut`이 `true`가 되고, `timeout`이 지나면 저절로 `false`로 돌아가며 `callback`을 실행해요.
 * 타이머가 도는 중에 재렌더링되어도 타이머는 유지되고, 콜백은 항상 가장 최근에 전달된 함수가 실행돼요.
 * 타이머가 도는 중에 `start`를 다시 호출하면 기존 타이머를 취소하고 처음부터 다시 재요.
 * `clear`를 호출하면 도는 타이머를 취소해요. `callback`은 실행되지 않고 `isTimedOut`은 `false`로 돌아가요.
 * 언마운트되면 타이머를 정리해서 콜백이 실행되지 않아요.
 *
 * 대상마다 따로 피드백을 주려면 대상 수만큼 훅을 호출하면 돼요.
 */
const useTimeout = ({ timeout = 2000, callback }: UseTimeoutParams = {}) => {
  const [isTimedOut, setIsTimedOut] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const callbackRef = useRef(callback);

  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  // 타이머만 지워요. 상태는 두므로 새 타이머를 걸기 전과 언마운트될 때 써요
  const clearTimer = useCallback(() => {
    if (timerRef.current === null) return;

    clearTimeout(timerRef.current);
    timerRef.current = null;
  }, []);

  const start = useCallback(() => {
    clearTimer();
    setIsTimedOut(true);

    timerRef.current = setTimeout(() => {
      timerRef.current = null;
      setIsTimedOut(false);
      callbackRef.current?.();
    }, timeout);
  }, [clearTimer, timeout]);

  const clear = useCallback(() => {
    clearTimer();
    setIsTimedOut(false);
  }, [clearTimer]);

  useEffect(() => clearTimer, [clearTimer]);

  return { isTimedOut, start, clear };
};

export default useTimeout;
