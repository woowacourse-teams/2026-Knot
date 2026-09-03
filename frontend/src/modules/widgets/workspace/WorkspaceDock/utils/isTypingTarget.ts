const TYPING_TAG_NAMES = ["INPUT", "TEXTAREA", "SELECT"];

/** 값이 없거나 `contenteditable`처럼 켜 두기만 한 것도 편집 가능이라 `false`만 빼요 */
const EDITABLE_SELECTOR = "[contenteditable]:not([contenteditable='false'])";

/**
 * 사용자가 이미 글을 적고 있는 곳인지 봅니다. 여기서 누른 키는 독이 가로채면 안 돼요.
 *
 * 편집 영역 안쪽 요소가 이벤트 대상으로 올 수 있어 조상까지 거슬러 봅니다.
 */
const isTypingTarget = (target: EventTarget | null) => {
  if (!(target instanceof HTMLElement)) return false;

  return (
    TYPING_TAG_NAMES.includes(target.tagName) ||
    target.closest(EDITABLE_SELECTOR) !== null
  );
};

export default isTypingTarget;
