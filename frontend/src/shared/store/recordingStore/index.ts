import { create } from "zustand";

import Microphone from "./microphone";
import {
  isRecordingAudioTypeSupported,
  UnsupportedRecordingAudioTypeError,
} from "./microphoneConnection";
import RecordedAudio from "./recordedAudio";

/** 녹음이 없는 상태·녹음 중·일시정지. 한 번에 하나만 녹음해요. */
export type RecordingStatus = "idle" | "recording" | "paused";

/** 서버에 열어 둔 녹음 세션. 일시정지·재개·종료 요청 경로에 들어가요 */
export interface RecordingSession {
  workspaceId: number;
  recordingId: number;
}

interface RecordingState {
  status: RecordingStatus;
  /** 서버 녹음 세션. 녹음 중·일시정지일 때만 값이 있어요 */
  session: RecordingSession | null;
  /** 지난 녹음 구간들의 길이를 더한 값(ms). 일시정지할 때마다 쌓여요 */
  accumulatedMs: number;
  /** 지금 구간을 시작하거나 이어 간 시각. 녹음 중일 때만 값이 있어요 */
  resumedAt: number | null;
  /** 마이크 소리 크기를 읽는 분석기. 마이크를 받고 있을 때만 값이 있어요 */
  analyser: AnalyserNode | null;
  /** 녹음 중에 마이크가 끊겨 저절로 일시정지했는지. 사용자가 알림을 확인하면 다시 `false`가 돼요 */
  isMicrophoneLost: boolean;
  /** 녹음을 끝내는 중인지. 수집을 멈춘 뒤 업로드를 마치고 녹음을 비울 때까지 `true`예요 */
  isEnding: boolean;
}

interface RecordingActions {
  /**
   * 녹음을 시작하기 전에 마이크를 받아요. 받지 못하면 `false`를 돌려주고 아무것도 바꾸지 않아요.
   * 브라우저가 서버가 받는 형식으로 녹음할 수 없으면 권한을 묻지 않고 `UnsupportedRecordingAudioTypeError`를 던져요
   */
  connectMicrophone: () => Promise<boolean>;
  /** 받아 둔 마이크로 녹음을 시작해요. 서버에 녹음을 연 뒤에 불러요 */
  startRecording: (session: RecordingSession) => void;
  pauseRecording: () => void;
  /** 이어서 녹음해요. 마이크가 끊겼다면 다시 받고, 받지 못하면 `false`를 돌려주고 일시정지를 유지해요 */
  resumeRecording: () => Promise<boolean>;
  /** 수집을 멈추고 지금까지 녹음한 오디오를 파일 하나로 돌려줘요. 녹음은 비우지 않아요 */
  stopRecording: () => Promise<Blob>;
  /** 녹음을 버리고 마이크를 꺼요 */
  discardRecording: () => void;
  /** 마이크가 끊겼다는 표시(`isMicrophoneLost`)를 지워요 */
  clearMicrophoneLost: () => void;
}

const IDLE_STATE: RecordingState = {
  status: "idle",
  session: null,
  accumulatedMs: 0,
  resumedAt: null,
  analyser: null,
  isMicrophoneLost: false,
  isEnding: false,
};

/** 진행 중인 구간을 누적 시간에 더해 시간 표시를 멈춰요 */
const freezeElapsed = ({
  accumulatedMs,
  resumedAt,
}: Pick<RecordingState, "accumulatedMs" | "resumedAt">) => ({
  accumulatedMs:
    resumedAt === null
      ? accumulatedMs
      : accumulatedMs + (Date.now() - resumedAt),
  resumedAt: null,
});

/**
 * 진행 중인 녹음을 담는 전역 저장소.
 *
 * 녹음은 독의 마이크로 시작하지만 다른 화면으로 옮겨 가도 계속되고, 녹음 화면과 독이 서로 다른 구획이라
 * 한곳에 두고 함께 읽어요.
 *
 * 마이크 권한은 녹음 화면에 들어가기 전에 받아요. 받지 못하면 상태를 바꾸지 않아, 부른 쪽이 그 자리에서
 * 안내를 띄울 수 있어요. 녹음 중에 마이크가 빠지면 저절로 일시정지하고 `isMicrophoneLost`로 알려요.
 *
 * 여기는 브라우저 안의 녹음만 다루고, 서버 녹음 세션 요청은 `@hooks/domain/recording/useRecordingControl`이 맡아요.
 * 화면은 도메인 훅으로 읽고, 테스트는 녹음이 테스트끼리 새지 않도록 여기서 직접 초기화해요.
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

    connectMicrophone: async () => {
      if (!isRecordingAudioTypeSupported()) {
        throw new UnsupportedRecordingAudioTypeError();
      }

      const analyser = await microphone.connect();
      if (!analyser) return false;

      set({ analyser });

      return true;
    },

    // 이미 녹음이 있으면 새로 시작하지 않는다.
    startRecording: (session) => {
      if (get().status !== "idle") return;

      recordedAudio.clear();
      microphone.start();
      set({
        status: "recording",
        session,
        accumulatedMs: 0,
        resumedAt: Date.now(),
      });
    },

    pauseRecording: () => {
      const { status, resumedAt } = get();
      if (status !== "recording" || resumedAt === null) return;

      microphone.pause();

      set({ status: "paused", ...freezeElapsed(get()) });
    },

    resumeRecording: async () => {
      if (get().status !== "paused") return get().status === "recording";

      if (microphone.isLive()) microphone.resume();
      else {
        const analyser = await microphone.connect();
        if (!analyser) return false;

        microphone.start();
        set({ analyser });
      }

      set({
        status: "recording",
        resumedAt: Date.now(),
        isMicrophoneLost: false,
      });

      return true;
    },

    stopRecording: async () => {
      set({ isEnding: true, ...freezeElapsed(get()) });

      await microphone.stop();
      set({ analyser: null });

      return recordedAudio.toBlob();
    },

    discardRecording: () => {
      microphone.disconnect();
      recordedAudio.clear();
      set(IDLE_STATE);
    },

    clearMicrophoneLost: () => set({ isMicrophoneLost: false }),
  };
});
