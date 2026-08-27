import styled from "@emotion/styled";
import Spacing from "@primitives/layout/Spacing";
import { Outlet } from "react-router";

import LogoIcon from "@/assets/logos/logo.svg";

/**
 * knot 로고 + 중앙 카드 레이아웃
 *
 * 워크스페이스 진입 전 플로우(온보딩, 워크스페이스 생성/참여, 초대 에러)가 공유한다.
 * 연한 배경 위에 로고를 띄우고 그 아래 화면 콘텐츠(카드)를 가운데 놓는다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=600-10188 워크스페이스 생성 및 참여
 */
export default function CenteredLayout() {
  return (
    <Root>
      <Logo />
      <Spacing size={2.5} /> {/* 40px */}
      <Outlet />
    </Root>
  );
}

const Root = styled.main`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  padding: 2.5rem 1.5rem;
  background-color: ${({ theme }) => theme.neutral[50]};
`;

const Logo = styled(LogoIcon)`
  flex-shrink: 0;
  width: 7.375rem; /* 118px */
  height: 2.5625rem; /* 41px */
  color: ${({ theme }) => theme.neutral[800]};
`;
