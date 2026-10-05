import styled from "@emotion/styled";

import ChevronRight from "@/assets/icons/chevronRight.svg";
import File from "@/assets/icons/file.svg";

interface EvidenceButtonProps {
  /** 답변의 근거가 된 기록 수 */
  count: number;
  /** 이 답변의 찾은 기록이 지금 열려 있는지 */
  isOpen: boolean;
  onClick: () => void;
}

/**
 * 답변 아래에서 그 답변의 찾은 기록을 여는 버튼.
 *
 * 지금 열려 있는 답변의 버튼은 채워진 모양이 되고, 스크린리더에도 눌린 상태로 알려요.
 */
export default function EvidenceButton({
  count,
  isOpen,
  onClick,
}: EvidenceButtonProps) {
  return (
    <Container
      type="button"
      aria-pressed={isOpen}
      $isOpen={isOpen}
      onClick={onClick}
    >
      <FileIcon size={16} />
      <Label>기록 {count}개에서 찾았어요</Label>
      <ChevronIcon size={12} />
    </Container>
  );
}

const Container = styled.button<{ $isOpen: boolean }>`
  display: inline-flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
  height: 2rem; /* 32px */
  padding: 0 0.5rem; /* 8px */
  border: 1px solid ${({ theme }) => theme.neutral[300]};
  border-radius: 0.5rem; /* 8px */
  background-color: ${({ theme, $isOpen }) =>
    $isOpen ? theme.neutral[100] : "transparent"};
  color: ${({ theme, $isOpen }) =>
    $isOpen ? theme.neutral[900] : theme.neutral[600]};
  transition:
    background-color 0.3s ease-in,
    color 0.3s ease-in;

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

const FileIcon = styled(File)`
  flex-shrink: 0;
`;

const ChevronIcon = styled(ChevronRight)`
  flex-shrink: 0;
`;

const Label = styled.span`
  white-space: nowrap;
  ${({ theme }) => theme.text.caption02};
`;
