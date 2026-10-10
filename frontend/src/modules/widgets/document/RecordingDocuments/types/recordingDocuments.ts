/**
 * 정리 화면이 그릴 상태.
 *
 * `completed` · `recording`은 이 화면에 머물 이유가 없어 다른 화면으로 보내는 상태예요.
 */
export type RecordingDocumentsStatus =
  | "loading"
  | "loadFailed"
  | "organizing"
  | "noContent"
  | "failed"
  | "completed"
  | "recording";
