import { useRecordingStore } from "@store/recordingStore";
import { Navigate, Outlet, useParams } from "react-router";

import { getRouterPath } from "../PATH_ROUTE";

/**
 * 진행 중인 녹음이 있을 때만 화면을 여는 가드.
 *
 * 녹음은 독의 마이크에서 권한을 받은 뒤 시작하고, 녹음 화면은 이미 시작한 녹음을 보여 주기만 해요.
 * 그래서 녹음 없이 들어오거나(주소를 직접 친 경우 등) 녹음을 끝내면 워크스페이스 홈으로 보내요.
 * 뒤로 가기로 빈 녹음 화면에 돌아오지 않도록 기록을 바꿔요.
 *
 * 녹음 시간을 다시 그리는 타이머가 필요 없어 `useRecording` 대신 상태만 읽어요.
 */
export default function RecordingGuard() {
  const { workspaceId } = useParams();
  const isRecordingIdle = useRecordingStore((state) => state.status === "idle");

  if (isRecordingIdle && workspaceId) {
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
