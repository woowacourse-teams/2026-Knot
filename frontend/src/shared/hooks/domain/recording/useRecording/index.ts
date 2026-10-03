import useInterval from "@/shared/hooks/common/useInterval";
import { useRecordingStore } from "@store/recordingStore";
import { calculateElapsedSeconds } from "@utils/calculateElapsedSeconds";
import { useEffect, useState } from "react";

/** 화면의 시간 표시가 초 단위라 1초마다 다시 계산해요. */
const TICK_INTERVAL_MS = 1000;

/**
 * 녹음 상태와 녹음한 시간을 다루는 도메인 훅.
 *
 * 시간은 매초 1씩 더하지 않고 시작 시각과의 차이로 계산해요. 탭이 백그라운드로 가서
 * 타이머가 늦게 불려도 돌아왔을 때 흐른 시간이 그대로 맞아요.
 *
 * 녹음 상태는 전역 저장소에 있어 어느 화면에서 불러도 같은 녹음을 봐요. 시간을 다시 그리는 타이머만
 * 부르는 화면마다 따로 두어, 화면이 사라지면 타이머도 함께 멈춰요.
 *
 * 녹음을 시작한 사람만 조작하므로 권한 구분은 없어요. 끝내면 처음 상태로 돌아가요.
 * 시작·이어서 녹음은 마이크를 받아야 해서 성공 여부(`Promise<boolean>`)를 돌려줘요.
 */
const useRecording = () => {
  const status = useRecordingStore((state) => state.status);
  const accumulatedMs = useRecordingStore((state) => state.accumulatedMs);
  const resumedAt = useRecordingStore((state) => state.resumedAt);
  const startRecording = useRecordingStore((state) => state.startRecording);
  const pauseRecording = useRecordingStore((state) => state.pauseRecording);
  const resumeRecording = useRecordingStore((state) => state.resumeRecording);
  const endRecording = useRecordingStore((state) => state.endRecording);
  const analyser = useRecordingStore((state) => state.analyser);
  const isMicrophoneLost = useRecordingStore((state) => state.isMicrophoneLost);
  const acknowledgeMicrophoneLost = useRecordingStore(
    (state) => state.acknowledgeMicrophoneLost,
  );

  const [now, setNow] = useState(() => Date.now());

  const { start, reset } = useInterval({
    callback: () => setNow(Date.now()),
    delay: TICK_INTERVAL_MS,
  });

  // 녹음이 시작되면 시간 표시를 다시 그리는 타이머를 켜요
  useEffect(() => {
    if (status === "idle") return;

    start();
  }, [start, status]);

  const elapsedSeconds = calculateElapsedSeconds(accumulatedMs, resumedAt, now);

  return {
    status,
    isRecordingActive: status !== "idle",
    elapsedSeconds,
    analyser,
    isMicrophoneLost,
    startRecording,
    pauseRecording,
    resumeRecording,
    endRecording,
    acknowledgeMicrophoneLost,
  };
};

export default useRecording;
