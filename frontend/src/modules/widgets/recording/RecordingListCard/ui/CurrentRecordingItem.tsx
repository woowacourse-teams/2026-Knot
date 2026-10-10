import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import MicIcon from "@/assets/icons/mic.svg";

import { RECORDING_STATUS_COPY } from "../constants/recordingStatus";
import type { CurrentRecordingStatus } from "../types/currentRecording";

interface CurrentRecordingItemProps {
  recordingStatus: CurrentRecordingStatus;
  /** 녹음 이름. 예: `유월 님의 녹음` */
  title: string;
  /** 녹음한 시간. 예: `12:48` */
  elapsedTime: string;
  /** 넘기지 않으면 「녹음 화면으로」 버튼을 숨겨요 */
  onOpenRecording?: () => void;
}

/**
 * 진행 중인 녹음 한 칸. 상태 줄(점 + 상태 · 시간) / 녹음 이름 / 안내 한 줄과 오른쪽 버튼으로 이뤄져요.
 */
export default function CurrentRecordingItem({
  recordingStatus,
  title,
  elapsedTime,
  onOpenRecording,
}: CurrentRecordingItemProps) {
  const { label, hint } = RECORDING_STATUS_COPY[recordingStatus];
  const isPaused = recordingStatus === "paused";

  return (
    <Container>
      <IconChip>
        <MicIcon size={24} />
      </IconChip>

      <Info>
        <Status $isPaused={isPaused}>
          <StatusDot aria-hidden="true" $isPaused={isPaused} />
          <StatusText>
            {label} · {elapsedTime}
          </StatusText>
        </Status>
        <Title>{title}</Title>
        <Hint>{hint}</Hint>
      </Info>

      {onOpenRecording && (
        <Button size="sm" variant="filled" onClick={onOpenRecording}>
          녹음 화면으로
        </Button>
      )}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  flex: 1;
  align-items: center;
  gap: 1rem; /* 16px */
  width: 100%;
  min-height: 0;
  padding: 1.25rem; /* 20px */
  border-radius: 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[100]};
`;

const IconChip = styled.span`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 3.25rem; /* 52px */
  height: 3.25rem;
  border-radius: 0.875rem; /* 14px */
  background-color: ${({ theme }) => theme.neutral[0]};
  color: ${({ theme }) => theme.neutral[500]};
`;

const Info = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.25rem; /* 4px */
  min-width: 0;
`;

/** 녹음 중이면 빨간 글자, 일시정지면 회색 글자예요. */
const Status = styled.p<{ $isPaused: boolean }>`
  display: flex;
  align-items: center;
  gap: 0.375rem; /* 6px */
  color: ${({ theme, $isPaused }) =>
    $isPaused ? theme.neutral[600] : theme.sub.warning[600]};
  ${({ theme }) => theme.text.caption02};
`;

const StatusDot = styled.span<{ $isPaused: boolean }>`
  flex-shrink: 0;
  width: 0.5rem; /* 8px */
  height: 0.5rem;
  border-radius: 50%;
  background-color: ${({ theme, $isPaused }) =>
    $isPaused ? theme.neutral[400] : theme.sub.warning[600]};
`;

const StatusText = styled.span`
  font-variant-numeric: tabular-nums; /* 시간이 바뀌어도 폭이 흔들리지 않아요 */
  white-space: nowrap;
`;

const Title = styled.p`
  overflow: hidden;
  color: ${({ theme }) => theme.neutral[900]};
  text-overflow: ellipsis;
  white-space: nowrap;
  ${({ theme }) => theme.text.heading04};
`;

const Hint = styled.p`
  overflow: hidden;
  color: ${({ theme }) => theme.neutral[500]};
  text-overflow: ellipsis;
  white-space: nowrap;
  ${({ theme }) => theme.text.caption02};
`;
