import styled from "@emotion/styled";
import MemberProfileAvatar from "@features/member/MemberProfileAvatar";
import type { ReactNode } from "react";

import WorkspaceNavPill from "./ui/WorkspaceNavPill";

interface WorkspaceGnbProps {
  /**
   * 좌측에 놓을 패널 트리거들.
   *
   * 어떤 패널을 열 수 있는지는 화면마다 다르므로(목록은 탐색 화면에만 있어요)
   * GNB가 정하지 않고 레이아웃에서 받아요.
   */
  children?: ReactNode;
}

/**
 * 워크스페이스 전역 상단바(GNB).
 *
 * 구성과 동작 규칙은 스토리북 `Workspace/WorkspaceGnb`에서 확인해요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-6863 GNB/Floating nav=홈}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-7028 GNB/Floating nav=탐색}
 */
export default function WorkspaceGnb({ children }: WorkspaceGnbProps) {
  return (
    <Container>
      <Side>{children}</Side>

      <WorkspaceNavPill />

      <Side $isTrailing>
        <MemberProfileAvatar />
      </Side>
    </Container>
  );
}

const Container = styled.header`
  display: flex;
  align-items: center;
  height: 2.75rem; /* 44px */
  padding: 0 1.5rem; /* 24px */
`;

const Side = styled.div<{ $isTrailing?: boolean }>`
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: ${({ $isTrailing }) =>
    $isTrailing ? "flex-end" : "flex-start"};
  gap: 0.5rem; /* 8px */
  min-width: 0;
`;
