import { Outlet } from "react-router";

import useDesktopDeepLink from "./model/useDesktopDeepLink";

/**
 * 라우터 맨 위에서 데스크톱 딥링크를 듣는 레이아웃.
 *
 * 화면을 그리지 않고 `Outlet`만 넘겨요. 라우터 안에 있어야 `useNavigate`를 쓸 수 있어서
 * `RouterProvider` 바깥이 아니라 경로 없는 최상위 레이아웃으로 둡니다(기획서 9.3 "딥링크 수신 → 라우터 이동").
 */
export default function DeepLinkListener() {
  useDesktopDeepLink();

  return <Outlet />;
}
