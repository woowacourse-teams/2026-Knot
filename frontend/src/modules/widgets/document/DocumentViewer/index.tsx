import { NO_DECISION_SENTENCE } from "@constants/document";
import styled from "@emotion/styled";
import DocumentHeader from "@features/document/DocumentHeader";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import DocumentBody from "@primitives/ui/DocumentBody";
import RetryNotice from "@primitives/ui/RetryNotice";
import { useId } from "react";
import { useParams } from "react-router";

import { useDocumentViewer } from "./model/useDocumentViewer";
import DocumentNotFound from "./ui/DocumentNotFound";
import DocumentSkeleton from "./ui/DocumentSkeleton";

/**
 * 문서 보기 섹션. 주소의 문서를 불러와 문서 머리와 본문을 보여줘요.
 *
 * 결정이 없는 회의 문서는 본문의 결정 없음 문장을 흐리게 그려요(STT-R23).
 * 제목은 문서 머리가 그리고, 섹션의 이름으로 쓰도록 제목 id를 넘겨요.
 */
export default function DocumentViewer() {
  const titleId = useId();
  const { workspaceId = "", documentId = "" } = useParams();
  const viewer = useDocumentViewer({
    workspaceId: Number(workspaceId),
    documentId: Number(documentId),
  });
  const { navigateToWorkspaceHome } = useNavigateToWorkspaceHome();

  if (viewer.status === "loading") {
    return (
      <Container aria-busy="true" aria-label="문서를 불러오고 있어요">
        <DocumentSkeleton />
      </Container>
    );
  }

  if (viewer.status === "notFound") {
    return (
      <Container>
        <DocumentNotFound
          onGoHome={() => navigateToWorkspaceHome({ workspaceId })}
        />
      </Container>
    );
  }

  if (viewer.status === "error") {
    return (
      <Container>
        <RetryNotice
          message="문서를 불러오지 못했어요"
          onRetry={viewer.retry}
        />
      </Container>
    );
  }

  return (
    <Container aria-labelledby={titleId}>
      <DocumentHeader documentId={viewer.document.id} titleId={titleId} />
      <DocumentBody
        content={viewer.document.content}
        mutedLines={[NO_DECISION_SENTENCE]}
      />
    </Container>
  );
}

/** 피그마 문서 열(Standard): 폭 720px, 문서 머리와 본문 사이 24px */
const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1.5rem; /* 24px */
  width: 100%;
  max-width: 45rem; /* 720px */
`;
