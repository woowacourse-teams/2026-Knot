import styled from "@emotion/styled";
import { useId } from "react";

import CurrentRecordingItem from "./ui/CurrentRecordingItem";
import EmptyRecording from "./ui/EmptyRecording";
import LoadingFallback from "./ui/LoadingFallback";

import type { CurrentRecording } from "./types/currentRecording";

interface RecordingListCardProps {
  /** 내가 지금 진행 중인 녹음. 없으면 `null` */
  recording: CurrentRecording | null;
  /** 녹음을 조회하는 중이면 뼈대를 보여 줘요 */
  isLoading: boolean;
  /** 넘기지 않으면 「녹음 화면으로」 버튼을 숨겨요 */
  onOpenRecording?: () => void;
}

/**
 * 홈의 「진행 중인 녹음」 카드. 내가 지금 진행 중인 녹음 하나만 보여 주고, 없으면 빈 상태 문구를 보여 줘요.
 */
export default function RecordingListCard({
  recording,
  isLoading,
  onOpenRecording,
}: RecordingListCardProps) {
  const titleId = useId();

  return (
    <Container aria-labelledby={titleId} aria-busy={isLoading}>
      <Header>
        <Title id={titleId}>진행 중인 녹음</Title>
      </Header>

      {isLoading ? (
        <LoadingFallback />
      ) : recording ? (
        <CurrentRecordingItem
          recordingStatus={recording.status}
          title={recording.title}
          elapsedTime={recording.elapsedTime}
          onOpenRecording={onOpenRecording}
        />
      ) : (
        <EmptyRecording />
      )}
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  flex-direction: column;
  gap: 0.5rem; /* 8px */
  width: 40.75rem; /* 652px */
  max-width: 100%;
  height: 13.375rem; /* 214px */
  overflow: hidden;
  padding: 1.25rem; /* 20px */
  border-radius: 1.5rem; /* 24px */
  background-color: ${({ theme }) => theme.neutral[0]};
  box-shadow: ${({ theme }) => theme.shadow02};
`;

const Header = styled.div`
  display: flex;
  align-items: center;
  padding: 0 0.25rem 0.5rem; /* 0 4px 8px */
`;

const Title = styled.h2`
  color: ${({ theme }) => theme.neutral[900]};
  ${({ theme }) => theme.text.label01};
`;
