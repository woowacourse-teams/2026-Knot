import { create } from "zustand";

import Microphone from "./microphone";
import RecordedAudio from "./recordedAudio";

/** 녹음이 없는 상태·녹음 중·일시정지. 한 번에 하나만 녹음해요. */
export type RecordingStatus = "idle" | "recording" | "paused";

interface RecordingState {
  status: RecordingStatus;
  /** 지난 녹음 구간들의 길이를 더한 값(ms). 일시정지할 때마다 쌓여요 */
  accumulatedMs: number;
  /** 지금 구간을 시작하거나 이어 간 시각. 녹음 중일 때만 값이 있어요 */
  resumedAt: number | null;
  /** 마이크 소리 크기를 읽는 분석기. 마이크를 받고 있을 때만 값이 있어요 */
  analyser: AnalyserNode | null;
  /** 녹음 중에 마이크가 끊겨 저절로 일시정지했는지. 사용자가 알림을 확인하면 다시 `false`가 돼요 */
  isMicrophoneLost: boolean;
}

interface RecordingActions {
  /** 마이크를 받아 녹음을 시작해요. 마이크를 받지 못하면 `false`를 돌려주고 아무것도 바꾸지 않아요 */
  startRecording: () => Promise<boolean>;
  pauseRecording: () => void;
  /** 이어서 녹음해요. 마이크가 끊겼다면 다시 받고, 받지 못하면 `false`를 돌려주고 일시정지를 유지해요 */
  resumeRecording: () => Promise<boolean>;
  /** 녹음을 버리고 마이크를 꺼요 */
  endRecording: () => void;
  /** 마이크가 끊겼다는 알림을 확인했어요 */
  acknowledgeMicrophoneLost: () => void;
}

const IDLE_STATE: RecordingState = {
  status: "idle",
  accumulatedMs: 0,
  resumedAt: null,
  analyser: null,
  isMicrophoneLost: false,
};

/**
 * 진행 중인 녹음을 담는 전역 저장소.
 *
 * 녹음은 독의 마이크로 시작하지만 다른 화면으로 옮겨 가도 계속되고, 녹음 화면과 독이 서로 다른 구획이라
 * 한곳에 두고 함께 읽어요.
 *
 * 마이크 권한은 녹음 화면에 들어가기 전에 받아요. 받지 못하면 상태를 바꾸지 않아, 부른 쪽이 그 자리에서
 * 안내를 띄울 수 있어요. 녹음 중에 마이크가 빠지면 저절로 일시정지하고 `isMicrophoneLost`로 알려요.
 *
 * 화면은 `@hooks/domain/recording/useRecording`으로 읽고, 테스트는 녹음이 테스트끼리 새지 않도록 여기서 직접 초기화해요.
 */
export const useRecordingStore = create<RecordingState & RecordingActions>()((
  set,
  get,
) => {
  const recordedAudio = new RecordedAudio();

  const microphone = new Microphone({
    onData: (chunk) => recordedAudio.append(chunk),
    onLost: () => {
      get().pauseRecording();
      set({ analyser: null, isMicrophoneLost: true });
    },
  });

  return {
    ...IDLE_STATE,

    // 이미 녹음이 있으면 새로 시작하지 않는다.
    startRecording: async () => {
      if (get().status !== "idle") return true;

      const analyser = await microphone.connect();
      if (!analyser) return false;

      recordedAudio.clear();
      set({
        status: "recording",
        accumulatedMs: 0,
        resumedAt: Date.now(),
        analyser,
      });

      return true;
    },

    pauseRecording: () => {
      const { status, resumedAt, accumulatedMs } = get();
      if (status !== "recording" || resumedAt === null) return;

      microphone.pause();

      set({
        status: "paused",
        accumulatedMs: accumulatedMs + (Date.now() - resumedAt),
        resumedAt: null,
      });
    },

    resumeRecording: async () => {
      if (get().status !== "paused") return get().status === "recording";

      if (microphone.isLive()) microphone.resume();
      else {
        const analyser = await microphone.connect();
        if (!analyser) return false;

        set({ analyser });
      }

      set({
        status: "recording",
        resumedAt: Date.now(),
        isMicrophoneLost: false,
      });

      return true;
    },

    endRecording: () => {
      microphone.disconnect();
      recordedAudio.clear();
      set(IDLE_STATE);
    },

    acknowledgeMicrophoneLost: () => set({ isMicrophoneLost: false }),
  };
});
