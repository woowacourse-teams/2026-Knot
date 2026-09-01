import styled from "@emotion/styled";

import DockExploreIcon from "@/assets/icons/dockExplore.svg";
import DockHomeIcon from "@/assets/icons/dockHome.svg";

import { useWorkspaceFloatingDock } from "./model/useWorkspaceFloatingDock";

interface WorkspaceFloatingDockProps {
  /**
   * 살아있는 대화 세션이 있으면 `탐색` 슬롯에 진행 중 점을 그려요.
   * 채팅 응답 완료와의 연결은 채팅 FE Issue에서 붙이고, 지금은 값만 받아 그립니다.
   */
  hasActiveChatSession?: boolean;
}

/**
 * 워크스페이스 화면 하단 중앙의 플로팅 독.
 *
 * `홈`과 `탐색` 두 슬롯만 두고 현재 라우트에 맞는 슬롯을 활성으로 표시해요.
 * `탐색`을 누르면 `/workspace/:workspaceId/chat`으로 push 이동하고, 이미 있는 화면의 슬롯은 눌러도 이동하지 않아요.
 * Figma에서 숨겨진 `작성` 슬롯과 구분선은 만들지 않아요.
 *
 * 화면 어디에 놓을지는 쓰는 페이지가 정해요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10088 Dock type=플로팅}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10113 홈 화면/채팅 응답 완료 (진행 중 점)}
 */
export default function WorkspaceFloatingDock({
  hasActiveChatSession = false,
}: WorkspaceFloatingDockProps) {
  const { isHomeActive, isChatActive, handleHomeClick, handleChatClick } =
    useWorkspaceFloatingDock();

  return (
    <Container aria-label="주요 화면 이동">
      <Slot
        type="button"
        aria-current={isHomeActive ? "page" : undefined}
        $isActive={isHomeActive}
        onClick={handleHomeClick}
      >
        <DockHomeIcon size={24} />
        <VisuallyHidden>홈</VisuallyHidden>
      </Slot>

      <Slot
        type="button"
        aria-current={isChatActive ? "page" : undefined}
        $isActive={isChatActive}
        onClick={handleChatClick}
      >
        <DockExploreIcon size={24} />
        <VisuallyHidden>탐색</VisuallyHidden>
        {hasActiveChatSession && (
          <>
            <ActiveChatDot $isActive={isChatActive} />
            <VisuallyHidden>진행 중인 대화 있음</VisuallyHidden>
          </>
        )}
      </Slot>
    </Container>
  );
}

const Container = styled.nav`
  display: inline-flex;
  align-items: center;
  gap: 0.25rem; /* 4px */
  height: 3.75rem; /* 60px */
  padding: 0.5rem 0.75rem; /* 8px 12px */
  border-radius: 6.25rem; /* 100px */
  background-color: ${({ theme }) => theme.neutral[800]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const Slot = styled.button<{ $isActive: boolean }>`
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem; /* 40px */
  height: 2.5rem;
  border-radius: 1.25rem; /* 20px */
  color: ${({ theme, $isActive }) =>
    $isActive ? theme.neutral[0] : theme.neutral[300]};
  background-color: ${({ theme, $isActive }) =>
    $isActive ? theme.neutral[900] : "transparent"};
  box-shadow: ${({ theme, $isActive }) =>
    $isActive ? `inset 0 0 0 1px ${theme.neutral[700]}` : "none"};
  transition:
    color 0.2s ease-in,
    background-color 0.2s ease-in;

  &:hover {
    color: ${({ theme }) => theme.neutral[0]};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

/**
 * 진행 중 점. 6px 점 둘레를 뒤에 깔린 슬롯 색으로 1.5px 도려내요.
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=687-558 Indicator/Dot 다크 배경}
 */
const ActiveChatDot = styled.span<{ $isActive: boolean }>`
  position: absolute;
  top: 0.21875rem; /* 3.5px */
  right: 0.21875rem;
  width: 0.5625rem; /* 9px = 6px 점 + 1.5px 테두리 × 2 */
  height: 0.5625rem;
  border: 0.09375rem solid
    ${({ theme, $isActive }) =>
      $isActive ? theme.neutral[900] : theme.neutral[800]};
  border-radius: 50%;
  background-color: ${({ theme }) => theme.sub.accent[500]};
`;

const VisuallyHidden = styled.span`
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  border: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
`;
