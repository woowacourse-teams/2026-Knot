import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import { useParams } from "react-router";

// TODO(#243): 미리보기 API 응답의 워크스페이스 이름으로 교체
const TEMP_WORKSPACE_NAME = "노티드의 워크스페이스";

export const useWorkspaceJoin = () => {
  const { workspaceId } = useParams();

  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();

  const workspaceName = TEMP_WORKSPACE_NAME;

  /** TODO(#244): 참여 API를 호출해 성공한 뒤 이동하도록 바꿔요. 지금은 현재 `:workspaceId`로 바로 이동해요. */
  const handleJoin = () => {
    if (!workspaceId) return;

    navigateToWorkspaceHome({ workspaceId });
  };

  return { workspaceName, handleJoin };
};
