import styled from "@emotion/styled";
import SearchReferenceCard from "@primitives/ui/SearchReferenceCard";
import { mock } from "./mock";
import { getReferenceSourceIcon } from "./utils/getReferenceSourceIcon";

/**
 * AI 탐색 답변의 근거가 된 문서 리스트를 보여주는 List UI.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7219&t=NtCKbgE8RjHqh556-11
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=606-2921&t=NtCKbgE8RjHqh556-11
 */

export default function SearchReferenceList() {
  return (
    <Container>
      <Header>
        <Title>찾은 문서</Title>
        <SortLabel>관련도순</SortLabel>
      </Header>

      <List>
        {mock.map((data) => {
          const ReferenceSourceIcon = getReferenceSourceIcon(
            data.referenceSource,
          );

          return (
            <SearchReferenceCard
              key={data.id}
              title={data.notionPage.title}
              documentPath={data.notionPage.path}
              href={data.notionPage.notionUrl}
              sourceIcon={<ReferenceSourceIcon size={20} color="#6B6862" />}
            />
          );
        })}
      </List>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 3.25rem;
  width: 23.75rem;
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
