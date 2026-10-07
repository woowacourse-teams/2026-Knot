import useMicrophoneUnavailableDialog from "@hooks/domain/recording/useMicrophoneUnavailableDialog";
import useRecording from "@hooks/domain/recording/useRecording";
import useRecordingControl from "@hooks/domain/recording/useRecordingControl";
import useRecordingElapsedTime from "@hooks/domain/recording/useRecordingElapsedTime";
import { formatRecordingTime } from "@utils/formatRecordingTime";

/**
 * 녹음 화면 상단 바의 녹음 조작을 다룹니다.
 *
 * 이어서 녹음할 때 마이크가 끊겨 있었다면 다시 받아요. 받지 못하면 [다시 시도]/[닫기] 모달을 띄우고
 * 일시정지를 유지해요.
 * 녹음 끝내기는 녹음 파일을 올리고 홈으로 갈 때까지 걸리므로, 그동안 버튼을 막아 두 번 보내지 않게 해요.
 */
export const useRecorderBar = () => {
  const { openMicrophoneUnavailableDialog } = useMicrophoneUnavailableDialog();
  const { status, analyser, isEnding } = useRecording();
  const { elapsedSeconds } = useRecordingElapsedTime();
  const { pauseRecording, resumeRecording, endRecording } =
    useRecordingControl();

  const handleResume = async () => {
    const isResumed = await resumeRecording();
    if (isResumed) return;

    openMicrophoneUnavailableDialog({ onRetry: handleResume });
  };

  return {
    isPaused: status === "paused",
    elapsedTime: formatRecordingTime(elapsedSeconds),
    analyser,
    isEnding,
    handlePause: () => void pauseRecording(),
    handleResume,
    handleEnd: () => void endRecording(),
  };
};
