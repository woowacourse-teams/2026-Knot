import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import CheckIcon from "@/assets/icons/check.svg";

interface DocumentConfirmButtonProps {
  /** 내가 이미 확인한 문서인지. `true`면 누를 수 없는 「확인했어요」를 그려요 */
  isConfirmed: boolean;
  /** 확인 요청과 그 뒤의 문서 다시 불러오기가 끝나지 않았는지. `true`인 동안에는 누를 수 없어요 */
  isConfirming: boolean;
  /** 버튼을 눌렀을 때 실행할 동작 */
  onConfirm: () => void;
}

/**
 * 문서를 확인했다고 기록하는 버튼. 확인한 뒤에는 누를 수 없는 「확인했어요」로 바뀌어요. 확인은 되돌릴 수 없어요.
 *
 * 누구에게 보일지와 눌렀을 때의 요청은 문서 보기가 정하고, 여기서는 버튼의 모양과 누를 수 있는지만 그려요.
 */
export default function DocumentConfirmButton({
  isConfirmed,
  isConfirming,
  onConfirm,
}: DocumentConfirmButtonProps) {
  if (isConfirmed) {
    return (
      <ConfirmedButton type="button" disabled>
        <CheckIcon />
        확인했어요
      </ConfirmedButton>
    );
  }

  return (
    <Button isLoading={isConfirming} onClick={onConfirm}>
      <CheckIcon />
      문서를 확인했어요
    </Button>
  );
}

/**
 * 피그마 Button/CTA/M · 확인했어요: 높이 48px, 좌우 24px, 모서리 13px, 아이콘 16px, 아이콘과 글자 사이 8px.
 * 공통 `Button`의 비활성 모양과 색 · 여백이 달라 따로 그려요
 */
const ConfirmedButton = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
  padding: 0.75rem 1.5rem; /* 12px 24px */
  border-radius: 0.8125rem; /* 13px */
  background-color: ${({ theme }) => theme.neutral[100]};
  color: ${({ theme }) => theme.neutral[500]};
  white-space: nowrap;

  ${({ theme }) => theme.text.label01};

  & > svg {
    flex-shrink: 0;
    width: 1rem; /* 16px */
    height: 1rem;
  }
`;
