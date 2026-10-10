import styled from "@emotion/styled";
import { getRouterPath } from "@routes/PATH_ROUTE";

import { useDocumentList } from "../model/useDocumentList";

import DocumentFolder from "./DocumentFolder";
import DocumentListEmpty from "./DocumentListEmpty";
import DocumentListLoadFailed from "./DocumentListLoadFailed";
import DocumentListSkeleton from "./DocumentListSkeleton";
import DocumentRow from "./DocumentRow";

interface DocumentFoldersProps {
  /** 주소에서 읽어 정수임을 확인한 워크스페이스 id */
  workspaceId: number;
}

/**
 * 워크스페이스의 문서를 모두 불러와 상태에 맞는 화면을 그려요. 주소 확인은 `DocumentList`가 끝낸 뒤예요.
 * 문서를 받으면 폴더별로 보여주고, 불러오는 중 · 문서 없음 · 불러오기 실패는 목록 자리에 안내를 둬요.
 *
 * 폴더는 이름순, 폴더 안의 문서는 최신순이에요. 확인 여부나 문서 상태로 묶거나 정렬하지 않아요(DOC-R11).
 */
export default function DocumentFolders({ workspaceId }: DocumentFoldersProps) {
  const documentList = useDocumentList({ workspaceId });

  if (documentList.status === "loading") return <DocumentListSkeleton />;

  if (documentList.status === "failed") {
    return <DocumentListLoadFailed onRetry={documentList.retry} />;
  }

  if (documentList.status === "empty") return <DocumentListEmpty />;

  return (
    <Container>
      {documentList.folders.map(({ topic, documentCount, documents }) => (
        <DocumentFolder key={topic} topic={topic} documentCount={documentCount}>
          {documents.map(
            ({ id, title, summary, createdAt, recordingDurationSeconds }) => (
              <DocumentRow
                key={id}
                href={getRouterPath({
                  routeKey: "DOCUMENT",
                  params: {
                    workspaceId: String(workspaceId),
                    documentId: String(id),
                  },
                })}
                title={title}
                summary={summary}
                createdAt={createdAt}
                recordingDurationSeconds={recordingDurationSeconds}
              />
            ),
          )}
        </DocumentFolder>
      ))}
    </Container>
  );
}

/** 피그마 List/Docs: 폴더 사이 28px */
const Container = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.75rem; /* 28px */
`;
