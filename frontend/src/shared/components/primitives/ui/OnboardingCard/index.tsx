import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";
import type { HTMLAttributes, ReactNode } from "react";

interface OnboardingCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

/**
 * 온보딩 플로우에서 내용을 담는 흰 카드.
 */
export default function OnboardingCard({
  children,
  ...props
}: OnboardingCardProps) {
  return <Root {...props}>{children}</Root>;
}

/**
 * 피그마 그림자 `0 12px 32px rgba(15,23,41,0.08)`은 theme에 없는 값이라 토큰으로 대신합니다.
 * 워크스페이스 생성·참여 카드가 `shadow02`를 쓰고 있어 같은 값으로 맞췄어요.
 */
const Root = styled(Stack)`
  width: 100%;
  max-width: 28.5rem; /* 456px = 360 + 좌우 padding 48 */
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;
