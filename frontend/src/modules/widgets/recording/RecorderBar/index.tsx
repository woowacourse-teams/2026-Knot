import styled from "@emotion/styled";
import Button from "@primitives/ui/Button";

import PauseIcon from "@/assets/icons/pause.svg";
import PlayIcon from "@/assets/icons/play.svg";

import { useRecorderBar } from "./model/useRecorderBar";
import Waveform from "./ui/Waveform";

/**
 * 녹음 화면 상단 바.
 *
 * 왼쪽에 녹음 상태·녹음한 시간·파형을, 오른쪽에 일시정지(이어서 녹음)와 녹음 끝내기 버튼을 둬요.
 * 녹음을 시작한 사람 혼자 쓰는 화면이라 권한에 따른 구분은 없어요.
 * 마이크·업로드를 연결하지 않은 UI 단계라 시간과 상태만 바뀌어요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1705-3280 Recorder/Bar}
 */
export default function RecorderBar() {
  const { isPaused, elapsedTime, handlePause, handleResume, handleEnd } =
    useRecorderBar();

  return (
    <Container aria-label="녹음 조작">
      <Info>
        <Status $isPaused={isPaused}>
          <RecDot aria-hidden="true" $isPaused={isPaused} />
          {isPaused ? "일시정지" : "녹음 중"}
        </Status>

        <ElapsedTime aria-label="녹음한 시간">{elapsedTime}</ElapsedTime>

        <Waveform isActive={!isPaused} />
      </Info>

      <Controls>
        {isPaused ? (
          <ControlButton size="md" variant="outline" onClick={handleResume}>
            <IconBox>
              <PlayIcon size={14} />
            </IconBox>
            이어서 녹음
          </ControlButton>
        ) : (
          <ControlButton size="md" variant="outline" onClick={handlePause}>
            <IconBox>
              <PauseIcon size={14} />
            </IconBox>
            일시정지
          </ControlButton>
        )}

        <Button size="md" variant="filled" onClick={handleEnd}>
          <IconBox>
            <StopMark />
          </IconBox>
          녹음 끝내기
        </Button>
      </Controls>
    </Container>
  );
}

const Container = styled.section`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem; /* 16px */
  width: 100%;
  padding: 1rem 1.5rem; /* 16px 24px */
  border: 1px solid ${({ theme }) => theme.neutral[200]};
  border-radius: 1rem; /* 16px */
  background-color: ${({ theme }) => theme.neutral[50]};
`;

const Info = styled.div`
  display: flex;
  align-items: center;
  gap: 1rem; /* 16px */
  min-width: 0;
  overflow: hidden; /* 좁아지면 파형 끝부터 잘려요 */
`;

/** 시간이 자라도 옆 글자가 밀리지 않게 상태 칸은 폭을 고정해요. */
const Status = styled.p<{ $isPaused: boolean }>`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.5rem; /* 8px */
  width: 6rem; /* 96px */
  color: ${({ theme, $isPaused }) =>
    $isPaused ? theme.neutral[500] : theme.sub.warning[600]};
  white-space: nowrap;
  ${({ theme }) => theme.text.label01};
`;

/** 상태 글자 앞의 점. 일시정지면 글자보다 한 단계 옅은 회색이에요. */
const RecDot = styled.span<{ $isPaused: boolean }>`
  flex-shrink: 0;
  width: 0.625rem; /* 10px */
  height: 0.625rem;
  border-radius: 50%;
  background-color: ${({ theme, $isPaused }) =>
    $isPaused ? theme.neutral[400] : theme.sub.warning[600]};
`;

const ElapsedTime = styled.p`
  flex-shrink: 0;
  color: ${({ theme }) => theme.neutral[900]};
  font-variant-numeric: tabular-nums; /* 숫자가 바뀌어도 폭이 흔들리지 않아요 */
  white-space: nowrap;
  ${({ theme }) => theme.text.title02};
`;

const Controls = styled.div`
  display: flex;
  flex-shrink: 0;
  gap: 0.5rem; /* 8px */
`;

/** Figma의 버튼 글자는 Neutral/800이라 outline 기본색(700)보다 한 단계 짙게 둬요. */
const ControlButton = styled(Button)`
  color: ${({ theme }) => theme.neutral[800]};
`;

/**
 * 버튼 안 아이콘 자리.
 *
 * `Button`은 바로 아래 svg를 18px로 맞추는데, Figma의 일시정지·재생 아이콘은 14px이라
 * 18px 칸 가운데에 원래 크기로 놓아요.
 */
const IconBox = styled.span`
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 1.125rem; /* 18px */
  height: 1.125rem;
`;

/** 녹음 끝내기(■). 모서리가 둥근 11px 사각형이에요. */
const StopMark = styled.span`
  width: 0.6875rem; /* 11px */
  height: 0.6875rem;
  border-radius: 0.15625rem; /* 2.5px */
  background-color: currentColor;
`;
