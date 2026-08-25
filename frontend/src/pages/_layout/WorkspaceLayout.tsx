import { Outlet } from "react-router";

/**
 * GNB 레이아웃
 *
 * 워크스페이스 입장 후의 내부 페이지(홈, 초대, 노션 연동, 탐색)가 공유한다.
 */
export default function WorkspaceLayout() {
  return <Outlet />;
}
