import type { CurrentRecordingStatus } from "../types/currentRecording";

/** 상태 줄의 이름과 그 아래 안내 문구 */
export const RECORDING_STATUS_COPY = {
  recording: {
    label: "녹음 중",
    hint: "다른 화면으로 이동해도 녹음은 계속돼요",
  },
  paused: {
    label: "일시정지",
    hint: "녹음 화면에서 이어서 녹음할 수 있어요",
  },
} as const satisfies Record<
  CurrentRecordingStatus,
  { label: string; hint: string }
>;
