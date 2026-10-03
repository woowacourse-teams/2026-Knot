import Button from "@primitives/ui/Button";
import useNavigateToWorkspaceCreate from "@hooks/domain/workspace/useNavigateToWorkspaceCreate";

/**
 * 새 워크스페이스 생성 화면(`/workspace/create`)으로 이동하는 버튼.
 *
 * 동작 규칙은 스토리북 `Workspace/WorkspaceCreateButton`에서 확인해요.
 */
export default function WorkspaceCreateButton() {
  const { navigateToWorkspaceCreate } = useNavigateToWorkspaceCreate();

  return (
    <Button size="lg" isFullWidth onClick={navigateToWorkspaceCreate}>
      새 워크스페이스 만들기
    </Button>
  );
}
