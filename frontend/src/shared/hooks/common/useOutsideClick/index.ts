import { useEffect, useRef } from "react";

interface UseOutsideClickParams {
  /** 켜져 있을 때만 바깥 클릭을 봐요. 닫혀 있는 동안에는 볼 필요가 없어요 */
  isEnabled: boolean;
  /** 바깥을 눌렀을 때 부를 함수 */
  onOutsideClick: () => void;
}

/**
 * 돌려준 `ref`를 붙인 요소의 바깥을 눌렀는지 알려줍니다.
 *
 * 열려 있는 것을 바깥을 눌러 닫는, 드롭다운·팝오버·입력창에서 두루 쓰는 UI 로직이라
 * 도메인을 알지 못합니다.
 *
 * `click`이 아니라 `pointerdown`을 보는 이유는 두 가지입니다. 누르는 순간 바로 닫혀 반응이 빠르고,
 * 누른 뒤 요소가 사라져 `click`이 끝내 오지 않는 경우에도 놓치지 않습니다.
 */
const useOutsideClick = <T extends HTMLElement>({
  isEnabled,
  onOutsideClick,
}: UseOutsideClickParams) => {
  const ref = useRef<T>(null);

  // 핸들러가 바뀔 때마다 리스너를 다시 달지 않도록 최신 것만 들고 있어요
  const handlerRef = useRef(onOutsideClick);
  handlerRef.current = onOutsideClick;

  useEffect(() => {
    if (!isEnabled) return;

    const handlePointerDown = (e: PointerEvent) => {
      if (!(e.target instanceof Node)) return;
      if (ref.current?.contains(e.target)) return;

      handlerRef.current();
    };

    document.addEventListener("pointerdown", handlePointerDown);

    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [isEnabled]);

  return { ref };
};

export default useOutsideClick;
