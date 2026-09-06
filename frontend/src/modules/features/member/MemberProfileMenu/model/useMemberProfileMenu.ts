import useOutsideClick from "@hooks/common/useOutsideClick";
import useLogout from "@hooks/domain/auth/useLogout";
import { useState } from "react";

/**
 * 프로필 메뉴의 여닫힘과 로그아웃을 묶은 훅.
 *
 * 여닫힘 상태는 이 컴포넌트의 렌더 구조(트리거 버튼과 그 아래 메뉴)에 묶여 있어 여기 둡니다.
 * 로그아웃이 실제로 무엇을 하는지는 화면과 무관하므로 `useLogout`이 가지고, 여기서는 그대로 넘겨요.
 *
 * 메뉴는 바깥을 누르면 닫혀요. 열려 있는 동안에만 바깥 클릭을 봅니다.
 */
const useMemberProfileMenu = () => {
  const [isOpen, setIsOpen] = useState(false);
  const { logout, isLoggingOut } = useLogout();

  const { ref: containerRef } = useOutsideClick<HTMLDivElement>({
    isEnabled: isOpen,
    onOutsideClick: () => setIsOpen(false),
  });

  const toggleMenu = () => setIsOpen((prevIsOpen) => !prevIsOpen);

  return { isOpen, toggleMenu, containerRef, logout, isLoggingOut };
};

export default useMemberProfileMenu;
