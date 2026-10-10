import styled from "@emotion/styled";
import { getRouterPath } from "@routes/PATH_ROUTE";
import { useId } from "react";
import { useParams } from "react-router";

import { useDocumentList } from "./model/useDocumentList";
import DocumentFolder from "./ui/DocumentFolder";
import DocumentListEmpty from "./ui/DocumentListEmpty";
import DocumentListLoadFailed from "./ui/DocumentListLoadFailed";
import DocumentListSkeleton from "./ui/DocumentListSkeleton";
import DocumentRow from "./ui/DocumentRow";

/**
 * 문서 목록 섹션. 워크스페이스의 모든 문서를 폴더(주제)별로 모아 보여주고, 행을 누르면 그 문서 보기로 가요.
 * 불러오는 중 · 문서 없음 · 불러오기 실패는 제목과 설명을 그대로 두고 목록 자리에 안내를 둬요.
 *
 * 폴더는 이름순, 폴더 안의 문서는 최신순이에요. 확인 여부나 문서 상태로 묶거나 정렬하지 않아요(DOC-R11).
 *
 * 주소의 워크스페이스 id는 숫자인지 확인하지 않고 그대로 요청해요. 잘못된 주소인지는 서버가 판단하고, 여기서는 그 응답에 따라 목록을 불러오지 못했다고 알려요.
 * 같은 판단을 프론트에도 두면 기준이 두 곳에 생기기 때문이에요.
 */
export default function DocumentList() {
  const titleId = useId();
  const params = useParams();

  const workspaceId = Number(params.workspaceId);
  const { status, folders, retry } = useDocumentList({ workspaceId });

  return (
    <Container aria-labelledby={titleId}>
      <Header>
        <Title id={titleId}>문서</Title>
        <Description>녹음하고 정리한 내용을 폴더별로 모아둬요</Description>
      </Header>
      {status === "loading" && <DocumentListSkeleton />}
      {status === "failed" && <DocumentListLoadFailed onRetry={retry} />}
      {status === "empty" && <DocumentListEmpty />}
      {status === "ready" && (
        <Folders>
          {folders.map(({ topic, documentCount, documents }) => (
            <DocumentFolder
              key={topic}
              topic={topic}
              documentCount={documentCount}
            >
              {documents.map(
                ({
                  id,
                  title,
                  summary,
                  createdAt,
                  recordingDurationSeconds,
                }) => (
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
        </Folders>
      )}
    </Container>
  );
}

/** 피그마 List/Docs: 폭 880px, 머리와 폴더 묶음 사이 28px */
const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1.75rem; /* 28px */
  width: 100%;
  max-width: 55rem; /* 880px */
`;

/** 피그마 머리: 제목과 설명 사이 6px */
const Header = styled.div`
  display: flex;
  flex-direction: column;
  gap: 0.375rem; /* 6px */
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading02};
  color: ${({ theme }) => theme.neutral[900]};
`;

const Description = styled.p`
  ${({ theme }) => theme.text.body01};
  color: ${({ theme }) => theme.neutral[600]};
`;

/** 피그마 List/Docs: 폴더 사이 28px */
const Folders = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.75rem; /* 28px */
`;
