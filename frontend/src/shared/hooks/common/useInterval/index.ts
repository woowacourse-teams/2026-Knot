import { useCallback, useEffect, useRef } from "react";

interface UseIntervalParams {
  /** 반복 실행할 콜백 함수 */
  callback: () => void;
  /** 반복 간격(ms). */
  delay: number;
}

/**
 * 일정 간격으로 반복 실행하는 훅.
 *
 * start를 호출하면 반복이 시작되고, reset을 호출하면 반복이 멈춰요.
 *
 * start를 여러번 호출해도 중복으로 반복이 실행되지 않아요.
 *
 * 콜백은 항상 가장 최근에 전달된 함수가 실행돼요.
 *
 * 언마운트되면 타이머를 정리해서 콜백이 실행되지 않아요.
 */
const useInterval = ({ callback, delay }: UseIntervalParams) => {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const callbackRef = useRef(callback);

  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  const start = useCallback(() => {
    if (intervalRef.current !== null) return;

    intervalRef.current = setInterval(() => {
      callbackRef.current();
    }, delay);
  }, [delay]);

  const reset = useCallback(() => {
    if (intervalRef.current === null) return;

    clearInterval(intervalRef.current);
    intervalRef.current = null;
  }, []);

  // 언마운트되면 타이머를 정리하기
  useEffect(() => reset, [reset]);

  return {
    start,
    reset,
  };
};

export default useInterval;
