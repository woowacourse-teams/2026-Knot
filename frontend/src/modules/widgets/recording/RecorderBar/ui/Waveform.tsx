import styled from "@emotion/styled";

import {
  WAVEFORM_BAR_HEIGHTS,
  WAVEFORM_PENDING_BAR_COUNT,
} from "../constants/waveform";

interface WaveformProps {
  /** `false`면 일시정지라 모든 막대를 흐리게 그려요. */
  isActive: boolean;
}

/**
 * 녹음 시간 옆의 파형.
 *
 * 스토리북 `Recording/RecorderBar`
 */
export default function Waveform({ isActive }: WaveformProps) {
  const activeBarCount = isActive
    ? WAVEFORM_BAR_HEIGHTS.length - WAVEFORM_PENDING_BAR_COUNT
    : 0;

  return (
    <Container aria-hidden="true">
      {WAVEFORM_BAR_HEIGHTS.map((height, index) => (
        <Bar key={index} $height={height} $isActive={index < activeBarCount} />
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
  transition: background-color 0.2s ease-in;
`;
