import useKeyDown from "@hooks/common/useKeyDown";
import useOutsideClick from "@hooks/common/useOutsideClick";
import { useId, useState } from "react";

/**
 * 워크스페이스 메뉴의 열림 상태를 다룹니다.
 *
 * 워크스페이스 이름을 누르면 열고 닫으며, 바깥을 누르거나 ESC를 눌러도 닫혀요.
 */
export const useWorkspaceMenu = () => {
  const menuId = useId();
  const [isOpen, setIsOpen] = useState(false);

  const close = () => setIsOpen(false);

  // 트리거 버튼도 이 영역 안에 있어야 트리거로 닫을 때 바깥 클릭과 겹쳐 다시 열리지 않아요
  const { ref: menuAreaRef } = useOutsideClick<HTMLDivElement>({
    isEnabled: isOpen,
    onOutsideClick: close,
  });

  useKeyDown({ key: "Escape", isEnabled: isOpen, onKeyDown: close });

  const handleToggle = () => setIsOpen((prev) => !prev);

  return { menuId, menuAreaRef, isOpen, handleToggle, close };
};
