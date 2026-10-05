import useWorkspaceQuery from "@api/queries/useWorkspaceQuery";
import useWorkspacesQuery from "@api/queries/useWorkspacesQuery";

import { useCurrentWorkspaceId } from "./useCurrentWorkspaceId";
import { useWorkspaceMenu } from "./useWorkspaceMenu";
import { useWorkspaceSwitch } from "./useWorkspaceSwitch";

/**
 * 워크스페이스 전환 버튼이 그리는 데 필요한 값과 동작을 한데 모읍니다.
 *
 * 메뉴 열림 상태(`useWorkspaceMenu`)와 화면 이동(`useWorkspaceSwitch`)을 잇고,
 * 지금 워크스페이스의 이름과 메뉴에 띄울 워크스페이스 목록을 함께 돌려줘요.
 */
export const useWorkspaceSwitcher = () => {
  const currentWorkspaceId = useCurrentWorkspaceId();
  const { menuId, menuAreaRef, isOpen, handleToggle, close } =
    useWorkspaceMenu();
  const { switchWorkspace, createWorkspace } = useWorkspaceSwitch({
    currentWorkspaceId,
  });
  // 레이아웃의 진입 판정과 같은 쿼리라 요청은 한 번만 나가요
  const { data: workspace } = useWorkspaceQuery({
    workspaceId: currentWorkspaceId,
  });
  // 메뉴를 열 때 기다리지 않도록 버튼이 그려질 때 미리 받아 둬요
  const { data: workspaceList } = useWorkspacesQuery();

  const workspaceName = workspace?.name ?? "";
  const workspaces = workspaceList?.workspaces ?? [];

  // 녹음 확인 모달이 뜰 때 메뉴가 이미 닫혀 있도록 먼저 닫아요. [취소]하면 메뉴 없이 지금 화면으로 돌아와요
  const handleSelectWorkspace = (workspaceId: number) => {
    close();
    switchWorkspace(workspaceId);
  };

  const handleCreateWorkspace = () => {
    close();
    createWorkspace();
  };

  return {
    currentWorkspaceId,
    workspaceName,
    workspaces,
    menuId,
    menuAreaRef,
    isOpen,
    handleToggle,
    handleSelectWorkspace,
    handleCreateWorkspace,
  };
};
