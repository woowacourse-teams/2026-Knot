import type { NotionOAuthAuthorizationResponse } from "@api/mock/types/notionConnection";

// 노션 연동 카드 테스트가 이 URL로 페이지 이동이 일어나는지 확인해요.
// 개발 서버(API_MOCKING)에서 실제로 이동하면 Notion이 가짜 client_id를 거절하니 눈으로 확인만 하세요
export const notionOAuthAuthorizationResponse = {
  authorizationUrl:
    "https://api.notion.com/v1/oauth/authorize?client_id=mock-client-id&response_type=code&owner=user&state=mock-state",
} satisfies NotionOAuthAuthorizationResponse;
