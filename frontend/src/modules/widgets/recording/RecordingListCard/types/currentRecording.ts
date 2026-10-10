/** 홈 카드가 보여 주는 녹음 상태. 문서 정리 중·실패는 아직 다루지 않아요 */
export type CurrentRecordingStatus = "recording" | "paused";

/** 내가 지금 진행 중인 녹음 하나 */
export interface CurrentRecording {
  status: CurrentRecordingStatus;
  /** 녹음 이름. 예: `유월 님의 녹음` */
  title: string;
  /** 녹음한 시간. 예: `12:48` */
  elapsedTime: string;
}
