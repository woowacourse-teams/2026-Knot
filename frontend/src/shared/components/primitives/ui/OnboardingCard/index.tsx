import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";
import type { HTMLAttributes, ReactNode } from "react";

interface OnboardingCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

/**
 * 온보딩 플로우에서 내용을 담는 흰 카드.
 *
 * 너비를 고정하지 않고 내용에 맞춰 늘어나요. 피그마 원본은 460px이지만
 * 좌우 padding 48과 내용 360을 더하면 456이라 오른쪽에 4px이 남습니다.
 * 내용이 바뀌면 따라 바뀌어야 하는 값이라 보고 고정하지 않았어요.
 *
 * 홈 화면 카드와는 padding·간격이 달라서(48/12 vs 20/32) 같은 컴포넌트로 묶지 않습니다.
 *
 * 자식 사이 간격은 자리마다 다르므로(12, 24) 카드가 정하지 않아요.
 * 쓰는 쪽에서 `Spacing`으로 벌리세요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=424-1193 Card/Onboarding & Workspace}
 */
export default function OnboardingCard({
  children,
  ...props
}: OnboardingCardProps) {
  return <Root {...props}>{children}</Root>;
}

/**
 * 피그마 그림자는 `0 12px 32px rgba(15,23,41,0.08)`로 theme에 없는 값이라
 * 가장 가까운 `shadow03`으로 대신합니다.
 */
const Root = styled(Stack)`
  padding: 3rem; /* 48px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow03};
`;
