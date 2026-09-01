import styled from "@emotion/styled";
import {
  useWorkspaceSidebar,
  WORKSPACE_SIDEBAR_ID,
} from "@provider/context/workspaceSidebarContext";

import AvatarGlyphIcon from "@/assets/icons/avatarGlyph.svg";
import SidebarIcon from "@/assets/icons/sidebar.svg";

/**
 * 워크스페이스 전역 상단바(GNB).
 *
 * 좌측 버튼으로 사이드바를 열고 닫고, 우측에는 프로필 아바타를 그려요.
 * 아바타는 회원 정보 API가 없어 자리만 잡고 클릭 동작은 없어요.
 * Figma의 중앙 세그먼티드 Nav는 하단 FloatingDock을 쓰는 화면이라 만들지 않아요(dock=false).
 *
 * 사이드바 열림/닫힘 상태는 `WorkspaceLayout`이 감싼 `WorkspaceSidebarProvider`가 가져요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10078 GNB}
 */
export default function WorkspaceGnb() {
  const { isSidebarOpen, toggleSidebar } = useWorkspaceSidebar();

  return (
    <Container>
      <SidebarToggleButton
        type="button"
        aria-label="사이드바"
        aria-expanded={isSidebarOpen}
        aria-controls={WORKSPACE_SIDEBAR_ID}
        onClick={toggleSidebar}
      >
        <SidebarIcon size={18} />
      </SidebarToggleButton>

      <Avatar role="img" aria-label="내 프로필">
        <AvatarGlyphIcon size={22} />
      </Avatar>
    </Container>
  );
}

const Container = styled.header`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  height: 3.5rem; /* 56px */
  padding: 0 1.5rem; /* 24px */
  border-bottom: 1px solid ${({ theme }) => theme.neutral[200]};
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=555-1203 Btn/sidebar} */
const SidebarToggleButton = styled.button`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem; /* 32px */
  height: 2rem;
  border-radius: 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[600]};
  transition: background-color 0.2s ease-in;

  &:hover {
    background-color: ${({ theme }) => theme.neutral[200]};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=587-516 Avatar size=32 type=이미지} */
const Avatar = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem; /* 32px */
  height: 2rem;
  border-radius: 50%;
  background-color: ${({ theme }) => theme.neutral[200]};
  color: ${({ theme }) => theme.neutral[500]};
`;
