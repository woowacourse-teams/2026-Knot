/** 녹음한 조각을 모아 두는 곳. 업로드를 붙이기 전이라 모으기만 하고, 녹음을 끝내면 비워요 */
export default class RecordedAudio {
  private chunks: Blob[] = [];

  append(chunk: Blob) {
    this.chunks.push(chunk);
  }

  clear() {
    this.chunks = [];
  }
}
