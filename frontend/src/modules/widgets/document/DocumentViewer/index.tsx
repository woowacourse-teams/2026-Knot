import { NO_DECISION_SENTENCE } from "@constants/document";
import styled from "@emotion/styled";
import DocumentConfirmButton from "@features/document/DocumentConfirmButton";
import DocumentConfirmCount from "@features/document/DocumentConfirmCount";
import DocumentCopyButton from "@features/document/DocumentCopyButton";
import useNavigateToWorkspaceHome from "@hooks/domain/workspace/useNavigateToWorkspaceHome";
import Breadcrumb from "@primitives/ui/Breadcrumb";
import Chip from "@primitives/ui/Chip";
import DocumentBody from "@primitives/ui/DocumentBody";
import RetryNotice from "@primitives/ui/RetryNotice";
import { formatDate } from "@utils/formatDate";
import { formatDurationFromSeconds } from "@utils/formatDurationFromSeconds";
import { useId } from "react";
import { useParams } from "react-router";

import { useDocumentViewer } from "./model/useDocumentViewer";
import DocumentNotFound from "./ui/DocumentNotFound";
import DocumentSkeleton from "./ui/DocumentSkeleton";

/**
 * 문서 보기 섹션. 주소의 문서를 불러와 경로 · 복사 버튼 · 제목 · 만든 날짜 · 녹음 길이 · 확인 수 · 본문 · 확인 버튼을 보여줘요.
 *
 * 받은 문서 값을 그리기만 하면 되는 것(경로 · 제목 · 날짜 · 녹음 길이 · 본문)은 여기서 직접 그려요.
 * 스스로 조회하거나 동작하는 것(복사 버튼 · 확인 수 · 확인 버튼)은 문서 ID만 받는 features를 놓아요.
 * 결정이 없는 회의 문서는 본문의 결정 없음 문장을 흐리게 그려요(STT-R23).
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
      <Breadcrumb parent="문서" current={viewer.document.title} />
      <TopRow>
        <DocumentCopyButton documentId={viewer.document.id} />
      </TopRow>
      <TitleBlock>
        <Title id={titleId}>{viewer.document.title}</Title>
        <MetaRow>
          <Meta>
            <CreatedDate dateTime={viewer.document.createdAt}>
              {formatDate(viewer.document.createdAt)}
            </CreatedDate>
            <Chip>
              {formatDurationFromSeconds(
                viewer.document.recordingDurationSeconds,
              )}
            </Chip>
          </Meta>
          <DocumentConfirmCount documentId={viewer.document.id} />
        </MetaRow>
      </TitleBlock>
      <DocumentBody
        content={viewer.document.content}
        mutedLines={[NO_DECISION_SENTENCE]}
      />
      <ConfirmArea>
        <DocumentConfirmButton documentId={viewer.document.id} />
      </ConfirmArea>
    </Container>
  );
}

/** 피그마 문서 열(Standard): 폭 720px, 경로 · 복사 줄 · 제목 묶음 · 본문 사이 24px */
const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1.5rem; /* 24px */
  width: 100%;
  max-width: 45rem; /* 720px */
`;

/** 피그마 TopRow: 복사 버튼을 오른쪽 끝에 놓는 줄 */
const TopRow = styled.div`
  display: flex;
  justify-content: flex-end;
`;

/** 피그마 TitleBlock: 제목과 날짜 줄 사이 4px */
const TitleBlock = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.25rem; /* 4px */
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.neutral[900]};
  overflow-wrap: break-word;
`;

/** 피그마 MetaRow: 왼쪽에 날짜 · 녹음 길이, 오른쪽 끝에 확인 수 */
const MetaRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem; /* 8px */
`;

/** 피그마 Meta: 날짜와 녹음 길이 칩 사이 8px */
const Meta = styled.div`
  display: flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
`;

const CreatedDate = styled.time`
  ${({ theme }) => theme.text.caption02};
  color: ${({ theme }) => theme.neutral[500]};
`;

/** 피그마 ConfirmArea: 확인 버튼을 가운데에 놓는 줄. 위 16px · 아래 8px */
const ConfirmArea = styled.div`
  display: flex;
  justify-content: center;
  padding: 1rem 0 0.5rem; /* 16px 0 8px */

  /* 확인 버튼은 스스로 보일지 정해요. 그리지 않을 때는 이 줄의 여백과 간격도 남기지 않아요 */
  &:empty {
    display: none;
  }
`;
