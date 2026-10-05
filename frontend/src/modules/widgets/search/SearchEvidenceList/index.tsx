import styled from "@emotion/styled";
import SearchEvidenceCard from "@primitives/ui/SearchEvidenceCard";
import { useSearchEvidenceList } from "./model/useSearchEvidenceList";
import LinkTo from "@/shared/components/primitives/ui/LinkTo";

/**
 * AI 탐색 답변의 근거가 된 문서 리스트를 보여주는 List UI.
 */

export default function SearchEvidenceList() {
  const { evidences } = useSearchEvidenceList();

  return (
    <Container>
      <Header>
        <Title>찾은 문서</Title>
        <SortLabel>관련도순</SortLabel>
      </Header>

      <List>
        {evidences.map(
          ({ id, title, documentPath, href, EvidenceSourceIcon }) => (
            <LinkTo href={href}>
              <SearchEvidenceCard
                key={id}
                title={title}
                documentPath={documentPath}
                evidenceSourceIcon={<EvidenceSourceIcon />}
              />
            </LinkTo>
          ),
        )}
      </List>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 3.25rem;
`;

const Header = styled.header`
  display: flex;
  justify-content: space-between;
  align-items: center;
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading01}
  color: ${({ theme }) => theme.neutral[500]}
`;

const SortLabel = styled.span`
  ${({ theme }) => theme.text.caption02}
  color: ${({ theme }) => theme.neutral[400]}
`;

const List = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
`;
