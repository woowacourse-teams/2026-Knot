import useDocumentsQuery from "@api/queries/useDocumentsQuery";
import styled from "@emotion/styled";
import { getRouterPath } from "@routes/PATH_ROUTE";
import { groupDocumentsByTopic } from "@utils/groupDocumentsByTopic";

import DocumentFolder from "./DocumentFolder";
import DocumentRow from "./DocumentRow";

interface DocumentFoldersProps {
  /** 주소에서 읽어 정수임을 확인한 워크스페이스 id */
  workspaceId: number;
}

/**
 * 워크스페이스의 문서를 모두 불러와 폴더별로 보여줘요. 주소 확인은 `DocumentList`가 끝낸 뒤예요.
 *
 * 폴더는 이름순, 폴더 안의 문서는 최신순이에요. 확인 여부나 문서 상태로 묶거나 정렬하지 않아요(DOC-R11).
 */
export default function DocumentFolders({ workspaceId }: DocumentFoldersProps) {
  const { data: documentList } = useDocumentsQuery({ workspaceId });

  if (documentList === undefined) return null;

  const folders = groupDocumentsByTopic({
    topics: documentList.topics,
    documents: documentList.items,
  });

  return (
    <Container>
      {folders.map(({ topic, documentCount, documents }) => (
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
