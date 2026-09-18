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
 * 배경 없이 본문 위에 떠 있고, 좌측 패널 트리거 · 가운데 내비 필 · 우측 프로필 아바타로 나뉘어요.
 * 좌우 영역이 같은 비율로 늘어나 내비 필이 늘 화면 한가운데에 놓여요.
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
