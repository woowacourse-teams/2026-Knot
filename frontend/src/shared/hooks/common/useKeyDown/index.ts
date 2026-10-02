import { useEffect, useRef } from "react";

/**
 * 지켜볼 수 있는 키. `KeyboardEvent.key` 값이에요.
 * 모달·목록·입력창 조작에 쓰는 키만 두고, 필요한 키가 생기면 여기에 더해요.
 */
type KeyDownKey =
  | "Escape"
  | "Enter"
  | "Tab"
  | " " // 스페이스
  | "ArrowUp"
  | "ArrowDown"
  | "ArrowLeft"
  | "ArrowRight";

interface UseKeyDownParams {
  /** 지켜볼 키. 예: `"Escape"`, `"Enter"` */
  key: KeyDownKey;
  /** 켜져 있을 때만 키를 봐요. 볼 필요가 없는 동안에는 꺼 둬요 */
  isEnabled: boolean;
  /** 그 키를 눌렀을 때 부를 함수 */
  onKeyDown: (e: KeyboardEvent) => void;
}

/**
 * 지정한 키를 눌렀는지 알려줍니다.
 *
 * ESC로 닫기, Enter로 확인처럼 키 하나에 동작을 붙이는 UI 로직이라 도메인을 알지 못합니다.
 * 포커스가 어디 있든 받도록 `document`에서 봐요.
 */
const useKeyDown = ({ key, isEnabled, onKeyDown }: UseKeyDownParams) => {
  // 핸들러가 바뀔 때마다 리스너를 다시 달지 않도록 최신 것만 들고 있어요
  const handlerRef = useRef(onKeyDown);
  handlerRef.current = onKeyDown;

  useEffect(() => {
    if (!isEnabled) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== key) return;

      handlerRef.current(e);
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [key, isEnabled]);
};

export default useKeyDown;
