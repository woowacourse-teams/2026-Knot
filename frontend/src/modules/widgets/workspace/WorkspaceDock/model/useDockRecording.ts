import useMicrophoneUnavailableDialog from "@hooks/domain/recording/useMicrophoneUnavailableDialog";
import useNavigateToRecording from "@hooks/domain/recording/useNavigateToRecording";
import useRecordingState from "@hooks/domain/recording/useRecordingState";
import useRecordingControl from "@hooks/domain/recording/useRecordingControl";
import useRecordingElapsedTime from "@hooks/domain/recording/useRecordingElapsedTime";
import { PATH_ROUTE } from "@routes/PATH_ROUTE";
import { formatRecordingTime } from "@utils/formatRecordingTime";
import { useEffect } from "react";
import { useMatch, useParams } from "react-router";

/**
 * 독의 회의 녹음(마이크) 슬롯을 다룹니다.
 *
 * 마이크를 누르면 녹음 화면에 들어가기 전에 마이크 권한을 받고 서버에 녹음을 열어 시작한 뒤 녹음 화면으로 가요.
 * 권한을 받지 못하면 [다시 시도]/[닫기] 모달을 띄우고, 서버가 시작을 거절하면 안내 없이 지금 화면에 남아요.
 * 녹음 화면에서는 갈 곳이 없어 마이크를 숨겨요.
 *
 * 녹음 화면이 아닌 곳에서 녹음이 이어지고 있으면 마이크 대신 녹음 칩을 보여줘요.
 * 칩을 누르면 권한을 다시 묻지 않고 녹음 화면으로 가고, 중지를 누르면 녹음을 끝내 올린 뒤 홈으로 가요.
 *
 * 독은 어느 화면에서나 떠 있으므로, 녹음 중에 마이크가 끊겼다는 알림도 여기서 같은 모달로 띄워요.
 * 녹음은 저장소가 이미 일시정지해 두었으므로 서버에도 일시정지를 알리고, [다시 시도]는 마이크를 다시 받아 이어 가요.
 */
export const useDockRecording = () => {
  const { workspaceId } = useParams();
  const isRecordingPageActive = useMatch(PATH_ROUTE.RECORDING) !== null;
  const { navigateToRecording } = useNavigateToRecording();
  const { openMicrophoneUnavailableDialog } = useMicrophoneUnavailableDialog();
  const { status, isRecordingActive, isMicrophoneLost, clearMicrophoneLost } =
    useRecordingState();
  const { elapsedSeconds } = useRecordingElapsedTime();
  const { startRecording, syncPause, resumeRecording, endRecording } =
    useRecordingControl();

  // 녹음 중 마이크가 끊기면 다시 시도 모달을 띄워요
  useEffect(() => {
    if (!isMicrophoneLost) return;

    // 알림은 한 번만 띄우도록 끊김 표시를 바로 지워요. 녹음은 일시정지로 남아요
    clearMicrophoneLost();
    void syncPause();

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
    syncPause,
  ]);

  const handleMicClick = async () => {
    if (!workspaceId) return;

    const result = await startRecording(Number(workspaceId));
    if (result === "microphoneUnavailable") {
      openMicrophoneUnavailableDialog({ onRetry: handleMicClick });
      return;
    }
    if (result === "failed") return;

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
    handleStopRecording: () => void endRecording(),
  };
};
