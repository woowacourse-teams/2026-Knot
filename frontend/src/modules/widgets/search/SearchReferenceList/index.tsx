import styled from "@emotion/styled";
import SearchReferenceCard from "@primitives/ui/SearchReferenceCard";
import { useSearchReferenceList } from "./model/useSearchReferenceList";
import LinkTo from "@/shared/components/primitives/ui/LinkTo";

/**
 * AI 탐색 답변의 근거가 된 문서 리스트를 보여주는 List UI.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7219&t=NtCKbgE8RjHqh556-11
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=606-2921&t=NtCKbgE8RjHqh556-11
 */

export default function SearchReferenceList() {
  const { references } = useSearchReferenceList();

  return (
    <Container>
      <Header>
        <Title>찾은 문서</Title>
        <SortLabel>관련도순</SortLabel>
      </Header>

      <List>
        {references.map(({ id, title, documentPath, href, SourceIcon }) => (
          <LinkTo href={href}>
            <SearchReferenceCard
              key={id}
              title={title}
              documentPath={documentPath}
              sourceIcon={<SourceIcon />}
            />
          </LinkTo>
        ))}
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
