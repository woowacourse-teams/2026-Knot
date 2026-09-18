import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";
import type { HTMLAttributes, ReactNode } from "react";

interface OnboardingCardProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode;
}

/**
 * 온보딩 플로우에서 내용을 담는 흰 카드.
 *
 * 너비를 고정하지 않고 `max-width`만 둡니다. 넓은 화면에서는 456px에서 멈추고,
 * 좁아지면 화면을 따라 줄어들어요. 안쪽 내용은 `padding` 48을 뺀 360px이 됩니다.
 *
 * 피그마 원본은 460px이지만 좌우 padding 48과 내용 360을 더하면 456입니다.
 * 남는 4px은 내용이 바뀌면 따라 바뀌어야 하는 값이라 보고 456으로 두었어요.
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
