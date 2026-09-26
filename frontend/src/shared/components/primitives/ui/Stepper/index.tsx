import styled from "@emotion/styled";

import ChevronLeftIcon from "@/assets/icons/chevronLeft.svg";
import ChevronRightIcon from "@/assets/icons/chevronRight.svg";

interface StepperProps {
  /** 지금 보고 있는 순서. 화면에 그대로 보여주므로 1부터 세요. */
  current: number;
  /** 전체 개수 */
  total: number;
  /** 이전 버튼을 눌렀을 때 실행할 동작 */
  onPrev: () => void;
  /** 다음 버튼을 눌렀을 때 실행할 동작 */
  onNext: () => void;
}

/**
 * 여러 항목을 이전·다음으로 하나씩 넘기는 컨트롤.
 *
 * 지금 순서는 쓰는 쪽이 들고 있고, 버튼을 누르면 `onPrev`·`onNext`로 알리기만 해요.
 * 첫 항목에서는 이전 버튼을, 마지막 항목에서는 다음 버튼을 막습니다.
 * 녹음 직후 확인 화면의 하단 바 안에서만 쓰고, 화면에 단독으로 두지 않아요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1750-743 Stepper/RecordingDocs}
 */
export default function Stepper({
  current,
  total,
  onPrev,
  onNext,
}: StepperProps) {
  const isFirst = current <= 1;
  const isLast = current >= total;

  return (
    <Container>
      <StepButton type="button" disabled={isFirst} onClick={onPrev}>
        <ChevronLeftIcon size={12} />
      </StepButton>
      <Count>
        <CurrentNumber>{current}</CurrentNumber>
        <Total>{` / ${total}`}</Total>
      </Count>
      <StepButton type="button" disabled={isLast} onClick={onNext}>
        <ChevronRightIcon size={12} />
      </StepButton>
    </Container>
  );
}

const Container = styled.div`
  display: inline-flex;
  align-items: center;
  padding: 0.1875rem; /* 3px */
  border-radius: 0.625rem; /* 10px */
  background-color: ${({ theme }) => theme.neutral[100]};
`;

const StepButton = styled.button`
  display: flex;
  justify-content: center;
  align-items: center;
  width: 1.75rem; /* 28px */
  height: 1.75rem; /* 28px */
  border-radius: 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[800]};

  &:disabled {
    color: ${({ theme }) => theme.neutral[300]};
  }
`;

const Count = styled.div`
  display: flex;
  align-items: center;
  gap: 0.125rem; /* 2px */
  height: 1.75rem; /* 28px */
  padding: 0 0.625rem; /* 10px */
  border-radius: 0.5rem; /* 8px */
  background-color: ${({ theme }) => theme.neutral[0]};

  ${({ theme }) => theme.text.label01};
`;

const CurrentNumber = styled.span`
  color: ${({ theme }) => theme.neutral[900]};
`;

const Total = styled.span`
  color: ${({ theme }) => theme.neutral[500]};
  /* 피그마 글자가 " / 3"처럼 앞 공백을 품고 있어요. flex 항목의 앞 공백은 지워지므로 살려 둡니다 */
  white-space: pre;
`;
