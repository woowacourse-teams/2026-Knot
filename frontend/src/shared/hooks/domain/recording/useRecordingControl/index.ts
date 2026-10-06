import { isApiError } from "@api/axiosInstance/apiClient";
import useEndRecordingMutation from "@api/mutations/useEndRecordingMutation";
import useIssueRecordingAudioUploadUrlMutation from "@api/mutations/useIssueRecordingAudioUploadUrlMutation";
import usePauseRecordingMutation from "@api/mutations/usePauseRecordingMutation";
import useResumeRecordingMutation from "@api/mutations/useResumeRecordingMutation";
import useStartRecordingMutation from "@api/mutations/useStartRecordingMutation";
import useUploadRecordingAudioMutation from "@api/mutations/useUploadRecordingAudioMutation";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import { useRecordingStore } from "@store/recordingStore";
import {
  clearRecordingStartProof,
  getRecordingControlProof,
  getRecordingStartProof,
} from "@utils/recordingControlProof";
import { useCallback } from "react";

/** 서버에서 이미 끝났거나 버려진 녹음이라 이 탭의 녹음도 버려야 하는 오류 코드 */
const CLOSED_RECORDING_ERROR_CODES = [
  "RECORDING_ALREADY_DISCARDED",
  "RECORDING_ALREADY_ENDED",
];

interface HandleControlErrorParams {
  action: string;
  error: unknown;
  workspaceId: number;
}

interface UploadRecordedAudioParams {
  workspaceId: number;
  recordingId: number;
  audio: Blob;
}

/**
 * 서버가 이미 끝났거나 버려진 녹음이라고 응답한 오류인지 확인해요.
 * HTTP 상태는 보지 않고 서버 오류 코드로만 판단하며, 응답이 없는 네트워크 오류는 `false`예요.
 */
const isClosedRecordingError = (error: unknown) =>
  isApiError(error) && CLOSED_RECORDING_ERROR_CODES.includes(error.code ?? "");

// TODO: 실패 안내·재시도 정책은 기획 논의가 필요해요. 정해질 때까지 안내 UI 없이 콘솔에만 남겨요(#465)
const logRecordingError = (action: string, error: unknown) => {
  console.error(`[녹음] ${action} 요청에 실패했어요`, error);
};

/**
 * 녹음을 시작·일시정지·이어서 녹음·끝내며 서버 녹음 세션과 맞추는 도메인 훅.
 *
 * - 시작: 마이크 권한 → 시작 요청이 성공해야 브라우저 녹음을 시작해요. 시작 요청이 실패하면 마이크를 꺼요.
 * - 일시정지·이어서 녹음: 브라우저 녹음을 먼저 바꾸고 서버에 알려요. 서버 요청이 실패해도 다시 보내지 않고
 *   사용자가 누른 대로 둬요.
 * - 끝내기: 종료 요청 → 업로드 URL 발급 → 오디오 PUT → 녹음 비우기 → 홈. 종료가 실패하면 녹음을 그대로 두고,
 *   업로드가 실패하면 오디오를 버리고 홈으로 가요.
 * - 서버에서 이미 끝났거나 버려진 녹음(409)이면 수집을 멈추고 오디오를 버린 뒤 홈으로 가요.
 *
 * 최초 탭 증명은 sessionStorage에 두고, 실패는 안내 없이 콘솔에만 남겨요.
 * 독의 마이크 끊김 알림처럼 효과 안에서 부르는 곳이 있어 동작의 참조를 고정해요.
 */
const useRecordingControl = () => {
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const { mutateAsync: startRecordingSession } = useStartRecordingMutation();
  const { mutateAsync: pauseRecordingSession } = usePauseRecordingMutation();
  const { mutateAsync: resumeRecordingSession } = useResumeRecordingMutation();
  const { mutateAsync: endRecordingSession } = useEndRecordingMutation();
  const { mutateAsync: issueAudioUploadUrl } =
    useIssueRecordingAudioUploadUrlMutation();
  const { mutateAsync: uploadAudio } = useUploadRecordingAudioMutation();

  /** 녹음을 버리고 다음 녹음은 새 시작 요청으로 보내도록 증명을 지운 뒤 홈으로 가요 */
  const closeRecording = useCallback(
    (workspaceId: number) => {
      useRecordingStore.getState().discardRecording();
      clearRecordingStartProof();
      navigateToWorkspaceHome({
        workspaceId: String(workspaceId),
        replace: true,
      });
    },
    [navigateToWorkspaceHome],
  );

  const handleControlError = useCallback(
    ({ action, error, workspaceId }: HandleControlErrorParams) => {
      logRecordingError(action, error);
      if (isClosedRecordingError(error)) closeRecording(workspaceId);
    },
    [closeRecording],
  );

  /** 녹음을 시작해요. 결과는 `started`·`microphoneUnavailable`·`failed`이고, 마이크를 받지 못한 경우만 부른 쪽이 안내해요 */
  const startRecording = useCallback(
    async (workspaceId: number) => {
      const store = useRecordingStore.getState();
      if (store.status !== "idle") return "started";

      try {
        const isConnected = await store.connectMicrophone();
        if (!isConnected) return "microphoneUnavailable";
      } catch (error) {
        // 서버가 받는 형식으로 녹음할 수 없는 브라우저예요. 안내는 다른 실패와 함께 정해요(#465)
        logRecordingError("녹음 시작", error);

        return "failed";
      }

      try {
        const { recordingId } = await startRecordingSession({
          workspaceId,
          ...getRecordingStartProof(),
        });
        useRecordingStore
          .getState()
          .startRecording({ workspaceId, recordingId });

        return "started";
      } catch (error) {
        // 응답을 못 받았을 뿐 서버엔 열렸을 수 있어, 다음 시작이 같은 요청으로 가도록 증명은 남겨요
        logRecordingError("녹음 시작", error);
        useRecordingStore.getState().discardRecording();

        return "failed";
      }
    },
    [startRecordingSession],
  );

  /** 서버에 일시정지를 알려요. 마이크가 끊겨 브라우저가 먼저 멈춘 경우에도 불러요 */
  const syncPause = useCallback(async () => {
    const { session, isEnding } = useRecordingStore.getState();
    const proof = getRecordingControlProof();
    if (!session || !proof || isEnding) return;

    try {
      await pauseRecordingSession({ ...session, ...proof });
    } catch (error) {
      handleControlError({
        action: "일시정지",
        error,
        workspaceId: session.workspaceId,
      });
    }
  }, [handleControlError, pauseRecordingSession]);

  const pauseRecording = useCallback(async () => {
    if (useRecordingStore.getState().isEnding) return;

    useRecordingStore.getState().pauseRecording();
    await syncPause();
  }, [syncPause]);

  /** 이어서 녹음해요. 마이크를 다시 받지 못하면 `false`를 돌려주고 서버에는 알리지 않아요 */
  const resumeRecording = useCallback(async () => {
    const store = useRecordingStore.getState();
    if (store.isEnding) return false;

    const isResumed = await store.resumeRecording();
    if (!isResumed) return false;

    const { session } = useRecordingStore.getState();
    const proof = getRecordingControlProof();
    if (!session || !proof) return true;

    try {
      await resumeRecordingSession({ ...session, ...proof });
    } catch (error) {
      handleControlError({
        action: "이어서 녹음",
        error,
        workspaceId: session.workspaceId,
      });
    }

    return true;
  }, [handleControlError, resumeRecordingSession]);

  const uploadRecordedAudio = useCallback(
    async ({ workspaceId, recordingId, audio }: UploadRecordedAudioParams) => {
      try {
        const { uploadUrl } = await issueAudioUploadUrl({
          workspaceId,
          recordingId,
          contentType: audio.type,
          contentLength: audio.size,
        });
        await uploadAudio({ uploadUrl, audio });
      } catch (error) {
        logRecordingError("녹음 파일 업로드", error);
      }
    },
    [issueAudioUploadUrl, uploadAudio],
  );

  const endRecording = useCallback(async () => {
    const { session, isEnding } = useRecordingStore.getState();
    if (!session || isEnding) return;

    try {
      await endRecordingSession(session);
    } catch (error) {
      handleControlError({ action: "녹음 종료", error, ...session });

      return;
    }

    const audio = await useRecordingStore.getState().stopRecording();
    await uploadRecordedAudio({ ...session, audio });
    closeRecording(session.workspaceId);
  }, [
    closeRecording,
    endRecordingSession,
    handleControlError,
    uploadRecordedAudio,
  ]);

  return {
    startRecording,
    pauseRecording,
    syncPause,
    resumeRecording,
    endRecording,
  };
};

export default useRecordingControl;
