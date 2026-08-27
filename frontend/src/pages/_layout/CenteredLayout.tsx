import styled from "@emotion/styled";
import Stack from "@primitives/layout/Stack";
import Logo from "@primitives/ui/Logo";
import { Outlet } from "react-router";

/**
 * knot 로고 + 화면 가운데 정렬 레이아웃
 *
 * 워크스페이스 진입 전 플로우(로그인, 온보딩, 워크스페이스 생성/참여, 초대 에러)가 공유한다.
 * 카드는 화면마다 있고 없고가 달라서 각 페이지가 직접 그린다.
 *
 * 로고 아래 간격은 레이아웃이 정하지 않는다. 지금은 네 화면 모두 40px이지만
 * 화면마다 달라질 수 있어서, 각 페이지가 첫 줄에 `Spacing`으로 직접 벌린다.
 */
export default function CenteredLayout() {
  return (
    <Root as="main" align="center" justify="center">
      <Logo />
      <Outlet />
    </Root>
  );
}

/**
 * 피그마는 화면 위에서 288px 지점에 내용을 두지만, 그 값은 1440×1024 아트보드
 * 기준이라 화면 높이가 달라지면 맞지 않아요. 세로 가운데 정렬로 대신합니다.
 */
const Root = styled(Stack)`
  min-height: 100dvh;
  background-color: ${({ theme }) => theme.neutral[50]};
`;
