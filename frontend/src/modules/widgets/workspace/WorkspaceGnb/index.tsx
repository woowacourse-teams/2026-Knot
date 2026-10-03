import styled from "@emotion/styled";
import MemberProfileAvatar from "@features/member/MemberProfileAvatar";
import type { ReactNode } from "react";

import WorkspaceNavPill from "./ui/WorkspaceNavPill";

interface WorkspaceGnbProps {
  /** 좌측에 놓을 패널 트리거들. 화면마다 달라 레이아웃이 넣어 줘요. */
  children?: ReactNode;
}

/**
 * 워크스페이스 전역 상단바(GNB).
 *
 * 동작 규칙은 스토리북 `Workspace/WorkspaceGnb`에서 확인해요.
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
