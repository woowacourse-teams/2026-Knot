/**
 * Notion 연결 상태별 안내 문구. 연결 상태 조회 응답의 `status`를 그대로 키로 써요.
 *
 * - `NOT_CONNECTED`: 연결한 적이 없거나 끊긴 상태
 * - `CONNECTED`: 연결됨
 * - `REAUTH_REQUIRED`: 연결을 승인한 멤버가 더 이상 OWNER가 아니라 OWNER가 다시 연결해야 하는 상태
 */
export const NOTION_CONNECTION_STATUS_LABEL = {
  NOT_CONNECTED: "노션이 연결되어 있지 않아요",
  CONNECTED: "노션이 연결되어 있어요",
  REAUTH_REQUIRED: "노션을 다시 연결해야 해요",
} as const;

/** 연결 상태 조회 자체가 실패했을 때 보여줄 안내 문구. */
export const NOTION_CONNECTION_STATUS_UNKNOWN_MESSAGE =
  "노션 연결 상태를 확인하지 못했어요";

/** 완료·실패 안내를 보여준 뒤 기본 상태로 돌아가기까지의 시간(ms). */
export const SYNC_RESULT_RESET_DELAY_MS = 2000;

/** 서버가 실패 사유를 주지 못했을 때(시작 요청 자체가 실패) 보여줄 안내 문구. */
export const SYNC_FAILED_MESSAGE =
  "동기화에 실패했어요. 잠시 후 다시 시도해 주세요";
