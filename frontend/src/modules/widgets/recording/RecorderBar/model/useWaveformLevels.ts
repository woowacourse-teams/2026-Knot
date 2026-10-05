import useInterval from "@hooks/common/useInterval";
import { useEffect, useState } from "react";

import {
  WAVEFORM_BAR_COUNT,
  WAVEFORM_PENDING_BAR_COUNT,
  WAVEFORM_SAMPLE_INTERVAL_MS,
} from "../constants/waveform";
import { measureLevel } from "../utils/measureLevel";

/** 소리가 들어온 막대 수. 끝의 흐린 막대는 소리를 담지 않아요 */
const LEVEL_COUNT = WAVEFORM_BAR_COUNT - WAVEFORM_PENDING_BAR_COUNT;

const createSilentLevels = () => Array<number>(LEVEL_COUNT).fill(0);

interface UseWaveformLevelsParams {
  /** 마이크 소리를 읽는 분석기. 없으면 재지 않아요 */
  analyser: AnalyserNode | null;
  /** `false`면 재지 않고 마지막 모양을 그대로 둬요 */
  isActive: boolean;
}

/**
 * 파형 막대마다 그릴 소리 크기(0~1)를 돌려줘요.
 *
 * 일정한 간격으로 마이크 소리를 재서 오른쪽 끝에 붙이고, 오래된 값은 왼쪽으로 밀려 사라져요.
 * 일시정지하면 재지 않아 마지막 모양에서 멈춰요.
 */
export const useWaveformLevels = ({
  analyser,
  isActive,
}: UseWaveformLevelsParams) => {
  const [levels, setLevels] = useState(createSilentLevels);

  const appendCurrentLevel = () => {
    if (!analyser) return;

    const samples = new Float32Array(analyser.fftSize);
    analyser.getFloatTimeDomainData(samples);
    const level = measureLevel(samples);

    setLevels((prev) => [...prev.slice(1), level]);
  };

  const { start, reset } = useInterval({
    callback: appendCurrentLevel,
    delay: WAVEFORM_SAMPLE_INTERVAL_MS,
  });

  // 녹음 중이고 분석기가 있을 때만 소리를 재고, 멈추거나 사라지면 재기를 멈춰요
  useEffect(() => {
    if (!isActive || !analyser) return;

    start();

    return reset;
  }, [analyser, isActive, reset, start]);

  return { levels };
};
