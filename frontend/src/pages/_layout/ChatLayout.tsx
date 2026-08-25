import { Outlet } from "react-router";

/**
 * 탐색 2단 분할 레이아웃
 *
 * 좌측 채팅 / 우측 결과 구조를 탐색 페이지가 공유한다.
 */
export default function ChatLayout() {
  return <Outlet />;
}
