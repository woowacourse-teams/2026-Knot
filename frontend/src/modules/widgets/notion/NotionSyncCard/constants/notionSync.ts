// TODO(마지막 동기화 시각 API 미정): 시각을 주는 API 연결 후 응답으로 교체

/** 마지막 동기화 시각 안내. Figma 예시 문구를 그대로 둔 임시 값이에요. */
export const LAST_SYNCED_AT_LABEL = "어제 오후 3:12에 동기화";

/** 완료·실패 안내를 보여준 뒤 기본 상태로 돌아가기까지의 시간(ms). */
export const SYNC_RESULT_RESET_DELAY_MS = 2000;

/** 서버가 실패 사유를 주지 못했을 때(시작 요청 자체가 실패) 보여줄 안내 문구. */
export const SYNC_FAILED_MESSAGE =
  "동기화에 실패했어요. 잠시 후 다시 시도해 주세요";
