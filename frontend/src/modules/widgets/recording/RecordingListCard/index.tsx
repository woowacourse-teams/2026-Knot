import styled from "@emotion/styled";

import { useRecordingTitle } from "./model/useRecordingTitle";
import { useServerRecording } from "./model/useServerRecording";
import { useThisTabRecording } from "./model/useThisTabRecording";
import { useTitleId } from "./model/useTitleId";
import CurrentRecordingItem from "./ui/CurrentRecordingItem";
import EmptyRecording from "./ui/EmptyRecording";
import LoadingFallback from "./ui/LoadingFallback";

/**
 * 홈의 「진행 중인 녹음」 카드. 내가 지금 진행 중인 녹음 하나만 보여 주고, 없으면 빈 상태 문구를 보여 줘요.
 *
 * 이 탭에서 시작한 녹음이 이어지고 있으면(녹음 탭) 이 탭의 상태와 시간을 보여 주고,
 * 「녹음 화면으로」로 녹음 화면에 갈 수 있게 해요.
 *
 * 그 밖에는(관찰 탭) 현재 `:workspaceId`의 현재 녹음 조회 응답을 보여 주고 버튼은 숨겨요.
 * 응답의 누적 시간에 응답을 받은 뒤 흐른 시간을 더해 녹음 중이면 매초 늘리고, 일시정지면 멈춰 둬요.
 * 녹음 중·일시정지가 아닌 녹음(문서 정리 중·실패)과 조회 실패는 녹음이 없는 것으로 보여 줘요.
 */
export default function RecordingListCard() {
  const titleId = useTitleId();
  const title = useRecordingTitle();
  const {
    status: thisTabStatus,
    elapsedTime: thisTabElapsedTime,
    handleOpenRecording,
  } = useThisTabRecording();
  const {
    status: serverStatus,
    elapsedTime: serverElapsedTime,
    isLoading: isServerLoading,
  } = useServerRecording();

  // 이 탭의 녹음은 조회를 기다리지 않고 바로 보여 줘요
  const isLoading = thisTabStatus === "idle" && isServerLoading;

  return (
    <Container aria-labelledby={titleId} aria-busy={isLoading}>
      <Header>
        <Title id={titleId}>진행 중인 녹음</Title>
      </Header>

      {isLoading ? (
        <LoadingFallback />
      ) : thisTabStatus !== "idle" ? (
        <CurrentRecordingItem
          recordingStatus={thisTabStatus}
          title={title}
          elapsedTime={thisTabElapsedTime}
          onOpenRecording={handleOpenRecording}
        />
      ) : serverStatus ? (
        <CurrentRecordingItem
          recordingStatus={serverStatus}
          title={title}
          elapsedTime={serverElapsedTime}
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
