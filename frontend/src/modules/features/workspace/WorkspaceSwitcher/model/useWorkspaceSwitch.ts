import useEndRecordingBeforeMove from "@hooks/domain/recording/useEndRecordingBeforeMove";
import useNavigateToWorkspaceCreate from "@hooks/domain/workspace/useNavigateToWorkspaceCreate";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";

interface UseWorkspaceSwitchParams {
  /** 지금 보고 있는 워크스페이스 ID */
  currentWorkspaceId: number;
}

/**
 * 다른 워크스페이스로 옮겨 가거나 새 워크스페이스를 만드는 화면으로 보냅니다.
 *
 * 다른 워크스페이스를 고르면 그 워크스페이스 홈으로, 새로 만들기를 고르면 생성 화면으로 가요.
 * 지금 워크스페이스를 고르면 갈 곳이 없어 아무것도 하지 않아요.
 * 마지막으로 본 워크스페이스는 옮겨 간 화면의 레이아웃이 갱신해요(`useWorkspaceEntry`).
 *
 * 녹음 중이면 옮겨 가기 전에 녹음을 끝낼지 물어요(`useEndRecordingBeforeMove`).
 */
export const useWorkspaceSwitch = ({
  currentWorkspaceId,
}: UseWorkspaceSwitchParams) => {
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();
  const { navigateToWorkspaceCreate } = useNavigateToWorkspaceCreate();
  const { moveAfterEndingRecording } = useEndRecordingBeforeMove();

  const switchWorkspace = (workspaceId: number) => {
    if (workspaceId === currentWorkspaceId) return;

    moveAfterEndingRecording(() =>
      navigateToWorkspaceHome({ workspaceId: String(workspaceId) }),
    );
  };

  const createWorkspace = () => {
    moveAfterEndingRecording(navigateToWorkspaceCreate);
  };

  return { switchWorkspace, createWorkspace };
};
