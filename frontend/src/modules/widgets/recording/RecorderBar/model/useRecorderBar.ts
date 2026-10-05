import useMicrophoneUnavailableDialog from "@hooks/domain/recording/useMicrophoneUnavailableDialog";
import useRecording from "@hooks/domain/recording/useRecording";
import { formatRecordingTime } from "@utils/formatRecordingTime";

/**
 * 녹음 화면 상단 바의 녹음 조작을 다룹니다.
 *
 * 이어서 녹음할 때 마이크가 끊겨 있었다면 다시 받아요. 받지 못하면 [다시 시도]/[닫기] 모달을 띄우고
 * 일시정지를 유지해요.
 */
export const useRecorderBar = () => {
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

  return {
    isPaused: status === "paused",
    elapsedTime: formatRecordingTime(elapsedSeconds),
    analyser,
    handlePause: pauseRecording,
    handleResume,
    handleEnd: endRecording,
  };
};
