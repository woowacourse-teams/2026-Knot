import Button from "@primitives/ui/Button";

import CheckIcon from "@/assets/icons/check.svg";

interface DocumentConfirmButtonProps {
  /** 확인 요청과 그 뒤의 문서 다시 불러오기가 끝나지 않았는지. `true`인 동안에는 누를 수 없어요 */
  isConfirming: boolean;
  /** 버튼을 눌렀을 때 실행할 동작 */
  onConfirm: () => void;
}

/**
 * 문서를 확인했다고 기록하는 버튼.
 *
 * 누구에게 보일지와 눌렀을 때의 요청은 문서 보기가 정하고, 여기서는 버튼의 모양과 누를 수 있는지만 그려요.
 */
export default function DocumentConfirmButton({
  isConfirming,
  onConfirm,
}: DocumentConfirmButtonProps) {
  return (
    <Button isLoading={isConfirming} onClick={onConfirm}>
      <CheckIcon />
      문서를 확인했어요
    </Button>
  );
}
