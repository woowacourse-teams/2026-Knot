import styled from "@emotion/styled";

import ChevronRightIcon from "@/assets/icons/chevronRight.svg";

interface BreadcrumbProps {
  /** 위 단계 이름. 문서 화면에서는 `문서`예요. */
  parent: string;
  /** 지금 보고 있는 항목 이름. 길면 한 줄에서 말줄임해요. */
  current: string;
}

/**
 * 지금 보고 있는 화면이 어디인지 보여주는 2단 경로.
 *
 * 동작 규칙은 스토리북 `Shared/Breadcrumb`에서 확인해요.
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1814-16211 Doc/Breadcrumb}
 */
export default function Breadcrumb({ parent, current }: BreadcrumbProps) {
  return (
    <Container>
      <Parent>{parent}</Parent>
      <ChevronRightIcon size={12} />
      <Current>{current}</Current>
    </Container>
  );
}

const Container = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 0.375rem; /* 6px */
  /* 놓인 자리보다 넓어지지 않아야 지금 항목이 줄어들며 말줄임돼요 */
  max-width: 100%;
  /* 가로 flex 줄에 다른 요소와 함께 놓여도 제목 전체 폭만큼 버티지 않고 줄어들어요 */
  min-width: 0;

  ${({ theme }) => theme.text.caption02};

  & > svg {
    flex-shrink: 0;
    color: ${({ theme }) => theme.neutral[400]};
  }
`;

const Parent = styled.span`
  flex-shrink: 0;
  color: ${({ theme }) => theme.neutral[500]};
`;

const Current = styled.span`
  overflow: hidden;
  color: ${({ theme }) => theme.neutral[700]};
  white-space: nowrap;
  text-overflow: ellipsis;
`;
