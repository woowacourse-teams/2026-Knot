/**
 * 정리 화면이 그릴 상태.
 *
 * 서버가 주는 녹음 상태(`recording.status`)와 다른 값이에요.
 * 조회 결과와 오류까지 해석한 값이라, 서버 상태에는 없는 `loading` · `loadFailed`가 있어요.
 * `completed` · `recording`은 이 화면에 머물 이유가 없어 다른 화면으로 보내는 상태예요.
 */
export type RecordingDocumentsViewStatus =
  | "loading"
  | "loadFailed"
  | "organizing"
  | "noContent"
  | "failed"
  | "completed"
  | "recording";
