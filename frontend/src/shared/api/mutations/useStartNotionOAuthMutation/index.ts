import { startNotionOAuthApi } from "@api/fetch/api/v1/workspaces/[workspaceId]/notionOauthAuthorizations";
import { useMutation } from "@tanstack/react-query";

/**
 * 워크스페이스의 Notion OAuth 연결을 시작하는 뮤테이션 훅.
 *
 * 요청 본문이 없어 `mutate(workspaceId)`로 워크스페이스 ID만 넘겨요.
 * 응답의 `authorizationUrl`로 페이지를 통째로 이동시키는 일은 쓰는 쪽이 맡아요.
 * 연결 결과는 Notion에서 돌아온 뒤 서버가 판정하므로 여기서 무효화할 쿼리는 없어요.
 */
const useStartNotionOAuthMutation = () => {
  return useMutation({
    mutationFn: (workspaceId: number) => startNotionOAuthApi(workspaceId),
  });
};

export default useStartNotionOAuthMutation;
