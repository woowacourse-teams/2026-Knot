export const stopTracks = (stream: MediaStream) =>
  stream.getTracks().forEach((track) => track.stop());

export interface MicrophoneConnectionParams {
  stream: MediaStream;
  onData: (chunk: Blob) => void;
  onLost: () => void;
}

/**
 * 받은 마이크 하나에 녹음기와 분석기를 붙인 연결. 셋은 함께 켜지고 함께 꺼져요.
 *
 * 마이크가 빠지면 `onLost`로 알리고, 닫은 뒤의 알림은 무시해요.
 */
export default class MicrophoneConnection {
  readonly analyser: AnalyserNode;

  private readonly stream: MediaStream;
  private readonly recorder: MediaRecorder;
  private readonly audioContext: AudioContext;
  private isClosed = false;

  constructor({ stream, onData, onLost }: MicrophoneConnectionParams) {
    this.stream = stream;

    this.recorder = new MediaRecorder(stream);
    this.recorder.addEventListener("dataavailable", (e: BlobEvent) => {
      if (e.data.size > 0) onData(e.data);
    });
    this.recorder.start();

    this.audioContext = new AudioContext();
    this.analyser = this.audioContext.createAnalyser();
    this.audioContext.createMediaStreamSource(stream).connect(this.analyser);

    stream.getTracks().forEach((track) =>
      track.addEventListener("ended", () => {
        if (!this.isClosed) onLost();
      }),
    );
  }

  isLive() {
    return this.stream.getTracks().some((track) => track.readyState === "live");
  }

  pause() {
    if (this.recorder.state === "recording") this.recorder.pause();
  }

  resume() {
    if (this.recorder.state === "paused") this.recorder.resume();
  }

  close() {
    this.isClosed = true;
    stopTracks(this.stream);
    if (this.recorder.state !== "inactive") this.recorder.stop();
    void this.audioContext.close();
  }
}
