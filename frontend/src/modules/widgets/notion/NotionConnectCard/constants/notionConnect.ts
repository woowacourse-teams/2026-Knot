/**
 * Notion OAuth를 마친 서버가 이 화면으로 돌려보낼 때 붙이는 쿼리 파라미터.
 *
 * `GET /api/v1/notion/oauth/callback`이 `/workspace/:workspaceId/notion-connection?result=connected|failed`로
 * 303 리다이렉트해요. 값은 백엔드 `NotionOAuthProperties`가 정해요.
 */
export const NOTION_CONNECTION_RESULT_PARAM = "result";

export const NOTION_CONNECTION_RESULT = {
  connected: "connected",
  failed: "failed",
} as const;

/**
 * 연결 시작이 실패했을 때 연결·다시 시도 버튼 아래에 띄울 문구.
 *
 * - `forbidden`: 연결 시작이 403. CSRF 실패는 httpClient가 한 번 재시도하므로 남는 403은 OWNER가 아닌 경우예요.
 * - `unknown`: 그 외(네트워크·5xx·timeout).
 *
 * 인증이 풀린 401은 로그인으로, 없는 워크스페이스 404는 선택 화면으로 보냅니다.
 * Notion에서 거절·취소해 `?result=failed`로 돌아온 경우는 문구가 아니라 실패 화면 전체로 안내해요.
 */
export const NOTION_CONNECT_ERROR_MESSAGE = {
  forbidden: "워크스페이스 소유자만 노션을 연결할 수 있어요.",
  unknown: "노션 연결을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.",
} as const;
