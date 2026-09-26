import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import useRecording from "@hooks/domain/recording/useRecording";
import { formatRecordingTime } from "@utils/formatRecordingTime";
import { useEffect } from "react";
import { useParams } from "react-router";

/**
 * 녹음 화면 상단 바의 녹음 조작을 다룹니다.
 *
 * 녹음 화면에 들어오면 바로 녹음을 시작해요. 다른 화면에 다녀와 다시 들어오면 새로 시작하지 않고
 * 이어지던 녹음을 그대로 보여 줍니다. 녹음 상태는 전역 저장소에 있어 화면을 떠나도 끊기지 않아요.
 *
 * 녹음을 끝내면 상태를 처음으로 되돌리고 홈으로 나가요. 끝난 녹음이 남지 않은 녹음 화면에 머물면
 * 다시 들어온 것으로 보여 새 녹음이 시작된 것처럼 헷갈리기 때문이에요.
 */
export const useRecorderBar = () => {
  const { workspaceId } = useParams();
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const {
    status,
    elapsedSeconds,
    startRecording,
    pauseRecording,
    resumeRecording,
    endRecording,
  } = useRecording();

  useEffect(() => {
    startRecording();
  }, [startRecording]);

  const handleEnd = () => {
    endRecording();

    if (workspaceId) navigateToWorkspaceHome({ workspaceId });
  };

  return {
    isPaused: status === "paused",
    elapsedTime: formatRecordingTime(elapsedSeconds),
    handlePause: pauseRecording,
    handleResume: resumeRecording,
    handleEnd,
  };
};
