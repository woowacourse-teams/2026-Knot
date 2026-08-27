import Button from "@primitives/ui/Button";
import useNavigateToWorkspaceCode from "@hooks/domain/workspace/useNavigateToWorkspaceCode";

/**
 * 초대 코드 참여 화면(`/workspace/code`)으로 이동하는 버튼.
 */
export default function JoinWorkspaceByCodeButton() {
  const { navigateToWorkspaceCode } = useNavigateToWorkspaceCode();

  return (
    <Button
      size="lg"
      variant="outline"
      isFullWidth
      onClick={navigateToWorkspaceCode}
    >
      초대 코드로 참여하기
    </Button>
  );
}
