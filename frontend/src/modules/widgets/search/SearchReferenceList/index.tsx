import styled from "@emotion/styled";
import LinkTo from "@primitives/ui/LinkTo";
import RetryNotice from "@primitives/ui/RetryNotice";
import SearchReferenceCard from "@primitives/ui/SearchReferenceCard";
import Skeleton from "@primitives/ui/Skeleton";

import CloseIcon from "@/assets/icons/sidebar.svg";

import { useSearchReferenceList } from "./model/useSearchReferenceList";

/** 응답 전에 자리를 잡아 두는 카드 수. 페이지로 묶이면 보통 이 정도예요 */
const SKELETON_CARD_COUNT = 3;

const SOURCES_ERROR_MESSAGE = "찾은 문서를 불러오지 못했어요.";
const EMPTY_SOURCES_MESSAGE = "이 답변에는 근거로 쓴 문서가 없어요.";

/**
 * AI 탐색 답변의 근거가 된 문서 리스트를 보여주는 List UI.
 *
 * 답변의 근거 버튼을 눌러야 열리므로, 열려 있지 않을 때 이 위젯은 화면에 놓이지 않습니다.
 * 그 판단은 화면(`ChatPage`)이 하고, 여기서는 열린 동안의 목록과 닫는 버튼만 맡습니다.
 * 서버가 준 청크 단위 출처(최대 8건)를 페이지로 묶어 관련도순으로 보여 줘요.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1434-2024 찾은 문서 열림
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=506-7219
 */
export default function SearchReferenceList() {
  const { references, isLoading, isError, handleRetry, handleClose } =
    useSearchReferenceList();

  return (
    <Container aria-label="찾은 문서">
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

      {isLoading && (
        <List aria-busy="true">
          {Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => (
            <Skeleton key={index} height={5.5} radius={1.5} />
          ))}
        </List>
      )}

      {isError && (
        <RetryNotice message={SOURCES_ERROR_MESSAGE} onRetry={handleRetry} />
      )}

      {!isLoading && !isError && references.length === 0 && (
        <EmptyMessage>{EMPTY_SOURCES_MESSAGE}</EmptyMessage>
      )}

      {references.length > 0 && (
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
      )}
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

const EmptyMessage = styled.p`
  ${({ theme }) => theme.text.body01}
  color: ${({ theme }) => theme.neutral[500]};
  text-align: center;
`;
