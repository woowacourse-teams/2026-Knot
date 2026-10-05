import useMicrophoneUnavailableDialog from "@hooks/domain/recording/useMicrophoneUnavailableDialog";
import useRecording from "@hooks/domain/recording/useRecording";
import { getRouterPath } from "@routes/PATH_ROUTE";
import { formatRecordingTime } from "@utils/formatRecordingTime";
import { useNavigate, useParams } from "react-router";

/**
 * 녹음 화면 상단 바의 녹음 조작을 다룹니다.
 *
 * 이어서 녹음할 때 마이크가 끊겨 있었다면 다시 받아요. 받지 못하면 [다시 시도]/[닫기] 모달을 띄우고
 * 일시정지를 유지해요.
 * 녹음을 끝내면 보여 줄 녹음이 없으므로 워크스페이스 홈으로 가요. 뒤로 가기로 빈 녹음 화면에 돌아오지 않도록 기록을 바꿔요.
 */
export const useRecorderBar = () => {
  const { workspaceId } = useParams();
  const navigate = useNavigate();
  const { openMicrophoneUnavailableDialog } = useMicrophoneUnavailableDialog();
  const {
    status,
    elapsedSeconds,
    analyser,
    pauseRecording,
    resumeRecording,
    endRecording,
  } = useRecording();

  const handleResume = async () => {
    const isResumed = await resumeRecording();
    if (isResumed) return;

    openMicrophoneUnavailableDialog({ onRetry: handleResume });
  };

  const handleEnd = () => {
    endRecording();
    if (!workspaceId) return;

    // 지금은 종료하면 바로 이동시킴. 추후에 api 연동할 예정
    navigate(
      getRouterPath({ routeKey: "WORKSPACE_HOME", params: { workspaceId } }),
      { replace: true },
    );
  };

  return {
    isPaused: status === "paused",
    elapsedTime: formatRecordingTime(elapsedSeconds),
    analyser,
    handlePause: pauseRecording,
    handleResume,
    handleEnd,
  };
};
