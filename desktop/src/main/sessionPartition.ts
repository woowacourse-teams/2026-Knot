/**
 * 웹 세션 파티션 이름.
 *
 * 메인 창·자식 창이 쓰는 영속 세션이다(기획서 5.1 — 재실행해도 OAuth 세션 쿠키가 남는다).
 * 창(`windows.ts`)과 인증 접착층(`auth/desktopAuth.ts`)이 같은 값을 보도록 한 곳에 둔다.
 */
export const SESSION_PARTITION = "persist:knot";
