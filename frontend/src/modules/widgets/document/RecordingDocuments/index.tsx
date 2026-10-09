import styled from "@emotion/styled";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import LoadingIndicator from "@primitives/ui/LoadingIndicator";
import { useId } from "react";
import { useParams } from "react-router";

import { useRecordingDocuments } from "./model/useRecordingDocuments";
import DocumentFailedState from "./ui/DocumentFailedState";
import DraftingState from "./ui/DraftingState";
import NothingToOrganizeState from "./ui/NothingToOrganizeState";

/**
 * 녹음을 끝낸 뒤 문서가 만들어질 때까지 보여 주는 정리 화면 섹션.
 *
 * 주소의 녹음 상태를 3초마다 다시 조회하고, 상태에 따라 정리 중 · 문서로 만들 내용 없음 · 문서를 만들지 못함 가운데 하나를 보여줘요.
 * 문서 만들기에 실패했으면 그 자리에서 다시 시도할 수 있고, 접수되면 정리 중으로 돌아가요.
 */
export default function RecordingDocuments() {
  const titleId = useId();
  const { workspaceId = "", recordingId = "" } = useParams();
  const view = useRecordingDocuments({
    workspaceId: Number(workspaceId),
    recordingId: Number(recordingId),
  });
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();

  const handleGoHome = () => navigateToWorkspaceHome({ workspaceId });

  if (view.status === "loading") {
    return (
      <Container aria-busy="true" aria-label="문서 정리 상태를 확인하고 있어요">
        <LoadingIndicator />
      </Container>
    );
  }

  return (
    <Container aria-labelledby={titleId}>
      {view.status === "organizing" && <DraftingState titleId={titleId} />}
      {view.status === "noContent" && (
        <NothingToOrganizeState titleId={titleId} onGoHome={handleGoHome} />
      )}
      {view.status === "failed" && (
        <DocumentFailedState
          titleId={titleId}
          onRetry={view.retry}
          isRetrying={view.isRetrying}
          onGoHome={handleGoHome}
        />
      )}
    </Container>
  );
}

/** 피그마 State/Drafting · State/DocNotCreated: 폭 518px 묶음 */
const Container = styled.section`
  width: 100%;
  max-width: 32.375rem; /* 518px */
`;
