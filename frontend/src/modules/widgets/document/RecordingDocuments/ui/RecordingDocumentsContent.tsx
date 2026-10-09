import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
import { getRouterPath } from "@routes/PATH_ROUTE";
import { Navigate } from "react-router";

import { useRecordingDocuments } from "../model/useRecordingDocuments";

import DocumentFailedState from "./DocumentFailedState";
import DraftingState from "./DraftingState";
import LoadFailedState from "./LoadFailedState";
import NothingToOrganizeState from "./NothingToOrganizeState";

interface RecordingDocumentsContentProps {
  /** 주소에서 읽어 정수임을 확인한 워크스페이스 id */
  workspaceId: number;
  /** 주소에서 읽어 정수임을 확인한 녹음 id */
  recordingId: number;
}

/**
 * 녹음 상태를 조회해 상태에 맞는 화면을 그려요. 주소 확인은 `RecordingDocuments`가 끝낸 뒤예요.
 *
 * 정리 중 · 문서로 만들 내용 없음 · 문서를 만들지 못함 · 불러오지 못함 가운데 하나를 보여 주고,
 * 이 화면에 머물 이유가 없는 녹음은 다른 화면으로 보내요.
 */
export default function RecordingDocumentsContent({
  workspaceId,
  recordingId,
}: RecordingDocumentsContentProps) {
  const view = useRecordingDocuments({ workspaceId, recordingId });
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();

  const routeParams = { workspaceId: String(workspaceId) };
  const handleGoHome = () => navigateToWorkspaceHome(routeParams);

  if (view.status === "loading") {
    return <LoadingIndicator label="문서 정리 상태를 확인하고 있어요" />;
  }

  // 정리가 끝난 녹음은 워크스페이스 홈으로 보내요. 녹음 직후 확인 화면이 생기면 그 화면으로 바꿔요.
  // 뒤로 가기로 이 주소에 돌아와 다시 보내지는 일이 없도록 지금 기록을 바꿔요
  if (view.status === "completed") {
    return (
      <Navigate
        to={getRouterPath({ routeKey: "WORKSPACE_HOME", params: routeParams })}
        replace
      />
    );
  }

  // 아직 끝내지 않은 녹음은 녹음 화면으로 돌려보내요
  if (view.status === "recording") {
    return (
      <Navigate
        to={getRouterPath({ routeKey: "RECORDING", params: routeParams })}
        replace
      />
    );
  }

  if (view.status === "organizing") return <DraftingState />;

  if (view.status === "noContent") {
    return <NothingToOrganizeState onGoHome={handleGoHome} />;
  }

  if (view.status === "failed") {
    return (
      <DocumentFailedState
        onRetry={view.retry}
        isRetrying={view.isRetrying}
        onGoHome={handleGoHome}
      />
    );
  }

  return <LoadFailedState onRetry={view.retry} />;
}
