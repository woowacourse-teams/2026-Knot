import useKeyDown from "@hooks/common/useKeyDown";
import ConfirmDialog from "@primitives/ui/ConfirmDialog";
import Dim from "@primitives/ui/Dim";
import { useDialog } from "@provider/context/dialogContext";
import { useRecordingStore } from "@store/recordingStore";

const TITLE = "녹음을 끝내고 이동할까요?";

interface EndRecordingBeforeMoveDialogProps {
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * 녹음 중에 다른 워크스페이스로 옮겨 갈 때 녹음을 끝낼지 먼저 묻는 도메인 훅.
 *
 * 녹음은 워크스페이스를 가리지 않는 전역 저장소에 있어, 그대로 옮겨 가면 다른 워크스페이스에서 녹음이 이어져요.
 * 그래서 녹음 중(일시정지 포함)이면 [취소]/[끝내고 이동] 모달을 띄우고, 녹음이 없으면 묻지 않고 바로 이동해요.
 * [끝내고 이동]은 녹음 끝내기와 같이 녹음을 버린 뒤 이동하고, [취소]는 녹음과 화면을 그대로 둬요.
 *
 * 녹음 시간을 그리지 않으므로 매초 다시 그리는 `useRecording` 대신 저장소에서 상태만 읽어요.
 */
const useEndRecordingBeforeMove = () => {
  const { open } = useDialog();
  const isRecordingActive = useRecordingStore(
    (state) => state.status !== "idle",
  );
  const endRecording = useRecordingStore((state) => state.endRecording);

  /** 녹음이 없으면 `move`를 바로 부르고, 있으면 모달로 물은 뒤 끝내기로 하면 녹음을 버리고 불러요 */
  const moveAfterEndingRecording = (move: () => void) => {
    if (!isRecordingActive) {
      move();
      return;
    }

    open(({ close }) => (
      <EndRecordingBeforeMoveDialog
        onCancel={close}
        onConfirm={() => {
          close();
          endRecording();
          move();
        }}
      />
    ));
  };

  return { moveAfterEndingRecording };
};

/**
 * 녹음 중 워크스페이스를 옮길 때의 확인 모달.
 *
 * 확인 유형이라 바깥을 누르거나 ESC를 눌러도 [취소]와 같이 모달만 닫혀요.
 * 본문의 "문서로 정리돼요"는 녹음 업로드가 붙은 뒤의 최종 동작 기준 문구예요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2017-35354 워크스페이스/녹음 중 전환 확인}
 */
function EndRecordingBeforeMoveDialog({
  onCancel,
  onConfirm,
}: EndRecordingBeforeMoveDialogProps) {
  useKeyDown({ key: "Escape", isEnabled: true, onKeyDown: onCancel });

  return (
    <Dim onClick={onCancel}>
      <ConfirmDialog
        role="dialog"
        aria-modal="true"
        aria-label={TITLE}
        onClick={(e) => e.stopPropagation()}
        title={TITLE}
        description={
          "녹음은 워크스페이스마다 따로 있어요.\n지금 녹음은 문서로 정리돼요."
        }
        cancelLabel="취소"
        confirmLabel="끝내고 이동"
        onCancel={onCancel}
        onConfirm={onConfirm}
      />
    </Dim>
  );
}

export default useEndRecordingBeforeMove;
