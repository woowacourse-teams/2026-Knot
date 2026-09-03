import styled from "@emotion/styled";
import SearchReferenceCard from "@primitives/ui/SearchReferenceCard";

import CloseIcon from "@/assets/icons/sidebar.svg";
import LinkTo from "@/shared/components/primitives/ui/LinkTo";

import { useSearchReferenceList } from "./model/useSearchReferenceList";

/**
 * AI 탐색 답변의 근거가 된 문서 리스트를 보여주는 List UI.
 *
 * 답변의 근거 버튼을 눌러야 열리므로, 열려 있지 않을 때 이 위젯은 화면에 놓이지 않습니다.
 * 그 판단은 화면(`ChatPage`)이 하고, 여기서는 열린 동안의 목록과 닫는 버튼만 맡습니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1434-2024 찾은 문서 열림
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7219
 */
export default function SearchReferenceList() {
  const { references, handleClose } = useSearchReferenceList();

  return (
    <Container>
      <Header>
        <Title>찾은 문서</Title>

        <Tools>
          <SortLabel>관련도순</SortLabel>
          <CloseButton
            type="button"
            aria-label="찾은 문서 닫기"
            onClick={handleClose}
          >
            <CloseIcon size={18} />
          </CloseButton>
        </Tools>
      </Header>

      <List>
        {references.map(({ id, title, documentPath, href, SourceIcon }) => (
          <LinkTo key={id} href={href}>
            <SearchReferenceCard
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
  gap: 3.25rem; /* 52px */
`;

const Header = styled.header`
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.75rem;
`;

const Title = styled.h2`
  ${({ theme }) => theme.text.heading01}
  color: ${({ theme }) => theme.neutral[500]}
`;

const Tools = styled.div`
  display: flex;
  align-items: center;
  gap: 0.75rem; /* 12px */
`;

const SortLabel = styled.span`
  ${({ theme }) => theme.text.caption02}
  color: ${({ theme }) => theme.neutral[400]}
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-836 Btn/사이드바} */
const CloseButton = styled.button`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: 0.5625rem; /* 9px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 62.4375rem;
  background-color: ${({ theme }) => theme.neutral[0]};
  color: ${({ theme }) => theme.neutral[800]};
  box-shadow: ${({ theme }) => theme.shadow02};

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const List = styled.div`
  display: flex;
  flex-direction: column;
  gap: 1.25rem; /* 20px */
`;
