import useMicrophoneUnavailableDialog from "@hooks/domain/recording/useMicrophoneUnavailableDialog";
import useNavigateToRecording from "@hooks/domain/recording/useNavigateToRecording";
import useRecording from "@hooks/domain/recording/useRecording";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { formatRecordingTime } from "@utils/formatRecordingTime";
import { useEffect } from "react";
import { useMatch, useParams } from "react-router";

/**
 * 독의 회의 녹음(마이크) 슬롯을 다룹니다.
 *
 * 마이크를 누르면 녹음 화면에 들어가기 전에 마이크 권한을 받아 녹음을 시작하고, 받으면 녹음 화면으로 가요.
 * 받지 못하면 [다시 시도]/[닫기] 모달을 띄우고 지금 화면에 남아요.
 * 녹음 화면에서는 갈 곳이 없어 마이크를 숨겨요.
 *
 * 녹음 화면이 아닌 곳에서 녹음이 이어지고 있으면 마이크 대신 녹음 칩을 보여줘요.
 * 칩을 누르면 권한을 다시 묻지 않고 녹음 화면으로 가고, 중지를 누르면 녹음을 끝내고 지금 화면에 남아요.
 *
 * 독은 어느 화면에서나 떠 있으므로, 녹음 중에 마이크가 끊겼다는 알림도 여기서 같은 모달로 띄워요.
 * 녹음은 저장소가 이미 일시정지해 두었고, [다시 시도]는 마이크를 다시 받아 이어 가요.
 */
export const useDockRecording = () => {
  const { workspaceId } = useParams();
  const isRecordingPageActive = useMatch(PATH_ROUTE.RECORDING) !== null;
  const { navigateToRecording } = useNavigateToRecording();
  const { openMicrophoneUnavailableDialog } = useMicrophoneUnavailableDialog();
  const {
    status,
    isRecordingActive,
    elapsedSeconds,
    isMicrophoneLost,
    startRecording,
    resumeRecording,
    endRecording,
    clearMicrophoneLost,
  } = useRecording();

  // 녹음 중 마이크가 끊기면 다시 시도 모달을 띄워요
  useEffect(() => {
    if (!isMicrophoneLost) return;

    // 알림은 한 번만 띄우도록 끊김 표시를 바로 지워요. 녹음은 일시정지로 남아요
    clearMicrophoneLost();

    const retryResume = async () => {
      const isResumed = await resumeRecording();
      if (isResumed) return;

      openMicrophoneUnavailableDialog({ onRetry: retryResume });
    };

    openMicrophoneUnavailableDialog({ onRetry: retryResume });
  }, [
    clearMicrophoneLost,
    isMicrophoneLost,
    openMicrophoneUnavailableDialog,
    resumeRecording,
  ]);

  const handleMicClick = async () => {
    if (!workspaceId) return;

    const isStarted = await startRecording();
    if (!isStarted) {
      openMicrophoneUnavailableDialog({ onRetry: handleMicClick });
      return;
    }

    navigateToRecording(workspaceId);
  };

  const handleOpenRecording = () => {
    if (!workspaceId) return;

    navigateToRecording(workspaceId);
  };

  return {
    isMicVisible: !isRecordingPageActive && !isRecordingActive,
    isRecordingChipVisible: !isRecordingPageActive && isRecordingActive,
    isRecordingPaused: status === "paused",
    elapsedTime: formatRecordingTime(elapsedSeconds),
    handleMicClick,
    handleOpenRecording,
    handleStopRecording: endRecording,
  };
};
