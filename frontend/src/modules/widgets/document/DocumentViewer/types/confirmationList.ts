/** 확인 대상 목록의 조회 상태. 확인한 사람 카드는 `ready`일 때만 목록을 그려요 */
export type ConfirmationListStatus = "loading" | "failed" | "ready";
