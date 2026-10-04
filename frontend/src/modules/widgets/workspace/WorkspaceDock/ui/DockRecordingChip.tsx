import styled from "@emotion/styled";

import ChevronRightIcon from "@/assets/icons/chevronRight.svg";

interface DockRecordingChipProps {
  /** 녹음한 시간. 예: `12:48` */
  elapsedTime: string;
  isPaused: boolean;
  /** 칩을 누르면 녹음 화면으로 가요 */
  onOpen: () => void;
  /** 넘기면 칩 오른쪽에 중지 버튼을 둬요. 펼친 독은 옆에 보내기 버튼이 있어 두지 않아요 */
  onStop?: () => void;
}

/**
 * 녹음 화면이 아닌 곳에서 녹음이 이어지고 있을 때 마이크 자리에 놓이는 녹음 칩.
 *
 * 빨간 점·녹음한 시간·`›`를 누르면 녹음 화면으로 가고, 접힌 독에서는 오른쪽 중지 버튼으로 녹음을 끝내요.
 */
export default function DockRecordingChip({
  elapsedTime,
  isPaused,
  onOpen,
  onStop,
}: DockRecordingChipProps) {
  return (
    <Container $hasStop={onStop !== undefined}>
      <OpenButton type="button" aria-label="녹음 화면으로 이동" onClick={onOpen}>
        <RecDot aria-hidden="true" $isPaused={isPaused} />
        <ElapsedTime>{elapsedTime}</ElapsedTime>
        <ChevronRightIcon size={12} />
      </OpenButton>

      {onStop && (
        <StopButton type="button" aria-label="녹음 끝내기" onClick={onStop}>
          <StopMark />
        </StopButton>
      )}
    </Container>
  );
}

/** 중지 버튼이 있으면 오른쪽 여백을 4px로 줄여 32px 버튼이 칩 끝에 붙어요. */
const Container = styled.div<{ $hasStop: boolean }>`
  display: flex;
  flex-shrink: 0;
  align-self: center;
  align-items: center;
  gap: 0.5rem; /* 8px */
  padding: ${({ $hasStop }) =>
    $hasStop
      ? "0.25rem 0.25rem 0.25rem 0.875rem" /* 4px 4px 4px 14px */
      : "0.25rem 0.75rem 0.25rem 0.875rem"}; /* 4px 12px 4px 14px */
  border-radius: 1.25rem; /* 20px */
  background-color: ${({ theme }) => theme.neutral[700]};
`;

const OpenButton = styled.button`
  display: flex;
  align-items: center;
  gap: 0.5rem; /* 8px */
  color: ${({ theme }) => theme.neutral[0]};

  & > svg {
    flex-shrink: 0;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

/** 녹음 중이면 빨간 점이고, 일시정지면 녹음 화면처럼 회색으로 바뀌어요. */
const RecDot = styled.span<{ $isPaused: boolean }>`
  flex-shrink: 0;
  width: 0.5rem; /* 8px */
  height: 0.5rem;
  border-radius: 50%;
  background-color: ${({ theme, $isPaused }) =>
    $isPaused ? theme.neutral[400] : theme.sub.warning[600]};
`;

const ElapsedTime = styled.span`
  font-variant-numeric: tabular-nums; /* 숫자가 바뀌어도 폭이 흔들리지 않아요 */
  white-space: nowrap;
  ${({ theme }) => theme.text.label01};
`;

const StopButton = styled.button`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 2rem; /* 32px */
  height: 2rem;
  border-radius: 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[0]};
  color: ${({ theme }) => theme.neutral[800]};

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

/** 녹음 끝내기(■). 모서리가 둥근 11px 사각형이에요. */
const StopMark = styled.span`
  width: 0.6875rem; /* 11px */
  height: 0.6875rem;
  border-radius: 0.15625rem; /* 2.5px */
  background-color: currentColor;
`;
