import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
import { useParams } from "react-router";

import { useRecordingDocuments } from "./model/useRecordingDocuments";
import { useRecordingDocumentsRedirect } from "./model/useRecordingDocumentsRedirect";
import DocumentFailedState from "./ui/DocumentFailedState";
import DraftingState from "./ui/DraftingState";
import LoadFailedState from "./ui/LoadFailedState";
import NothingToOrganizeState from "./ui/NothingToOrganizeState";

/**
 * 녹음을 끝낸 뒤 문서가 만들어질 때까지 보여 주는 정리 화면 섹션.
 *
 * 주소의 녹음 상태를 3초마다 다시 조회하고, 상태에 따라 정리 중 · 문서로 만들 내용 없음 · 문서를 만들지 못함 · 불러오지 못함 가운데 하나를 보여줘요.
 * 문서 만들기에 실패했으면 그 자리에서 다시 시도할 수 있고, 접수되면 정리 중으로 돌아가요.
 * 이 화면에 머물 이유가 없는 녹음은 다른 화면으로 보내요.
 *
 * 주소의 두 id는 숫자인지 확인하지 않고 그대로 요청해요. 잘못된 주소인지는 서버가 판단하고, 여기서는 그 응답에 따라 문서를 불러오지 못했다고 알려요.
 * 같은 판단을 프론트에도 두면 기준이 두 곳에 생기기 때문이에요.
 */
export default function RecordingDocuments() {
  const params = useParams();

  const workspaceId = Number(params.workspaceId);
  const recordingId = Number(params.recordingId);
  const { status, retryLoad, retryGeneration, isRetrying } =
    useRecordingDocuments({ workspaceId, recordingId });
  const { isRedirecting } = useRecordingDocumentsRedirect({
    workspaceId,
    status,
  });
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();

  const handleGoHome = () =>
    navigateToWorkspaceHome({ workspaceId: String(workspaceId) });

  // 다른 화면으로 보내는 동안에는 그릴 것이 없어요
  if (isRedirecting) return null;

  if (status === "loading") {
    return <LoadingIndicator label="문서 정리 상태를 확인하고 있어요" />;
  }

  if (status === "organizing") return <DraftingState />;

  if (status === "noContent") {
    return <NothingToOrganizeState onGoHome={handleGoHome} />;
  }

  if (status === "failed") {
    return (
      <DocumentFailedState
        onRetry={retryGeneration}
        isRetrying={isRetrying}
        onGoHome={handleGoHome}
      />
    );
  }

  return <LoadFailedState onRetry={retryLoad} />;
}
