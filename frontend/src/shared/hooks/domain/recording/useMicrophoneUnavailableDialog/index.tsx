import useKeyDown from "@hooks/common/useKeyDown";
import ConfirmDialog from "@primitives/ui/ConfirmDialog";
import Dim from "@primitives/ui/Dim";
import { useDialog } from "@provider/context/dialogContext";
import { useCallback } from "react";

const TITLE = "마이크를 사용할 수 없어요";

interface MicrophoneUnavailableDialogProps {
  onClose: () => void;
  onRetry: () => void;
}

/**
 * 마이크를 받지 못했을 때 [다시 시도]/[닫기] 모달을 띄우는 도메인 훅.
 *
 * 독에서 녹음을 시작할 때, 녹음 화면에서 이어서 녹음할 때, 녹음 중 마이크가 끊겼을 때 같은 모달을 써요.
 * [닫기]는 모달만 닫고 화면과 녹음 상태는 그대로 둬요.
 */
const useMicrophoneUnavailableDialog = () => {
  const { open } = useDialog();

  // 효과 안에서 부르는 곳(독의 마이크 끊김 알림)이 있어 참조를 고정해요
  const openMicrophoneUnavailableDialog = useCallback(
    ({ onRetry }: OpenMicrophoneUnavailableDialogParams) =>
      open(({ close }) => (
        <MicrophoneUnavailableDialog
          onClose={close}
          onRetry={() => {
            close();
            onRetry();
          }}
        />
      )),
    [open],
  );

  return { openMicrophoneUnavailableDialog };
};

/**
 * 마이크 권한이 없거나 마이크를 찾지 못했을 때의 확인 모달.
 *
 * 확인 유형이라 바깥을 누르거나 ESC를 눌러도 [닫기]와 같이 모달만 닫혀요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2167-24736 Dialog/마이크 권한 없음}
 */
function MicrophoneUnavailableDialog({
  onClose,
  onRetry,
}: MicrophoneUnavailableDialogProps) {
  useKeyDown({ key: "Escape", isEnabled: true, onKeyDown: onClose });

  return (
    <Dim onClick={onClose}>
      <ConfirmDialog
        role="dialog"
        aria-modal="true"
        aria-label={TITLE}
        onClick={(e) => e.stopPropagation()}
        title={TITLE}
        description="주소창의 권한 설정에서 마이크를 허용해 주세요."
        cancelLabel="닫기"
        confirmLabel="다시 시도"
        onCancel={onClose}
        onConfirm={onRetry}
      />
    </Dim>
  );
}

interface OpenMicrophoneUnavailableDialogParams {
  /** [다시 시도]를 누르면 모달을 닫은 뒤 부를 함수. 마이크를 다시 받아요 */
  onRetry: () => void;
}

export default useMicrophoneUnavailableDialog;
