import styled from "@emotion/styled";
import useWorkspaceNav from "@hooks/domain/workspace/useWorkspaceNav";

/**
 * GNB 가운데에서 홈과 탐색을 오가는 내비 필.
 *
 * 스토리북 `Workspace/WorkspaceGnb`
 */
export default function WorkspaceNavPill() {
  const { isHomeActive, isChatActive, navigateToHome, navigateToExplore } =
    useWorkspaceNav();

  return (
    <Container aria-label="워크스페이스 화면 이동">
      <NavItem
        type="button"
        aria-current={isHomeActive ? "page" : undefined}
        $isActive={isHomeActive}
        onClick={navigateToHome}
      >
        홈
      </NavItem>
      <NavItem
        type="button"
        aria-current={isChatActive ? "page" : undefined}
        $isActive={isChatActive}
        onClick={navigateToExplore}
      >
        탐색
      </NavItem>
    </Container>
  );
}

const Container = styled.nav`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.125rem; /* 2px */
  height: 2.75rem; /* 44px */
  padding: 0.375rem; /* 6px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 62.4375rem; /* 999px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const NavItem = styled.button<{ $isActive: boolean }>`
  display: flex;
  align-items: center;
  height: 2rem; /* 32px */
  padding: 0 1.125rem; /* 18px */
  border-radius: 62.4375rem;
  background-color: ${({ theme, $isActive }) =>
    $isActive ? theme.neutral[700] : "transparent"};
  color: ${({ theme, $isActive }) =>
    $isActive ? theme.neutral[0] : theme.neutral[600]};
  white-space: nowrap;
  transition:
    background-color 0.2s ease-in,
    color 0.2s ease-in;
  ${({ theme }) => theme.text.caption02};

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;
