import useDesktop from "@hooks/common/useDesktop";
import useOutsideClick from "@hooks/common/useOutsideClick";
import useNavigateToAgentConnection from "@hooks/domain/agent/useNavigateToAgentConnection";
import useLogout from "@hooks/domain/auth/useLogout";
import { useState } from "react";

/**
 * 프로필 메뉴의 여닫힘과 로그아웃을 묶은 훅.
 *
 * 여닫힘 상태는 이 컴포넌트의 렌더 구조(트리거 버튼과 그 아래 메뉴)에 묶여 있어 여기 둡니다.
 * 로그아웃이 실제로 무엇을 하는지는 화면과 무관하므로 `useLogout`이 가지고, 여기서는 그대로 넘겨요.
 *
 * 메뉴는 바깥을 누르면 닫혀요. 열려 있는 동안에만 바깥 클릭을 봅니다.
 *
 * `CLI 에이전트 연결` 항목은 로컬 MCP 서버를 띄우는 데스크톱 셸(`knotDesktop.agent`)에서만
 * 뜻이 있어 그때만 보여 줘요(기획서 6.4).
 */
const useMemberProfileMenu = () => {
  const [isOpen, setIsOpen] = useState(false);
  const { logout, isLoggingOut } = useLogout();
  const { desktopApi } = useDesktop();
  const { navigateToAgentConnection } = useNavigateToAgentConnection();
  const hasAgentConnection = desktopApi?.agent !== undefined;

  const { ref: containerRef } = useOutsideClick<HTMLDivElement>({
    isEnabled: isOpen,
    onOutsideClick: () => setIsOpen(false),
  });

  const toggleMenu = () => setIsOpen((prevIsOpen) => !prevIsOpen);

  const goToAgentConnection = () => {
    setIsOpen(false);
    navigateToAgentConnection();
  };

  return {
    isOpen,
    toggleMenu,
    containerRef,
    logout,
    isLoggingOut,
    hasAgentConnection,
    goToAgentConnection,
  };
};

export default useMemberProfileMenu;
