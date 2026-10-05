import MicrophoneConnection, {
  type MicrophoneConnectionParams,
  stopTracks,
} from "./microphoneConnection";

type MicrophoneParams = Omit<MicrophoneConnectionParams, "stream">;

/**
 * 녹음에 쓰는 마이크. 권한 받기·연결 교체·끄기를 한곳에서 맡아요.
 *
 * - 여러 번 연결해도 권한 창은 한 번만 띄우고 같은 결과를 함께 기다려요
 * - 권한을 기다리는 사이 끊었다면 받은 마이크를 바로 꺼요
 * - 끊긴 뒤 다시 연결하면 이전 연결을 정리하고 새로 붙여요
 */
export default class Microphone {
  private readonly onData: MicrophoneParams["onData"];
  private readonly onLost: MicrophoneParams["onLost"];
  private connection: MicrophoneConnection | null = null;

  /** 끊을 때마다 1씩 늘어요. 권한을 받는 사이 끊겼는지 알아보는 데 써요 */
  private generation = 0;

  private pendingConnect: Promise<AnalyserNode | null> | null = null;

  constructor({ onData, onLost }: MicrophoneParams) {
    this.onData = onData;
    this.onLost = onLost;
  }

  /** 마이크를 받아 연결하고 분석기를 돌려줘요. 받지 못하면 `null`이에요 */
  connect() {
    this.pendingConnect ??= this.open().finally(() => {
      this.pendingConnect = null;
    });

    return this.pendingConnect;
  }

  isLive() {
    return this.connection?.isLive() ?? false;
  }

  pause() {
    this.connection?.pause();
  }

  resume() {
    this.connection?.resume();
  }

  disconnect() {
    this.generation += 1;
    this.connection?.close();
    this.connection = null;
  }

  private async open() {
    const requestedGeneration = this.generation;

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });

      if (requestedGeneration !== this.generation) {
        stopTracks(stream);
        return null;
      }

      this.connection?.close();
      this.connection = new MicrophoneConnection({
        stream,
        onData: this.onData,
        onLost: this.onLost,
      });

      return this.connection.analyser;
    } catch {
      // 권한 거부나 장치 없음은 연결하지 않고 `null`만 돌려줘요
      return null;
    }
  }
}
