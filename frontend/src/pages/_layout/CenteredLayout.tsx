import { Outlet } from "react-router";

/**
 * knot 로고 + 중앙 카드 레이아웃
 *
 * 워크스페이스 진입 전 플로우(온보딩, 워크스페이스 생성/참여, 초대 에러)가 공유한다.
 */
export default function CenteredLayout() {
  return <Outlet />;
}
