import { useCallback, useEffect, useRef } from "react";

interface UseReturnFocusParams {
  /** 켜져 있는 동안은 기억한 자리를 들고 있고, 꺼지면 그 자리로 돌아가요 */
  isActive: boolean;
}

/**
 * 모달처럼 포커스를 가져가는 UI가 꺼지면 켜기 전 포커스 자리로 돌려줍니다.
 *
 * 켜는 쪽이 `remember`를 부르면 지금 포커스 자리를 기억하고, `isActive`가 꺼지면 그 자리로 돌려줘요.
 * 켜진 채 다음 것으로 이어질 때는 처음 기억한 자리를 그대로 들고 있어요.
 * 기억은 렌더 뒤가 아니라 켜기 직전에 해야 해서, 새로 그린 내용이 `autoFocus`로
 * 포커스를 먼저 가져가도 켜기 전 자리를 놓치지 않아요.
 */
const useReturnFocus = ({ isActive }: UseReturnFocusParams) => {
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const remember = useCallback(() => {
    if (returnFocusRef.current !== null) return;
    if (!(document.activeElement instanceof HTMLElement)) return;

    returnFocusRef.current = document.activeElement;
  }, []);

  useEffect(() => {
    if (isActive) return;

    // 내용이 먼저 지워져 브라우저가 포커스를 돌려주지 못해서 직접 돌려줘요
    returnFocusRef.current?.focus();
    returnFocusRef.current = null;
  }, [isActive]);

  return { remember };
};

export default useReturnFocus;
