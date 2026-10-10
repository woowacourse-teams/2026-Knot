import useInterval from "@/shared/hooks/common/useInterval";
import { useRecordingStore } from "@store/recordingStore";
import { calculateElapsedSeconds } from "@utils/calculateElapsedSeconds";
import { useEffect, useState } from "react";

/** 화면의 시간 표시가 초 단위라 1초마다 다시 계산해요. */
const TICK_INTERVAL_MS = 1000;

/**
 * 녹음한 시간을 계산/반환하는 도메인 훅.
 *
 * 시간은 매초 1씩 더하지 않고 시작 시각과의 차이로 계산해요. 탭이 백그라운드로 가서
 * 타이머가 늦게 불려도 돌아왔을 때 흐른 시간이 그대로 맞아요.
 *
 * 타이머는 부르는 화면마다 따로 두어, 화면이 사라지면 타이머도 함께 멈춰요.
 * 시간을 보여 주는 화면만 매초 다시 그리도록 `useRecordingState`과 나눠 두었어요.
 */
const useRecordingElapsedTime = () => {
  const status = useRecordingStore((state) => state.status);
  const accumulatedMs = useRecordingStore((state) => state.accumulatedMs);
  const resumedAt = useRecordingStore((state) => state.resumedAt);

  const [now, setNow] = useState(() => Date.now());

  const { start } = useInterval({
    callback: () => setNow(Date.now()),
    delay: TICK_INTERVAL_MS,
  });

  // 녹음이 시작되면 시간 표시를 다시 그리는 타이머를 켜요
  useEffect(() => {
    if (status === "idle") return;

    start();
  }, [start, status]);

  const elapsedSeconds = calculateElapsedSeconds(accumulatedMs, resumedAt, now);

  return { elapsedSeconds };
};

export default useRecordingElapsedTime;
