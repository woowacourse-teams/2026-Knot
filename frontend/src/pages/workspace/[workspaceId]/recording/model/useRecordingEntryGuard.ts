import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import { useRecordingStore } from "@store/recordingStore";
import { useEffect } from "react";
import { useParams } from "react-router";

/**
 * 진행 중인 녹음이 없으면 홈으로 보내요.
 *
 * 녹음은 독의 마이크에서 권한을 받은 뒤 시작하고, 이 화면은 이미 시작한 녹음을 보여 주기만 해요.
 * 그래서 녹음 없이 들어오거나(주소를 직접 친 경우 등) 녹음을 끝내면 홈으로 나가요.
 * 뒤로 가기로 빈 녹음 화면에 돌아오지 않도록 기록을 바꿔요.
 *
 * 녹음 시간을 다시 그리는 타이머가 필요 없어 `useRecording` 대신 상태만 읽어요.
 */
export const useRecordingEntryGuard = () => {
  const { workspaceId } = useParams();
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const isRecordingIdle = useRecordingStore((state) => state.status === "idle");

  useEffect(() => {
    if (!isRecordingIdle || !workspaceId) return;

    navigateToWorkspaceHome({ workspaceId, replace: true });
  }, [isRecordingIdle, navigateToWorkspaceHome, workspaceId]);
};
