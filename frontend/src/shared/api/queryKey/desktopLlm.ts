/**
 * 데스크톱 셸 Claude 구독 쿼리 키
 *
 * - `status` 내 구독 로그인 상태(preload `llm.getStatus`). 서버 API가 아니라 셸에서 읽어요
 * - `settings` 모델·effort와 허용 목록(preload `llm.getSettings`)
 */
export const desktopLlmKeys = {
  all: ["desktopLlm"] as const,

  status: () => [...desktopLlmKeys.all, "status"] as const,
  settings: () => [...desktopLlmKeys.all, "settings"] as const,
};
