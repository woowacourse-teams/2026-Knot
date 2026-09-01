import styled from "@emotion/styled";

interface ChatSessionRowProps {
  title: string;
  lastMessageAt: string;
  isSelected?: boolean;
  onSelect: () => void;
}

/**
 * 대화 목록의 한 줄. 대화 제목과 마지막으로 오간 시각을 보여줍니다.
 *
 * 지금 보고 있는 대화는 `isSelected`로 채워진 모양이 되며, 이 상태를 `aria-current`로도 알립니다.
 *
 * @see https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=580-1438
 */
export default function ChatSessionRow({
  title,
  lastMessageAt,
  isSelected = false,
  onSelect,
}: ChatSessionRowProps) {
  return (
    <Container
      type="button"
      aria-current={isSelected ? "page" : undefined}
      $isSelected={isSelected}
      onClick={onSelect}
    >
      <Title $isSelected={isSelected}>{title}</Title>
      <Time>{lastMessageAt}</Time>
    </Container>
  );
}

const Container = styled.button<{ $isSelected: boolean }>`
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
  width: 100%;
  padding: 0.75rem;
  border-radius: 0.875rem;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.3s ease-in;

  background-color: ${({ $isSelected }) =>
    $isSelected ? "rgba(255, 255, 255, 0.08)" : "transparent"};

  &:hover {
    background-color: rgba(255, 255, 255, 0.04);
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const Title = styled.span<{ $isSelected: boolean }>`
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  ${({ theme }) => theme.text.body01};

  color: ${({ theme, $isSelected }) =>
    $isSelected ? theme.neutral[0] : theme.neutral[300]};
`;

const Time = styled.span`
  ${({ theme }) => theme.text.caption01};
  color: ${({ theme }) => theme.neutral[500]};
`;
