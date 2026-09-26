import { create } from "zustand";

/** 녹음이 없는 상태·녹음 중·일시정지. 한 번에 하나만 녹음해요. */
export type RecordingStatus = "idle" | "recording" | "paused";

interface RecordingState {
  status: RecordingStatus;
  /** 지난 녹음 구간들의 길이를 더한 값(ms). 일시정지할 때마다 쌓여요 */
  accumulatedMs: number;
  /** 지금 구간을 시작하거나 이어 간 시각. 녹음 중일 때만 값이 있어요 */
  resumedAt: number | null;
}

interface RecordingActions {
  startRecording: () => void;
  pauseRecording: () => void;
  resumeRecording: () => void;
  endRecording: () => void;
}

const IDLE_STATE: RecordingState = {
  status: "idle",
  accumulatedMs: 0,
  resumedAt: null,
};

/**
 * 진행 중인 녹음을 담는 전역 저장소.
 *
 * 녹음은 녹음 화면에서 시작하지만 다른 화면으로 옮겨 가도 계속되고, 그동안 화면 아래 독이
 * 녹음 시간과 중지 버튼을 보여 줘요. 녹음 화면과 독이 서로 다른 구획이라 한곳에 두고 함께 읽어요.
 *
 * 화면은 `@hooks/domain/recording/useRecording`으로 읽고, 테스트는 녹음이 테스트끼리 새지 않도록 여기서 직접 초기화해요.
 */
export const useRecordingStore = create<RecordingState & RecordingActions>()(
  (set) => ({
    ...IDLE_STATE,

    // 이미 녹음이 있으면 새로 시작하지 않아요. 녹음 화면에 다시 들어와도 이어지던 녹음이 그대로예요
    startRecording: () =>
      set((prev) =>
        prev.status === "idle"
          ? { status: "recording", accumulatedMs: 0, resumedAt: Date.now() }
          : prev,
      ),

    pauseRecording: () =>
      set((prev) => {
        if (prev.status !== "recording" || prev.resumedAt === null) return prev;

        return {
          status: "paused",
          accumulatedMs: prev.accumulatedMs + (Date.now() - prev.resumedAt),
          resumedAt: null,
        };
      }),

    resumeRecording: () =>
      set((prev) =>
        prev.status === "paused"
          ? { status: "recording", resumedAt: Date.now() }
          : prev,
      ),

    endRecording: () => set(IDLE_STATE),
  }),
);
