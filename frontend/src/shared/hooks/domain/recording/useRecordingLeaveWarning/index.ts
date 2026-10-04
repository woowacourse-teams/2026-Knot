import useBeforeUnload from "@hooks/common/useBeforeUnload";
import { useRecordingStore } from "@store/recordingStore";

/**
 * 녹음 중·일시정지 상태에서 새로고침하거나 탭을 닫으려 하면 확인 창을 띄우는 도메인 훅.
 *
 * 녹음은 브라우저 안에만 있어 페이지를 떠나면 사라지기 때문이에요.
 * 녹음 시간을 다시 그리는 타이머가 필요 없어 `useRecording` 대신 상태만 읽어요.
 */
const useRecordingLeaveWarning = () => {
  const isRecordingActive = useRecordingStore(
    (state) => state.status !== "idle",
  );

  useBeforeUnload({ isEnabled: isRecordingActive });
};

export default useRecordingLeaveWarning;
