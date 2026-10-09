import { NO_DECISION_SENTENCE } from "@constants/document";
import styled from "@emotion/styled";
import DocumentBody from "@primitives/ui/DocumentBody";
import { useId } from "react";

import { useDocumentViewer } from "../model/useDocumentViewer";

import DocumentLoadFailed from "./DocumentLoadFailed";
import DocumentSkeleton from "./DocumentSkeleton";

interface DocumentContentProps {
  /** 주소에서 읽어 정수임을 확인한 워크스페이스 id */
  workspaceId: number;
  /** 주소에서 읽어 정수임을 확인한 문서 id */
  documentId: number;
}

/**
 * 문서를 불러와 상태에 맞는 화면을 그려요. 주소 확인은 `DocumentViewer`가 끝낸 뒤예요.
 *
 * 결정이 없는 회의 문서는 본문의 결정 없음 문장을 흐리게 그려요(STT-R23).
 * 문서 머리(경로 · 날짜 · 확인 수 · 복사)와 확인 버튼은 다음 PR에서 더해요.
 */
export default function DocumentContent({
  workspaceId,
  documentId,
}: DocumentContentProps) {
  const titleId = useId();
  const viewer = useDocumentViewer({ workspaceId, documentId });

  if (viewer.status === "loading") {
    return (
      <Container aria-busy="true" aria-label="문서를 불러오고 있어요">
        <DocumentSkeleton />
      </Container>
    );
  }

  if (viewer.status === "error") {
    return <DocumentLoadFailed onRetry={viewer.retry} />;
  }

  return (
    <Container aria-labelledby={titleId}>
      <Title id={titleId}>{viewer.document.title}</Title>
      <DocumentBody
        content={viewer.document.content}
        mutedLines={[NO_DECISION_SENTENCE]}
      />
    </Container>
  );
}

/** 피그마 문서 열(Standard): 폭 720px, 제목과 본문 사이 24px */
const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1.5rem; /* 24px */
  width: 100%;
  max-width: 45rem; /* 720px */
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.neutral[900]};
  overflow-wrap: break-word;
`;
