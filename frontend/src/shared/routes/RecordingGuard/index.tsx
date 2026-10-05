import { useRecordingStore } from "@store/recordingStore";
import { useState } from "react";
import { Navigate, Outlet, useParams } from "react-router";

import { getRouterPath } from "../PATH_ROUTE";

/**
 * 진행 중인 녹음이 있을 때만 화면을 여는 가드.
 *
 * 녹음은 독의 마이크에서 권한을 받은 뒤 시작하고, 녹음 화면은 이미 시작한 녹음을 보여 주기만 해요.
 * 그래서 녹음 없이 들어오면(주소를 직접 친 경우 등) 워크스페이스 홈으로 보내요.
 * 뒤로 가기로 빈 녹음 화면에 돌아오지 않도록 기록을 바꿔요.
 *
 * 들어올 때 한 번만 확인하고 녹음 상태를 구독하지 않아요. 녹음을 끝낸 뒤의 이동은 끝낸 쪽이 정해요.
 * 구독하면 [끝내고 이동]처럼 끝내기와 이동이 함께 일어날 때, 이동이 반영되기 전에 가드가 홈으로 보내
 * 이동을 덮어써요.
 */
export default function RecordingGuard() {
  const { workspaceId } = useParams();
  const [isIdleOnEntry] = useState(
    () => useRecordingStore.getState().status === "idle",
  );

  if (isIdleOnEntry && workspaceId) {
    return (
      <Navigate
        to={getRouterPath({
          routeKey: "WORKSPACE_HOME",
          params: { workspaceId },
        })}
        replace
      />
    );
  }

  return <Outlet />;
}
