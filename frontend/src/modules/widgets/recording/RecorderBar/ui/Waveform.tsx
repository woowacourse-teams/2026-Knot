import styled from "@emotion/styled";

import {
  WAVEFORM_MAX_BAR_HEIGHT,
  WAVEFORM_MIN_BAR_HEIGHT,
  WAVEFORM_PENDING_BAR_COUNT,
} from "../constants/waveform";
import { useWaveformLevels } from "../model/useWaveformLevels";

interface WaveformProps {
  /** 마이크 소리를 읽는 분석기 */
  analyser: AnalyserNode | null;
  /** `false`면 일시정지라 파형을 멈추고 모든 막대를 흐리게 그려요. */
  isActive: boolean;
}

const toBarHeight = (level: number) =>
  WAVEFORM_MIN_BAR_HEIGHT +
  level * (WAVEFORM_MAX_BAR_HEIGHT - WAVEFORM_MIN_BAR_HEIGHT);

/**
 * 녹음 시간 옆의 파형.
 *
 * 마이크에 들어오는 소리 크기를 막대 높이로 그리고, 새 소리가 오른쪽 끝에서 들어와 왼쪽으로 흘러가요.
 * 끝의 몇 개는 아직 들어오지 않은 소리 자리라 흐리게 두고, 일시정지면 전부 흐려요.
 * 녹음 중임을 보여 주는 장식이라 낭독기에서는 숨겨요.
 */
export default function Waveform({ analyser, isActive }: WaveformProps) {
  const { levels } = useWaveformLevels({ analyser, isActive });

  return (
    <Container aria-hidden="true">
      {levels.map((level, index) => (
        <Bar key={index} $height={toBarHeight(level)} $isActive={isActive} />
      ))}
      {Array.from({ length: WAVEFORM_PENDING_BAR_COUNT }, (_, index) => (
        <Bar
          key={`pending-${index}`}
          $height={WAVEFORM_MIN_BAR_HEIGHT}
          $isActive={false}
        />
      ))}
    </Container>
  );
}

const Container = styled.div`
  display: flex;
  align-items: center;
  gap: 0.25rem; /* 4px */
  height: 1.875rem; /* 30px */
`;

const Bar = styled.span<{ $height: number; $isActive: boolean }>`
  flex-shrink: 0;
  width: 0.25rem; /* 4px */
  height: ${({ $height }) => `${$height / 16}rem`};
  border-radius: 0.125rem; /* 2px */
  background-color: ${({ theme, $isActive }) =>
    $isActive ? theme.neutral[700] : theme.neutral[300]};
  transition:
    height 0.12s ease-out,
    background-color 0.2s ease-in;

  @media (prefers-reduced-motion: reduce) {
    transition: none;
  }
`;
