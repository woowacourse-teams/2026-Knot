import styled from "@emotion/styled";
import useConversationId from "@hooks/domain/search/useConversationId";
import useOpenedSearchEvidences from "@hooks/domain/search/useOpenedSearchEvidences";
import LinkTo from "@primitives/ui/LinkTo";
import SearchEvidenceCard from "@primitives/ui/SearchEvidenceCard";
import { getRouterPath } from "@routes/PATH_ROUTE";
import { useId } from "react";
import { useParams } from "react-router";

/**
 * 찾은 기록 패널. 지금 펼친 답변의 근거가 된 문서를 관련도 순으로 최대 3개 보여줘요.
 *
 * 카드를 누르면 그 문서 보기 화면으로 가요.
 * 펼친 답변이 없거나 그 답변에 근거가 없으면 아무것도 그리지 않아요.
 * 여닫기는 답변 아래 버튼과 GNB의 찾은 기록 버튼이 맡으므로 패널 안에는 닫기 버튼을 두지 않아요.
 */
export default function SearchEvidenceList() {
  const titleId = useId();
  const { workspaceId = "" } = useParams();
  const { conversationId } = useConversationId();
  const { evidences } = useOpenedSearchEvidences({ conversationId });

  if (evidences.length === 0) return null;

  return (
    <Container aria-labelledby={titleId}>
      <Title id={titleId}>찾은 기록</Title>

      <List>
        {evidences.map(({ documentId, title, sourceType, createdAt }) => (
          <li key={documentId}>
            <CardLink
              href={getRouterPath({
                routeKey: "DOCUMENT",
                params: { workspaceId, documentId: String(documentId) },
              })}
            >
              <SearchEvidenceCard
                title={title}
                sourceType={sourceType}
                createdAt={createdAt}
              />
            </CardLink>
          </li>
        ))}
      </List>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 1rem; /* 16px */
  padding: 1.5rem; /* 24px */
  border-radius: 1.25rem; /* 20px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow03};
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.label01};
  color: ${({ theme }) => theme.neutral[900]};
`;

const List = styled.ul`
  display: flex;
  flex-direction: column;
  gap: 0.75rem; /* 12px */
`;

const CardLink = styled(LinkTo)`
  display: block;
  border-radius: 0.75rem; /* 12px */

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;
