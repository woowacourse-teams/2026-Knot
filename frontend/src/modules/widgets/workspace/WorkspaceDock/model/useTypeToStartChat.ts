import { useEffect } from "react";

import isTypingTarget from "../utils/isTypingTarget";

interface UseTypeToStartChatParams {
  /** 글자 키를 눌렀을 때 부를 함수. 누른 글자를 넘겨요. 조립 중인 한글이면 빈 문자열이에요 */
  onType: (typedText: string) => void;
}

/**
 * 화면 아무 데서나 글자를 치면 독에서 이어 쓰게 합니다.
 *
 * 브라우저는 포커스가 옮겨 가기 전에 눌린 키를 이전 자리로 보내므로, 누른 글자는 여기서 직접 넘겨줍니다.
 *
 * 아래는 가로채지 않습니다. 화면이 늘어나도 다른 조작을 잡아먹지 않게 하려는 것입니다.
 * - `Ctrl`·`Cmd`·`Alt`가 눌린 조합 (브라우저·앱 단축키)
 * - 글자가 아닌 키 (`Tab`·`Enter`·방향키처럼 이름이 두 글자 이상인 키)
 * - 이미 입력창·contenteditable에 적고 있을 때
 *
 * 한글은 IME가 조립을 시작하면 눌린 글자를 알 수 없어, 글자 없이 독만 열고 커서를 옮깁니다.
 */
export const useTypeToStartChat = ({ onType }: UseTypeToStartChatParams) => {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return;
      if (isTypingTarget(e.target)) return;

      if (e.isComposing || e.key === "Process") {
        onType("");
        return;
      }

      if (e.key.length !== 1) return;

      e.preventDefault();
      onType(e.key);
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onType]);
};
