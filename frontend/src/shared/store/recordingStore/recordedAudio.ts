import { RECORDING_AUDIO_TYPE } from "./microphoneConnection";

/** 녹음한 조각을 모아 두는 곳. 녹음을 끝내면 한 파일로 묶어 올리고, 버리면 비워요 */
export default class RecordedAudio {
  private chunks: Blob[] = [];

  append(chunk: Blob) {
    this.chunks.push(chunk);
  }

  /** 지금까지 모은 조각을 서버가 받는 형식의 파일 하나로 묶어요 */
  toBlob() {
    return new Blob(this.chunks, { type: RECORDING_AUDIO_TYPE });
  }

  clear() {
    this.chunks = [];
  }
}
