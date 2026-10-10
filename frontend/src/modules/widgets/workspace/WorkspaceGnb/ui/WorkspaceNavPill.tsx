import styled from "@emotion/styled";
import useWorkspaceNav from "@hooks/domain/workspace/useWorkspaceNav";

/**
 * GNB 가운데의 내비 필. 홈 · 탐색 · 문서를 오가고 지금 화면을 채워진 모양으로 알려줘요.
 */
export default function WorkspaceNavPill() {
  const {
    isHomeActive,
    isChatActive,
    isDocumentsActive,
    navigateToHome,
    navigateToExplore,
    navigateToDocuments,
  } = useWorkspaceNav();

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
      <NavItem
        type="button"
        aria-current={isDocumentsActive ? "page" : undefined}
        $isActive={isDocumentsActive}
        onClick={navigateToDocuments}
      >
        문서
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
